package com.clipr.app.data.remote.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

@Serializable
data class GeminiUploadedFileEnvelope(
    val file: GeminiUploadedFileMetadata? = null
)

@Serializable
data class GeminiUploadedFileMetadata(
    val name: String,
    val uri: String,
    val mimeType: String = "video/mp4",
    val state: String = "PROCESSING"
)

/**
 * Implements Google's Gemini Files API resumable upload protocol (`/upload/v1beta/files`)
 * so CLIPR can upload video proxy streams using the user's BYOK key, wait for `ACTIVE` state,
 * and delete the remote file immediately after highlight detection finishes (Section 04, 05 & 10).
 */
class GeminiFilesUploadClient(
    private val httpClient: OkHttpClient = GeminiRestClient.defaultOkHttpClient(),
    private val json: Json = GeminiRestClient.defaultJson()
) {

    suspend fun uploadVideoAndAwaitActive(
        apiKey: String,
        videoFile: File,
        mimeType: String = "video/mp4",
        onStatusUpdate: (String) -> Unit = {}
    ): Result<GeminiUploadedFileMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            require(videoFile.exists() && videoFile.length() > 0L) {
                "Proxy video file is missing or empty."
            }

            onStatusUpdate("Initiating secure upload to Gemini Files API...")
            val numBytes = videoFile.length()
            val startJson = "{"file":{"display_name":"${videoFile.name}"}}"

            val initRequest = Request.Builder()
                .url(UPLOAD_INIT_URL)
                .header("x-goog-api-key", apiKey.trim())
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", numBytes.toString())
                .header("X-Goog-Upload-Header-Content-Type", mimeType)
                .header("Content-Type", "application/json")
                .post(startJson.toRequestBody("application/json".toMediaType()))
                .build()

            val uploadUrl = httpClient.newCall(initRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = GeminiErrorMapper.mapHttpError(response.code, response.body?.string())
                    throw IllegalStateException(err.userMessage)
                }
                response.header("X-Goog-Upload-URL")
                    ?: response.header("x-goog-upload-url")
                    ?: throw IllegalStateException("Gemini Files API did not return an upload session URL.")
            }

            onStatusUpdate("Uploading media stream (${numBytes / 1024} KB)...")
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .header("Content-Length", numBytes.toString())
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .post(videoFile.asRequestBody(mimeType.toMediaType()))
                .build()

            var metadata = httpClient.newCall(uploadRequest).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = GeminiErrorMapper.mapHttpError(response.code, rawBody)
                    throw IllegalStateException(err.userMessage)
                }
                val envelope = json.decodeFromString<GeminiUploadedFileEnvelope>(rawBody)
                envelope.file ?: json.decodeFromString<GeminiUploadedFileMetadata>(rawBody)
            }

            // Poll until Gemini video indexing reaches ACTIVE state
            var pollAttempts = 0
            while (metadata.state.equals("PROCESSING", ignoreCase = true) && pollAttempts < MAX_POLL_ATTEMPTS) {
                pollAttempts++
                onStatusUpdate("Gemini indexing video frames & audio (pass $pollAttempts)...")
                delay(2_000L)

                val pollReq = Request.Builder()
                    .url("${GeminiRestClient.BASE_URL}/${metadata.name}")
                    .header("x-goog-api-key", apiKey.trim())
                    .get()
                    .build()

                httpClient.newCall(pollReq).execute().use { pollResp ->
                    val body = pollResp.body?.string().orEmpty()
                    if (pollResp.isSuccessful && body.isNotBlank()) {
                        metadata = json.decodeFromString<GeminiUploadedFileMetadata>(body)
                    }
                }
            }

            if (metadata.state.equals("FAILED", ignoreCase = true)) {
                throw IllegalStateException("Gemini video processing failed on remote file.")
            }

            metadata
        }
    }

    /**
     * Deletes the uploaded video from Google's Gemini Files API immediately after analysis completes.
     */
    suspend fun deleteRemoteFile(apiKey: String, fileResourceName: String) = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = fileResourceName.removePrefix("/")
            val req = Request.Builder()
                .url("${GeminiRestClient.BASE_URL}/$cleanName")
                .header("x-goog-api-key", apiKey.trim())
                .delete()
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    companion object {
        const val UPLOAD_INIT_URL = "https://generativelanguage.googleapis.com/upload/v1beta/files"
        private const val MAX_POLL_ATTEMPTS = 30
    }
}
