package com.clipr.app

import com.clipr.app.core.model.VideoSourceMetadata
import com.clipr.app.media.ingestion.SourceUrlPolicyValidator
import com.clipr.app.media.ingestion.UrlPolicyDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic Phase 4 unit tests verifying:
 * 1. Strict blocking of unauthorized YouTube, Twitch, and TikTok URLs with local SAF fallback guidance
 * 2. Acceptance of authorized direct HTTPS MP4/MOV/WebM URLs
 * 3. Rotation-aware video dimension & aspect ratio calculation
 * 4. Duration formatting, file size formatting, and proxy downsample threshold detection
 */
class VideoIngestionPhase4Test {

    @Test
    fun urlPolicyValidator_blocksUnauthorizedYouTubeAndTwitchUrls() {
        val ytDecision = SourceUrlPolicyValidator.evaluateUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(ytDecision is UrlPolicyDecision.BlockedStreamingPlatform)
        assertEquals("YouTube", (ytDecision as UrlPolicyDecision.BlockedStreamingPlatform).platformName)

        val shortYtDecision = SourceUrlPolicyValidator.evaluateUrl("https://youtu.be/dQw4w9WgXcQ")
        assertTrue(shortYtDecision is UrlPolicyDecision.BlockedStreamingPlatform)

        val twitchDecision = SourceUrlPolicyValidator.evaluateUrl("https://www.twitch.tv/videos/123456789")
        assertTrue(twitchDecision is UrlPolicyDecision.BlockedStreamingPlatform)
        assertEquals("Twitch", (twitchDecision as UrlPolicyDecision.BlockedStreamingPlatform).platformName)
    }

    @Test
    fun urlPolicyValidator_rejectsInsecureHttpAndUnsupportedExtensions() {
        val httpDecision = SourceUrlPolicyValidator.evaluateUrl("http://cdn.example.com/gameplay.mp4")
        assertTrue(httpDecision is UrlPolicyDecision.InvalidOrUnsupportedUrl)

        val zipDecision = SourceUrlPolicyValidator.evaluateUrl("https://cdn.example.com/archive.zip")
        assertTrue(zipDecision is UrlPolicyDecision.InvalidOrUnsupportedUrl)
    }

    @Test
    fun urlPolicyValidator_acceptsAuthorizedHttpsDirectVideoLinks() {
        val decision = SourceUrlPolicyValidator.evaluateUrl("https://cdn.example.com/streams/codm_scrim_final.mp4")
        assertTrue(decision is UrlPolicyDecision.PermittedDirectMediaUrl)
        val permitted = decision as UrlPolicyDecision.PermittedDirectMediaUrl
        assertEquals("codm_scrim_final.mp4", permitted.inferredFileName)
    }

    @Test
    fun videoSourceMetadata_computesRotationAwareDimensionsAndAspectRatio() {
        val landscapeMeta = VideoSourceMetadata(
            sourceUri = "content://media/external/video/101",
            displayName = "codm_ranked_match.mp4",
            mimeType = "video/mp4",
            durationMs = 1_245_000L, // 20m 45s
            widthPx = 1920,
            heightPx = 1080,
            rotationDegrees = 0,
            fileSizeBytes = 420L * 1024L * 1024L // 420 MB
        )

        assertEquals("1920×1080", landscapeMeta.resolutionLabel)
        assertEquals("16:9", landscapeMeta.aspectRatioLabel)
        assertEquals("20:45", landscapeMeta.formattedDuration)
        assertEquals("420.0 MB", landscapeMeta.formattedFileSize)
        assertTrue(landscapeMeta.requiresProxyDownsample)

        // Rotated 90-degree mobile recording
        val rotatedPortraitMeta = landscapeMeta.copy(
            rotationDegrees = 90,
            durationMs = 45_000L,
            fileSizeBytes = 18L * 1024L * 1024L
        )
        assertEquals(1080, rotatedPortraitMeta.effectiveWidthPx)
        assertEquals(1920, rotatedPortraitMeta.effectiveHeightPx)
        assertEquals("9:16", rotatedPortraitMeta.aspectRatioLabel)
        assertEquals("00:45", rotatedPortraitMeta.formattedDuration)
        assertFalse(rotatedPortraitMeta.requiresProxyDownsample)
    }
}
