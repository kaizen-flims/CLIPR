package com.clipr.app.core.model

/**
 * Locked brand constants, color hex values, and target export specifications for CLIPR.
 * These constants are verified by automated unit tests and must not be reinterpreted.
 */
object CliprBrandConfig {
    const val PRODUCT_NAME = "CLIPR"
    const val TAGLINE = "From Long to Legendary."

    // Locked Brand Palette (ARGB Longs)
    const val OBSIDIAN_BLACK_ARGB: Long = 0xFF080808L
    const val GRAPHITE_ARGB: Long = 0xFF171717L
    const val PORCELAIN_WHITE_ARGB: Long = 0xFFF5F5F5L
    const val SIGNAL_RED_ARGB: Long = 0xFFE53935L
    const val SECONDARY_GREY_ARGB: Long = 0xFFA3A3A3L

    // Structural Hairline & Surface Elevation Tokens (Flat, Non-Glowing)
    const val HAIRLINE_DIVIDER_ARGB: Long = 0xFF262626L
    const val GRAPHITE_ELEVATED_ARGB: Long = 0xFF1F1F1FL

    // Locked Typography Family Name
    const val PRIMARY_FONT_FAMILY = "Plus Jakarta Sans"

    // Locked Target Export Specification (Section 06)
    const val EXPORT_WIDTH_PX = 1080
    const val EXPORT_HEIGHT_PX = 1920
    const val EXPORT_ASPECT_RATIO_LABEL = "9:16"
    const val EXPORT_CONTAINER_FORMAT = "MP4"
    const val EXPORT_VIDEO_MIME = "video/avc"        // H.264
    const val EXPORT_AUDIO_MIME = "audio/mp4a-latm"  // AAC
    const val TARGET_DISTINCT_CLIP_COUNT = 2
}
