package com.clipr.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Locked Primary Application Destinations (Section 08):
 * 1. Home
 * 2. Discover
 * 3. Studio
 * 4. Library
 * 5. Settings
 */
enum class CliprDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(
        route = "home",
        label = "Home",
        icon = Icons.Outlined.Home
    ),
    DISCOVER(
        route = "discover",
        label = "Discover",
        icon = Icons.Outlined.Explore
    ),
    STUDIO(
        route = "studio",
        label = "Studio",
        icon = Icons.Outlined.AutoAwesomeMotion
    ),
    LIBRARY(
        route = "library",
        label = "Library",
        icon = Icons.Outlined.VideoLibrary
    ),
    SETTINGS(
        route = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings
    );

    companion object {
        fun fromRoute(route: String?): CliprDestination {
            return entries.firstOrNull { it.route == route } ?: HOME
        }
    }
}
