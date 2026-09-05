package com.joel.gta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joel.gta.ui.theme.LocalGtaColors

/**
 * Minimalist 4-String Fretboard Branding Icon
 *
 * Designed to resemble pro audio gear / boutique pedal branding:
 * - Dark rounded squircle container (#16171D with subtle border #2C2F3A)
 * - 4 parallel vertical string lines with realistic gauge progression (thicker on left, thinner on right)
 * - 2 horizontal fret wire lines
 * - A minimalist geometric fret marker dot centered between frets using the theme accent color
 */
@Composable
fun GtaBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val customColors = LocalGtaColors.current
    val accentColor = customColors.chordAccent
    val squircleShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(squircleShape)
            .background(Color(0xFF16171D))
            .border(1.dp, Color(0xFF2C2F3A), squircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val width = this.size.width
            val height = this.size.height

            // 2 Horizontal Fret Wire Lines
            val fret1Y = height * 0.33f
            val fret2Y = height * 0.68f
            val fretWireColor = Color(0xFF474C59)
            val fretWireStroke = 1.4f.dp.toPx()

            drawLine(
                color = fretWireColor,
                start = Offset(0f, fret1Y),
                end = Offset(width, fret1Y),
                strokeWidth = fretWireStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = fretWireColor,
                start = Offset(0f, fret2Y),
                end = Offset(width, fret2Y),
                strokeWidth = fretWireStroke,
                cap = StrokeCap.Round
            )

            // Minimalist Geometric Fret Marker Dot (Center between frets 1 and 2)
            val markerCenterX = width / 2f
            val markerCenterY = (fret1Y + fret2Y) / 2f
            val markerRadius = 2.4f.dp.toPx()

            // Subtle glow around dot
            drawCircle(
                color = accentColor.copy(alpha = 0.25f),
                radius = markerRadius * 1.8f,
                center = Offset(markerCenterX, markerCenterY)
            )
            // Accent fret marker dot
            drawCircle(
                color = accentColor,
                radius = markerRadius,
                center = Offset(markerCenterX, markerCenterY)
            )

            // 4 Vertical Strings (Gauge decreases from Left to Right: thickest -> thinnest)
            val stringGauges = listOf(
                2.6f.dp.toPx(), // String 4 (Thickest)
                2.0f.dp.toPx(), // String 3
                1.4f.dp.toPx(), // String 2
                1.0f.dp.toPx()  // String 1 (Thinnest)
            )
            val stringColor = Color(0xFFE2E6EE)
            val stringStep = width / 5f

            for (i in 0 until 4) {
                val x = stringStep * (i + 1)
                drawLine(
                    color = stringColor.copy(alpha = 0.90f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = stringGauges[i],
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
