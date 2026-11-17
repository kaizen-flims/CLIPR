package com.clipr.app

import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.ui.navigation.CliprDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic Phase 2 unit tests verifying the locked CLIPR brand identity,
 * export specifications, and 5 primary navigation destinations.
 */
class BrandAndNavigationFoundationTest {

    @Test
    fun lockedBrandColors_matchSpecificationExactly() {
        assertEquals(0xFF080808L, CliprBrandConfig.OBSIDIAN_BLACK_ARGB)
        assertEquals(0xFF171717L, CliprBrandConfig.GRAPHITE_ARGB)
        assertEquals(0xFFF5F5F5L, CliprBrandConfig.PORCELAIN_WHITE_ARGB)
        assertEquals(0xFFE53935L, CliprBrandConfig.SIGNAL_RED_ARGB)
        assertEquals(0xFFA3A3A3L, CliprBrandConfig.SECONDARY_GREY_ARGB)
    }

    @Test
    fun lockedTypographyAndIdentity_matchSpecification() {
        assertEquals("CLIPR", CliprBrandConfig.PRODUCT_NAME)
        assertEquals("From Long to Legendary.", CliprBrandConfig.TAGLINE)
        assertEquals("Plus Jakarta Sans", CliprBrandConfig.PRIMARY_FONT_FAMILY)
    }

    @Test
    fun targetExportFormat_isVertical1080x1920Mp4AvcAac() {
        assertEquals(1080, CliprBrandConfig.EXPORT_WIDTH_PX)
        assertEquals(1920, CliprBrandConfig.EXPORT_HEIGHT_PX)
        assertEquals("9:16", CliprBrandConfig.EXPORT_ASPECT_RATIO_LABEL)
        assertEquals("MP4", CliprBrandConfig.EXPORT_CONTAINER_FORMAT)
        assertEquals("video/avc", CliprBrandConfig.EXPORT_VIDEO_MIME)
        assertEquals("audio/mp4a-latm", CliprBrandConfig.EXPORT_AUDIO_MIME)
        assertEquals(2, CliprBrandConfig.TARGET_DISTINCT_CLIP_COUNT)
    }

    @Test
    fun primaryNavigationDestinations_containExactFiveLockedScreens() {
        val destinations = CliprDestination.entries
        assertEquals(5, destinations.size)
        assertEquals(
            listOf("Home", "Discover", "Studio", "Library", "Settings"),
            destinations.map { it.label }
        )
        assertTrue(destinations.all { it.route.isNotBlank() })
    }
}
