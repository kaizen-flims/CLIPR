package com.clipr.app.domain.clipping

import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.domain.gemini.ParseAndValidateHighlightJsonUseCase

/**
 * Pure domain logic for user-driven clip trimming, approval locking, and regeneration filtering
 * (Section 05: Clip selection).
 */
object ClipAdjustmentValidator {

    /**
     * Adjusts a clip's [startMs] and [endMs] by [deltaStartMs] and [deltaEndMs],
     * clamping strictly within [0, sourceDurationMs] and maintaining at least 5 seconds duration.
     * Marks `isUserEdited = true` so subsequent AI regenerations never overwrite the user's edit.
     */
    fun adjustClipBounds(
        clip: ClipCandidate,
        deltaStartMs: Long,
        deltaEndMs: Long,
        sourceDurationMs: Long
    ): ClipCandidate {
        val minDur = ParseAndValidateHighlightJsonUseCase.MIN_CLIP_DURATION_MS
        val maxDur = ParseAndValidateHighlightJsonUseCase.MAX_CLIP_DURATION_MS

        var newStart = (clip.startMs + deltaStartMs).coerceIn(0L, (sourceDurationMs - minDur).coerceAtLeast(0L))
        var newEnd = (clip.endMs + deltaEndMs).coerceIn(newStart + minDur, sourceDurationMs)

        if (newEnd - newStart > maxDur) {
            if (deltaStartMs != 0L) {
                newStart = newEnd - maxDur
            } else {
                newEnd = newStart + maxDur
            }
        }

        return clip.copy(
            startMs = newStart,
            endMs = newEnd,
            isUserEdited = true
        )
    }

    /**
     * Toggles user approval on a clip candidate. Approved clips are locked against AI regeneration.
     */
    fun toggleClipApproval(clip: ClipCandidate): ClipCandidate {
        return clip.copy(isApproved = !clip.isApproved)
    }
}
