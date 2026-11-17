package com.clipr.app.ui.screens.library

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipr.app.core.model.ExportedShort
import com.clipr.app.core.model.VideoSourceMetadata
import com.clipr.app.ui.components.CliprFlatPanel
import com.clipr.app.ui.components.CliprOutlineButton
import com.clipr.app.ui.components.CliprPrimaryButton
import com.clipr.app.ui.components.CliprSectionHeader
import com.clipr.app.ui.components.CliprStatusBadge
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey

/**
 * Library Destination (Section 08 & Phase 7):
 * - Saved projects (with permanent delete action)
 * - Rendered 1080×1920 clips (with FileProvider Share and Delete actions)
 * - Project history
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onSelectTab: (LibraryTab) -> Unit,
    onStartNewClipClick: () -> Unit,
    onDeleteProject: (String) -> Unit = {},
    onShareExportedShort: (ExportedShort) -> Unit = {},
    onDeleteExportedShort: (ExportedShort) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                CliprStatusBadge(
                    text = tab.title,
                    isActive = uiState.selectedTab == tab,
                    isAccent = uiState.selectedTab == tab,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectTab(tab) }
                )
            }
        }

        if (uiState.selectedTab == LibraryTab.SAVED_PROJECTS) {
            CliprSectionHeader(
                title = "Saved Projects (${uiState.savedProjects.size})",
                subtitle = "App-private project state with non-destructive edit locks"
            )

            if (uiState.savedProjects.isEmpty()) {
                CliprFlatPanel {
                    Text(
                        text = "No Saved Projects",
                        style = MaterialTheme.typography.titleMedium,
                        color = PorcelainWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Projects created in Discover and edited in Studio are saved in app-private storage with full history, deletion, and re-export controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryGrey
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    CliprPrimaryButton(
                        text = "New Clipping Project",
                        leadingIcon = Icons.Outlined.Add,
                        onClick = onStartNewClipClick
                    )
                }
            } else {
                uiState.savedProjects.forEach { project ->
                    CliprFlatPanel {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PorcelainWhite
                                )
                                Text(
                                    text = "${project.resolutionLabel} · ${VideoSourceMetadata.formatDurationMs(project.sourceDurationMs)} · ${project.lastModifiedLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryGrey
                                )
                            }
                            CliprOutlineButton(
                                text = "Delete",
                                leadingIcon = Icons.Outlined.DeleteOutline,
                                onClick = { onDeleteProject(project.projectId) }
                            )
                        }
                    }
                }
            }
        } else {
            CliprSectionHeader(
                title = "Rendered Clips (${uiState.renderedClips.size})",
                subtitle = "1080×1920 9:16 MP4 Shorts ready to share or delete"
            )

            if (uiState.renderedClips.isEmpty()) {
                CliprFlatPanel {
                    Text(
                        text = "No Rendered Shorts",
                        style = MaterialTheme.typography.titleMedium,
                        color = PorcelainWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Exported vertical clips appear here with file size, codec verification, native Android Share Sheet integration, and single-tap permanent deletion.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryGrey
                    )
                }
            } else {
                uiState.renderedClips.forEach { short ->
                    CliprFlatPanel {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = short.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PorcelainWhite
                                )
                                Text(
                                    text = "${short.resolution} · ${VideoSourceMetadata.formatDurationMs(short.durationMs)} · ${"%.1f".format(short.fileSizeMb)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryGrey
                                )
                            }
                            CliprStatusBadge(text = short.exportedAtLabel, isActive = true)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CliprPrimaryButton(
                                text = "Share Short",
                                leadingIcon = Icons.Outlined.Share,
                                onClick = { onShareExportedShort(short) },
                                modifier = Modifier.weight(1f)
                            )
                            CliprOutlineButton(
                                text = "Delete",
                                leadingIcon = Icons.Outlined.DeleteOutline,
                                onClick = { onDeleteExportedShort(short) }
                            )
                        }
                    }
                }
            }
        }
    }
}
