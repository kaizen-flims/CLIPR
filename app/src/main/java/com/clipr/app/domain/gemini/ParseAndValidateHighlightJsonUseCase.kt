package com.clipr.app.domain.gemini

import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.data.remote.gemini.GeminiHighlightAnalysisDto
import com.clipr.app.data.remote.gemini.GeminiRestClient
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

/**
 * Validates structured JSON responses from Gemini (Section 04 & Section 05):
 * - Parses raw JSON into [GeminiHighlightAnalysisDto]
 * - Clamps [startMs, endMs] strictly within [0, sourceDurationMs]
 * - Ensures 2 distinct clips with minimal overlap (< 10% overlap ratio)
 * - Never overwrites user-approved or user-edited clips (`isApproved || isUserEdited`)
 */
class ParseAndValidateHighlightJsonUseCase(
    private val json: Json = GeminiRestClient.defaultJson()
) {

    fun parseAndValidate(
        rawJson: String,
        sourceDurationMs: Long,
        existingClips: List<ClipCandidate> = emptyList()
    ): Result<List<ClipCandidate>> = runCatching {
        require(sourceDurationMs >= MIN_CLIP_DURATION_MS) {
            "Source video duration ($sourceDurationMs ms) must be at least ${MIN_CLIP_DURATION_MS / 1000} seconds."
        }

        val dto = json.decodeFromString<GeminiHighlightAnalysisDto>(rawJson)
        validateDto(dto, sourceDurationMs, existingClips)
    }

    fun validateDto(
        dto: GeminiHighlightAnalysisDto,
        sourceDurationMs: Long,
        existingClips: List<ClipCandidate> = emptyList()
    ): List<ClipCandidate> {
        val lockedClips = existingClips.filter { it.isApproved || it.isUserEdited }
        if (lockedClips.size >= CliprBrandConfig.TARGET_DISTINCT_CLIP_COUNT) {
            return lockedClips.take(CliprBrandConfig.TARGET_DISTINCT_CLIP_COUNT)
        }

        val sanitizedIncoming = dto.clips
            .sortedByDescending { it.hookScore }
            .mapNotNull { raw ->
                val clampedStart = raw.startMs.coerceIn(0L, sourceDurationMs - MIN_CLIP_DURATION_MS)
                val maxAllowedEnd = min(sourceDurationMs, clampedStart + MAX_CLIP_DURATION_MS)
                val clampedEnd = raw.endMs.coerceIn(clampedStart + MIN_CLIP_DURATION_MS, maxAllowedEnd)

                if (clampedEnd - clampedStart < MIN_CLIP_DURATION_MS) {
                    null
                } else {
                    ClipCandidate(
                        id = "clip_${raw.clipNumber}_${clampedStart}_${clampedEnd}",
                        clipNumber = raw.clipNumber,
                        title = raw.title.trim().ifBlank { "Highlight #${raw.clipNumber}" },
                        startMs = clampedStart,
                        endMs = clampedEnd,
                        rationale = raw.rationale.trim(),
                        hookScore = raw.hookScore.coerceIn(1, 100),
                        categoryTag = raw.categoryTag.trim().ifBlank { "Highlight" },
                        suggestedLayoutId = raw.suggestedLayoutId
                    )
                }
            }

        val finalSelection = mutableListOf<ClipCandidate>()
        finalSelection.addAll(lockedClips)

        for (candidate in sanitizedIncoming) {
            if (finalSelection.size >= CliprBrandConfig.TARGET_DISTINCT_CLIP_COUNT) break
            val hasExcessiveOverlap = finalSelection.any { existing ->
                computeOverlapRatio(existing.startMs, existing.endMs, candidate.startMs, candidate.endMs) > MAX_OVERLAP_RATIO
            }
            if (!hasExcessiveOverlap) {
                finalSelection.add(
                    candidate.copy(clipNumber = finalSelection.size + 1)
                )
            }
        }

        require(finalSelection.size >= CliprBrandConfig.TARGET_DISTINCT_CLIP_COUNT) {
            "Gemini returned fewer than ${CliprBrandConfig.TARGET_DISTINCT_CLIP_COUNT} distinct non-overlapping clips within source duration."
        }

        return finalSelection
    }

    fun computeOverlapRatio(startA: Long, endA: Long, startB: Long, endB: Long): Double {
        val overlapStart = max(startA, startB)
        val overlapEnd = min(endA, endB)
        val overlapMs = (overlapEnd - overlapStart).coerceAtLeast(0L)
        if (overlapMs == 0L) return 0.0
        val shorterDuration = min(endA - startA, endB - startB).coerceAtLeast(1L)
        return overlapMs.toDouble() / shorterDuration.toDouble()
    }

    companion object {
        const val MIN_CLIP_DURATION_MS = 5_000L
        const val MAX_CLIP_DURATION_MS = 90_000L
        const val MAX_OVERLAP_RATIO = 0.10
    }
}
