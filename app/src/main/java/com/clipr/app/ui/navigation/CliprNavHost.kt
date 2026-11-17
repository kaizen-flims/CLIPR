package com.clipr.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clipr.app.ui.components.CliprTopBar
import com.clipr.app.ui.screens.discover.DiscoverScreen
import com.clipr.app.ui.screens.discover.DiscoverViewModel
import com.clipr.app.ui.screens.home.HomeScreen
import com.clipr.app.ui.screens.home.HomeViewModel
import com.clipr.app.ui.screens.library.LibraryScreen
import com.clipr.app.ui.screens.library.LibraryViewModel
import com.clipr.app.ui.screens.onboarding.ByokOnboardingScreen
import com.clipr.app.ui.screens.settings.SettingsScreen
import com.clipr.app.ui.screens.settings.SettingsViewModel
import com.clipr.app.ui.screens.studio.StudioScreen
import com.clipr.app.ui.screens.studio.StudioViewModel
import com.clipr.app.ui.theme.Graphite
import com.clipr.app.ui.theme.HairlineDivider
import com.clipr.app.ui.theme.ObsidianBlack
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed

@Composable
fun CliprAppScaffold(
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = CliprDestination.fromRoute(backStackEntry?.destination?.route)

    val homeViewModel: HomeViewModel = viewModel()
    val discoverViewModel: DiscoverViewModel = viewModel()
    val studioViewModel: StudioViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    if (settingsState.isOnboardingRequired) {
        ByokOnboardingScreen(
            uiState = settingsState,
            onApiKeyInputChanged = settingsViewModel::onApiKeyInputChanged,
            onToggleKeyVisibility = settingsViewModel::toggleApiKeyVisibility,
            onSelectModel = settingsViewModel::selectGeminiModel,
            onValidateAndSaveClick = settingsViewModel::validateAndSaveApiKey,
            onSkipOnboardingClick = settingsViewModel::skipOnboardingForNow
        )
        return
    }

    val navigateToDestination: (CliprDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        containerColor = ObsidianBlack,
        topBar = {
            CliprTopBar(
                destinationLabel = currentDestination.label,
                apiKeyStatus = settingsState.apiKeyStatus,
                onApiKeyBadgeClick = { navigateToDestination(CliprDestination.SETTINGS) }
            )
        },
        bottomBar = {
            CliprPillNavigationBar(
                currentDestination = currentDestination,
                onSelectDestination = navigateToDestination
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CliprDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBlack)
        ) {
            composable(CliprDestination.HOME.route) {
                val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    uiState = uiState,
                    apiKeyStatus = settingsState.apiKeyStatus,
                    onStartClippingClick = { navigateToDestination(CliprDestination.DISCOVER) },
                    onConfigureApiKeyClick = { navigateToDestination(CliprDestination.SETTINGS) },
                    onOpenStudioClick = { navigateToDestination(CliprDestination.STUDIO) },
                    onOpenLibraryClick = { navigateToDestination(CliprDestination.LIBRARY) }
                )
            }

            composable(CliprDestination.DISCOVER.route) {
                val uiState by discoverViewModel.uiState.collectAsStateWithLifecycle()
                DiscoverScreen(
                    uiState = uiState,
                    apiKeyStatus = settingsState.apiKeyStatus,
                    onIngestLocalSafUri = discoverViewModel::ingestLocalSafUri,
                    onToggleDirectUrlInput = discoverViewModel::toggleDirectUrlInput,
                    onDirectUrlInputChanged = discoverViewModel::onDirectUrlInputChanged,
                    onSubmitDirectUrl = discoverViewModel::ingestDirectUrl,
                    onClearSourceVideo = discoverViewModel::clearSourceVideo,
                    onSelectClipDuration = discoverViewModel::setTargetClipDurationSec,
                    onToggleFocusCategory = discoverViewModel::setFocusCategory,
                    onRunAiHighlightDetection = discoverViewModel::runGeminiHighlightDetection,
                    onSelectCandidateForPreview = discoverViewModel::selectCandidateForPreview,
                    onAdjustCandidateBounds = discoverViewModel::adjustCandidateClipBounds,
                    onToggleApproveCandidate = discoverViewModel::toggleApproveCandidate,
                    onRejectCandidate = discoverViewModel::rejectCandidateClip,
                    onOpenStudioWithSourceAndClips = { metadata, clips ->
                        studioViewModel.attachSourceAndClips(metadata, clips)
                        navigateToDestination(CliprDestination.STUDIO)
                    },
                    onConfigureApiKeyClick = { navigateToDestination(CliprDestination.SETTINGS) }
                )
            }

            composable(CliprDestination.STUDIO.route) {
                val uiState by studioViewModel.uiState.collectAsStateWithLifecycle()
                StudioScreen(
                    uiState = uiState,
                    onSelectClipIndex = studioViewModel::selectActiveClipIndex,
                    onSelectLayoutPreset = studioViewModel::selectLayoutPreset,
                    onUpdateFacecamSplitRatio = studioViewModel::updateUpperSplitRatio,
                    onToggleSeparatorBorder = studioViewModel::toggleSeparatorBorder,
                    onRunAutoFaceDetection = studioViewModel::runAutoFacecamDetection,
                    onPanFacecamCrop = studioViewModel::panFacecamCrop,
                    onPanGameplayCrop = studioViewModel::panGameplayCrop,
                    onAttachSecondaryFacecamTrack = studioViewModel::attachSecondaryFacecamTrack,
                    onSelectSubtitlePreset = studioViewModel::selectSubtitlePreset,
                    onUpdateSubtitleFontSize = studioViewModel::updateSubtitleFontSizeSp,
                    onUpdateSubtitleVerticalRatio = studioViewModel::updateSubtitleVerticalRatio,
                    onUpdateActiveWordColor = studioViewModel::updateActiveWordColor,
                    onToggleWordHighlight = studioViewModel::toggleAnimatedWordHighlight,
                    onToggleSubtitleBackgroundBox = studioViewModel::toggleSubtitleBackgroundBox,
                    onTranscriptInputChanged = studioViewModel::onTranscriptInputChanged,
                    onUpdatePreviewScrubPosition = studioViewModel::updatePreviewScrubPositionMs,
                    onImportInDiscoverClick = { navigateToDestination(CliprDestination.DISCOVER) },
                    onRenderShortClick = {
                        studioViewModel.startLocalShortRender {
                            navigateToDestination(CliprDestination.LIBRARY)
                        }
                    },
                    onCancelRenderClick = studioViewModel::cancelActiveRender
                )
            }

            composable(CliprDestination.LIBRARY.route) {
                val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                LibraryScreen(
                    uiState = uiState,
                    onSelectTab = libraryViewModel::selectTab,
                    onStartNewClipClick = { navigateToDestination(CliprDestination.DISCOVER) },
                    onDeleteProject = libraryViewModel::deleteProject,
                    onShareExportedShort = libraryViewModel::shareExportedShort,
                    onDeleteExportedShort = libraryViewModel::deleteExportedShort
                )
            }

            composable(CliprDestination.SETTINGS.route) {
                SettingsScreen(
                    uiState = settingsState,
                    onApiKeyInputChanged = settingsViewModel::onApiKeyInputChanged,
                    onToggleKeyVisibility = settingsViewModel::toggleApiKeyVisibility,
                    onValidateAndSaveKey = settingsViewModel::validateAndSaveApiKey,
                    onRemoveApiKey = settingsViewModel::removeAndWipeApiKey,
                    onSelectModel = settingsViewModel::selectGeminiModel,
                    onToggleHardwareEncoding = settingsViewModel::setPreferHardwareEncoding,
                    onToggleCpuFallback = settingsViewModel::setAllowCpuFallback
                )
            }
        }
    }
}

@Composable
private fun CliprPillNavigationBar(
    currentDestination: CliprDestination,
    onSelectDestination: (CliprDestination) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBlack)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Graphite, CircleShape)
                .border(width = 1.dp, color = HairlineDivider, shape = CircleShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CliprDestination.entries.forEach { destination ->
                val isSelected = currentDestination == destination

                val pillFillColor by animateColorAsState(
                    targetValue = if (isSelected) SignalRed else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "navPillFill"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) PorcelainWhite else SecondaryGrey,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "navPillContent"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(pillFillColor, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectDestination(destination) }
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
