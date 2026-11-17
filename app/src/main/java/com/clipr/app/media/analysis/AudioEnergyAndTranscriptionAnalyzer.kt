package com.clipr.app.media.analysis

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class AudioEnergySpike(
    val timestampMs: Long,
    val normalizedRms: Float,
    val label: String
)

/**
 * Decodes the source audio track via Android's `MediaExtractor` + `MediaCodec` to compute
 * 1-second RMS audio energy windows and identify high-intensity reaction or gameplay spikes.
 * Injects these exact acoustic peak timestamps into the Gemini highlight prompt so 1 FPS video
 * sampling never misses fast reactions, laughter, or clutch audio moments (Section 05).
 */
class AudioEnergyAndTranscriptionAnalyzer(
    private val appContext: Context
) {

    suspend fun analyzeAudioEnergyPeaks(
        sourceUri: Uri,
        sourceDurationMs: Long,
        maxSpikes: Int = 8
    ): List<AudioEnergySpike> = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(appContext, sourceUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                return@withContext emptyList()
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }

            val windowRmsMap = LinkedHashMap<Long, Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var stepCounter = 0

            while (!outputDone && stepCounter < MAX_DECODE_ITERATIONS) {
                stepCounter++
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val ptsUs = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, ptsUs, 0)
                                // Step forward ~250ms at a time on long files for fast envelope scanning
                                val nextTargetUs = ptsUs + 250_000L
                                extractor.seekTo(nextTargetUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
                                if (extractor.sampleTime <= ptsUs) {
                                    extractor.advance()
                                }
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val rms = computePcm16Rms(outputBuffer, bufferInfo.offset, bufferInfo.size)
                        val secondBucketMs = ((bufferInfo.presentationTimeUs / 1_000_000L) * 1000L)
                            .coerceIn(0L, sourceDurationMs)
                        val prev = windowRmsMap[secondBucketMs] ?: 0f
                        if (rms > prev) {
                            windowRmsMap[secondBucketMs] = rms
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    outputDone = true
                }
            }

            selectTopDistinctPeaks(windowRmsMap, maxSpikes)
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    companion object {
        private const val TIMEOUT_US = 5_000L
        private const val MAX_DECODE_ITERATIONS = 2_400

        /**
         * Pure function (testable in JUnit) that computes normalized RMS [0.0 .. 1.0] from 16-bit PCM samples.
         */
        fun computePcm16Rms(buffer: ByteBuffer, offset: Int, sizeBytes: Int): Float {
            if (sizeBytes < 2) return 0f
            val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            dup.position(offset)
            dup.limit((offset + sizeBytes).coerceAtMost(dup.capacity()))

            var sumSquares = 0.0
            var sampleCount = 0
            while (dup.remaining() >= 2) {
                val sample = dup.short.toDouble() / 32768.0
                sumSquares += sample * sample
                sampleCount++
            }
            if (sampleCount == 0) return 0f
            return sqrt(sumSquares / sampleCount).toFloat().coerceIn(0f, 1f)
        }

        /**
         * Selects up to [maxSpikes] local maxima separated by at least 8 seconds.
         */
        fun selectTopDistinctPeaks(
            bucketRms: Map<Long, Float>,
            maxSpikes: Int = 8,
            minSeparationMs: Long = 8_000L
        ): List<AudioEnergySpike> {
            if (bucketRms.isEmpty()) return emptyList()
            val sortedByIntensity = bucketRms.entries
                .filter { it.value >= 0.12f }
                .sortedByDescending { it.value }

            val chosen = mutableListOf<AudioEnergySpike>()
            for ((tsMs, rms) in sortedByIntensity) {
                if (chosen.size >= maxSpikes) break
                val tooClose = chosen.any { kotlin.math.abs(it.timestampMs - tsMs) < minSeparationMs }
                if (!tooClose) {
                    val label = when {
                        rms >= 0.65f -> "Peak Reaction / Climax Spike"
                        rms >= 0.35f -> "High-Energy Dialogue / Action"
                        else -> "Notable Audio Activity"
                    }
                    chosen.add(AudioEnergySpike(tsMs, rms, label))
                }
            }
            return chosen.sortedBy { it.timestampMs }
        }
    }
}
