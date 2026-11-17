package com.clipr.app.core.model

import kotlinx.serialization.Serializable

/**
 * Represents the current state of the user's Bring-Your-Own-Key (BYOK) Gemini API configuration.
 */
enum class ApiKeyStatus(val label: String) {
    UNCONFIGURED("API Key Required"),
    VALIDATING("Validating Key..."),
    VALID("BYOK Active"),
    INVALID("Invalid Key"),
    QUOTA_EXCEEDED("Quota Reached"),
    NETWORK_ERROR("Network Error")
}

/**
 * Supported Gemini models selectable by the user in Settings and Onboarding.
 */
enum class GeminiModelOption(
    val modelId: String,
    val displayName: String,
    val description: String
) {
    GEMINI_2_5_FLASH(
        modelId = "gemini-2.5-flash",
        displayName = "Gemini 2.5 Flash",
        description = "Fast multimodal video highlight analysis and timestamp ranking (Default)."
    ),
    GEMINI_2_5_PRO(
        modelId = "gemini-2.5-pro",
        displayName = "Gemini 2.5 Pro",
        description = "Deep narrative, dialogue, and complex gameplay reaction reasoning."
    ),
    GEMINI_2_5_FLASH_LITE(
        modelId = "gemini-2.5-flash-lite",
        displayName = "Gemini 2.5 Flash-Lite",
        description = "High-efficiency, low-quota-consumption highlight detection for long streams."
    );

    companion object {
        fun fromModelId(id: String?): GeminiModelOption {
            return entries.firstOrNull { it.modelId == id } ?: GEMINI_2_5_FLASH
        }
    }
}

/**
 * Layout presets supported in the CLIPR Studio vertical 9:16 canvas.
 */
enum class StudioLayoutPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultUpperRatio: Float
) {
    FACECAM_GAMEPLAY_SPLIT(
        id = "facecam_gameplay_split",
        title = "Facecam + Gameplay",
        subtitle = "Upper facecam crop (35%) over lower gameplay action crop (65%)",
        defaultUpperRatio = 0.35f
    ),
    PORTRAIT_SUBJECT_CROP(
        id = "portrait_subject_crop",
        title = "9:16 Subject Tracking",
        subtitle = "Full-height vertical crop centered on active speaker or action",
        defaultUpperRatio = 0.0f
    ),
    CENTER_FIT_LETTERBOX(
        id = "center_fit_letterbox",
        title = "16:9 Cinema Fit",
        subtitle = "Full widescreen frame centered on Obsidian Black 9:16 canvas",
        defaultUpperRatio = 0.0f
    )
}

/**
 * Subtitle styling presets for animated word-level captions.
 */
enum class SubtitleStylePreset(
    val id: String,
    val displayName: String,
    val activeWordHex: Long,
    val baseTextHex: Long,
    val fontSizeSp: Int,
    val strokeWidthPx: Float
) {
    SIGNAL_PUNCH(
        id = "signal_punch",
        displayName = "Signal Punch",
        activeWordHex = CliprBrandConfig.SIGNAL_RED_ARGB,
        baseTextHex = CliprBrandConfig.PORCELAIN_WHITE_ARGB,
        fontSizeSp = 24,
        strokeWidthPx = 4f
    ),
    MONOLITH_CLEAN(
        id = "monolith_clean",
        displayName = "Monolith Clean",
        activeWordHex = CliprBrandConfig.PORCELAIN_WHITE_ARGB,
        baseTextHex = CliprBrandConfig.SECONDARY_GREY_ARGB,
        fontSizeSp = 20,
        strokeWidthPx = 3f
    ),
    OBSIDIAN_BOX(
        id = "obsidian_box",
        displayName = "Obsidian Box",
        activeWordHex = CliprBrandConfig.SIGNAL_RED_ARGB,
        baseTextHex = CliprBrandConfig.PORCELAIN_WHITE_ARGB,
        fontSizeSp = 22,
        strokeWidthPx = 0f
    )
}

/**
 * Structured highlight candidate returned by Gemini AI analysis and validated against source duration.
 */
@Serializable
data class ClipCandidate(
    val id: String,
    val clipNumber: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val rationale: String,
    val hookScore: Int,
    val categoryTag: String,
    val suggestedLayoutId: String = StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT.id,
    val isApproved: Boolean = false,
    val isUserEdited: Boolean = false
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Summary of a clipping project stored in app-private database/storage.
 */
data class ProjectSummary(
    val projectId: String,
    val title: String,
    val sourceFileName: String,
    val sourceDurationMs: Long,
    val resolutionLabel: String,
    val clipCount: Int,
    val lastModifiedLabel: String,
    val statusLabel: String
)

/**
 * Metadata for a locally rendered 1080x1920 MP4 Short.
 */
data class ExportedShort(
    val exportId: String,
    val projectId: String,
    val title: String,
    val outputFilePath: String,
    val durationMs: Long,
    val fileSizeMb: Float,
    val exportedAtLabel: String,
    val resolution: String = "1080×1920 · 9:16"
)

/**
 * Export configuration enforced by the Studio and Rendering Engine.
 */
data class ExportConfig(
    val widthPx: Int = CliprBrandConfig.EXPORT_WIDTH_PX,
    val heightPx: Int = CliprBrandConfig.EXPORT_HEIGHT_PX,
    val frameRate: Int = 30,
    val videoBitrateMbps: Int = 8,
    val audioBitrateKbps: Int = 128,
    val preferHardwareEncoding: Boolean = true,
    val allowCpuFallback: Boolean = true
)
