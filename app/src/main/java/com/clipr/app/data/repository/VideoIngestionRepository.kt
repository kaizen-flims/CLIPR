package com.clipr.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.clipr.app.CliprApplication
import com.clipr.app.core.model.SourceIngestionOutcome
import com.clipr.app.media.ingestion.SourceUrlPolicyValidator
import com.clipr.app.media.ingestion.UrlPolicyDecision
import com.clipr.app.media.ingestion.VideoMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Coordinates local Storage Access Framework (SAF) video imports and authorized direct HTTPS media URLs.
 * Strictly blocks unauthorized streaming platform downloads (YouTube, Twitch, etc.).
 */
class VideoIngestionRepository(
    private val appContext: Context,
    private val metadataExtractor: VideoMetadataExtractor = VideoMetadataExtractor(appContext),
    private val httpClient: OkHttpClient = defaultDownloadClient()
) {

    /**
     * Ingests a local video selected via Android's Storage Access Framework (`OpenDocument`).
     * Takes a persistable URI read permission so background workers can read the source without duplicating it.
     */
    suspend fun ingestLocalSafVideo(uri: Uri): SourceIngestionOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            appContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
        metadataExtractor.extractFromSafUri(uri, isRemoteAuthorizedCopy = false)
    }

    /**
     * Evaluates and downloads an authorized direct HTTPS video link into app-private working cache,
     * then extracts and validates its media tracks.
     */
    suspend fun ingestAuthorizedVideoUrl(
        rawUrl: String,
        onProgressPercent: (Int) -> Unit = {}
    ): SourceIngestionOutcome = withContext(Dispatchers.IO) {
        when (val decision = SourceUrlPolicyValidator.evaluateUrl(rawUrl)) {
            is UrlPolicyDecision.BlockedStreamingPlatform -> {
                SourceIngestionOutcome.UnauthorizedPlatformUrl(
                    platformName = decision.platformName,
                    message = decision.explanation
                )
            }
            is UrlPolicyDecision.InvalidOrUnsupportedUrl -> {
                SourceIngestionOutcome.UnsupportedFormat(
                    formatDetail = "Invalid URL",
                    message = decision.reason
                )
            }
            is UrlPolicyDecision.PermittedDirectMediaUrl -> {
                downloadAndExtractAuthorizedMedia(
                    url = decision.normalizedUrl,
                    fileName = decision.inferredFileName,
                    onProgressPercent = onProgressPercent
                )
            }
        }
    }

    private suspend fun downloadAndExtractAuthorizedMedia(
        url: String,
        fileName: String,
        onProgressPercent: (Int) -> Unit
    ): SourceIngestionOutcome {
        val workDir = File(appContext.cacheDir, CliprApplication.WORK_CACHE_DIR_NAME).apply { mkdirs() }
        val targetFile = File(workDir, "ingested_${System.currentTimeMillis()}_$fileName")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return SourceIngestionOutcome.UnreadableSource(
                        "Direct URL returned HTTP ${response.code}. Ensure the media file is publicly accessible or import a local copy via SAF."
                    )
                }

                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    return SourceIngestionOutcome.UnauthorizedPlatformUrl(
                        platformName = "Webpage Link",
                        message = "This URL points to an HTML webpage rather than a direct video file. Import a permitted local video copy via SAF instead."
                    )
                }

                val body = response.body ?: return SourceIngestionOutcome.UnreadableSource(
                    "Remote server returned an empty media stream."
                )

                val totalBytes = body.contentLength()
                if (totalBytes > MAX_DIRECT_DOWNLOAD_BYTES) {
                    return SourceIngestionOutcome.UnsupportedFormat(
                        formatDetail = "File > 500 MB",
                        message = "Remote URL exceeds the 500 MB direct-fetch cap. Download the file locally and import via Storage Access Framework."
                    )
                }

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int
                        var accumulated = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            accumulated += bytesRead
                            if (totalBytes > 0L) {
                                val pct = ((accumulated * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                onProgressPercent(pct)
                            }
                        }
                        output.flush()
                    }
                }
            }

            metadataExtractor.extractFromLocalFile(
                file = targetFile,
                isRemoteAuthorizedCopy = true
            )
        } catch (e: Exception) {
            runCatching { targetFile.delete() }
            SourceIngestionOutcome.UnreadableSource(
                "Failed to fetch authorized media URL: ${e.message ?: "Network interrupted."}"
            )
        }
    }

    companion object {
        private const val MAX_DIRECT_DOWNLOAD_BYTES = 500L * 1024L * 1024L // 500 MB

        fun defaultDownloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
