package com.clipr.app.media.render

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.clipr.app.CliprApplication
import com.clipr.app.data.local.CliprDatabase
import com.clipr.app.data.local.ExportedShortEntity
import com.clipr.app.media.effects.SplitReframeGlEffect
import com.clipr.app.media.subtitles.AnimatedWordSubtitleOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume

/**
 * Production on-device 1080×1920 (9:16) H.264 + AAC rendering engine built on AndroidX Media3 Transformer.
 * - Preserves exact audio-video presentation timestamps (PTS) across clipped windows.
 * - Uses hardware `MediaCodec` encoding by default with automatic compatibility fallback.
 * - Reports live progress percentage and measured ETA.
 * - Supports instant user cancellation and clean temp-file recovery (Section 07).
 */
class Media3RenderEngine(
    private val appContext: Context
) {
    private val cliprDao = CliprDatabase.getInstance(appContext).cliprDao()

    @Volatile
    private var activeTransformer: Transformer? = null

    suspend fun renderClipToShort(
        request: ClipRenderRequest,
        onProgress: (RenderProgressState) -> Unit
    ): RenderProgressState = withContext(Dispatchers.Main) {
        val exportsDir = File(appContext.filesDir, CliprApplication.EXPORTS_DIR_NAME).apply { mkdirs() }
        val epochNow = System.currentTimeMillis()
        val fileName = RenderProgressCalculator.buildOutputFileName(
            clipTitle = request.clip.title,
            clipNumber = request.clip.clipNumber,
            epochMs = epochNow
        )
        val outputFile = File(exportsDir, fileName)

        onProgress(
            RenderProgressState(
                status = RenderJobStatus.PREPARING,
                progressPercent = 0
            )
        )

        // Primary hardware pass
        val primaryOutcome = executeTransformerPass(
            request = request,
            outputFile = outputFile,
            useCompatibilityFallback = false,
            onProgress = onProgress
        )

        val finalOutcome = if (
            primaryOutcome.status == RenderJobStatus.FAILED &&
            request.exportConfig.allowCpuFallback
        ) {
            // Automatic fallback pass with stepped-down bitrate & encoder fallback enabled
            runCatching { outputFile.delete() }
            executeTransformerPass(
                request = request.copy(
                    exportConfig = RenderProgressCalculator.buildFallbackExportConfig(request.exportConfig)
                ),
                outputFile = outputFile,
                useCompatibilityFallback = true,
                onProgress = onProgress
            )
        } else {
            primaryOutcome
        }

        if (finalOutcome.status == RenderJobStatus.COMPLETED && outputFile.exists() && outputFile.length() > 0L) {
            val mediaStoreUri = publishToScopedMediaStore(outputFile, request.clip.title)
            val entity = ExportedShortEntity(
                exportId = "exp_$epochNow",
                projectId = request.projectId,
                clipId = request.clip.id,
                title = request.clip.title,
                outputFilePath = outputFile.absolutePath,
                mediaStoreUri = mediaStoreUri?.toString(),
                durationMs = request.clip.durationMs,
                fileSizeBytes = outputFile.length(),
                widthPx = request.exportConfig.widthPx,
                heightPx = request.exportConfig.heightPx,
                exportedAtEpochMs = epochNow
            )
            withContext(Dispatchers.IO) {
                cliprDao.insertExportedShort(entity)
            }
        }

        finalOutcome
    }

    fun cancelActiveRender() {
        runCatching { activeTransformer?.cancel() }
        activeTransformer = null
    }

    private suspend fun executeTransformerPass(
        request: ClipRenderRequest,
        outputFile: File,
        useCompatibilityFallback: Boolean,
        onProgress: (RenderProgressState) -> Unit
    ): RenderProgressState = withContext(Dispatchers.Main) {
        val startTimeMs = System.currentTimeMillis()
        val statusMode = if (useCompatibilityFallback) {
            RenderJobStatus.RENDERING_FALLBACK
        } else {
            RenderJobStatus.RENDERING_HARDWARE
        }

        try {
            suspendCancellableCoroutine { continuation ->
                val clippedMediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(request.sourceUri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(request.clip.startMs.coerceAtLeast(0L))
                            .setEndPositionMs(request.clip.endMs.coerceAtLeast(request.clip.startMs + 1_000L))
                            .build()
                    )
                    .build()

                val videoEffects = mutableListOf<Effect>()
                // 1. 9:16 Vertical Split / Subject Crop OpenGL Shader
                videoEffects.add(
                    SplitReframeGlEffect(
                        layoutPreset = request.layoutPreset,
                        upperSplitRatio = request.upperSplitRatio,
                        facecamCrop = request.facecamCrop,
                        gameplayCrop = request.gameplayCrop,
                        showSeparatorBorder = request.showSeparatorBorder,
                        outputWidthPx = request.exportConfig.widthPx,
                        outputHeightPx = request.exportConfig.heightPx
                    )
                )

                // 2. Animated Word-Level Burned-In Subtitle Overlay
                if (request.subtitleWords.isNotEmpty()) {
                    val subtitleOverlay: TextureOverlay = AnimatedWordSubtitleOverlay(
                        wordTimings = request.subtitleWords,
                        config = request.subtitleConfig,
                        canvasWidthPx = request.exportConfig.widthPx,
                        canvasHeightPx = request.exportConfig.heightPx
                    )
                    videoEffects.add(OverlayEffect(listOf(subtitleOverlay)))
                }

                val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()

                val videoEncoderSettings = VideoEncoderSettings.Builder()
                    .setBitrate(request.exportConfig.videoBitrateMbps * 1_000_000)
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(appContext)
                    .setRequestedVideoEncoderSettings(videoEncoderSettings)
                    .setEnableFallback(true)
                    .build()

                var pollJob: Job? = null

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        pollJob?.cancel()
                        activeTransformer = null
                        val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1_000L).coerceAtLeast(1L)
                        val doneState = RenderProgressState(
                            status = RenderJobStatus.COMPLETED,
                            progressPercent = 100,
                            elapsedSeconds = elapsedSec,
                            estimatedRemainingSeconds = 0L,
                            outputFilePath = outputFile.absolutePath,
                            usedFallbackProfile = useCompatibilityFallback
                        )
                        onProgress(doneState)
                        if (continuation.isActive) {
                            continuation.resume(doneState)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        pollJob?.cancel()
                        activeTransformer = null
                        runCatching { outputFile.delete() }
                        val failState = RenderProgressState(
                            status = RenderJobStatus.FAILED,
                            progressPercent = 0,
                            elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1_000L,
                            errorMessage = "Media3 export error (${exportException.errorCodeName}): ${exportException.message ?: "Codec failure"}",
                            usedFallbackProfile = useCompatibilityFallback
                        )
                        onProgress(failState)
                        if (continuation.isActive) {
                            continuation.resume(failState)
                        }
                    }
                }

                val transformer = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()

                activeTransformer = transformer
                transformer.start(editedMediaItem, outputFile.absolutePath)

                val progressHolder = ProgressHolder()
                pollJob = CoroutineScope(Dispatchers.Main).launch {
                    while (isActive) {
                        val state = transformer.getProgress(progressHolder)
                        val pct = if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            progressHolder.progress.coerceIn(0, 99)
                        } else {
                            0
                        }
                        val elapsedMs = System.currentTimeMillis() - startTimeMs
                        val etaSec = RenderProgressCalculator.computeEstimatedRemainingSeconds(elapsedMs, pct)
                        onProgress(
                            RenderProgressState(
                                status = statusMode,
                                progressPercent = pct,
                                elapsedSeconds = elapsedMs / 1_000L,
                                estimatedRemainingSeconds = etaSec,
                                usedFallbackProfile = useCompatibilityFallback
                            )
                        )
                        delay(200L)
                    }
                }

                continuation.invokeOnCancellation {
                    pollJob.cancel()
                    runCatching { transformer.cancel() }
                    activeTransformer = null
                    runCatching { outputFile.delete() }
                }
            }
        } catch (_: CancellationException) {
            runCatching { outputFile.delete() }
            val cancelledState = RenderProgressState(
                status = RenderJobStatus.CANCELLED,
                progressPercent = 0,
                errorMessage = "Export cancelled by user; temporary files cleaned up."
            )
            onProgress(cancelledState)
            cancelledState
        }
    }

    private suspend fun publishToScopedMediaStore(
        exportedFile: File,
        displayTitle: String
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, exportedFile.name)
                put(MediaStore.Video.Media.TITLE, displayTitle)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CLIPR")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, values) ?: return@runCatching null
            resolver.openOutputStream(itemUri)?.use { outStream ->
                FileInputStream(exportedFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
            }
            itemUri
        }.getOrNull()
    }
}
