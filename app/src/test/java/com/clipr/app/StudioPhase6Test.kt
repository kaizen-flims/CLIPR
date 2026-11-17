package com.clipr.app

import com.clipr.app.core.model.NormalizedCropRect
import com.clipr.app.media.subtitles.WhisperAndGeminiSubtitleEngine
import com.clipr.app.media.vision.FaceCamDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic Phase 6 unit tests verifying:
 * 1. Normalized UV crop pan and zoom boundary clamping
 * 2. ML Kit face bounding box classification (corner facecam vs center speaker)
 * 3. Word-level subtitle timestamp alignment and active word phrase lookup
 */
class StudioPhase6Test {

    @Test
    fun normalizedCropRect_panAndZoomStayWithinUnitSquare() {
        val initial = NormalizedCropRect.DEFAULT_FACECAM_CORNER
        val pannedLeftTop = initial.panBy(-0.50f, -0.50f)
        assertEquals(0.0f, pannedLeftTop.left, 0.001f)
        assertEquals(0.0f, pannedLeftTop.top, 0.001f)

        val pannedRightBottom = initial.panBy(2.0f, 2.0f)
        assertEquals(1.0f, pannedRightBottom.right, 0.001f)
        assertEquals(1.0f, pannedRightBottom.bottom, 0.001f)

        val zoomedIn = initial.zoom(2.0f)
        assertTrue(zoomedIn.width < initial.width)
        assertTrue(zoomedIn.height < initial.height)
    }

    @Test
    fun faceCamDetector_identifiesTopLeftCornerFacecamOverlay() {
        val cornerFace = FaceCamDetector.normalizeFaceBoundingBox(
            leftPx = 120f,
            topPx = 80f,
            rightPx = 300f,
            bottomPx = 260f,
            frameWidthPx = 1920f,
            frameHeightPx = 1080f
        )
        val suggestion = FaceCamDetector.computeReframeSuggestionFromBoxes(listOf(cornerFace))
        assertTrue(suggestion.isCornerOverlayFacecam)
        assertTrue(suggestion.facecamCropRect.centerX < 0.30f)
    }

    @Test
    fun whisperAndGeminiSubtitleEngine_generatesWordTimingsAndFindsActiveWord() {
        val words = WhisperAndGeminiSubtitleEngine.generateWordTimingsFromText(
            transcriptText = "From long to legendary right now on stream",
            clipDurationMs = 8_000L
        )
        assertEquals(8, words.size)
        assertEquals("FROM", words.first().word)
        assertEquals("STREAM", words.last().word)

        val midPhrase = WhisperAndGeminiSubtitleEngine.findActivePhraseAtPosition(
            words = words,
            positionMs = words[2].relativeStartMs + 50L,
            wordsPerGroup = 4
        )
        assertNotNull(midPhrase)
        assertEquals(0, midPhrase!!.groupIndex)
        assertEquals(2, midPhrase.activeWordIndexInGroup)
    }
}
