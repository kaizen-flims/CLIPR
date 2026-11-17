package com.clipr.app

import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.domain.clipping.ClipAdjustmentValidator
import com.clipr.app.media.analysis.AudioEnergyAndTranscriptionAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Deterministic Phase 5 unit tests verifying:
 * 1. 16-bit PCM RMS acoustic energy calculation and distinct peak selection
 * 2. User start/end trim adjustment clamping and automatic `isUserEdited = true` locking
 * 3. Approval toggle lock behavior
 */
class AiClippingPhase5Test {

    @Test
    fun pcm16RmsCalculator_computesAccurateNormalizedEnergy() {
        val sampleCount = 100
        val buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) {
            buffer.putShort(16384.toShort()) // 0.5 amplitude
        }
        buffer.flip()

        val rms = AudioEnergyAndTranscriptionAnalyzer.computePcm16Rms(buffer, 0, sampleCount * 2)
        assertEquals(0.5f, rms, 0.02f)
    }

    @Test
    fun selectTopDistinctPeaks_enforcesMinimumSeparationBetweenSpikes() {
        val buckets = mapOf(
            10_000L to 0.85f,
            12_000L to 0.80f, // within 8s of 10_000L -> must be skipped
            35_000L to 0.72f,
            70_000L to 0.45f
        )

        val peaks = AudioEnergyAndTranscriptionAnalyzer.selectTopDistinctPeaks(
            bucketRms = buckets,
            maxSpikes = 5,
            minSeparationMs = 8_000L
        )

        assertEquals(3, peaks.size)
        assertEquals(listOf(10_000L, 35_000L, 70_000L), peaks.map { it.timestampMs })
    }

    @Test
    fun clipAdjustmentValidator_clampsTrimmedBoundsAndLocksUserEdits() {
        val original = ClipCandidate(
            id = "clip_1",
            clipNumber = 1,
            title = "Opening Rush",
            startMs = 2_000L,
            endMs = 22_000L,
            rationale = "High action",
            hookScore = 92,
            categoryTag = "Gameplay",
            isApproved = false,
            isUserEdited = false
        )

        // Attempt to trim startMs below 0 (-5,000 ms)
        val trimmed = ClipAdjustmentValidator.adjustClipBounds(
            clip = original,
            deltaStartMs = -5_000L,
            deltaEndMs = 3_000L,
            sourceDurationMs = 60_000L
        )

        assertEquals(0L, trimmed.startMs)
        assertEquals(25_000L, trimmed.endMs)
        assertTrue(trimmed.isUserEdited)

        val approved = ClipAdjustmentValidator.toggleClipApproval(trimmed)
        assertTrue(approved.isApproved)
    }
}
