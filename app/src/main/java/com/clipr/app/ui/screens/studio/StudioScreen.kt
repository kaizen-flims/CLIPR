package com.clipr.app.ui.screens.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.core.model.StudioLayoutPreset
import com.clipr.app.core.model.SubtitleStylePreset
import com.clipr.app.media.subtitles.WhisperAndGeminiSubtitleEngine
import com.clipr.app.ui.components.CliprFlatPanel
import com.clipr.app.ui.components.CliprFrameBracketBox
import com.clipr.app.ui.components.CliprHairlineDivider
import com.clipr.app.ui.components.CliprOutlineButton
import com.clipr.app.ui.components.CliprPrimaryButton
import com.clipr.app.ui.components.CliprSectionHeader
import com.clipr.app.ui.components.CliprStatusBadge
import com.clipr.app.ui.theme.CliprShapes
import com.clipr.app.ui.theme.GraphiteElevated
import com.clipr.app.ui.theme.HairlineDivider
import com.clipr.app.ui.theme.ObsidianBlack
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed

/**
 * Studio Destination (Section 06, Section 08 & Phase 6):
 * - 1080 × 1920 (9:16) Vertical Canvas with live Facecam + Gameplay split preview
 * - ML Kit Face Detection auto-reframing + independent manual crop pan controls
 * - Support for both single composite video region cropping AND optional separate facecam track
 * - Animated word-level subtitle engine with custom font size, Y-position, active word color,
 *   background box, and stroke outline options
 */
@Composable
fun StudioScreen(
    uiState: StudioUiState,
    onSelectClipIndex: (Int) -> Unit = {},
    onSelectLayoutPreset: (StudioLayoutPreset) -> Unit,
    onUpdateFacecamSplitRatio: (Float) -> Unit,
    onToggleSeparatorBorder: () -> Unit = {},
    onRunAutoFaceDetection: () -> Unit = {},
    onPanFacecamCrop: (Float, Float) -> Unit = { _, _ -> },
    onPanGameplayCrop: (Float, Float) -> Unit = { _, _ -> },
    onAttachSecondaryFacecamTrack: (Uri?) -> Unit = {},
    onSelectSubtitlePreset: (SubtitleStylePreset) -> Unit,
    onUpdateSubtitleFontSize: (Int) -> Unit = {},
    onUpdateSubtitleVerticalRatio: (Float) -> Unit = {},
    onUpdateActiveWordColor: (Long) -> Unit = {},
    onToggleWordHighlight: () -> Unit,
    onToggleSubtitleBackgroundBox: () -> Unit = {},
    onTranscriptInputChanged: (String) -> Unit = {},
    onUpdatePreviewScrubPosition: (Long) -> Unit = {},
    onImportInDiscoverClick: () -> Unit,
    onRenderShortClick: () -> Unit = {},
    onCancelRenderClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val secondaryTrackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onAttachSecondaryFacecamTrack(uri)
    }

    val activePhrase = WhisperAndGeminiSubtitleEngine.findActivePhraseAtPosition(
        words = uiState.subtitleWords,
        positionMs = uiState.previewScrubPositionMs,
        wordsPerGroup = uiState.subtitleConfig.wordsPerGroup
    )

    val clipDurationMs = (uiState.activeClip?.durationMs ?: 10_000L).coerceAtLeast(1_000L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Export Spec Header & Clip Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "9:16 VERTICAL STUDIO CANVAS",
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryGrey
            )
            CliprStatusBadge(
                text = "${CliprBrandConfig.EXPORT_WIDTH_PX}×${CliprBrandConfig.EXPORT_HEIGHT_PX} · MP4 · H.264 / AAC",
                isActive = true
            )
        }

        if (uiState.selectedClips.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.selectedClips.forEachIndexed { idx, clip ->
                    CliprStatusBadge(
                        text = "CLIP #${clip.clipNumber}: ${clip.title.take(18)}",
                        isActive = uiState.activeClipIndex == idx,
                        isAccent = uiState.activeClipIndex == idx,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectClipIndex(idx) }
                    )
                }
            }
        }

        // 2. 9:16 Vertical Preview Canvas (1080×1920 Aspect Ratio)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CliprFrameBracketBox(
                modifier = Modifier
                    .width(236.dp)
                    .aspectRatio(9f / 16f),
                backgroundColor = ObsidianBlack
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.selectedLayoutPreset == StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT) {
                        val upperWeight = uiState.upperSplitRatio
                        val lowerWeight = (1f - upperWeight).coerceAtLeast(0.1f)

                        // Upper Facecam Section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(upperWeight)
                                .background(GraphiteElevated)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (uiState.secondaryFacecamTrackUri != null) {
                                        "SEPARATE FACECAM TRACK"
                                    } else {
                                        "FACECAM REGION CROP"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalRed
                                )
                                Text(
                                    text = "UV [${"%.2f".format(uiState.facecamCrop.left)}, ${"%.2f".format(uiState.facecamCrop.top)} → ${"%.2f".format(uiState.facecamCrop.right)}, ${"%.2f".format(uiState.facecamCrop.bottom)}]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryGrey
                                )
                            }
                        }

                        if (uiState.showSeparatorBorder) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(SignalRed)
                            )
                        }

                        // Lower Gameplay Section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(lowerWeight)
                                .background(ObsidianBlack)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "GAMEPLAY ACTION CROP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PorcelainWhite
                                )
                                Text(
                                    text = "UV [${"%.2f".format(uiState.gameplayCrop.left)}, ${"%.2f".format(uiState.gameplayCrop.top)} → ${"%.2f".format(uiState.gameplayCrop.right)}, ${"%.2f".format(uiState.gameplayCrop.bottom)}]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryGrey
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = uiState.selectedLayoutPreset.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PorcelainWhite
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Subject X=${(uiState.gameplayCrop.centerX * 100).toInt()}% · 1080×1920",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryGrey
                                )
                            }
                        }
                    }
                }

                // Live Animated Word-Level Subtitle Overlay Preview
                if (activePhrase != null) {
                    val topOffsetDp = ((uiState.subtitleConfig.verticalPositionRatio * 340f).toInt()).dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topOffsetDp, start = 8.dp, end = 8.dp)
                            .background(
                                if (uiState.subtitleConfig.showBackgroundBox) ObsidianBlack else Color.Transparent
                            )
                            .border(
                                width = if (uiState.subtitleConfig.showBackgroundBox) 1.dp else 0.dp,
                                color = if (uiState.subtitleConfig.showBackgroundBox) HairlineDivider else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activePhrase.words.forEachIndexed { idx, w ->
                                val isCurrent = uiState.subtitleConfig.animatedWordHighlightEnabled &&
                                    idx == activePhrase.activeWordIndexInGroup
                                Text(
                                    text = w.word,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = (uiState.subtitleConfig.fontSizeSp * 0.55f).sp
                                    ),
                                    color = if (isCurrent) {
                                        Color(uiState.subtitleConfig.activeWordColorHex)
                                    } else {
                                        Color(uiState.subtitleConfig.baseWordColorHex)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Timeline & Word-Level Caption Scrubber
        CliprFlatPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CAPTION & TIMELINE SCRUBBER (${uiState.previewScrubPositionMs} ms)",
                    style = MaterialTheme.typography.labelMedium,
                    color = PorcelainWhite
                )
                Text(
                    text = "PTS SYNCED",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalRed
                )
            }
            Slider(
                value = uiState.previewScrubPositionMs.toFloat(),
                onValueChange = { onUpdatePreviewScrubPosition(it.toLong()) },
                valueRange = 0f..clipDurationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = SignalRed,
                    activeTrackColor = SignalRed,
                    inactiveTrackColor = HairlineDivider
                )
            )
        }

        // 4. Automatic Vertical Reframing & Facecam/Gameplay Layout Controls
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "1. Vertical Reframing & Facecam/Gameplay Layout",
                subtitle = "ML Kit face tracking, single-video region cropping, or separate track composition"
            )

            CliprFlatPanel {
                StudioLayoutPreset.entries.forEachIndexed { index, preset ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CliprHairlineDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLayoutPreset(preset) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = PorcelainWhite
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryGrey
                            )
                        }
                        CliprStatusBadge(
                            text = if (uiState.selectedLayoutPreset == preset) "ACTIVE" else "USE",
                            isAccent = uiState.selectedLayoutPreset == preset
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                CliprHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = uiState.faceDetectionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGrey
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CliprOutlineButton(
                        text = if (uiState.isScanningFaces) "Scanning..." else "Auto-Detect Facecam (ML Kit)",
                        leadingIcon = Icons.Outlined.CenterFocusStrong,
                        enabled = !uiState.isScanningFaces && uiState.activeSource != null,
                        onClick = onRunAutoFaceDetection,
                        modifier = Modifier.weight(1f)
                    )
                    CliprOutlineButton(
                        text = if (uiState.secondaryFacecamTrackUri != null) "2nd Track Loaded" else "+ Facecam Track",
                        leadingIcon = Icons.Outlined.VideoCall,
                        onClick = { secondaryTrackLauncher.launch(arrayOf("video/*")) }
                    )
                }

                if (uiState.selectedLayoutPreset == StudioLayoutPreset.FACECAM_GAMEPLAY_SPLIT) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Upper Facecam / Lower Gameplay Proportions: ${(uiState.upperSplitRatio * 100).toInt()}% / ${((1f - uiState.upperSplitRatio) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = PorcelainWhite
                    )
                    Slider(
                        value = uiState.upperSplitRatio,
                        onValueChange = onUpdateFacecamSplitRatio,
                        valueRange = 0.20f..0.55f,
                        colors = SliderDefaults.colors(
                            thumbColor = SignalRed,
                            activeTrackColor = SignalRed,
                            inactiveTrackColor = HairlineDivider
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleSeparatorBorder)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Signal Red Split Separator Border",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PorcelainWhite
                        )
                        CliprStatusBadge(
                            text = if (uiState.showSeparatorBorder) "ON" else "OFF",
                            isAccent = uiState.showSeparatorBorder
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Independent Region Positioning (Nudge Crop Regions)",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryGrey
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CliprStatusBadge(
                            text = "Cam ←",
                            modifier = Modifier.weight(1f).clickable { onPanFacecamCrop(-0.04f, 0f) }
                        )
                        CliprStatusBadge(
                            text = "Cam →",
                            modifier = Modifier.weight(1f).clickable { onPanFacecamCrop(0.04f, 0f) }
                        )
                        CliprStatusBadge(
                            text = "Game ←",
                            modifier = Modifier.weight(1f).clickable { onPanGameplayCrop(-0.04f, 0f) }
                        )
                        CliprStatusBadge(
                            text = "Game →",
                            modifier = Modifier.weight(1f).clickable { onPanGameplayCrop(0.04f, 0f) }
                        )
                    }
                }
            }
        }

        // 5. Subtitle Engine & Customization
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "2. Animated Word-Level Subtitle Engine",
                subtitle = "Custom font sizes, positioning, active word highlight color, stroke & background"
            )

            CliprFlatPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitleStylePreset.entries.forEach { preset ->
                        CliprStatusBadge(
                            text = preset.displayName,
                            isActive = uiState.subtitleConfig.preset == preset,
                            isAccent = uiState.subtitleConfig.preset == preset,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectSubtitlePreset(preset) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.transcriptInputText,
                    onValueChange = onTranscriptInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = "Clip Transcript (Auto-aligned to word-level timestamps)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    shape = CliprShapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalRed,
                        unfocusedBorderColor = HairlineDivider,
                        focusedTextColor = PorcelainWhite,
                        unfocusedTextColor = PorcelainWhite,
                        cursorColor = SignalRed,
                        focusedContainerColor = ObsidianBlack,
                        unfocusedContainerColor = ObsidianBlack
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Font Size: ${uiState.subtitleConfig.fontSizeSp} sp",
                    style = MaterialTheme.typography.labelLarge,
                    color = PorcelainWhite
                )
                Slider(
                    value = uiState.subtitleConfig.fontSizeSp.toFloat(),
                    onValueChange = { onUpdateSubtitleFontSize(it.toInt()) },
                    valueRange = 16f..36f,
                    colors = SliderDefaults.colors(
                        thumbColor = SignalRed,
                        activeTrackColor = SignalRed,
                        inactiveTrackColor = HairlineDivider
                    )
                )

                Text(
                    text = "Vertical Position: ${(uiState.subtitleConfig.verticalPositionRatio * 100).toInt()}% of 9:16 Height",
                    style = MaterialTheme.typography.labelLarge,
                    color = PorcelainWhite
                )
                Slider(
                    value = uiState.subtitleConfig.verticalPositionRatio,
                    onValueChange = onUpdateSubtitleVerticalRatio,
                    valueRange = 0.15f..0.90f,
                    colors = SliderDefaults.colors(
                        thumbColor = SignalRed,
                        activeTrackColor = SignalRed,
                        inactiveTrackColor = HairlineDivider
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active Word Highlight Colour",
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryGrey
                )
                Spacer(modifier = Modifier.height(6.dp))
                val colorOptions = listOf(
                    "Signal Red" to CliprBrandConfig.SIGNAL_RED_ARGB,
                    "Porcelain" to CliprBrandConfig.PORCELAIN_WHITE_ARGB,
                    "Amber" to 0xFFFFB300L,
                    "Emerald" to 0xFF00E676L
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorOptions.forEach { (name, hex) ->
                        CliprStatusBadge(
                            text = name,
                            isAccent = uiState.subtitleConfig.activeWordColorHex == hex,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onUpdateActiveWordColor(hex) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleWordHighlight() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Animated Word-by-Word Highlighting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PorcelainWhite
                    )
                    CliprStatusBadge(
                        text = if (uiState.subtitleConfig.animatedWordHighlightEnabled) "ENABLED" else "OFF",
                        isAccent = uiState.subtitleConfig.animatedWordHighlightEnabled
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSubtitleBackgroundBox() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Obsidian Subtitle Background Box + Stroke",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PorcelainWhite
                    )
                    CliprStatusBadge(
                        text = if (uiState.subtitleConfig.showBackgroundBox) "ENABLED" else "OFF",
                        isAccent = uiState.subtitleConfig.showBackgroundBox
                    )
                }
            }
        }

        // 6. On-Device Media3 Export Status & Controls
        if (uiState.renderProgress.status.name != "IDLE") {
            CliprFlatPanel(borderColor = SignalRed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.renderProgress.status.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = PorcelainWhite
                    )
                    CliprStatusBadge(
                        text = "${uiState.renderProgress.progressPercent}%",
                        isAccent = true
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val etaText = uiState.renderProgress.estimatedRemainingSeconds?.let { " · ETA ~${it}s" } ?: ""
                Text(
                    text = "Elapsed: ${uiState.renderProgress.elapsedSeconds}s$etaText",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGrey
                )
                if (!uiState.renderProgress.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.renderProgress.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = SignalRed
                    )
                }
                if (uiState.isRendering) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CliprOutlineButton(
                        text = "Cancel Export & Clean Up",
                        onClick = onCancelRenderClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 7. Export Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CliprOutlineButton(
                text = "Import Footage",
                leadingIcon = Icons.Outlined.UploadFile,
                onClick = onImportInDiscoverClick,
                modifier = Modifier.weight(1f)
            )
            CliprPrimaryButton(
                text = if (uiState.isRendering) "Rendering (${uiState.renderProgress.progressPercent}%)..." else "Render 1080×1920 Short",
                enabled = !uiState.isRendering && uiState.activeSource != null,
                leadingIcon = Icons.Outlined.FileDownload,
                onClick = onRenderShortClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
