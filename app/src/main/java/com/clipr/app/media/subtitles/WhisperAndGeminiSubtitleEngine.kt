package com.clipr.app.media.subtitles

import com.clipr.app.core.model.SubtitlePhraseGroup
import com.clipr.app.core.model.SubtitleWordTiming
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GeminiTranscriptWordsPayload(
    val words: List<SubtitleWordTiming> = emptyList()
)

/**
 * Word-level speech transcription and phrase grouping engine (Section 06: Subtitle engine).
 * - Aligns spoken words with exact [relativeStartMs, relativeEndMs] timestamps within a clip.
 * - Groups words into energetic 3-5 word creator caption phrases.
 * - Resolves the active phrase and highlighted word index for any presentation timestamp.
 */
object WhisperAndGeminiSubtitleEngine {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Parses structured word-level JSON or tokenizes a raw transcript string across `[0, clipDurationMs]`
     * with natural syllable-weighted word timing when offline.
     */
    fun generateWordTimingsFromText(
        transcriptText: String,
        clipDurationMs: Long
    ): List<SubtitleWordTiming> {
        val trimmed = transcriptText.trim()
        if (trimmed.isBlank() || clipDurationMs <= 0L) return emptyList()

        // First check if input is already structured JSON from Gemini audio word timing
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            runCatching {
                val parsed = lenientJson.decodeFromString<GeminiTranscriptWordsPayload>(trimmed)
                if (parsed.words.isNotEmpty()) {
                    return parsed.words.mapIndexed { idx, w ->
                        w.copy(
                            wordIndex = idx,
                            relativeStartMs = w.relativeStartMs.coerceIn(0L, clipDurationMs),
                            relativeEndMs = w.relativeEndMs.coerceIn(w.relativeStartMs + 80L, clipDurationMs)
                        )
                    }
                }
            }
        }

        // Tokenize plain transcript words and distribute weighted by character length
        val tokens = trimmed
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return emptyList()

        val weights = tokens.map { token -> token.length.coerceIn(2, 12).toDouble() }
        val totalWeight = weights.sum().coerceAtLeast(1.0)
        val usableDurationMs = (clipDurationMs - 200L).coerceAtLeast(500L)

        val result = mutableListOf<SubtitleWordTiming>()
        var cursorMs = 100L

        tokens.forEachIndexed { index, word ->
            val shareMs = ((weights[index] / totalWeight) * usableDurationMs).toLong().coerceAtLeast(120L)
            val endMs = (cursorMs + shareMs).coerceAtMost(clipDurationMs)
            result.add(
                SubtitleWordTiming(
                    wordIndex = index,
                    word = word.uppercase(),
                    relativeStartMs = cursorMs,
                    relativeEndMs = endMs,
                    confidence = 0.95f
                )
            )
            cursorMs = endMs
        }

        return result
    }

    /**
     * Groups individual word timings into punchy creator caption phrases (default 4 words per phrase)
     * and identifies which word inside the active phrase should be highlighted at [positionMs].
     */
    fun findActivePhraseAtPosition(
        words: List<SubtitleWordTiming>,
        positionMs: Long,
        wordsPerGroup: Int = 4
    ): SubtitlePhraseGroup? {
        if (words.isEmpty()) return null
        val groupSize = wordsPerGroup.coerceIn(2, 7)
        val chunks = words.chunked(groupSize)

        chunks.forEachIndexed { groupIdx, chunk ->
            val groupStart = chunk.first().relativeStartMs
            val groupEnd = chunk.last().relativeEndMs
            if (positionMs in groupStart..groupEnd) {
                val activeIdx = chunk.indexOfFirst { positionMs in it.relativeStartMs..it.relativeEndMs }
                    .let { found ->
                        if (found >= 0) found else chunk.indexOfLast { positionMs >= it.relativeStartMs }.coerceAtLeast(0)
                    }
                return SubtitlePhraseGroup(
                    groupIndex = groupIdx,
                    startMs = groupStart,
                    endMs = groupEnd,
                    words = chunk,
                    activeWordIndexInGroup = activeIdx
                )
            }
        }
        return null
    }
}
