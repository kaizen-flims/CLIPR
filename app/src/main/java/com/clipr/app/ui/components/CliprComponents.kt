package com.clipr.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.ui.theme.CliprShapes
import com.clipr.app.ui.theme.Graphite
import com.clipr.app.ui.theme.GraphiteElevated
import com.clipr.app.ui.theme.HairlineDivider
import com.clipr.app.ui.theme.ObsidianBlack
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed

/**
 * Top application bar displaying the official CLIPR geometric mark, active destination label,
 * and current BYOK API key status badge.
 */
@Composable
fun CliprTopBar(
    destinationLabel: String,
    apiKeyStatus: ApiKeyStatus,
    onApiKeyBadgeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CliprBrandLockup(showTagline = false, symbolSize = 24.dp)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(HairlineDivider)
                )
                Text(
                    text = destinationLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryGrey
                )
            }

            CliprStatusBadge(
                text = apiKeyStatus.label,
                isActive = apiKeyStatus == ApiKeyStatus.VALID,
                isAccent = apiKeyStatus == ApiKeyStatus.UNCONFIGURED || apiKeyStatus == ApiKeyStatus.INVALID,
                modifier = Modifier.clickable(onClick = onApiKeyBadgeClick)
            )
        }
        CliprHairlineDivider()
    }
}

/**
 * Restrained flat structural surface (#171717 Graphite) with a 1dp #262626 hairline border.
 * Zero drop shadows, zero neon borders, zero excessive corner rounding.
 */
@Composable
fun CliprFlatPanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Graphite,
    borderColor: Color = HairlineDivider,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = CliprShapes.small)
            .border(width = 1.dp, color = borderColor, shape = CliprShapes.small)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Architectural container framed with the four CLIPR geometric corner brackets.
 * Used for the 9:16 vertical Studio canvas and Discover video ingestion dropzone.
 */
@Composable
fun CliprFrameBracketBox(
    modifier: Modifier = Modifier,
    bracketColor: Color = SignalRed,
    backgroundColor: Color = Graphite,
    cornerLength: Dp = 14.dp,
    strokeWidth: Dp = 2.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(1.dp, HairlineDivider)
            .drawWithContent {
                drawContent()
                val len = cornerLength.toPx()
                val stroke = strokeWidth.toPx()
                val w = size.width
                val h = size.height

                // Top-left bracket
                drawRect(color = bracketColor, topLeft = Offset(0f, 0f), size = Size(len, stroke))
                drawRect(color = bracketColor, topLeft = Offset(0f, 0f), size = Size(stroke, len))
                // Top-right bracket
                drawRect(color = bracketColor, topLeft = Offset(w - len, 0f), size = Size(len, stroke))
                drawRect(color = bracketColor, topLeft = Offset(w - stroke, 0f), size = Size(stroke, len))
                // Bottom-left bracket
                drawRect(color = bracketColor, topLeft = Offset(0f, h - stroke), size = Size(len, stroke))
                drawRect(color = bracketColor, topLeft = Offset(0f, h - len), size = Size(stroke, len))
                // Bottom-right bracket
                drawRect(color = bracketColor, topLeft = Offset(w - len, h - stroke), size = Size(len, stroke))
                drawRect(color = bracketColor, topLeft = Offset(w - stroke, h - len), size = Size(stroke, len))
            },
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

@Composable
fun CliprPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CliprShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = SignalRed,
            contentColor = PorcelainWhite,
            disabledContainerColor = GraphiteElevated,
            disabledContentColor = SecondaryGrey
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) PorcelainWhite else SecondaryGrey
        )
    }
}

@Composable
fun CliprOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CliprShapes.small,
        border = BorderStroke(1.dp, HairlineDivider),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Graphite,
            contentColor = PorcelainWhite,
            disabledContainerColor = ObsidianBlack,
            disabledContentColor = SecondaryGrey
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = PorcelainWhite
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) PorcelainWhite else SecondaryGrey
        )
    }
}

@Composable
fun CliprSectionHeader(
    title: String,
    subtitle: String? = null,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PorcelainWhite
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryGrey
                    )
                }
            }
            if (trailingLabel != null && onTrailingClick != null) {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = SignalRed,
                    modifier = Modifier
                        .clickable(onClick = onTrailingClick)
                        .padding(start = 12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        CliprHairlineDivider()
    }
}

@Composable
fun CliprStatusBadge(
    text: String,
    isActive: Boolean = false,
    isAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isAccent -> SignalRed
        isActive -> PorcelainWhite
        else -> HairlineDivider
    }
    val textColor = when {
        isAccent -> SignalRed
        isActive -> PorcelainWhite
        else -> SecondaryGrey
    }

    Box(
        modifier = modifier
            .background(Graphite, shape = CliprShapes.extraSmall)
            .border(1.dp, borderColor, shape = CliprShapes.extraSmall)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
fun CliprHairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = HairlineDivider
    )
}
