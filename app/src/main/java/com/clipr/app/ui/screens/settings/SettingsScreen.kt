package com.clipr.app.ui.screens.settings

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clipr.app.BuildConfig
import com.clipr.app.R
import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.core.model.GeminiModelOption
import com.clipr.app.ui.components.CliprFlatPanel
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
 * Settings Destination (Section 04, Section 08 & Section 10):
 * - Gemini API configuration (BYOK input, validation, replacement, and cryptographic removal)
 * - Model selection (`gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash-lite`)
 * - Rendering preferences (Hardware MediaCodec + CPU fallback)
 * - Privacy information (explicit disclosure of Gemini external API processing)
 * - Application information
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiKeyInputChanged: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onValidateAndSaveKey: () -> Unit,
    onRemoveApiKey: () -> Unit,
    onSelectModel: (GeminiModelOption) -> Unit,
    onToggleHardwareEncoding: (Boolean) -> Unit,
    onToggleCpuFallback: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Gemini BYOK Configuration (Validate, Replace, or Remove Key)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "Gemini API Configuration (BYOK)",
                subtitle = "Encrypted at rest via Android Keystore (AES-256-GCM)"
            )

            CliprFlatPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Stored Key Preview",
                            style = MaterialTheme.typography.titleSmall,
                            color = PorcelainWhite
                        )
                        Text(
                            text = uiState.maskedKeyPreview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryGrey
                        )
                    }
                    CliprStatusBadge(
                        text = uiState.apiKeyStatus.label,
                        isActive = uiState.apiKeyStatus == ApiKeyStatus.VALID,
                        isAccent = uiState.apiKeyStatus != ApiKeyStatus.VALID
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                CliprHairlineDivider()
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.apiKeyInput,
                    onValueChange = onApiKeyInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = if (uiState.hasStoredKey) {
                                "Replace Gemini API Key (AIza...)"
                            } else {
                                "Enter Gemini API Key (AIza...)"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (uiState.isApiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = SignalRed
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleKeyVisibility) {
                            Icon(
                                imageVector = if (uiState.isApiKeyVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = "Toggle key visibility",
                                tint = SecondaryGrey
                            )
                        }
                    },
                    shape = CliprShapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalRed,
                        unfocusedBorderColor = HairlineDivider,
                        focusedTextColor = PorcelainWhite,
                        unfocusedTextColor = PorcelainWhite,
                        cursorColor = SignalRed,
                        focusedContainerColor = ObsidianBlack,
                        unfocusedContainerColor = ObsidianBlack,
                        focusedLabelColor = PorcelainWhite,
                        unfocusedLabelColor = SecondaryGrey
                    )
                )

                if (!uiState.statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBlack)
                            .border(
                                width = 1.dp,
                                color = if (uiState.isErrorMessage) SignalRed else PorcelainWhite
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isErrorMessage) SignalRed else PorcelainWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CliprPrimaryButton(
                        text = if (uiState.apiKeyStatus == ApiKeyStatus.VALIDATING) {
                            "Validating..."
                        } else if (uiState.hasStoredKey) {
                            "Validate & Replace Key"
                        } else {
                            "Validate & Save Key"
                        },
                        leadingIcon = Icons.Outlined.CheckCircle,
                        enabled = uiState.apiKeyStatus != ApiKeyStatus.VALIDATING && uiState.apiKeyInput.isNotBlank(),
                        onClick = onValidateAndSaveKey,
                        modifier = Modifier.weight(1f)
                    )

                    if (uiState.hasStoredKey) {
                        CliprOutlineButton(
                            text = "Remove Key",
                            leadingIcon = Icons.Outlined.DeleteOutline,
                            onClick = onRemoveApiKey
                        )
                    }
                }
            }
        }

        // 2. Gemini Model Selection
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "Model Selection",
                subtitle = "Select the Gemini multimodal model used for highlight detection"
            )

            CliprFlatPanel {
                GeminiModelOption.entries.forEachIndexed { index, option ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CliprHairlineDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectModel(option) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${option.displayName} (${option.modelId})",
                                style = MaterialTheme.typography.titleSmall,
                                color = PorcelainWhite
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryGrey
                            )
                        }
                        CliprStatusBadge(
                            text = if (uiState.selectedModel == option) "ACTIVE" else "SELECT",
                            isAccent = uiState.selectedModel == option
                        )
                    }
                }
            }
        }

        // 3. Rendering Preferences
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "Rendering Preferences",
                subtitle = "On-device Media3 Transformer 1080×1920 H.264 + AAC export pipeline"
            )

            CliprFlatPanel {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleHardwareEncoding(!uiState.preferHardwareEncoding) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hardware MediaCodec Acceleration",
                            style = MaterialTheme.typography.titleSmall,
                            color = PorcelainWhite
                        )
                        Text(
                            text = "Use hardware H.264 (video/avc) encoder when supported by device SoC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryGrey
                        )
                    }
                    CliprStatusBadge(
                        text = if (uiState.preferHardwareEncoding) "ON" else "OFF",
                        isAccent = uiState.preferHardwareEncoding
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                CliprHairlineDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleCpuFallback(!uiState.allowCpuFallback) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic CPU Encoder Fallback",
                            style = MaterialTheme.typography.titleSmall,
                            color = PorcelainWhite
                        )
                        Text(
                            text = "Step down gracefully to software encoder if hardware codec rejects input profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryGrey
                        )
                    }
                    CliprStatusBadge(
                        text = if (uiState.allowCpuFallback) "ON" else "OFF",
                        isAccent = uiState.allowCpuFallback
                    )
                }
            }
        }

        // 4. Privacy & Storage Disclosure
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "Privacy & External AI Processing Disclosure",
                subtitle = "Transparent hybrid cloud-AI and local-rendering boundaries"
            )

            CliprFlatPanel {
                Text(
                    text = stringResource(id = R.string.privacy_disclosure_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PorcelainWhite
                )
                Spacer(modifier = Modifier.height(10.dp))
                CliprHairlineDivider()
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${CliprBrandConfig.PRODUCT_NAME} v${BuildConfig.VERSION_NAME} · Target SDK 35 · Min SDK 26",
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryGrey
                )
            }
        }
    }
}
