package com.clipr.app.media.effects

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.core.model.NormalizedCropRect
import com.clipr.app.core.model.StudioLayoutPreset

/**
 * Custom AndroidX Media3 [GlEffect] that transforms a 16:9 input video stream into a
 * 1080×1920 (9:16) vertical output frame in a single hardware OpenGL ES pass (Section 06 & 07).
 *
 * Supports:
 * 1. `FACECAM_GAMEPLAY_SPLIT`: Upper section samples `facecamCrop` UV region; lower section samples
 *    `gameplayCrop` UV region, separated by an optional flat Signal Red / Porcelain White border.
 * 2. `PORTRAIT_SUBJECT_CROP`: Full-height 9:16 vertical crop sampled from `gameplayCrop`.
 * 3. `CENTER_FIT_LETTERBOX`: Full 16:9 frame centered over Obsidian Black (#080808).
 */
class SplitReframeGlEffect(
    private val layoutPreset: StudioLayoutPreset,
    private val upperSplitRatio: Float,
    private val facecamCrop: NormalizedCropRect,
    private val gameplayCrop: NormalizedCropRect,
    private val showSeparatorBorder: Boolean = true,
    private val outputWidthPx: Int = CliprBrandConfig.EXPORT_WIDTH_PX,
    private val outputHeightPx: Int = CliprBrandConfig.EXPORT_HEIGHT_PX
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return SplitReframeShaderProgram(
            useHdr = useHdr,
            layoutPreset = layoutPreset,
            upperSplitRatio = upperSplitRatio.coerceIn(0.20f, 0.55f),
            facecamCrop = facecamCrop,
            gameplayCrop = gameplayCrop,
            showSeparatorBorder = showSeparatorBorder,
            outputWidthPx = outputWidthPx,
            outputHeightPx = outputHeightPx
        )
    }
}

internal class SplitReframeShaderProgram(
    useHdr: Boolean,
    private val layoutPreset: StudioLayoutPreset,
    private val upperSplitRatio: Float,
    private val facecamCrop: NormalizedCropRect,
    private val gameplayCrop: NormalizedCropRect,
    private val showSeparatorBorder: Boolean,
    private val outputWidthPx: Int,
    private val outputHeightPx: Int
) : BaseGlShaderProgram(useHdr, /* capacity= */ 1) {

    private val glProgram: GlProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
        setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        return Size(outputWidthPx, outputHeightPx)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        glProgram.use()
        glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)

        val modeInt = when (layoutPreset) {
            StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT -> 0
            StudioLayoutPreset.PORTRAIT_SUBJECT_CROP -> 1
            StudioLayoutPreset.CENTER_FIT_LETTERBOX -> 2
        }
        glProgram.setIntUniform("uLayoutMode", modeInt)
        glProgram.setFloatUniform("uSplitY", 1.0f - upperSplitRatio)
        glProgram.setFloatUniform("uBorderHalfThickness", if (showSeparatorBorder) 0.0022f else 0.0f)

        // Note: OpenGL texture Y=0 is bottom, Y=1 is top; convert top-down NormalizedCropRect to GL UV
        glProgram.setFloatsUniform(
            "uFacecamUv",
            floatArrayOf(
                facecamCrop.left,
                1.0f - facecamCrop.bottom,
                facecamCrop.right,
                1.0f - facecamCrop.top
            )
        )
        glProgram.setFloatsUniform(
            "uGameplayUv",
            floatArrayOf(
                gameplayCrop.left,
                1.0f - gameplayCrop.bottom,
                gameplayCrop.right,
                1.0f - gameplayCrop.top
            )
        )

        glProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        runCatching { glProgram.delete() }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform int uLayoutMode;
            uniform float uSplitY;
            uniform float uBorderHalfThickness;
            uniform vec4 uFacecamUv;
            uniform vec4 uGameplayUv;
            varying vec2 vTexSamplingCoord;

            void main() {
              if (uLayoutMode == 0) {
                // Facecam + Gameplay Split Mode
                if (abs(vTexSamplingCoord.y - uSplitY) <= uBorderHalfThickness) {
                  // Signal Red (#E53935) separator line
                  gl_FragColor = vec4(0.898, 0.224, 0.208, 1.0);
                  return;
                }
                if (vTexSamplingCoord.y > uSplitY) {
                  float normY = (vTexSamplingCoord.y - uSplitY) / max(0.001, (1.0 - uSplitY));
                  vec2 uv = vec2(
                    mix(uFacecamUv.x, uFacecamUv.z, vTexSamplingCoord.x),
                    mix(uFacecamUv.y, uFacecamUv.w, normY)
                  );
                  gl_FragColor = texture2D(uTexSampler, uv);
                } else {
                  float normY = vTexSamplingCoord.y / max(0.001, uSplitY);
                  vec2 uv = vec2(
                    mix(uGameplayUv.x, uGameplayUv.z, vTexSamplingCoord.x),
                    mix(uGameplayUv.y, uGameplayUv.w, normY)
                  );
                  gl_FragColor = texture2D(uTexSampler, uv);
                }
              } else if (uLayoutMode == 1) {
                // Full 9:16 Subject Portrait Crop
                vec2 uv = vec2(
                  mix(uGameplayUv.x, uGameplayUv.z, vTexSamplingCoord.x),
                  mix(uGameplayUv.y, uGameplayUv.w, vTexSamplingCoord.y)
                );
                gl_FragColor = texture2D(uTexSampler, uv);
              } else {
                // 16:9 Cinema Fit centered vertically on Obsidian Black (#080808)
                float bandBottom = 0.34;
                float bandTop = 0.66;
                if (vTexSamplingCoord.y >= bandBottom && vTexSamplingCoord.y <= bandTop) {
                  float normY = (vTexSamplingCoord.y - bandBottom) / (bandTop - bandBottom);
                  gl_FragColor = texture2D(uTexSampler, vec2(vTexSamplingCoord.x, normY));
                } else {
                  gl_FragColor = vec4(0.031, 0.031, 0.031, 1.0);
                }
              }
            }
        """
    }
}
