package com.clipr.app.ui.screens.studio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.core.model.ExportConfig
import com.clipr.app.core.model.NormalizedCropRect
import com.clipr.app.core.model.StudioLayoutPreset
import com.clipr.app.core.model.SubtitleCustomConfig
import com.clipr.app.core.model.SubtitleStylePreset
import com.clipr.app.core.model.SubtitleWordTiming
import com.clipr.app.core.model.VideoSourceMetadata
import com.clipr.app.media.render.ClipRenderRequest
import com.clipr.app.media.render.Media3RenderEngine
import com.clipr.app.media.render.RenderJobStatus
import com.clipr.app.media.render.RenderProgressState
import com.clipr.app.media.subtitles.WhisperAndGeminiSubtitleEngine
import com.clipr.app.media.vision.FaceCamDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val activeProjectId: String = "proj_studio",
    val activeSource: VideoSourceMetadata? = null,
    val secondaryFacecamTrackUri: String? = null,
    val selectedClips: List<ClipCandidate> = emptyList(),
    val activeClipIndex: Int = 0,
    val selectedLayoutPreset: StudioLayoutPreset = StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT,
    val upperSplitRatio: Float = StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT.defaultUpperRatio,
    val showSeparatorBorder: Boolean = true,
    val facecamCrop: NormalizedCropRect = NormalizedCropRect.DEFAULT_FACECAM_CORNER,
    val gameplayCrop: NormalizedCropRect = NormalizedCropRect.DEFAULT_GAMEPLAY_CENTER,
    val faceDetectionStatus: String = "Tap 'Auto-Detect Facecam (ML Kit)' to scan frames",
    val isScanningFaces: Boolean = false,
    val subtitleConfig: SubtitleCustomConfig = SubtitleCustomConfig(),
    val transcriptInputText: String = "FROM LONG TO LEGENDARY CLUTCH MOMENT ON STREAM RIGHT NOW",
    val subtitleWords: List<SubtitleWordTiming> = emptyList(),
    val previewScrubPositionMs: Long = 800L,
    val exportConfig: ExportConfig = ExportConfig(),
    val renderProgress: RenderProgressState = RenderProgressState()
) {
    val activeClip: ClipCandidate?
        get() = selectedClips.getOrNull(activeClipIndex)

    val isRendering: Boolean
        get() = renderProgress.status == RenderJobStatus.PREPARING ||
            renderProgress.status == RenderJobStatus.RENDERING_HARDWARE ||
            renderProgress.status == RenderJobStatus.RENDERING_FALLBACK
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val faceCamDetector = FaceCamDetector(appContext)
    private val renderEngine = Media3RenderEngine(appContext)
    private var activeRenderJob: Job? = null

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        regenerateWordTimings()
    }

    fun attachSourceAndClips(metadata: VideoSourceMetadata?, clips: List<ClipCandidate>) {
        if (metadata == null) return
        val firstClip = clips.firstOrNull()
        val initialTranscript = firstClip?.title?.uppercase() ?: _uiState.value.transcriptInputText
        _uiState.update {
            it.copy(
                activeSource = metadata,
                selectedClips = clips,
                activeClipIndex = 0,
                transcriptInputText = initialTranscript,
                renderProgress = RenderProgressState()
            )
        }
        regenerateWordTimings()
    }

    fun attachSecondaryFacecamTrack(uri: Uri?) {
        _uiState.update { it.copy(secondaryFacecamTrackUri = uri?.toString()) }
    }

    fun selectActiveClipIndex(index: Int) {
        _uiState.update { state ->
            if (index in state.selectedClips.indices) {
                val clip = state.selectedClips[index]
                state.copy(
                    activeClipIndex = index,
                    transcriptInputText = clip.title.uppercase(),
                    renderProgress = RenderProgressState()
                )
            } else {
                state
            }
        }
        regenerateWordTimings()
    }

    fun runAutoFacecamDetection() {
        val source = _uiState.value.activeSource ?: return
        val clip = _uiState.value.activeClip
        val startMs = clip?.startMs ?: 0L
        val endMs = clip?.endMs ?: source.durationMs.coerceAtMost(30_000L)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanningFaces = true,
                    faceDetectionStatus = "Scanning keyframes with Google ML Kit Face Detection..."
                )
            }
            val suggestion = faceCamDetector.detectSubjectAndFacecamRegions(
                sourceUri = Uri.parse(source.sourceUri),
                clipStartMs = startMs,
                clipEndMs = endMs
            )
            _uiState.update {
                it.copy(
                    isScanningFaces = false,
                    facecamCrop = suggestion.facecamCropRect,
                    gameplayCrop = if (it.selectedLayoutPreset == StudioLayoutPreset.PORTRAIT_SUBJECT_CROP) {
                        suggestion.subjectPortraitCropRect
                    } else {
                        it.gameplayCrop
                    },
                    faceDetectionStatus = suggestion.summaryLabel
                )
            }
        }
    }

    /**
     * Starts on-device 1080×1920 H.264 + AAC hardware rendering via [Media3RenderEngine].
     */
    fun startLocalShortRender(onCompletedNavigateToLibrary: () -> Unit = {}) {
        val state = _uiState.value
        val source = state.activeSource ?: return
        val clip = state.activeClip ?: ClipCandidate(
            id = "clip_default",
            clipNumber = 1,
            title = source.displayName.substringBeforeLast('.'),
            startMs = 0L,
            endMs = source.durationMs.coerceAtMost(30_000L),
            rationale = "Full studio clip",
            hookScore = 95,
            categoryTag = "Studio"
        )

        val request = ClipRenderRequest(
            projectId = state.activeProjectId,
            sourceUri = source.sourceUri,
            secondaryFacecamUri = state.secondaryFacecamTrackUri,
            clip = clip,
            layoutPreset = state.selectedLayoutPreset,
            upperSplitRatio = state.upperSplitRatio,
            showSeparatorBorder = state.showSeparatorBorder,
            facecamCrop = state.facecamCrop,
            gameplayCrop = state.gameplayCrop,
            subtitleConfig = state.subtitleConfig,
            subtitleWords = state.subtitleWords,
            exportConfig = state.exportConfig
        )

        activeRenderJob?.cancel()
        activeRenderJob = viewModelScope.launch {
            val finalState = renderEngine.renderClipToShort(request) { progressUpdate ->
                _uiState.update { it.copy(renderProgress = progressUpdate) }
            }
            if (finalState.status == RenderJobStatus.COMPLETED) {
                onCompletedNavigateToLibrary()
            }
        }
    }

    fun cancelActiveRender() {
        renderEngine.cancelActiveRender()
        activeRenderJob?.cancel()
        _uiState.update {
            it.copy(
                renderProgress = RenderProgressState(
                    status = RenderJobStatus.CANCELLED,
                    errorMessage = "Rendering cancelled and partial output file deleted."
                )
            )
        }
    }

    fun selectLayoutPreset(preset: StudioLayoutPreset) {
        _uiState.update {
            it.copy(
                selectedLayoutPreset = preset,
                upperSplitRatio = preset.defaultUpperRatio
            )
        }
    }

    fun updateUpperSplitRatio(ratio: Float) {
        _uiState.update { it.copy(upperSplitRatio = ratio.coerceIn(0.20f, 0.55f)) }
    }

    fun toggleSeparatorBorder() {
        _uiState.update { it.copy(showSeparatorBorder = !it.showSeparatorBorder) }
    }

    fun panFacecamCrop(dx: Float, dy: Float) {
        _uiState.update { it.copy(facecamCrop = it.facecamCrop.panBy(dx, dy)) }
    }

    fun panGameplayCrop(dx: Float, dy: Float) {
        _uiState.update { it.copy(gameplayCrop = it.gameplayCrop.panBy(dx, dy)) }
    }

    fun selectSubtitlePreset(preset: SubtitleStylePreset) {
        _uiState.update {
            it.copy(
                subtitleConfig = it.subtitleConfig.copy(
                    preset = preset,
                    activeWordColorHex = preset.activeWordHex,
                    baseWordColorHex = preset.baseTextHex,
                    fontSizeSp = preset.fontSizeSp,
                    outlineStrokeWidthPx = preset.strokeWidthPx
                )
            )
        }
    }

    fun updateSubtitleFontSizeSp(sizeSp: Int) {
        _uiState.update {
            it.copy(subtitleConfig = it.subtitleConfig.copy(fontSizeSp = sizeSp.coerceIn(16, 36)))
        }
    }

    fun updateSubtitleVerticalRatio(ratio: Float) {
        _uiState.update {
            it.copy(subtitleConfig = it.subtitleConfig.copy(verticalPositionRatio = ratio.coerceIn(0.15f, 0.90f)))
        }
    }

    fun updateActiveWordColor(colorHex: Long) {
        _uiState.update {
            it.copy(subtitleConfig = it.subtitleConfig.copy(activeWordColorHex = colorHex))
        }
    }

    fun toggleAnimatedWordHighlight() {
        _uiState.update {
            it.copy(
                subtitleConfig = it.subtitleConfig.copy(
                    animatedWordHighlightEnabled = !it.subtitleConfig.animatedWordHighlightEnabled
                )
            )
        }
    }

    fun toggleSubtitleBackgroundBox() {
        _uiState.update {
            it.copy(
                subtitleConfig = it.subtitleConfig.copy(
                    showBackgroundBox = !it.subtitleConfig.showBackgroundBox
                )
            )
        }
    }

    fun onTranscriptInputChanged(newText: String) {
        _uiState.update { it.copy(transcriptInputText = newText) }
        regenerateWordTimings()
    }

    fun updatePreviewScrubPositionMs(positionMs: Long) {
        _uiState.update { it.copy(previewScrubPositionMs = positionMs.coerceAtLeast(0L)) }
    }

    private fun regenerateWordTimings() {
        val state = _uiState.value
        val durationMs = state.activeClip?.durationMs ?: 10_000L
        val words = WhisperAndGeminiSubtitleEngine.generateWordTimingsFromText(
            transcriptText = state.transcriptInputText,
            clipDurationMs = durationMs
        )
        _uiState.update { it.copy(subtitleWords = words) }
    }
}
