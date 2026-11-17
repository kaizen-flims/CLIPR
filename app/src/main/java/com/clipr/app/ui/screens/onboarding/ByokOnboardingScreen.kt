package com.clipr.app.ui.screens.onboarding

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
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
import com.clipr.app.R
import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.core.model.GeminiModelOption
import com.clipr.app.ui.components.CliprBrandLockup
import com.clipr.app.ui.components.CliprFlatPanel
import com.clipr.app.ui.components.CliprFrameBracketBox
import com.clipr.app.ui.components.CliprHairlineDivider
import com.clipr.app.ui.components.CliprOutlineButton
import com.clipr.app.ui.components.CliprPrimaryButton
import com.clipr.app.ui.components.CliprSectionHeader
import com.clipr.app.ui.components.CliprStatusBadge
import com.clipr.app.ui.screens.settings.SettingsUiState
import com.clipr.app.ui.theme.CliprShapes
import com.clipr.app.ui.theme.Graphite
import com.clipr.app.ui.theme.HairlineDivider
import com.clipr.app.ui.theme.ObsidianBlack
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed

/**
 * First-Launch BYOK Onboarding Screen (Section 04):
 * 1. API key input screen with masked visibility toggle
 * 2. Model selection from supported Gemini models
 * 3. Live API key validation with clear success & failure states
 * 4. Explicit privacy explanation of Google Gemini external media processing vs local Media3 rendering
 */
@Composable
fun ByokOnboardingScreen(
    uiState: SettingsUiState,
    onApiKeyInputChanged: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onSelectModel: (GeminiModelOption) -> Unit,
    onValidateAndSaveClick: () -> Unit,
    onSkipOnboardingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Brand Header
        CliprFrameBracketBox(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CliprBrandLockup(showTagline = true, symbolSize = 34.dp)
                    CliprStatusBadge(text = "BYOK ONBOARDING", isAccent = true)
                }
                Text(
                    text = "CLIPR operates on a Bring-Your-Own-Key (BYOK) architecture. No mandatory subscriptions and no proprietary middleman servers—you control your own Google Gemini API usage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryGrey
                )
            }
        }

        // 2. API Key Input & Validation Panel
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "1. Enter Your Gemini API Key",
                subtitle = "Protected at rest with Android Keystore AES-256-GCM encryption"
            )

            CliprFlatPanel {
                OutlinedTextField(
                    value = uiState.apiKeyInput,
                    onValueChange = onApiKeyInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = "Google AI Studio Gemini API Key (AIza...)",
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = SecondaryGrey
                    )
                    Text(
                        text = "Your key is never hardcoded, logged, committed to Git, or exported in project files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryGrey
                    )
                }
            }
        }

        // 3. Model Selection
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "2. Select Gemini Model",
                subtitle = "Choose the default multimodal intelligence model for highlight ranking"
            )

            CliprFlatPanel {
                GeminiModelOption.entries.forEachIndexed { index, model ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CliprHairlineDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectModel(model) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${model.displayName} (${model.modelId})",
                                style = MaterialTheme.typography.titleSmall,
                                color = PorcelainWhite
                            )
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryGrey
                            )
                        }
                        CliprStatusBadge(
                            text = if (uiState.selectedModel == model) "ACTIVE" else "SELECT",
                            isAccent = uiState.selectedModel == model
                        )
                    }
                }
            }
        }

        // 4. Mandatory Privacy & External Processing Explanation
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CliprSectionHeader(
                title = "3. Privacy & Processing Architecture",
                subtitle = "What runs on Google's Gemini API vs what runs locally on your device"
            )

            CliprFlatPanel {
                Text(
                    text = stringResource(id = R.string.privacy_disclosure_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PorcelainWhite
                )
            }
        }

        // 5. Primary & Secondary Onboarding Actions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CliprPrimaryButton(
                text = if (uiState.apiKeyStatus == ApiKeyStatus.VALIDATING) {
                    "Validating Key With Google Gemini..."
                } else {
                    "Validate & Encrypt API Key"
                },
                leadingIcon = Icons.Outlined.CheckCircle,
                enabled = uiState.apiKeyStatus != ApiKeyStatus.VALIDATING && uiState.apiKeyInput.isNotBlank(),
                onClick = onValidateAndSaveClick,
                modifier = Modifier.fillMaxWidth()
            )

            CliprOutlineButton(
                text = "Configure Later (Explore Studio First)",
                onClick = onSkipOnboardingClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
