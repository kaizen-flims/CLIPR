package com.clipr.app

import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.data.remote.gemini.GeminiErrorMapper
import com.clipr.app.data.remote.gemini.GeminiHighlightSchemaBuilder
import com.clipr.app.data.security.LogRedactor
import com.clipr.app.domain.gemini.ParseAndValidateHighlightJsonUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic Phase 3 unit test suite verifying:
 * 1. API key format validation, masking, and log redaction
 * 2. HTTP quota & authentication error mapping
 * 3. Structured JSON highlight response parsing, timestamp clamping, and 2-clip overlap validation
 * 4. Protection of user-approved edits against AI overwrites
 */
class GeminiPhase3IntegrationTest {

    @Test
    fun logRedactor_scrubsGeminiKeysAndHeadersCompletely() {
        val fakeKey = "AIzaSyD9876543210abcdefABCDEF_123456789"
        val rawLog = "Request failed for key=$fakeKey and header x-goog-api-key: $fakeKey"
        val redacted = LogRedactor.redact(rawLog)

        assertFalse(redacted.contains(fakeKey))
        assertTrue(redacted.contains("[REDACTED_QUERY_KEY]"))
        assertTrue(redacted.contains("[REDACTED_HEADER_KEY]"))
    }

    @Test
    fun apiKeyFormatValidator_acceptsValidAndRejectsMalformedKeys() {
        assertTrue(LogRedactor.isPlausibleGeminiApiKeyFormat("AIzaSyD9876543210abcdefABCDEF_123456789"))
        assertFalse(LogRedactor.isPlausibleGeminiApiKeyFormat("sk-1234567890abcdef"))
        assertFalse(LogRedactor.isPlausibleGeminiApiKeyFormat("short"))
        assertEquals(
            "AIza••••••••6789",
            LogRedactor.maskApiKey("AIzaSyD9876543210abcdefABCDEF_123456789")
        )
    }

    @Test
    fun geminiErrorMapper_mapsAuthAndQuotaCodesCorrectly() {
        val authErr = GeminiErrorMapper.mapHttpError(401, "API_KEY_INVALID")
        assertEquals(ApiKeyStatus.INVALID, authErr.mappedKeyStatus)

        val quotaErr = GeminiErrorMapper.mapHttpError(429, "Resource has been exhausted")
        assertEquals(ApiKeyStatus.QUOTA_EXCEEDED, quotaErr.mappedKeyStatus)
    }

    @Test
    fun parseAndValidateHighlightJson_clampsTimestampsAndSelectsTwoDistinctClips() {
        val useCase = ParseAndValidateHighlightJsonUseCase()
        val sampleJson = """
            {
              "videoSummary": "High-intensity Call of Duty Mobile Hardpoint match.",
              "clips": [
                {
                  "clipNumber": 1,
                  "title": "1v4 Sniper Clutch on Standoff",
                  "startMs": 12000,
                  "endMs": 42000,
                  "rationale": "Rapid 4-piece sniper feed with loud team reaction.",
                  "hookScore": 96,
                  "categoryTag": "Gameplay + Reactions",
                  "suggestedLayoutId": "facecam_gameplay_split"
                },
                {
                  "clipNumber": 2,
                  "title": "Duplicate Overlapping Candidate",
                  "startMs": 14000,
                  "endMs": 40000,
                  "rationale": "Overlaps clip 1 and must be filtered out.",
                  "hookScore": 91,
                  "categoryTag": "Gameplay + Reactions",
                  "suggestedLayoutId": "facecam_gameplay_split"
                },
                {
                  "clipNumber": 3,
                  "title": "Final Hill Hold & Victory Reaction",
                  "startMs": 95000,
                  "endMs": 140000,
                  "rationale": "Decisive rotation and post-match celebration.",
                  "hookScore": 89,
                  "categoryTag": "Gameplay + Reactions",
                  "suggestedLayoutId": "facecam_gameplay_split"
                }
              ]
            }
        """.trimIndent()

        // Source video is 120,000 ms (2 minutes), so clip 3 endMs (140,000) must clamp to 120,000
        val validated = useCase.parseAndValidate(
            rawJson = sampleJson,
            sourceDurationMs = 120_000L
        ).getOrThrow()

        assertEquals(2, validated.size)
        assertEquals("1v4 Sniper Clutch on Standoff", validated[0].title)
        assertEquals(12_000L, validated[0].startMs)
        assertEquals(42_000L, validated[0].endMs)

        // Second selected clip skipped the overlapping candidate and clamped 140,000 -> 120,000
        assertEquals("Final Hill Hold & Victory Reaction", validated[1].title)
        assertEquals(95_000L, validated[1].startMs)
        assertEquals(120_000L, validated[1].endMs)
    }

    @Test
    fun parseAndValidateHighlightJson_neverOverwritesUserApprovedClips() {
        val useCase = ParseAndValidateHighlightJsonUseCase()
        val lockedUserClip = ClipCandidate(
            id = "user_locked_1",
            clipNumber = 1,
            title = "User Trimmed Opening Hook",
            startMs = 5_000L,
            endMs = 25_000L,
            rationale = "Manual trim",
            hookScore = 100,
            categoryTag = "Manual",
            isApproved = true,
            isUserEdited = true
        )

        val aiJson = """
            {
              "videoSummary": "Regenerated suggestions",
              "clips": [
                {
                  "clipNumber": 1,
                  "title": "AI Replacement Attempt",
                  "startMs": 6000,
                  "endMs": 24000,
                  "rationale": "Overlaps user clip",
                  "hookScore": 99,
                  "categoryTag": "Gameplay"
                },
                {
                  "clipNumber": 2,
                  "title": "Fresh Second Clip",
                  "startMs": 60000,
                  "endMs": 90000,
                  "rationale": "Distinct non-overlapping segment",
                  "hookScore": 88,
                  "categoryTag": "Gameplay"
                }
              ]
            }
        """.trimIndent()

        val result = useCase.parseAndValidate(
            rawJson = aiJson,
            sourceDurationMs = 120_000L,
            existingClips = listOf(lockedUserClip)
        ).getOrThrow()

        assertEquals(2, result.size)
        assertEquals("User Trimmed Opening Hook", result[0].title)
        assertTrue(result[0].isApproved)
        assertEquals("Fresh Second Clip", result[1].title)
    }

    @Test
    fun highlightSchemaBuilder_producesValidRequiredProperties() {
        val schema = GeminiHighlightSchemaBuilder.buildHighlightResponseSchema()
        assertEquals("\"OBJECT\"", schema["type"].toString())
        assertTrue(schema.toString().contains("hookScore"))
        assertTrue(schema.toString().contains("startMs"))
    }
}
