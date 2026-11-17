package com.clipr.app

import com.clipr.app.core.model.ExportConfig
import com.clipr.app.media.render.RenderProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic Phase 7 unit tests verifying:
 * 1. Real-time ETA calculation from observed elapsed time and percentage
 * 2. Safe MP4 filename sanitization
 * 3. Compatibility fallback encoder configuration step-down
 */
class RenderingPhase7Test {

    @Test
    fun renderProgressCalculator_computesAccurateEtaAfterWarmup() {
        // Under 5% -> null ETA to avoid noisy initial estimates
        assertNull(RenderProgressCalculator.computeEstimatedRemainingSeconds(elapsedMs = 800L, progressPercent = 2))

        // At 50% after 10,000 ms (10s) -> remaining should be ~10s
        val etaHalfway = RenderProgressCalculator.computeEstimatedRemainingSeconds(
            elapsedMs = 10_000L,
            progressPercent = 50
        )
        assertNotNull(etaHalfway)
        assertEquals(10L, etaHalfway!!)
    }

    @Test
    fun renderProgressCalculator_sanitizesOutputFilename() {
        val fileName = RenderProgressCalculator.buildOutputFileName(
            clipTitle = "1v4 Sniper Clutch!! @Standoff #CODM",
            clipNumber = 1,
            epochMs = 1763380000000L
        )
        assertTrue(fileName.startsWith("CLIPR_1_1v4_sniper_clutch_standoff"))
        assertTrue(fileName.endsWith(".mp4"))
    }

    @Test
    fun renderProgressCalculator_stepsDownBitrateInCompatibilityFallback() {
        val primary = ExportConfig(frameRate = 60, videoBitrateMbps = 12)
        val fallback = RenderProgressCalculator.buildFallbackExportConfig(primary)
        assertEquals(30, fallback.frameRate)
        assertEquals(5, fallback.videoBitrateMbps)
    }
}
