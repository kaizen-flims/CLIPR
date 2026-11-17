package com.clipr.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipr.app.core.model.CliprBrandConfig
import com.clipr.app.ui.theme.PlusJakartaSansFontFamily
import com.clipr.app.ui.theme.PorcelainWhite
import com.clipr.app.ui.theme.SecondaryGrey
import com.clipr.app.ui.theme.SignalRed

/**
 * Official Locked CLIPR Symbol & Wordmark (Section 02):
 * - Four flat geometric frame corners
 * - Simple play triangle in the centre
 * - CLIPR wordmark in bold Plus Jakarta Sans
 * Zero glow, zero neon, zero gradients.
 */
@Composable
fun CliprSymbolMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.105f
        val arm = w * 0.28f

        // 1. Top-Left Geometric Frame Corner
        drawRect(color = SignalRed, topLeft = Offset(0f, 0f), size = Size(arm, stroke))
        drawRect(color = SignalRed, topLeft = Offset(0f, 0f), size = Size(stroke, arm))

        // 2. Top-Right Geometric Frame Corner
        drawRect(color = SignalRed, topLeft = Offset(w - arm, 0f), size = Size(arm, stroke))
        drawRect(color = SignalRed, topLeft = Offset(w - stroke, 0f), size = Size(stroke, arm))

        // 3. Bottom-Left Geometric Frame Corner
        drawRect(color = SignalRed, topLeft = Offset(0f, h - stroke), size = Size(arm, stroke))
        drawRect(color = SignalRed, topLeft = Offset(0f, h - arm), size = Size(stroke, arm))

        // 4. Bottom-Right Geometric Frame Corner
        drawRect(color = SignalRed, topLeft = Offset(w - arm, h - stroke), size = Size(arm, stroke))
        drawRect(color = SignalRed, topLeft = Offset(w - stroke, h - arm), size = Size(stroke, arm))

        // 5. Center Flat Play Triangle
        val playPath = Path().apply {
            moveTo(w * 0.38f, h * 0.30f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.38f, h * 0.70f)
            close()
        }
        drawPath(path = playPath, color = PorcelainWhite)
    }
}

@Composable
fun CliprBrandLockup(
    modifier: Modifier = Modifier,
    showTagline: Boolean = false,
    symbolSize: Dp = 28.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CliprSymbolMark(size = symbolSize)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = CliprBrandConfig.PRODUCT_NAME,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.2.sp,
                color = PorcelainWhite
            )
            if (showTagline) {
                Text(
                    text = CliprBrandConfig.TAGLINE,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGrey
                )
            }
        }
    }
}
