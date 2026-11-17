package com.clipr.app.data.security

/**
 * Strict credential hygiene utility (Section 04 & Section 10).
 * Ensures Gemini API keys are never printed in logs, exception stack traces, or UI error banners.
 */
object LogRedactor {

    private val GEMINI_KEY_REGEX = Regex("AIza[0-9A-Za-z\\-_]{20,60}")
    private val HEADER_KEY_REGEX = Regex("(?i)(x-goog-api-key\\s*[:=]\\s*)([^\\s,;\\"']+)")
    private val QUERY_KEY_REGEX = Regex("(?i)([?&]key=)([^&\\s\\"']+)")

    /**
     * Scrubs any occurrence of a Gemini API key from diagnostic or error strings.
     */
    fun redact(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return input
            .replace(GEMINI_KEY_REGEX, "[REDACTED_GEMINI_KEY]")
            .replace(HEADER_KEY_REGEX, "$1[REDACTED_HEADER_KEY]")
            .replace(QUERY_KEY_REGEX, "$1[REDACTED_QUERY_KEY]")
    }

    /**
     * Validates basic syntactic plausibility of a user-entered Gemini API key before network calls.
     */
    fun isPlausibleGeminiApiKeyFormat(rawKey: String): Boolean {
        val trimmed = rawKey.trim()
        if (trimmed.length !in 30..64) return false
        if (!trimmed.startsWith("AIza")) return false
        return trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    /**
     * Produces a safe masked display preview (e.g., "AIza••••••••7F9a") for the Settings UI.
     */
    fun maskApiKey(rawKey: String?): String {
        val trimmed = rawKey?.trim().orEmpty()
        if (trimmed.length < 10) return "Not Configured"
        val prefix = trimmed.take(4)
        val suffix = trimmed.takeLast(4)
        return "$prefix••••••••$suffix"
    }
}
