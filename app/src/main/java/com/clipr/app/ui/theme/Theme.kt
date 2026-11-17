package com.clipr.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Locked CLIPR Dark Color Scheme.
 * Strictly flat Obsidian Black (#080808) and Graphite (#171717) surfaces,
 * Porcelain White (#F5F5F5) primary ink, Secondary Grey (#A3A3A3) muted copy,
 * and Signal Red (#E53935) functional accent.
 */
private val CliprDarkColorScheme = darkColorScheme(
    primary = SignalRed,
    onPrimary = PorcelainWhite,
    primaryContainer = GraphiteElevated,
    onPrimaryContainer = PorcelainWhite,
    secondary = SecondaryGrey,
    onSecondary = ObsidianBlack,
    secondaryContainer = Graphite,
    onSecondaryContainer = PorcelainWhite,
    tertiary = PorcelainWhite,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = PorcelainWhite,
    surface = Graphite,
    onSurface = PorcelainWhite,
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = SecondaryGrey,
    outline = HairlineDivider,
    outlineVariant = HairlineDivider,
    error = SignalRed,
    onError = PorcelainWhite
)

/**
 * Architectural sharp shapes — avoids excessive rounded cards (Section 02).
 */
val CliprShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp)
)

@Immutable
data class CliprSpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
    val hairline: Dp = 1.dp
)

val LocalCliprSpacing = staticCompositionLocalOf { CliprSpacing() }

@Composable
fun CliprTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CliprDarkColorScheme,
        typography = CliprTypography,
        shapes = CliprShapes,
        content = content
    )
}
