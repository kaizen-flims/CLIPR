package com.clipr.app.data.remote.gemini

import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.data.security.LogRedactor
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Domain-level error hierarchy for Gemini API interactions (Section 04).
 * Never contains unredacted API keys.
 */
sealed class GeminiDomainError(
    val userTitle: String,
    val userMessage: String,
    val mappedKeyStatus: ApiKeyStatus
) {
    class InvalidApiKey(detail: String) : GeminiDomainError(
        userTitle = "Invalid Gemini API Key",
        userMessage = detail,
        mappedKeyStatus = ApiKeyStatus.INVALID
    )

    class QuotaExceeded(detail: String) : GeminiDomainError(
        userTitle = "Gemini API Quota Reached",
        userMessage = detail,
        mappedKeyStatus = ApiKeyStatus.QUOTA_EXCEEDED
    )

    class ModelUnavailable(detail: String) : GeminiDomainError(
        userTitle = "Selected Model Unavailable",
        userMessage = detail,
        mappedKeyStatus = ApiKeyStatus.VALID
    )

    class SafetyBlocked(detail: String) : GeminiDomainError(
        userTitle = "Content Blocked by Safety Filter",
        userMessage = detail,
        mappedKeyStatus = ApiKeyStatus.VALID
    )

    class NetworkFailure(detail: String) : GeminiDomainError(
        userTitle = "Network Connection Error",
        userMessage = detail,
        mappedKeyStatus = ApiKeyStatus.NETWORK_ERROR
    )

    class StructuredOutputParseError(detail: String) : GeminiDomainError(
        userTitle = "AI Response Format Error",
        userMessage = detail,
        mappedKeyStatus = ApiKeyStatus.VALID
    )
}

object GeminiErrorMapper {

    /**
     * Maps HTTP status codes and response bodies from `generativelanguage.googleapis.com`
     * into safe, redacted [GeminiDomainError] instances.
     */
    fun mapHttpError(httpCode: Int, rawBody: String?): GeminiDomainError {
        val safeSnippet = LogRedactor.redact(rawBody).take(220)
        return when (httpCode) {
            400 -> {
                if (safeSnippet.contains("API_KEY_INVALID", ignoreCase = true) ||
                    safeSnippet.contains("key not valid", ignoreCase = true)
                ) {
                    GeminiDomainError.InvalidApiKey(
                        "Google rejected this API key (HTTP 400). Verify that you copied the full key from Google AI Studio."
                    )
                } else {
                    GeminiDomainError.InvalidApiKey(
                        "Request rejected by Gemini API (HTTP 400). Check your key and model selection."
                    )
                }
            }
            401, 403 -> GeminiDomainError.InvalidApiKey(
                "Authentication failed (HTTP $httpCode). Ensure this Gemini API key is active and unrestricted for Generative Language API."
            )
            404 -> GeminiDomainError.ModelUnavailable(
                "The selected Gemini model is not available on your API tier (HTTP 404). Switch to Gemini 2.5 Flash in Settings."
            )
            429 -> GeminiDomainError.QuotaExceeded(
                "Your Gemini API key has hit its rate or token quota limit (HTTP 429). Wait a moment, switch to Gemini 2.5 Flash-Lite, or check your Google AI Studio billing/tier limits."
            )
            500, 502, 503, 504 -> GeminiDomainError.ModelUnavailable(
                "Google's Gemini service is temporarily overloaded (HTTP $httpCode). Retry in a few seconds."
            )
            else -> GeminiDomainError.NetworkFailure(
                "Unexpected HTTP $httpCode from Gemini API."
            )
        }
    }

    fun mapThrowable(throwable: Throwable): GeminiDomainError {
        val safeMsg = LogRedactor.redact(throwable.message)
        return when (throwable) {
            is UnknownHostException -> GeminiDomainError.NetworkFailure(
                "Unable to reach generativelanguage.googleapis.com. Check your internet connection."
            )
            is SocketTimeoutException -> GeminiDomainError.NetworkFailure(
                "Connection to Gemini API timed out while waiting for analysis. Try again or use a shorter proxy clip."
            )
            is IOException -> GeminiDomainError.NetworkFailure(
                "Network I/O failure while communicating with Gemini API: ${safeMsg.ifBlank { "Connection interrupted" }}"
            )
            else -> GeminiDomainError.StructuredOutputParseError(
                "Unexpected error during Gemini processing: ${safeMsg.ifBlank { throwable.javaClass.simpleName }}"
            )
        }
    }
}
