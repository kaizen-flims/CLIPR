package com.clipr.app.media.render

import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.core.model.ExportConfig
import com.clipr.app.core.model.NormalizedCropRect
import com.clipr.app.core.model.StudioLayoutPreset
import com.clipr.app.core.model.SubtitleCustomConfig
import com.clipr.app.core.model.SubtitleWordTiming

enum class RenderJobStatus(val label: String) {
    IDLE("Ready to Export"),
    PREPARING("Preparing Media3 Pipeline..."),
    RENDERING_HARDWARE("Rendering 1080×1920 (Hardware MediaCodec)..."),
    RENDERING_FALLBACK("Rendering (Compatibility Encoder Fallback)..."),
    COMPLETED("Export Complete"),
    CANCELLED("Export Cancelled"),
    FAILED("Export Failed")
}

data class RenderProgressState(
    val status: RenderJobStatus = RenderJobStatus.IDLE,
    val progressPercent: Int = 0,
    val elapsedSeconds: Long = 0L,
    val estimatedRemainingSeconds: Long? = null,
    val outputFilePath: String? = null,
    val errorMessage: String? = null,
    val usedFallbackProfile: Boolean = false
)

data class ClipRenderRequest(
    val projectId: String,
    val sourceUri: String,
    val secondaryFacecamUri: String? = null,
    val clip: ClipCandidate,
    val layoutPreset: StudioLayoutPreset,
    val upperSplitRatio: Float,
    val showSeparatorBorder: Boolean,
    val facecamCrop: NormalizedCropRect,
    val gameplayCrop: NormalizedCropRect,
    val subtitleConfig: SubtitleCustomConfig,
    val subtitleWords: List<SubtitleWordTiming>,
    val exportConfig: ExportConfig
)

/**
 * Pure calculation and fallback utilities for on-device rendering (Section 07).
 * Never promises hardcoded 20-second renders; computes honest ETA from real device progress.
 */
object RenderProgressCalculator {

    /**
     * Computes estimated remaining time in seconds based on actual observed elapsed time and progress.
     * Returns `null` until progress reaches at least 5% and 1.5 seconds have elapsed to avoid wild spikes.
     */
    fun computeEstimatedRemainingSeconds(
        elapsedMs: Long,
        progressPercent: Int
    ): Long? {
        if (progressPercent !in 5..99 || elapsedMs < 1_500L) return null
        val totalEstimatedMs = (elapsedMs * 100L) / progressPercent.toLong()
        val remainingMs = (totalEstimatedMs - elapsedMs).coerceAtLeast(1_000L)
        return remainingMs / 1_000L
    }

    /**
     * Generates a safe, deterministic MP4 output filename for a rendered Short.
     */
    fun buildOutputFileName(clipTitle: String, clipNumber: Int, epochMs: Long): String {
        val sanitized = clipTitle
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "short" }
        return "CLIPR_${clipNumber}_${sanitized}_${epochMs}.mp4"
    }

    /**
     * Step-down compatibility configuration used when a device's primary hardware H.264 encoder
     * rejects an high-bitrate or high-framerate configuration (Section 07: CPU / Encoder fallback).
     */
    fun buildFallbackExportConfig(primary: ExportConfig): ExportConfig {
        return primary.copy(
            frameRate = 30,
            videoBitrateMbps = primary.videoBitrateMbps.coerceAtMost(5),
            audioBitrateKbps = 128
        )
    }
}
