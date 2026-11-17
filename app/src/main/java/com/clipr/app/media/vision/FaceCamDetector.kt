package com.clipr.app.media.vision

import android.content.Context
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.clipr.app.core.model.NormalizedCropRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class FaceReframeSuggestion(
    val detectedFaceCount: Int,
    val isCornerOverlayFacecam: Boolean,
    val facecamCropRect: NormalizedCropRect,
    val subjectPortraitCropRect: NormalizedCropRect,
    val summaryLabel: String
)

/**
 * Uses Google ML Kit Face Detection (`com.google.mlkit:face-detection:16.1.7`) to scan
 * sampled keyframes within a clip window and automatically locate:
 * 1. Streamer facecam overlays in the corners of 16:9 gameplay videos.
 * 2. Primary speaker positions for 9:16 subject-aware vertical reframing (Section 06).
 */
class FaceCamDetector(
    private val appContext: Context
) {

    suspend fun detectSubjectAndFacecamRegions(
        sourceUri: Uri,
        clipStartMs: Long,
        clipEndMs: Long
    ): FaceReframeSuggestion = withContext(Dispatchers.Default) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.04f)
            .build()

        val detector = FaceDetection.getClient(options)
        val retriever = MediaMetadataRetriever()
        val observedNormalizedBoxes = mutableListOf<NormalizedCropRect>()

        try {
            retriever.setDataSource(appContext, sourceUri)
            val durationWindowMs = (clipEndMs - clipStartMs).coerceAtLeast(1_000L)
            val samplePointsMs = listOf(
                clipStartMs + (durationWindowMs * 0.15).toLong(),
                clipStartMs + (durationWindowMs * 0.45).toLong(),
                clipStartMs + (durationWindowMs * 0.75).toLong()
            )

            for (sampleMs in samplePointsMs) {
                val bitmap = retriever.getFrameAtTime(
                    sampleMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue

                val frameW = bitmap.width.toFloat().coerceAtLeast(1f)
                val frameH = bitmap.height.toFloat().coerceAtLeast(1f)
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                val faces = suspendCancellableCoroutine<List<Rect>> { cont ->
                    detector.process(inputImage)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) {
                                cont.resume(result.map { it.boundingBox })
                            }
                        }
                        .addOnFailureListener {
                            if (cont.isActive) {
                                cont.resume(emptyList())
                            }
                        }
                }
                bitmap.recycle()

                faces.firstOrNull()?.let { box ->
                    observedNormalizedBoxes.add(
                        normalizeFaceBoundingBox(
                            leftPx = box.left.toFloat(),
                            topPx = box.top.toFloat(),
                            rightPx = box.right.toFloat(),
                            bottomPx = box.bottom.toFloat(),
                            frameWidthPx = frameW,
                            frameHeightPx = frameH
                        )
                    )
                }
            }

            computeReframeSuggestionFromBoxes(observedNormalizedBoxes)
        } catch (_: Exception) {
            defaultFallbackSuggestion()
        } finally {
            runCatching { retriever.release() }
            runCatching { detector.close() }
        }
    }

    companion object {
        /**
         * Pure geometric helper (testable in JUnit) that converts a raw face bounding box
         * into a padded normalized crop rectangle and classifies whether it is a corner facecam.
         */
        fun normalizeFaceBoundingBox(
            leftPx: Float,
            topPx: Float,
            rightPx: Float,
            bottomPx: Float,
            frameWidthPx: Float,
            frameHeightPx: Float
        ): NormalizedCropRect {
            val l = (leftPx / frameWidthPx).coerceIn(0f, 1f)
            val t = (topPx / frameHeightPx).coerceIn(0f, 1f)
            val r = (rightPx / frameWidthPx).coerceIn(0f, 1f)
            val b = (bottomPx / frameHeightPx).coerceIn(0f, 1f)
            return NormalizedCropRect(l, t, r, b)
        }

        fun computeReframeSuggestionFromBoxes(
            boxes: List<NormalizedCropRect>
        ): FaceReframeSuggestion {
            if (boxes.isEmpty()) {
                return defaultFallbackSuggestion()
            }

            val avgCenterX = boxes.map { it.centerX }.average().toFloat()
            val avgCenterY = boxes.map { it.centerY }.average().toFloat()
            val avgW = boxes.map { it.width }.average().toFloat()
            val avgH = boxes.map { it.height }.average().toFloat()

            // A face is classified as a corner webcam overlay if its center sits near the outer edges
            // and its height is <= 32% of the full frame height.
            val isCornerFacecam = (avgCenterX < 0.32f || avgCenterX > 0.68f) &&
                (avgCenterY < 0.38f || avgCenterY > 0.62f) &&
                avgH <= 0.32f

            // Expand face box by 2.2x to include head, shoulders, and webcam frame
            val camW = (avgW * 2.4f).coerceIn(0.18f, 0.42f)
            val camH = (avgH * 2.4f).coerceIn(0.20f, 0.45f)
            val camLeft = (avgCenterX - camW * 0.5f).coerceIn(0f, 1f - camW)
            val camTop = (avgCenterY - camH * 0.5f).coerceIn(0f, 1f - camH)
            val facecamRect = NormalizedCropRect(camLeft, camTop, camLeft + camW, camTop + camH)

            // 9:16 portrait crop centered horizontally on the detected face
            val portraitWidthUv = (9f / 16f) * (9f / 16f) // ~0.316 of a 16:9 width
            val pLeft = (avgCenterX - portraitWidthUv * 0.5f).coerceIn(0f, 1f - portraitWidthUv)
            val portraitRect = NormalizedCropRect(pLeft, 0f, pLeft + portraitWidthUv, 1f)

            val label = if (isCornerFacecam) {
                "ML Kit detected corner facecam at (${(avgCenterX * 100).toInt()}%, ${(avgCenterY * 100).toInt()}%)"
            } else {
                "ML Kit locked onto speaker at X=${(avgCenterX * 100).toInt()}%"
            }

            return FaceReframeSuggestion(
                detectedFaceCount = boxes.size,
                isCornerOverlayFacecam = isCornerFacecam,
                facecamCropRect = facecamRect,
                subjectPortraitCropRect = portraitRect,
                summaryLabel = label
            )
        }

        fun defaultFallbackSuggestion(): FaceReframeSuggestion = FaceReframeSuggestion(
            detectedFaceCount = 0,
            isCornerOverlayFacecam = false,
            facecamCropRect = NormalizedCropRect.DEFAULT_FACECAM_CORNER,
            subjectPortraitCropRect = NormalizedCropRect.DEFAULT_PORTRAIT_9_16,
            summaryLabel = "Default 16:9 → 9:16 crop regions active (adjust manually below)"
        )
    }
}
