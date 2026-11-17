package com.clipr.app.media.ingestion

import java.net.URI
import java.util.Locale

sealed class UrlPolicyDecision {
    data class PermittedDirectMediaUrl(
        val normalizedUrl: String,
        val inferredFileName: String
    ) : UrlPolicyDecision()

    data class BlockedStreamingPlatform(
        val platformName: String,
        val explanation: String
    ) : UrlPolicyDecision()

    data class InvalidOrUnsupportedUrl(
        val reason: String
    ) : UrlPolicyDecision()
}

/**
 * Enforces Section 05 Video Ingestion Rules:
 * - Explicitly blocks unauthorized YouTube, Twitch, TikTok, Instagram, and DRM streaming URLs.
 * - Directs users with platform links to import a permitted local copy via Storage Access Framework.
 * - Accepts authorized HTTPS direct video URLs (.mp4, .webm, .mov, .mkv, .m4v).
 */
object SourceUrlPolicyValidator {

    private val BLOCKED_PLATFORM_HOSTS = mapOf(
        "youtube.com" to "YouTube",
        "www.youtube.com" to "YouTube",
        "m.youtube.com" to "YouTube",
        "youtu.be" to "YouTube",
        "twitch.tv" to "Twitch",
        "www.twitch.tv" to "Twitch",
        "m.twitch.tv" to "Twitch",
        "clips.twitch.tv" to "Twitch",
        "tiktok.com" to "TikTok",
        "www.tiktok.com" to "TikTok",
        "instagram.com" to "Instagram",
        "www.instagram.com" to "Instagram",
        "vimeo.com" to "Vimeo",
        "dailymotion.com" to "Dailymotion",
        "netflix.com" to "Netflix"
    )

    val SUPPORTED_VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv")

    val SUPPORTED_VIDEO_MIMES = setOf(
        "video/mp4",
        "video/quicktime",
        "video/webm",
        "video/x-matroska",
        "video/3gpp"
    )

    val SUPPORTED_VIDEO_CODECS = setOf(
        "video/avc",
        "video/hevc",
        "video/vp8",
        "video/vp9",
        "video/av01",
        "video/mp4v-es"
    )

    fun evaluateUrl(rawInput: String): UrlPolicyDecision {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return UrlPolicyDecision.InvalidOrUnsupportedUrl("Enter a valid HTTPS direct video URL.")
        }

        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return UrlPolicyDecision.InvalidOrUnsupportedUrl("Malformed URL syntax.")

        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "https") {
            return UrlPolicyDecision.InvalidOrUnsupportedUrl(
                "Only secure HTTPS media URLs are supported (received '${scheme ?: "none"}')."
            )
        }

        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (host.isBlank()) {
            return UrlPolicyDecision.InvalidOrUnsupportedUrl("URL is missing a valid hostname.")
        }

        val matchedPlatform = BLOCKED_PLATFORM_HOSTS.entries.firstOrNull { (domain, _) ->
            host == domain || host.endsWith(".$domain")
        }?.value

        if (matchedPlatform != null) {
            return UrlPolicyDecision.BlockedStreamingPlatform(
                platformName = matchedPlatform,
                explanation = "CLIPR does not perform unauthorized $matchedPlatform downloading. Download your own permitted stream/video locally and tap 'Import Local Video (SAF)'."
            )
        }

        val path = uri.path.orEmpty().trimEnd('/')
        val lastSegment = path.substringAfterLast('/', "").ifBlank { "authorized_stream.mp4" }
        val ext = lastSegment.substringAfterLast('.', "").lowercase(Locale.US)

        if (ext.isNotEmpty() && ext !in SUPPORTED_VIDEO_EXTENSIONS) {
            return UrlPolicyDecision.InvalidOrUnsupportedUrl(
                "Unsupported URL file extension '.$ext'. Direct links must point to MP4, MOV, WebM, or MKV media."
            )
        }

        val safeFileName = if (ext in SUPPORTED_VIDEO_EXTENSIONS) {
            lastSegment
        } else {
            "${lastSegment.take(32)}.mp4"
        }

        return UrlPolicyDecision.PermittedDirectMediaUrl(
            normalizedUrl = trimmed,
            inferredFileName = safeFileName
        )
    }

    fun isSupportedContainerMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        val normalized = mimeType.lowercase(Locale.US).substringBefore(';').trim()
        return normalized in SUPPORTED_VIDEO_MIMES
    }

    fun isSupportedVideoCodec(codecMime: String?): Boolean {
        if (codecMime.isNullOrBlank()) return false
        return codecMime.lowercase(Locale.US).trim() in SUPPORTED_VIDEO_CODECS
    }
}
