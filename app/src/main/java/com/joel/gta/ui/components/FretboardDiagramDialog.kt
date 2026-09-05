package com.joel.gta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joel.gta.data.model.ChordVoicing
import com.joel.gta.ui.theme.LocalGtaColors

@Composable
fun FretboardDiagramDialog(
    voicing: ChordVoicing,
    onDismissRequest: () -> Unit
) {
    val customColors = LocalGtaColors.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = customColors.surfaceBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Chord Name & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = voicing.chord,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = customColors.chordAccent
                        )
                        val fretsSummary = voicing.frets.joinToString(" ") { if (it == -1) "x" else it.toString() }
                        Text(
                            text = "Frets: $fretsSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.textSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Chord Chart",
                            tint = customColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas Fretboard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(customColors.canvasBackground, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CanvasFretboard(
                        voicing = voicing,
                        accentColor = customColors.chordAccent,
                        textColor = customColors.textPrimary,
                        mutedColor = customColors.textSecondary,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fret & String Hint
                Text(
                    text = "Low E  •  A  •  D  •  G  •  B  •  High E",
                    style = MaterialTheme.typography.labelSmall,
                    color = customColors.textSecondary.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap outside to dismiss",
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textSecondary.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CanvasFretboard(
    voicing: ChordVoicing,
    accentColor: Color,
    textColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val numStrings = 6
        val numFrets = 4 // Display 4 frets
        val leftMargin = 38f
        val rightMargin = 20f
        val topMargin = 34f
        val bottomMargin = 22f

        val boardWidth = size.width - leftMargin - rightMargin
        val boardHeight = size.height - topMargin - bottomMargin

        val stringSpacing = boardWidth / (numStrings - 1)
        val fretSpacing = boardHeight / numFrets

        // 1. Draw Nut or Fret Position Indicator
        val isNut = voicing.baseFret == 1
        if (isNut) {
            // Nut is a thick top bar
            drawLine(
                color = textColor,
                start = Offset(leftMargin, topMargin),
                end = Offset(leftMargin + boardWidth, topMargin),
                strokeWidth = 6f
            )
        } else {
            // Thin wire at top
            drawLine(
                color = mutedColor.copy(alpha = 0.7f),
                start = Offset(leftMargin, topMargin),
                end = Offset(leftMargin + boardWidth, topMargin),
                strokeWidth = 2f
            )
            // Draw starting fret number text on left (e.g. "3fr")
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 32f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                drawText("${voicing.baseFret}fr", leftMargin - 8f, topMargin + fretSpacing * 0.7f, paint)
            }
        }

        // 2. Draw Horizontal Fret Wires
        for (f in 1..numFrets) {
            val y = topMargin + f * fretSpacing
            drawLine(
                color = mutedColor.copy(alpha = 0.6f),
                start = Offset(leftMargin, y),
                end = Offset(leftMargin + boardWidth, y),
                strokeWidth = 2f
            )
        }

        // 3. Draw Vertical Strings (String 6 on left, String 1 on right)
        for (s in 0 until numStrings) {
            val x = leftMargin + s * stringSpacing
            // Thicker lines for lower bass strings (E, A, D)
            val stroke = when (s) {
                0 -> 4f
                1 -> 3.5f
                2 -> 3f
                3 -> 2.5f
                4 -> 2f
                else -> 1.5f
            }
            drawLine(
                color = textColor.copy(alpha = 0.85f),
                start = Offset(x, topMargin),
                end = Offset(x, topMargin + boardHeight),
                strokeWidth = stroke
            )
        }

        // 4. Draw Open (O) and Muted (X) indicators above the nut
        for (s in 0 until numStrings) {
            val fret = voicing.frets.getOrNull(s) ?: 0
            val x = leftMargin + s * stringSpacing
            val y = topMargin - 16f

            if (fret == -1) {
                // Draw 'X' for muted string
                val xSize = 7f
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(x - xSize, y - xSize),
                    end = Offset(x + xSize, y + xSize),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(x + xSize, y - xSize),
                    end = Offset(x - xSize, y + xSize),
                    strokeWidth = 2.5f
                )
            } else if (fret == 0) {
                // Draw 'O' for open string
                drawCircle(
                    color = accentColor,
                    radius = 6f,
                    center = Offset(x, y),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                )
            }
        }

        // 5. Draw Barre chords if any
        for (barreFret in voicing.barres) {
            val relativeFret = if (voicing.baseFret > 1) (barreFret - voicing.baseFret + 1) else barreFret
            if (relativeFret in 1..numFrets) {
                val y = topMargin + (relativeFret - 0.5f) * fretSpacing
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.85f),
                    topLeft = Offset(leftMargin, y - 11f),
                    size = Size(boardWidth, 22f),
                    cornerRadius = CornerRadius(11f, 11f)
                )
            }
        }

        // 6. Draw Finger Dots on active frets
        for (s in 0 until numStrings) {
            val rawFret = voicing.frets.getOrNull(s) ?: continue
            if (rawFret > 0) {
                val relativeFret = if (voicing.baseFret > 1) (rawFret - voicing.baseFret + 1) else rawFret
                if (relativeFret in 1..numFrets) {
                    val x = leftMargin + s * stringSpacing
                    val y = topMargin + (relativeFret - 0.5f) * fretSpacing
                    val finger = voicing.fingers.getOrNull(s) ?: 0

                    // Dot circle
                    drawCircle(
                        color = accentColor,
                        radius = 13f,
                        center = Offset(x, y)
                    )

                    // Finger number text inside dot (1, 2, 3, 4)
                    if (finger in 1..4) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = 24f
                                isFakeBoldText = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText(finger.toString(), x, y + 8f, paint)
                        }
                    }
                }
            }
        }
    }
}
