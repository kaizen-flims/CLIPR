package com.clipr.app.media.subtitles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.effect.BitmapOverlay
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.core.model.SubtitleCustomConfig
import com.clipr.app.core.model.SubtitlePhraseGroup
import com.clipr.app.core.model.SubtitleWordTiming

/**
 * AndroidX Media3 [BitmapOverlay] that burns animated word-by-word captions into the
 * 1080×1920 vertical frame during hardware rendering (Section 06 & Section 07).
 *
 * Performance Optimization:
 * Rather than re-allocating and drawing a 1080×1920 Bitmap on every 60fps video frame,
 * caches the rendered Bitmap keyed by `(groupIndex, activeWordIndexInGroup)` so a new Bitmap
 * is rasterized only when the spoken word actually changes (~3-5 times per second).
 */
class AnimatedWordSubtitleOverlay(
    private val wordTimings: List<SubtitleWordTiming>,
    private val config: SubtitleCustomConfig,
    private val canvasWidthPx: Int = CliprBrandConfig.EXPORT_WIDTH_PX,
    private val canvasHeightPx: Int = CliprBrandConfig.EXPORT_HEIGHT_PX
) : BitmapOverlay() {

    private var cachedStateKey: Int = Int.MIN_VALUE
    private var cachedBitmap: Bitmap? = null
    private val emptyTransparentBitmap: Bitmap by lazy {
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xD8080808.toInt() // Obsidian Black with 85% opacity
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val positionMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
        val phrase = WhisperAndGeminiSubtitleEngine.findActivePhraseAtPosition(
            words = wordTimings,
            positionMs = positionMs,
            wordsPerGroup = config.wordsPerGroup
        ) ?: return emptyTransparentBitmap

        val highlightIdx = if (config.animatedWordHighlightEnabled) {
            phrase.activeWordIndexInGroup
        } else {
            -1
        }
        val stateKey = (phrase.groupIndex shl 8) or (highlightIdx and 0xFF)
        if (stateKey == cachedStateKey && cachedBitmap != null) {
            return cachedBitmap!!
        }

        val rendered = renderPhraseBitmap(phrase, highlightIdx)
        cachedStateKey = stateKey
        cachedBitmap = rendered
        return rendered
    }

    private fun renderPhraseBitmap(
        phrase: SubtitlePhraseGroup,
        activeWordIdx: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(canvasWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textSizePx = (config.fontSizeSp * 2.6f).coerceIn(36f, 96f)
        fillPaint.textSize = textSizePx
        strokePaint.textSize = textSizePx
        strokePaint.strokeWidth = config.outlineStrokeWidthPx * 2.2f
        strokePaint.color = config.outlineColorHex.toInt()

        val spaceWidth = fillPaint.measureText(" ") * 1.15f
        val wordWidths = phrase.words.map { fillPaint.measureText(it.word) }
        val totalLineWidth = wordWidths.sum() + spaceWidth * (phrase.words.size - 1).coerceAtLeast(0)

        val startX = ((canvasWidthPx - totalLineWidth) * 0.5f).coerceAtLeast(48f)
        val baselineY = (canvasHeightPx * config.verticalPositionRatio).coerceIn(220f, canvasHeightPx - 220f)

        if (config.showBackgroundBox) {
            val padH = 28f
            val padV = 22f
            val rect = RectF(
                startX - padH,
                baselineY - textSizePx - padV * 0.5f,
                (startX + totalLineWidth + padH).coerceAtMost(canvasWidthPx - 32f),
                baselineY + padV
            )
            canvas.drawRoundRect(rect, 8f, 8f, boxPaint)
        }

        var cursorX = startX
        phrase.words.forEachIndexed { idx, wordTiming ->
            val isHighlighted = idx == activeWordIdx
            if (config.outlineStrokeWidthPx > 0f) {
                canvas.drawText(wordTiming.word, cursorX, baselineY, strokePaint)
            }
            fillPaint.color = if (isHighlighted) {
                config.activeWordColorHex.toInt()
            } else {
                config.baseWordColorHex.toInt()
            }
            canvas.drawText(wordTiming.word, cursorX, baselineY, fillPaint)
            cursorX += wordWidths[idx] + spaceWidth
        }

        return bitmap
    }
}
