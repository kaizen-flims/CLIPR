package com.clipr.app.ui.screens.discover

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.core.model.SourceIngestionOutcome
import com.clipr.app.core.model.StudioLayoutPreset
import com.clipr.app.core.model.SubtitleStylePreset
import com.clipr.app.core.model.VideoSourceMetadata
import com.clipr.app.data.local.ClipCandidateEntity
import com.clipr.app.data.local.CliprDatabase
import com.clipr.app.data.local.ProjectEntity
import com.clipr.app.data.remote.gemini.GeminiAnalysisResult
import com.clipr.app.data.remote.gemini.GeminiFilesUploadClient
import com.clipr.app.data.remote.gemini.GeminiRestClient
import com.clipr.app.data.repository.VideoIngestionRepository
import com.clipr.app.data.security.ByokCredentialRepository
import com.clipr.app.domain.clipping.ClipAdjustmentValidator
import com.clipr.app.domain.gemini.ParseAndValidateHighlightJsonUseCase
import com.clipr.app.media.analysis.AudioEnergyAndTranscriptionAnalyzer
import com.clipr.app.media.analysis.AudioEnergySpike
import com.clipr.app.media.analysis.ProxyMediaGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val activeProjectId: String? = null,
    val sourceMetadata: VideoSourceMetadata? = null,
    val isIngesting: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val showDirectUrlInput: Boolean = false,
    val directUrlInput: String = "",
    val ingestionErrorTitle: String? = null,
    val ingestionErrorMessage: String? = null,
    val showLocalImportFallbackCta: Boolean = false,
    val targetClipDurationSec: Int = 30,
    val targetClipCount: Int = CliprBrandConfig.TARGET_DISTINCT_CLIP_COUNT,
    val selectedFocusCategory: String = "Gameplay + Reactions",
    val isAnalyzingAi: Boolean = false,
    val aiStatusStepMessage: String? = null,
    val aiErrorMessage: String? = null,
    val detectedAudioSpikes: List<AudioEnergySpike> = emptyList(),
    val candidates: List<ClipCandidate> = emptyList(),
    val previewingClipId: String? = null
)

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val ingestionRepository = VideoIngestionRepository(appContext)
    private val credentialRepository = ByokCredentialRepository(appContext)
    private val geminiRestClient = GeminiRestClient()
    private val geminiFilesUploadClient = GeminiFilesUploadClient()
    private val audioAnalyzer = AudioEnergyAndTranscriptionAnalyzer(appContext)
    private val proxyGenerator = ProxyMediaGenerator(appContext)
    private val highlightValidator = ParseAndValidateHighlightJsonUseCase()
    private val cliprDao = CliprDatabase.getInstance(appContext).cliprDao()

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    fun ingestLocalSafUri(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isIngesting = true,
                    ingestionErrorTitle = null,
                    ingestionErrorMessage = null,
                    showLocalImportFallbackCta = false,
                    aiErrorMessage = null
                )
            }
            handleIngestionOutcome(ingestionRepository.ingestLocalSafVideo(uri))
        }
    }

    fun toggleDirectUrlInput() {
        _uiState.update {
            it.copy(
                showDirectUrlInput = !it.showDirectUrlInput,
                ingestionErrorTitle = null,
                ingestionErrorMessage = null
            )
        }
    }

    fun onDirectUrlInputChanged(url: String) {
        _uiState.update {
            it.copy(
                directUrlInput = url,
                ingestionErrorTitle = null,
                ingestionErrorMessage = null
            )
        }
    }

    fun ingestDirectUrl() {
        val rawUrl = _uiState.value.directUrlInput.trim()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isIngesting = true,
                    downloadProgressPercent = 0,
                    ingestionErrorTitle = null,
                    ingestionErrorMessage = null,
                    showLocalImportFallbackCta = false,
                    aiErrorMessage = null
                )
            }
            val outcome = ingestionRepository.ingestAuthorizedVideoUrl(
                rawUrl = rawUrl,
                onProgressPercent = { pct ->
                    _uiState.update { state -> state.copy(downloadProgressPercent = pct) }
                }
            )
            handleIngestionOutcome(outcome)
        }
    }

    private suspend fun handleIngestionOutcome(outcome: SourceIngestionOutcome) {
        when (outcome) {
            is SourceIngestionOutcome.Success -> {
                val meta = outcome.metadata
                val projectId = "proj_${System.currentTimeMillis()}"
                val now = System.currentTimeMillis()
                cliprDao.upsertProject(
                    ProjectEntity(
                        projectId = projectId,
                        title = meta.displayName.substringBeforeLast('.'),
                        sourceUri = meta.sourceUri,
                        sourceFileName = meta.displayName,
                        sourceDurationMs = meta.durationMs,
                        widthPx = meta.effectiveWidthPx,
                        heightPx = meta.effectiveHeightPx,
                        frameRate = meta.frameRate,
                        videoCodecMime = meta.videoCodecMime,
                        audioCodecMime = meta.audioCodecMime,
                        layoutPresetId = StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT.id,
                        upperSplitRatio = StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT.defaultUpperRatio,
                        subtitlePresetId = SubtitleStylePreset.SIGNAL_PUNCH.id,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                )
                _uiState.update {
                    it.copy(
                        activeProjectId = projectId,
                        sourceMetadata = meta,
                        isIngesting = false,
                        showDirectUrlInput = false,
                        ingestionErrorTitle = null,
                        ingestionErrorMessage = null,
                        showLocalImportFallbackCta = false,
                        candidates = emptyList(),
                        detectedAudioSpikes = emptyList(),
                        aiErrorMessage = null
                    )
                }
            }
            is SourceIngestionOutcome.UnauthorizedPlatformUrl -> {
                _uiState.update {
                    it.copy(
                        isIngesting = false,
                        ingestionErrorTitle = "${outcome.platformName} Direct Download Restricted",
                        ingestionErrorMessage = outcome.message,
                        showLocalImportFallbackCta = true
                    )
                }
            }
            is SourceIngestionOutcome.UnsupportedFormat -> {
                _uiState.update {
                    it.copy(
                        isIngesting = false,
                        ingestionErrorTitle = "Unsupported Video Format (${outcome.formatDetail})",
                        ingestionErrorMessage = outcome.message,
                        showLocalImportFallbackCta = true
                    )
                }
            }
            is SourceIngestionOutcome.UnreadableSource -> {
                _uiState.update {
                    it.copy(
                        isIngesting = false,
                        ingestionErrorTitle = "Unable to Open Video Source",
                        ingestionErrorMessage = outcome.message,
                        showLocalImportFallbackCta = true
                    )
                }
            }
        }
    }

    /**
     * Executes the Phase 5 AI Highlight Detection pipeline:
     * 1. Decrypts the user's BYOK Gemini key from Android Keystore.
     * 2. Scans the source audio track for RMS energy spikes.
     * 3. Generates a 360p Media3 proxy stream and uploads it to the Gemini Files API.
     * 4. Calls Gemini `generateContent` with strict JSON Schema output.
     * 5. Validates and clamps timestamps into 2 distinct, non-overlapping clips without overwriting user-locked edits.
     */
    fun runGeminiHighlightDetection() {
        val meta = _uiState.value.sourceMetadata ?: return
        val projectId = _uiState.value.activeProjectId ?: "proj_${System.currentTimeMillis()}"

        viewModelScope.launch {
            val apiKey = credentialRepository.getDecryptedApiKey()
            if (apiKey.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        aiErrorMessage = "No Gemini API key found in Android Keystore. Configure your key in Settings first."
                    )
                }
                return@launch
            }

            val storedByok = credentialRepository.byokStateFlow.first()
            val selectedModel = storedByok.selectedModel

            _uiState.update {
                it.copy(
                    isAnalyzingAi = true,
                    aiStatusStepMessage = "Scanning audio track for high-energy reaction & gameplay peaks...",
                    aiErrorMessage = null
                )
            }

            // Step 1: On-device PCM RMS acoustic spike analysis
            val spikes = audioAnalyzer.analyzeAudioEnergyPeaks(
                sourceUri = Uri.parse(meta.sourceUri),
                sourceDurationMs = meta.durationMs
            )
            _uiState.update { it.copy(detectedAudioSpikes = spikes) }

            val acousticSummary = if (spikes.isEmpty()) {
                "Source filename: ${meta.displayName}, resolution: ${meta.resolutionLabel}, duration: ${meta.formattedDuration}."
            } else {
                "Detected acoustic RMS peaks at: " + spikes.joinToString("; ") { spike ->
                    "${VideoSourceMetadata.formatDurationMs(spike.timestampMs)} (${spike.timestampMs} ms, RMS=${"%.2f".format(spike.normalizedRms)}, ${spike.label})"
                }
            }

            // Step 2: Generate 360p proxy and upload to Gemini Files API
            _uiState.update {
                it.copy(aiStatusStepMessage = "Generating 360p Media3 proxy for fast Gemini upload...")
            }
            var uploadedRemoteFileName: String? = null
            var uploadedRemoteFileUri: String? = null

            val proxyFileResult = proxyGenerator.generate360pProxy(Uri.parse(meta.sourceUri))
            val proxyFile = proxyFileResult.getOrNull()
            if (proxyFile != null && proxyFile.exists()) {
                val uploadResult = geminiFilesUploadClient.uploadVideoAndAwaitActive(
                    apiKey = apiKey,
                    videoFile = proxyFile,
                    mimeType = "video/mp4",
                    onStatusUpdate = { status ->
                        _uiState.update { s -> s.copy(aiStatusStepMessage = status) }
                    }
                )
                uploadResult.getOrNull()?.let { uploadedMeta ->
                    uploadedRemoteFileName = uploadedMeta.name
                    uploadedRemoteFileUri = uploadedMeta.uri
                }
                runCatching { proxyFile.delete() }
            }

            // Step 3: Request structured JSON highlights from Gemini
            _uiState.update {
                it.copy(
                    aiStatusStepMessage = "Ranking 2 distinct highlight clips with ${selectedModel.displayName}..."
                )
            }

            val analysisResult = geminiRestClient.requestStructuredHighlights(
                apiKey = apiKey,
                model = selectedModel,
                sourceDurationMs = meta.durationMs,
                targetClipDurationSec = _uiState.value.targetClipDurationSec,
                focusCategory = _uiState.value.selectedFocusCategory,
                uploadedFileUri = uploadedRemoteFileUri,
                uploadedFileMimeType = "video/mp4",
                audioTranscriptContext = acousticSummary
            )

            // Clean up remote Gemini Files API entry immediately after analysis
            if (!uploadedRemoteFileName.isNullOrBlank()) {
                geminiFilesUploadClient.deleteRemoteFile(apiKey, uploadedRemoteFileName!!)
            }

            when (analysisResult) {
                is GeminiAnalysisResult.Success -> {
                    val existingClips = _uiState.value.candidates
                    val validatedResult = runCatching {
                        highlightValidator.validateDto(
                            dto = analysisResult.payload,
                            sourceDurationMs = meta.durationMs,
                            existingClips = existingClips
                        )
                    }

                    validatedResult.fold(
                        onSuccess = { validClips ->
                            persistClipsToDatabase(projectId, validClips)
                            _uiState.update {
                                it.copy(
                                    isAnalyzingAi = false,
                                    aiStatusStepMessage = null,
                                    aiErrorMessage = null,
                                    candidates = validClips,
                                    previewingClipId = validClips.firstOrNull()?.id
                                )
                            }
                        },
                        onFailure = { validationErr ->
                            _uiState.update {
                                it.copy(
                                    isAnalyzingAi = false,
                                    aiStatusStepMessage = null,
                                    aiErrorMessage = validationErr.message ?: "Timestamp validation failed."
                                )
                            }
                        }
                    )
                }
                is GeminiAnalysisResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isAnalyzingAi = false,
                            aiStatusStepMessage = null,
                            aiErrorMessage = "${analysisResult.error.userTitle}: ${analysisResult.error.userMessage}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Adjusts a candidate clip's start or end point and locks it as `isUserEdited = true`.
     */
    fun adjustCandidateClipBounds(clipId: String, deltaStartMs: Long, deltaEndMs: Long) {
        val durationMs = _uiState.value.sourceMetadata?.durationMs ?: return
        val projectId = _uiState.value.activeProjectId ?: return

        val updatedList = _uiState.value.candidates.map { clip ->
            if (clip.id == clipId) {
                ClipAdjustmentValidator.adjustClipBounds(clip, deltaStartMs, deltaEndMs, durationMs)
            } else {
                clip
            }
        }
        _uiState.update { it.copy(candidates = updatedList) }
        viewModelScope.launch {
            persistClipsToDatabase(projectId, updatedList)
        }
    }

    /**
     * Toggles user approval on a suggested clip. Approved clips are protected from AI overwrites.
     */
    fun toggleApproveCandidate(clipId: String) {
        val projectId = _uiState.value.activeProjectId ?: return
        val updatedList = _uiState.value.candidates.map { clip ->
            if (clip.id == clipId) {
                ClipAdjustmentValidator.toggleClipApproval(clip)
            } else {
                clip
            }
        }
        _uiState.update { it.copy(candidates = updatedList) }
        viewModelScope.launch {
            persistClipsToDatabase(projectId, updatedList)
        }
    }

    /**
     * Rejects an unapproved candidate clip so the user can regenerate a replacement.
     */
    fun rejectCandidateClip(clipId: String) {
        val projectId = _uiState.value.activeProjectId ?: return
        val filtered = _uiState.value.candidates
            .filterNot { it.id == clipId }
            .mapIndexed { idx, clip -> clip.copy(clipNumber = idx + 1) }

        _uiState.update { it.copy(candidates = filtered) }
        viewModelScope.launch {
            persistClipsToDatabase(projectId, filtered)
        }
    }

    fun selectCandidateForPreview(clipId: String) {
        _uiState.update { it.copy(previewingClipId = clipId) }
    }

    private suspend fun persistClipsToDatabase(projectId: String, clips: List<ClipCandidate>) {
        cliprDao.deleteUnlockedClipsForProject(projectId)
        val entities = clips.map { clip ->
            ClipCandidateEntity(
                clipId = clip.id,
                projectId = projectId,
                clipNumber = clip.clipNumber,
                title = clip.title,
                startMs = clip.startMs,
                endMs = clip.endMs,
                rationale = clip.rationale,
                hookScore = clip.hookScore,
                categoryTag = clip.categoryTag,
                suggestedLayoutId = clip.suggestedLayoutId,
                isApproved = clip.isApproved,
                isUserEdited = clip.isUserEdited
            )
        }
        cliprDao.upsertClips(entities)
    }

    fun clearSourceVideo() {
        _uiState.update {
            it.copy(
                activeProjectId = null,
                sourceMetadata = null,
                candidates = emptyList(),
                detectedAudioSpikes = emptyList(),
                ingestionErrorTitle = null,
                ingestionErrorMessage = null,
                aiErrorMessage = null
            )
        }
    }

    fun setTargetClipDurationSec(durationSec: Int) {
        _uiState.update { it.copy(targetClipDurationSec = durationSec) }
    }

    fun setFocusCategory(category: String) {
        _uiState.update { it.copy(selectedFocusCategory = category) }
    }
}
