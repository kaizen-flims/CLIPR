package com.clipr.app.media.render

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clipr.app.R
import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.core.model.ExportConfig
import com.clipr.app.core.model.NormalizedCropRect
import com.clipr.app.core.model.StudioLayoutPreset
import com.clipr.app.core.model.SubtitleCustomConfig
import com.clipr.app.media.subtitles.WhisperAndGeminiSubtitleEngine

/**
 * WorkManager [CoroutineWorker] that runs Media3 1080×1920 rendering under an Android 14/15
 * `FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING` notification with cooperative cancellation.
 */
class CliprRenderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val renderEngine = Media3RenderEngine(appContext)

    override suspend fun doWork(): Result {
        val sourceUri = inputData.getString(KEY_SOURCE_URI) ?: return Result.failure()
        val projectId = inputData.getString(KEY_PROJECT_ID) ?: "proj_default"
        val clipId = inputData.getString(KEY_CLIP_ID) ?: "clip_1"
        val clipTitle = inputData.getString(KEY_CLIP_TITLE) ?: "CLIPR Short"
        val startMs = inputData.getLong(KEY_START_MS, 0L)
        val endMs = inputData.getLong(KEY_END_MS, 15_000L)
        val transcript = inputData.getString(KEY_TRANSCRIPT).orEmpty()

        ensureNotificationChannel()
        setForeground(createForegroundInfo(clipTitle, 0))

        val clip = ClipCandidate(
            id = clipId,
            clipNumber = 1,
            title = clipTitle,
            startMs = startMs,
            endMs = endMs,
            rationale = "Rendered via WorkManager",
            hookScore = 95,
            categoryTag = "Short",
            isApproved = true
        )

        val words = WhisperAndGeminiSubtitleEngine.generateWordTimingsFromText(
            transcriptText = transcript,
            clipDurationMs = clip.durationMs
        )

        val request = ClipRenderRequest(
            projectId = projectId,
            sourceUri = sourceUri,
            clip = clip,
            layoutPreset = StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT,
            upperSplitRatio = 0.35f,
            showSeparatorBorder = true,
            facecamCrop = NormalizedCropRect.DEFAULT_FACECAM_CORNER,
            gameplayCrop = NormalizedCropRect.DEFAULT_GAMEPLAY_CENTER,
            subtitleConfig = SubtitleCustomConfig(),
            subtitleWords = words,
            exportConfig = ExportConfig()
        )

        val outcome = renderEngine.renderClipToShort(request) { state ->
            setProgressAsync(
                workDataOf(
                    KEY_PROGRESS_PERCENT to state.progressPercent,
                    KEY_ETA_SECONDS to (state.estimatedRemainingSeconds ?: -1L)
                )
            )
        }

        return if (outcome.status == RenderJobStatus.COMPLETED) {
            Result.success(workDataOf(KEY_OUTPUT_PATH to outcome.outputFilePath))
        } else {
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to outcome.errorMessage))
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Rendering CLIPR Short: $title")
            .setContentText("Exporting 1080×1920 H.264 + AAC ($progress%)")
            .setSmallIcon(R.drawable.ic_clipr_logo)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CLIPR Video Rendering",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "clipr_render_channel"
        private const val NOTIFICATION_ID = 4102

        const val KEY_PROJECT_ID = "project_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_CLIP_ID = "clip_id"
        const val KEY_CLIP_TITLE = "clip_title"
        const val KEY_START_MS = "start_ms"
        const val KEY_END_MS = "end_ms"
        const val KEY_TRANSCRIPT = "transcript"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_ETA_SECONDS = "eta_seconds"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR_MESSAGE = "error_message"
    }
}
