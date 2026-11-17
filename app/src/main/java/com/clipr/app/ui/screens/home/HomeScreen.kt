package com.clipr.app.ui.screens.home

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
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.ui.components.CliprBrandLockup
import com.clipr.app.ui.components.CliprFlatPanel
import com.clipr.app.ui.components.CliprFrameBracketBox
import com.clipr.app.ui.components.CliprOutlineButton
import com.clipr.app.ui.components.CliprPrimaryButton
import com.clipr.app.ui.components.CliprSectionHeader
import com.clipr.app.ui.components.CliprStatusBadge
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed

/**
 * Home Destination (Section 08):
 * - CLIPR branding ("From Long to Legendary.")
 * - Start clipping action
 * - Recent projects
 * - Quick access to previous exports
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    apiKeyStatus: ApiKeyStatus,
    onStartClippingClick: () -> Unit,
    onConfigureApiKeyClick: () -> Unit,
    onOpenStudioClick: () -> Unit,
    onOpenLibraryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Hero Branding & Primary Start Clipping Action
        CliprFrameBracketBox(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    CliprBrandLockup(showTagline = true, symbolSize = 36.dp)
                    CliprStatusBadge(
                        text = "${CliprBrandConfig.EXPORT_WIDTH_PX}×${CliprBrandConfig.EXPORT_HEIGHT_PX} · ${CliprBrandConfig.EXPORT_ASPECT_RATIO_LABEL}",
                        isActive = true
                    )
                }

                Text(
                    text = "Transform long-form videos, gaming sessions, and streams into vertical 9:16 Shorts using your own Gemini API key and on-device Media3 hardware rendering.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryGrey
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CliprPrimaryButton(
                        text = "Start Clipping",
                        leadingIcon = Icons.Outlined.AddCircleOutline,
                        onClick = onStartClippingClick,
                        modifier = Modifier.weight(1f)
                    )
                    CliprOutlineButton(
                        text = "Open Studio",
                        leadingIcon = Icons.Outlined.MovieFilter,
                        onClick = onOpenStudioClick
                    )
                }
            }
        }

        // 2. BYOK Onboarding Status Banner
        if (apiKeyStatus != ApiKeyStatus.VALID) {
            CliprFlatPanel(borderColor = SignalRed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bring Your Own Gemini API Key (BYOK)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PorcelainWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Configure your personal Gemini API key in Settings before running AI highlight detection. Stored locally with Android Keystore encryption.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryGrey
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                CliprOutlineButton(
                    text = "Configure Gemini Key",
                    leadingIcon = Icons.Outlined.Key,
                    onClick = onConfigureApiKeyClick
                )
            }
        }

        // 3. Recent Projects Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "Recent Projects",
                subtitle = "Active clipping workspaces stored in app-private storage",
                trailingLabel = "View Library",
                onTrailingClick = onOpenLibraryClick
            )

            if (uiState.recentProjects.isEmpty()) {
                CliprFlatPanel {
                    Text(
                        text = "No clipping projects yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = PorcelainWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import a local video file or permitted stream recording in Discover to extract 2 non-overlapping highlight clips.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryGrey
                    )
                }
            }
        }

        // 4. Quick Access to Previous Exports
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "Previous Exports",
                subtitle = "Rendered 1080×1920 H.264 + AAC vertical Shorts",
                trailingLabel = "All Exports",
                onTrailingClick = onOpenLibraryClick
            )

            if (uiState.recentExports.isEmpty()) {
                CliprFlatPanel {
                    Text(
                        text = "No rendered Shorts yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = PorcelainWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Completed 9:16 exports rendered on-device via Media3 Transformer will appear here for instant preview and sharing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryGrey
                    )
                }
            }
        }
    }
}
