package com.clipr.app.data.remote.gemini

import com.clipr.app.core.model.GeminiModelOption
import com.clipr.app.data.security.LogRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ValidatedKeyResult(
    val isSuccess: Boolean,
    val availableModelIds: List<String> = emptyList(),
    val verifiedModel: GeminiModelOption = GeminiModelOption.GEMINI_2_5_FLASH,
    val error: GeminiDomainError? = null
)

sealed class GeminiAnalysisResult {
    data class Success(val payload: GeminiHighlightAnalysisDto) : GeminiAnalysisResult()
    data class Failure(val error: GeminiDomainError) : GeminiAnalysisResult()
}

/**
 * Direct REST client for Google's Gemini API v1beta.
 * - Transmits the user's BYOK key strictly via the `x-goog-api-key` request header.
 * - Never logs the API key or sends requests to any proprietary proxy server.
 */
class GeminiRestClient(
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    private val json: Json = defaultJson()
) {

    /**
     * Validates a user-provided Gemini API key by querying `/v1beta/models`
     * and confirming support for `generateContent`.
     */
    suspend fun validateApiKey(
        rawApiKey: String,
        preferredModel: GeminiModelOption
    ): ValidatedKeyResult = withContext(Dispatchers.IO) {
        val trimmedKey = rawApiKey.trim()
        if (!LogRedactor.isPlausibleGeminiApiKeyFormat(trimmedKey)) {
            return@withContext ValidatedKeyResult(
                isSuccess = false,
                error = GeminiDomainError.InvalidApiKey(
                    "Invalid key format. Gemini API keys start with 'AIza' and are 39 characters long."
                )
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header(HEADER_API_KEY, trimmedKey)
            .header("Accept", "application/json")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ValidatedKeyResult(
                        isSuccess = false,
                        error = GeminiErrorMapper.mapHttpError(response.code, bodyString)
                    )
                }

                val parsed = json.decodeFromString<GeminiModelsListResponse>(bodyString)
                val generativeModels = parsed.models
                    .filter { "generateContent" in it.supportedGenerationMethods }
                    .map { it.name.removePrefix("models/") }

                if (generativeModels.isEmpty()) {
                    return@withContext ValidatedKeyResult(
                        isSuccess = false,
                        error = GeminiDomainError.InvalidApiKey(
                            "This API key succeeded but has no generative models enabled."
                        )
                    )
                }

                ValidatedKeyResult(
                    isSuccess = true,
                    availableModelIds = generativeModels,
                    verifiedModel = preferredModel
                )
            }
        }.getOrElse { throwable ->
            ValidatedKeyResult(
                isSuccess = false,
                error = GeminiErrorMapper.mapThrowable(throwable)
            )
        }
    }

    /**
     * Requests structured JSON highlight recommendations from Gemini using `responseSchema`.
     */
    suspend fun requestStructuredHighlights(
        apiKey: String,
        model: GeminiModelOption,
        sourceDurationMs: Long,
        targetClipDurationSec: Int,
        focusCategory: String,
        uploadedFileUri: String? = null,
        uploadedFileMimeType: String = "video/mp4",
        audioTranscriptContext: String? = null
    ): GeminiAnalysisResult = withContext(Dispatchers.IO) {
        val parts = mutableListOf<GeminiPartDto>()
        if (!uploadedFileUri.isNullOrBlank()) {
            parts.add(
                GeminiPartDto(
                    fileData = GeminiFileDataDto(
                        mimeType = uploadedFileMimeType,
                        fileUri = uploadedFileUri
                    )
                )
            )
        }

        val promptText = buildString {
            appendLine("You are CLIPR's lead video highlight editor.")
            appendLine("Source video duration: $sourceDurationMs ms (${sourceDurationMs / 1000} seconds).")
            appendLine("Target clip duration: ~$targetClipDurationSec seconds per clip.")
            appendLine("Primary editing focus: $focusCategory.")
            if (!audioTranscriptContext.isNullOrBlank()) {
                appendLine("Audio & motion energy context: $audioTranscriptContext")
            }
            appendLine("Select exactly 2 distinct, non-overlapping highlight clips that stay strictly within [0, $sourceDurationMs] ms.")
            appendLine("Rank them by hookScore (1-100) and explain why each moment is engaging.")
        }
        parts.add(GeminiPartDto(text = promptText))

        val requestPayload = GeminiGenerateContentRequest(
            contents = listOf(GeminiContentDto(role = "user", parts = parts)),
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.2f,
                responseMimeType = "application/json",
                responseSchema = GeminiHighlightSchemaBuilder.buildHighlightResponseSchema()
            )
        )

        val requestBodyJson = json.encodeToString(requestPayload)
        val httpRequest = Request.Builder()
            .url("$BASE_URL/models/${model.modelId}:generateContent")
            .header(HEADER_API_KEY, apiKey.trim())
            .header("Content-Type", "application/json")
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(httpRequest).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext GeminiAnalysisResult.Failure(
                        GeminiErrorMapper.mapHttpError(response.code, rawBody)
                    )
                }

                val envelope = json.decodeFromString<GeminiGenerateContentResponse>(rawBody)
                val blockReason = envelope.promptFeedback?.blockReason
                if (!blockReason.isNullOrBlank()) {
                    return@withContext GeminiAnalysisResult.Failure(
                        GeminiDomainError.SafetyBlocked(
                            "Gemini safety filter blocked analysis ($blockReason)."
                        )
                    )
                }

                val jsonText = envelope.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstNotNullOfOrNull { it.text }
                    ?.trim()

                if (jsonText.isNullOrBlank()) {
                    return@withContext GeminiAnalysisResult.Failure(
                        GeminiDomainError.StructuredOutputParseError(
                            "Gemini returned an empty candidate response."
                        )
                    )
                }

                val analysisDto = json.decodeFromString<GeminiHighlightAnalysisDto>(jsonText)
                GeminiAnalysisResult.Success(analysisDto)
            }
        }.getOrElse { throwable ->
            GeminiAnalysisResult.Failure(GeminiErrorMapper.mapThrowable(throwable))
        }
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val HEADER_API_KEY = "x-goog-api-key"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}
