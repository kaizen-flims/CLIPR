package com.clipr.app.media.analysis

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.clipr.app.CliprApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Generates a lightweight 360p H.264 + AAC proxy MP4 in app-private cache using AndroidX Media3 Transformer.
 * Ensures long-form 1080p/4K gaming recordings upload rapidly to the Gemini Files API.
 */
class ProxyMediaGenerator(
    private val appContext: Context
) {

    suspend fun generate360pProxy(
        sourceUri: Uri
    ): Result<File> = withContext(Dispatchers.Main) {
        val workDir = File(appContext.cacheDir, CliprApplication.WORK_CACHE_DIR_NAME).apply { mkdirs() }
        val outputFile = File(workDir, "gemini_proxy_${System.currentTimeMillis()}.mp4")

        suspendCancellableCoroutine { continuation ->
            val mediaItem = MediaItem.fromUri(sourceUri)
            val effects = Effects(
                emptyList(),
                listOf(Presentation.createForHeight(360))
            )
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(outputFile))
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    runCatching { outputFile.delete() }
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exportException))
                    }
                }
            }

            val transformer = Transformer.Builder(appContext)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            transformer.start(editedMediaItem, outputFile.absolutePath)

            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
                runCatching { outputFile.delete() }
            }
        }
    }
}
