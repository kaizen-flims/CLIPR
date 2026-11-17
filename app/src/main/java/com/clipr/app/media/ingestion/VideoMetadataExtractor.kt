package com.clipr.app.media.ingestion

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.clipr.app.core.model.SourceIngestionOutcome
import com.clipr.app.core.model.VideoSourceMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Extracts real container, track, codec, dimension, frame-rate, and audio parameters
 * from a local SAF `content://` URI or cached file using Android's `MediaMetadataRetriever`
 * and `MediaExtractor` (Section 03 & Section 05).
 */
class VideoMetadataExtractor(
    private val appContext: Context
) {

    suspend fun extractFromSafUri(
        uri: Uri,
        isRemoteAuthorizedCopy: Boolean = false
    ): SourceIngestionOutcome = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        var displayName = "imported_video.mp4"
        var fileSizeBytes = 0L

        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx)
                        }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            fileSizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
        }

        val resolvedMime = resolver.getType(uri)?.lowercase(Locale.US)
            ?: inferMimeFromFileName(displayName)

        if (!SourceUrlPolicyValidator.isSupportedContainerMime(resolvedMime)) {
            return@withContext SourceIngestionOutcome.UnsupportedFormat(
                formatDetail = resolvedMime,
                message = "Unsupported container format ($resolvedMime). Import an MP4, MOV, WebM, or MKV video file."
            )
        }

        extractMediaTracksAndRetriever(
            uri = uri,
            displayName = displayName,
            mimeType = resolvedMime,
            fileSizeBytes = fileSizeBytes,
            isRemoteAuthorizedCopy = isRemoteAuthorizedCopy
        )
    }

    suspend fun extractFromLocalFile(
        file: File,
        isRemoteAuthorizedCopy: Boolean = false
    ): SourceIngestionOutcome = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext SourceIngestionOutcome.UnreadableSource(
                "Selected video file does not exist or is empty (0 bytes)."
            )
        }

        val mimeType = inferMimeFromFileName(file.name)
        if (!SourceUrlPolicyValidator.isSupportedContainerMime(mimeType)) {
            return@withContext SourceIngestionOutcome.UnsupportedFormat(
                formatDetail = mimeType,
                message = "Unsupported video file extension in '${file.name}'. Import an MP4, MOV, WebM, or MKV file."
            )
        }

        extractMediaTracksAndRetriever(
            uri = Uri.fromFile(file),
            displayName = file.name,
            mimeType = mimeType,
            fileSizeBytes = file.length(),
            isRemoteAuthorizedCopy = isRemoteAuthorizedCopy
        )
    }

    private fun extractMediaTracksAndRetriever(
        uri: Uri,
        displayName: String,
        mimeType: String,
        fileSizeBytes: Long,
        isRemoteAuthorizedCopy: Boolean
    ): SourceIngestionOutcome {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        return try {
            retriever.setDataSource(appContext, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val widthPx = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val heightPx = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull() ?: 0L
            val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()

            if (durationMs < MIN_SUPPORTED_DURATION_MS) {
                return SourceIngestionOutcome.UnreadableSource(
                    "Video is too short (${durationMs / 1000.0}s). Import a source video that is at least 5 seconds long."
                )
            }

            if (widthPx <= 0 || heightPx <= 0) {
                return SourceIngestionOutcome.UnsupportedFormat(
                    formatDetail = "No decodable video stream",
                    message = "This file contains no readable video dimensions. It may be audio-only or corrupted."
                )
            }

            var videoCodecMime = "video/avc"
            var trackFrameRate = captureFps ?: 30f
            var audioCodecMime: String? = null
            var audioSampleRateHz = 44100
            var audioChannelCount = 2

            runCatching {
                extractor.setDataSource(appContext, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (trackMime.startsWith("video/")) {
                        videoCodecMime = trackMime
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            trackFrameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                    } else if (trackMime.startsWith("audio/")) {
                        audioCodecMime = trackMime
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            audioSampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            audioChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }

            if (!SourceUrlPolicyValidator.isSupportedVideoCodec(videoCodecMime)) {
                return SourceIngestionOutcome.UnsupportedFormat(
                    formatDetail = videoCodecMime,
                    message = "Video codec '$videoCodecMime' is not supported by Android hardware decoders. Transcode to H.264, HEVC, or VP9."
                )
            }

            SourceIngestionOutcome.Success(
                VideoSourceMetadata(
                    sourceUri = uri.toString(),
                    displayName = displayName,
                    mimeType = mimeType,
                    durationMs = durationMs,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    rotationDegrees = rotationDegrees,
                    frameRate = trackFrameRate.coerceIn(1f, 240f),
                    bitrateBps = bitrateBps,
                    videoCodecMime = videoCodecMime,
                    audioCodecMime = audioCodecMime,
                    audioSampleRateHz = audioSampleRateHz,
                    audioChannelCount = audioChannelCount,
                    fileSizeBytes = fileSizeBytes,
                    isRemoteAuthorizedCopy = isRemoteAuthorizedCopy
                )
            )
        } catch (e: Exception) {
            SourceIngestionOutcome.UnreadableSource(
                "Could not read media headers from '$displayName': ${e.message ?: "Corrupt or inaccessible file."}"
            )
        } finally {
            runCatching { retriever.release() }
            runCatching { extractor.release() }
        }
    }

    private fun inferMimeFromFileName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
    }

    companion object {
        const val MIN_SUPPORTED_DURATION_MS = 5_000L
    }
}
