package com.clipr.app.ui.screens.discover

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.core.model.ClipCandidate
import com.clipr.app.core.model.VideoSourceMetadata
import com.clipr.app.ui.components.CliprExoPlayerPreview
import com.clipr.app.ui.components.CliprFlatPanel
import com.clipr.app.ui.components.CliprFrameBracketBox
import com.clipr.app.ui.components.CliprHairlineDivider
import com.clipr.app.ui.components.CliprOutlineButton
import com.clipr.app.ui.components.CliprPrimaryButton
import com.clipr.app.ui.components.CliprSectionHeader
import com.clipr.app.ui.components.CliprStatusBadge
import com.clipr.app.ui.theme.CliprShapes
import com.clipr.app.ui.theme.HairlineDivider
import com.clipr.app.ui.theme.ObsidianBlack
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed

/**
 * Discover Destination (Section 05, Section 08 & Phase 5):
 * - Video Ingestion (SAF & Authorized HTTPS URLs)
 * - Gemini AI Highlight Detection with Acoustic RMS Spike Analysis
 * - 2 Distinct Non-Overlapping Clip Candidates with Individual Bounded Preview
 * - Start/End Trim Adjustment (-1s / +1s), Approve/Reject Lock, and Regeneration
 */
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    apiKeyStatus: ApiKeyStatus,
    onIngestLocalSafUri: (Uri?) -> Unit,
    onToggleDirectUrlInput: () -> Unit,
    onDirectUrlInputChanged: (String) -> Unit,
    onSubmitDirectUrl: () -> Unit,
    onClearSourceVideo: () -> Unit,
    onSelectClipDuration: (Int) -> Unit,
    onToggleFocusCategory: (String) -> Unit,
    onRunAiHighlightDetection: () -> Unit,
    onSelectCandidateForPreview: (String) -> Unit,
    onAdjustCandidateBounds: (String, Long, Long) -> Unit,
    onToggleApproveCandidate: (String) -> Unit,
    onRejectCandidate: (String) -> Unit,
    onOpenStudioWithSourceAndClips: (VideoSourceMetadata?, List<ClipCandidate>) -> Unit,
    onConfigureApiKeyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onIngestLocalSafUri(uri)
    }

    val launchSafPicker = {
        safVideoLauncher.launch(
            arrayOf("video/mp4", "video/quicktime", "video/webm", "video/x-matroska", "video/*")
        )
    }

    val durationOptions = listOf(15, 30, 45, 60)
    val focusCategories = listOf(
        "Gameplay + Reactions",
        "Engaging Conversations",
        "High-Energy Clutch Moments",
        "Key Commentary"
    )

    val activePreviewClip = uiState.candidates.firstOrNull { it.id == uiState.previewingClipId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Video Ingestion & Source Preview
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "1. Video Ingestion & Source Preview",
                subtitle = "Storage Access Framework (SAF) local import or authorized direct HTTPS media link"
            )

            val metadata = uiState.sourceMetadata
            if (metadata == null) {
                CliprFrameBracketBox(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (uiState.isIngesting) {
                                if (uiState.downloadProgressPercent > 0) {
                                    "Downloading Authorized Media (${uiState.downloadProgressPercent}%)..."
                                } else {
                                    "Inspecting Video Tracks & Metadata..."
                                }
                            } else {
                                "Import Long-Form Video or Stream Recording"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = PorcelainWhite
                        )
                        Text(
                            text = "Select a local MP4, MOV, WebM, or MKV recording via Android's Storage Access Framework, or provide an authorized direct HTTPS video link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryGrey
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CliprPrimaryButton(
                                text = "Import Local Video (SAF)",
                                leadingIcon = Icons.Outlined.FolderOpen,
                                enabled = !uiState.isIngesting,
                                onClick = launchSafPicker,
                                modifier = Modifier.weight(1f)
                            )
                            CliprOutlineButton(
                                text = if (uiState.showDirectUrlInput) "Hide URL" else "Direct URL",
                                leadingIcon = Icons.Outlined.Link,
                                enabled = !uiState.isIngesting,
                                onClick = onToggleDirectUrlInput
                            )
                        }
                    }
                }
            } else {
                CliprFlatPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = metadata.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = PorcelainWhite
                            )
                            Text(
                                text = "${metadata.resolutionLabel} (${metadata.aspectRatioLabel}) · ${metadata.formattedDuration} · ${metadata.formattedFileSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryGrey
                            )
                        }
                        CliprStatusBadge(
                            text = if (activePreviewClip != null) {
                                "PREVIEWING CLIP #${activePreviewClip.clipNumber}"
                            } else if (metadata.requiresProxyDownsample) {
                                "360P PROXY MODE"
                            } else {
                                "DIRECT UPLOAD"
                            },
                            isAccent = activePreviewClip != null,
                            isActive = activePreviewClip == null
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    CliprExoPlayerPreview(
                        sourceUri = metadata.sourceUri,
                        totalDurationMs = metadata.durationMs,
                        clipStartMs = activePreviewClip?.startMs ?: 0L,
                        clipEndMs = activePreviewClip?.endMs ?: metadata.durationMs
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    CliprHairlineDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetadataMetricCell(
                            label = "DURATION",
                            value = metadata.formattedDuration,
                            modifier = Modifier.weight(1f)
                        )
                        MetadataMetricCell(
                            label = "RESOLUTION",
                            value = "${metadata.resolutionLabel} (${metadata.aspectRatioLabel})",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetadataMetricCell(
                            label = "CODECS",
                            value = metadata.shortCodecSummary,
                            modifier = Modifier.weight(1f)
                        )
                        MetadataMetricCell(
                            label = "FRAME RATE",
                            value = "${metadata.frameRate.toInt()} FPS · ${if (metadata.hasAudioTrack) "${metadata.audioSampleRateHz / 1000}kHz" else "Muted"}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CliprOutlineButton(
                            text = "Replace Video",
                            leadingIcon = Icons.Outlined.FolderOpen,
                            onClick = launchSafPicker,
                            modifier = Modifier.weight(1f)
                        )
                        CliprOutlineButton(
                            text = "Clear",
                            leadingIcon = Icons.Outlined.DeleteOutline,
                            onClick = onClearSourceVideo
                        )
                    }
                }
            }

            if (uiState.showDirectUrlInput && metadata == null) {
                CliprFlatPanel {
                    Text(
                        text = "Authorized Direct Video URL",
                        style = MaterialTheme.typography.titleSmall,
                        color = PorcelainWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter a direct HTTPS link to an authorized MP4, MOV, or WebM file. Unauthorized YouTube or Twitch downloading is strictly prohibited.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryGrey
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.directUrlInput,
                        onValueChange = onDirectUrlInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text = "https://example.com/media/stream.mp4",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri
                        ),
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
                    CliprPrimaryButton(
                        text = "Verify & Load Direct URL",
                        leadingIcon = Icons.Outlined.CheckCircle,
                        enabled = !uiState.isIngesting && uiState.directUrlInput.isNotBlank(),
                        onClick = onSubmitDirectUrl,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!uiState.ingestionErrorTitle.isNullOrBlank() && !uiState.ingestionErrorMessage.isNullOrBlank()) {
                CliprFlatPanel(borderColor = SignalRed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = SignalRed
                        )
                        Text(
                            text = uiState.ingestionErrorTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = SignalRed
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.ingestionErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = PorcelainWhite
                    )
                    if (uiState.showLocalImportFallbackCta) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CliprPrimaryButton(
                            text = "Import Permitted Local Copy (SAF)",
                            leadingIcon = Icons.Outlined.FolderOpen,
                            onClick = launchSafPicker,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 2. AI Clipping Preferences & Analysis Trigger
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "2. AI Clipping Preferences",
                subtitle = "Target 2 distinct, non-overlapping clips validated against source duration"
            )

            CliprFlatPanel {
                Text(
                    text = "Target Clip Duration",
                    style = MaterialTheme.typography.titleSmall,
                    color = PorcelainWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    durationOptions.forEach { seconds ->
                        CliprStatusBadge(
                            text = "${seconds}s",
                            isActive = uiState.targetClipDurationSec == seconds,
                            isAccent = uiState.targetClipDurationSec == seconds,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectClipDuration(seconds) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Highlight Detection Focus",
                    style = MaterialTheme.typography.titleSmall,
                    color = PorcelainWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    focusCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleFocusCategory(category) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyLarge,
                                color = PorcelainWhite
                            )
                            CliprStatusBadge(
                                text = if (uiState.selectedFocusCategory == category) "ACTIVE" else "SELECT",
                                isAccent = uiState.selectedFocusCategory == category
                            )
                        }
                    }
                }

                if (!uiState.aiStatusStepMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBlack)
                            .border(1.dp, SignalRed)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.aiStatusStepMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = PorcelainWhite
                        )
                    }
                }

                if (!uiState.aiErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBlack)
                            .border(1.dp, SignalRed)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.aiErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SignalRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                CliprPrimaryButton(
                    text = when {
                        uiState.sourceMetadata == null -> "Import a Video Above to Begin"
                        apiKeyStatus != ApiKeyStatus.VALID -> "Configure Gemini API Key to Analyse"
                        uiState.isAnalyzingAi -> "Running Gemini Highlight Detection..."
                        uiState.candidates.isNotEmpty() -> "Regenerate Unapproved Clips"
                        else -> "Detect 2 Highlight Clips With Gemini"
                    },
                    leadingIcon = if (uiState.candidates.isNotEmpty()) {
                        Icons.Outlined.Refresh
                    } else {
                        Icons.Outlined.AutoAwesome
                    },
                    enabled = uiState.sourceMetadata != null && !uiState.isAnalyzingAi,
                    onClick = {
                        if (apiKeyStatus == ApiKeyStatus.VALID) {
                            onRunAiHighlightDetection()
                        } else {
                            onConfigureApiKeyClick()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Suggested Highlights Queue (Review, Preview, Adjust Start/End, Approve/Reject)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "3. Suggested Highlights (${uiState.candidates.size}/2 Clips)",
                subtitle = "User-approved or trimmed clips are locked and never overwritten by AI regeneration"
            )

            if (uiState.candidates.isEmpty()) {
                CliprFlatPanel {
                    Text(
                        text = if (uiState.sourceMetadata != null) {
                            "Ready to Analyse '${uiState.sourceMetadata.displayName}'"
                        } else {
                            "No Clip Candidates Yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = PorcelainWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Detect 2 Highlight Clips With Gemini' above to analyze audio energy peaks and video frames, returning 2 ranked non-overlapping clips with timestamps and explanations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryGrey
                    )
                }
            } else {
                uiState.candidates.forEach { clip ->
                    ClipCandidateCard(
                        clip = clip,
                        isPreviewSelected = uiState.previewingClipId == clip.id,
                        onPreviewClick = { onSelectCandidateForPreview(clip.id) },
                        onTrimStartMinus = { onAdjustCandidateBounds(clip.id, -1_000L, 0L) },
                        onTrimStartPlus = { onAdjustCandidateBounds(clip.id, 1_000L, 0L) },
                        onTrimEndMinus = { onAdjustCandidateBounds(clip.id, 0L, -1_000L) },
                        onTrimEndPlus = { onAdjustCandidateBounds(clip.id, 0L, 1_000L) },
                        onToggleApprove = { onToggleApproveCandidate(clip.id) },
                        onReject = { onRejectCandidate(clip.id) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                CliprPrimaryButton(
                    text = "Open Selected Clips in 9:16 Studio",
                    leadingIcon = Icons.Outlined.MovieFilter,
                    onClick = {
                        onOpenStudioWithSourceAndClips(uiState.sourceMetadata, uiState.candidates)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ClipCandidateCard(
    clip: ClipCandidate,
    isPreviewSelected: Boolean,
    onPreviewClick: () -> Unit,
    onTrimStartMinus: () -> Unit,
    onTrimStartPlus: () -> Unit,
    onTrimEndMinus: () -> Unit,
    onTrimEndPlus: () -> Unit,
    onToggleApprove: () -> Unit,
    onReject: () -> Unit
) {
    CliprFlatPanel(
        borderColor = if (clip.isApproved || isPreviewSelected) SignalRed else HairlineDivider
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CLIP #${clip.clipNumber} · ${clip.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = PorcelainWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${VideoSourceMetadata.formatDurationMs(clip.startMs)} → ${VideoSourceMetadata.formatDurationMs(clip.endMs)} (${clip.durationMs / 1000}s) · ${clip.categoryTag}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SignalRed
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CliprStatusBadge(
                    text = "HOOK ${clip.hookScore}/100",
                    isActive = true
                )
                if (clip.isApproved || clip.isUserEdited) {
                    CliprStatusBadge(
                        text = if (clip.isApproved) "APPROVED LOCK" else "USER EDITED",
                        isAccent = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = clip.rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryGrey
        )

        Spacer(modifier = Modifier.height(12.dp))
        CliprHairlineDivider()
        Spacer(modifier = Modifier.height(10.dp))

        // Fine Start / End Point Trim Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "START: ${VideoSourceMetadata.formatDurationMs(clip.startMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = PorcelainWhite
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CliprStatusBadge(
                    text = "-1.0s",
                    modifier = Modifier.clickable(onClick = onTrimStartMinus)
                )
                CliprStatusBadge(
                    text = "+1.0s",
                    modifier = Modifier.clickable(onClick = onTrimStartPlus)
                )
            }

            Text(
                text = "END: ${VideoSourceMetadata.formatDurationMs(clip.endMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = PorcelainWhite
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CliprStatusBadge(
                    text = "-1.0s",
                    modifier = Modifier.clickable(onClick = onTrimEndMinus)
                )
                CliprStatusBadge(
                    text = "+1.0s",
                    modifier = Modifier.clickable(onClick = onTrimEndPlus)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CliprOutlineButton(
                text = if (isPreviewSelected) "Previewing" else "Preview",
                leadingIcon = Icons.Outlined.PlayCircleOutline,
                onClick = onPreviewClick,
                modifier = Modifier.weight(1f)
            )
            CliprPrimaryButton(
                text = if (clip.isApproved) "Approved" else "Approve",
                leadingIcon = Icons.Outlined.CheckCircle,
                onClick = onToggleApprove,
                modifier = Modifier.weight(1f)
            )
            if (!clip.isApproved) {
                CliprOutlineButton(
                    text = "Reject",
                    leadingIcon = Icons.Outlined.Close,
                    onClick = onReject
                )
            }
        }
    }
}

@Composable
private fun MetadataMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ObsidianBlack)
            .border(1.dp, HairlineDivider)
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SecondaryGrey
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = PorcelainWhite
        )
    }
}
