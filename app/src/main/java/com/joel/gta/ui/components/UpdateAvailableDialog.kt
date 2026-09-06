package com.joel.gta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joel.gta.data.update.ReleaseInfo
import com.joel.gta.data.update.UpdateManager
import com.joel.gta.ui.theme.LocalGtaColors

@Composable
fun UpdateAvailableDialog(
    releaseInfo: ReleaseInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customColors = LocalGtaColors.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.surfaceBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = customColors.chordAccent.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = customColors.chordAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "New Update Available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = customColors.textPrimary
                        )
                        Text(
                            text = "GTAR v${releaseInfo.versionName} is ready to install",
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.chordAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Release Title & Notes Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = customColors.canvasBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 280.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(14.dp)
                    ) {
                        if (releaseInfo.releaseTitle.isNotBlank()) {
                            Text(
                                text = releaseInfo.releaseTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = customColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Text(
                            text = releaseInfo.releaseNotes.ifBlank { "Performance updates and stage stability enhancements." },
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = customColors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Later Button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Later",
                            color = customColors.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Browser Link (Fallback)
                    OutlinedButton(
                        onClick = {
                            UpdateManager.openBrowserUrl(context, releaseInfo.htmlUrl)
                            onDismiss()
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "GitHub Release",
                            modifier = Modifier.size(16.dp),
                            tint = customColors.textPrimary
                        )
                    }

                    // Update Now Button
                    Button(
                        onClick = {
                            if (releaseInfo.apkDownloadUrl != null) {
                                UpdateManager.downloadAndInstallApk(
                                    context,
                                    releaseInfo.apkDownloadUrl,
                                    releaseInfo.versionName
                                )
                            } else {
                                UpdateManager.openBrowserUrl(context, releaseInfo.htmlUrl)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.chordAccent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Update Now",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
