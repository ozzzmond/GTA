package com.joel.gta.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joel.gta.BuildConfig
import com.joel.gta.data.logger.AppLogManager

@Composable
fun DebugLogsDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val liveLogs by AppLogManager.liveLogs.collectAsState()
    val scrollState = rememberScrollState()

    val logFilePath = "Android/data/${context.packageName}/files/logs/gtar-debug.log"

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF073642),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF002B36))
                                .border(1.dp, Color(0xFF2AA198), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF2AA198),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "GTAR Debug Logs",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFFDF6E3)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFD32F2F))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "DEV",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                text = "${BuildConfig.APPLICATION_ID} • ${BuildConfig.VERSION_NAME}",
                                fontSize = 11.sp,
                                color = Color(0xFF93A1A1),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF93A1A1)
                        )
                    }
                }

                // File path description banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF002B36))
                        .border(1.dp, Color(0xFF1A4A55), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Path: $logFilePath",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF2AA198),
                        maxLines = 2
                    )
                }

                // Monospace Log Console Area
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(min = 200.dp, max = 450.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF001E26))
                        .border(1.dp, Color(0xFF1A4A55), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = liveLogs.ifBlank { "No logs recorded yet." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFEEE8D5),
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [ Clear Logs ]
                    OutlinedButton(
                        onClick = {
                            val cleared = AppLogManager.clearLogs()
                            if (cleared) {
                                Toast.makeText(context, "Debug logs cleared", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFDC6E67)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // [ Copy All ]
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("GTAR Debug Logs", liveLogs)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFDF6E3)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Copy All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // [ Export .log ]
                    TextButton(
                        onClick = {
                            val intent = AppLogManager.createExportIntent(context)
                            if (intent != null) {
                                val chooser = Intent.createChooser(intent, "Export GTAR Debug Log")
                                context.startActivity(chooser)
                            } else {
                                Toast.makeText(context, "No log file found to export", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color(0xFF2AA198),
                            contentColor = Color(0xFF002B36)
                        ),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Export .log", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
