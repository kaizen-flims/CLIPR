package com.clipr.app.core.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Comprehensive metadata extracted from an imported video source via
 * Android's MediaMetadataRetriever, MediaExtractor, and ContentResolver (Section 03 & Section 05).
 */
@Serializable
data class VideoSourceMetadata(
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int = 0,
    val frameRate: Float = 30f,
    val bitrateBps: Long = 0L,
    val videoCodecMime: String = "video/avc",
    val audioCodecMime: String? = "audio/mp4a-latm",
    val audioSampleRateHz: Int = 44100,
    val audioChannelCount: Int = 2,
    val fileSizeBytes: Long = 0L,
    val isRemoteAuthorizedCopy: Boolean = false
) {
    val effectiveWidthPx: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) heightPx else widthPx

    val effectiveHeightPx: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) widthPx else heightPx

    val resolutionLabel: String
        get() = "${effectiveWidthPx}×${effectiveHeightPx}"

    val aspectRatioLabel: String
        get() {
            if (effectiveWidthPx <= 0 || effectiveHeightPx <= 0) return "Unknown"
            val ratio = effectiveWidthPx.toDouble() / effectiveHeightPx.toDouble()
            return when {
                kotlin.math.abs(ratio - (16.0 / 9.0)) < 0.08 -> "16:9"
                kotlin.math.abs(ratio - (9.0 / 16.0)) < 0.08 -> "9:16"
                kotlin.math.abs(ratio - (4.0 / 3.0)) < 0.08 -> "4:3"
                kotlin.math.abs(ratio - 1.0) < 0.05 -> "1:1"
                kotlin.math.abs(ratio - (21.0 / 9.0)) < 0.12 -> "21:9"
                else -> String.format(Locale.US, "%.2f:1", ratio)
            }
        }

    val formattedDuration: String
        get() = formatDurationMs(durationMs)

    val formattedFileSize: String
        get() = formatFileSizeBytes(fileSizeBytes)

    val hasAudioTrack: Boolean
        get() = !audioCodecMime.isNullOrBlank()

    /**
     * Long-form videos (> 3 minutes or > 40 MB) use CLIPR's 360p @ 1fps proxy extractor
     * before uploading to the Gemini Files API.
     */
    val requiresProxyDownsample: Boolean
        get() = fileSizeBytes > PROXY_THRESHOLD_BYTES || durationMs > PROXY_THRESHOLD_DURATION_MS

    val shortCodecSummary: String
        get() {
            val vName = videoCodecMime.substringAfter('/').uppercase(Locale.US)
            val aName = audioCodecMime?.substringAfter('/')?.uppercase(Locale.US) ?: "NO AUDIO"
            return "$vName + $aName"
        }

    companion object {
        const val PROXY_THRESHOLD_BYTES: Long = 40L * 1024L * 1024L // 40 MB
        const val PROXY_THRESHOLD_DURATION_MS: Long = 180_000L       // 3 minutes

        fun formatDurationMs(durationMs: Long): String {
            val clamped = durationMs.coerceAtLeast(0L)
            val totalSeconds = clamped / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        fun formatFileSizeBytes(bytes: Long): String {
            if (bytes <= 0L) return "Unknown size"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
    }
}

/**
 * Strongly-typed outcome of video ingestion and validation (Section 05).
 */
sealed class SourceIngestionOutcome {
    data class Success(val metadata: VideoSourceMetadata) : SourceIngestionOutcome()

    /**
     * Triggered when the user enters a YouTube, Twitch, or other restricted platform URL.
     * Directs the user to import a permitted local copy via SAF.
     */
    data class UnauthorizedPlatformUrl(
        val platformName: String,
        val message: String
    ) : SourceIngestionOutcome()

    /**
     * Triggered when the container, MIME type, or video codec is unsupported.
     */
    data class UnsupportedFormat(
        val formatDetail: String,
        val message: String
    ) : SourceIngestionOutcome()

    /**
     * Triggered when a file is corrupt, has 0ms duration, or cannot be opened.
     */
    data class UnreadableSource(
        val message: String
    ) : SourceIngestionOutcome()
}
