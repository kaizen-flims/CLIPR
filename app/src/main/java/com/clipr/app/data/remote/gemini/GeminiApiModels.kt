package com.clipr.app.data.remote.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serializable DTOs for Google Gemini API v1beta (`models` and `generateContent` endpoints).
 */
@Serializable
data class GeminiModelsListResponse(
    val models: List<GeminiModelMetadataDto> = emptyList()
)

@Serializable
data class GeminiModelMetadataDto(
    val name: String,
    val displayName: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null
)

@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContentDto>,
    val systemInstruction: GeminiContentDto? = null,
    val generationConfig: GeminiGenerationConfigDto? = null
)

@Serializable
data class GeminiContentDto(
    val role: String = "user",
    val parts: List<GeminiPartDto>
)

@Serializable
data class GeminiPartDto(
    val text: String? = null,
    @SerialName("file_data")
    val fileData: GeminiFileDataDto? = null
)

@Serializable
data class GeminiFileDataDto(
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("file_uri")
    val fileUri: String
)

@Serializable
data class GeminiGenerationConfigDto(
    val temperature: Float = 0.2f,
    val responseMimeType: String = "application/json",
    val responseSchema: JsonObject? = null
)

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidateDto> = emptyList(),
    val promptFeedback: GeminiPromptFeedbackDto? = null
)

@Serializable
data class GeminiCandidateDto(
    val content: GeminiContentDto? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiPromptFeedbackDto(
    val blockReason: String? = null
)

/**
 * Structured JSON output returned by Gemini when analyzing video footage for CLIPR highlights.
 */
@Serializable
data class GeminiHighlightAnalysisDto(
    val videoSummary: String = "",
    val clips: List<GeminiHighlightClipDto> = emptyList()
)

@Serializable
data class GeminiHighlightClipDto(
    val clipNumber: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val rationale: String,
    val hookScore: Int,
    val categoryTag: String,
    val suggestedLayoutId: String = "facecam_gameplay_split"
)

/**
 * Builds the strict OpenAPI 3.0 JSON Schema passed in `generationConfig.responseSchema`
 * so Gemini deterministically returns valid highlight JSON.
 */
object GeminiHighlightSchemaBuilder {

    fun buildHighlightResponseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put(
                    "videoSummary",
                    buildJsonObject {
                        put("type", "STRING")
                        put("description", "Concise summary of the video content and pacing.")
                    }
                )
                put(
                    "clips",
                    buildJsonObject {
                        put("type", "ARRAY")
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("clipNumber", buildJsonObject { put("type", "INTEGER") })
                                        put("title", buildJsonObject { put("type", "STRING") })
                                        put("startMs", buildJsonObject { put("type", "INTEGER") })
                                        put("endMs", buildJsonObject { put("type", "INTEGER") })
                                        put("rationale", buildJsonObject { put("type", "STRING") })
                                        put("hookScore", buildJsonObject { put("type", "INTEGER") })
                                        put("categoryTag", buildJsonObject { put("type", "STRING") })
                                        put("suggestedLayoutId", buildJsonObject { put("type", "STRING") })
                                    }
                                )
                                put(
                                    "required",
                                    buildJsonArray {
                                        add(JsonPrimitive("clipNumber"))
                                        add(JsonPrimitive("title"))
                                        add(JsonPrimitive("startMs"))
                                        add(JsonPrimitive("endMs"))
                                        add(JsonPrimitive("rationale"))
                                        add(JsonPrimitive("hookScore"))
                                        add(JsonPrimitive("categoryTag"))
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("videoSummary"))
                add(JsonPrimitive("clips"))
            }
        )
    }
}
