package com.joel.gta.presentation

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joel.gta.data.logger.AppLogManager
import com.joel.gta.data.model.ParsedSong
import com.joel.gta.ui.components.MetaBadge
import com.joel.gta.ui.components.RenderSongLine
import com.joel.gta.ui.components.splitSongLinesForColumns
import com.joel.gta.ui.theme.AppThemeMode
import com.joel.gta.ui.theme.GTATheme
import com.joel.gta.ui.theme.LocalGtaColors
import com.joel.gta.ui.theme.SongFontStyle
import kotlinx.coroutines.flow.StateFlow

fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

data class StagePresentationData(
    val song: ParsedSong? = null,
    val scrollFraction: Float = 0f,
    val fontSizeSp: Float = 24f,
    val songFontStyle: SongFontStyle = SongFontStyle.MONOSPACE,
    val columnCount: Int = 1,
    val activeCapo: String = "No Capo",
    val isPrivacyCurtainActive: Boolean = false
)

/**
 * Android Presentation window that projects a clean, distraction-free stage teleprompter
 * to external monitors, TVs, projectors, or Chromecast / wireless screens.
 * Contains only fullscreen scrolling chords and lyrics with real-time sync.
 */
class StagePresentation(
    context: Context,
    display: Display,
    private val presentationDataFlow: StateFlow<StagePresentationData>,
    private val hostActivity: ComponentActivity? = context.findComponentActivity()
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val presentationWindow = window ?: return
        val decorView = presentationWindow.decorView

        // Bind owners from the host ComponentActivity to the secondary window's decorView
        // to prevent "ViewTreeLifecycleOwner not found from ComposeView" crash
        val activity = hostActivity ?: context.findComponentActivity()
        if (activity != null) {
            decorView.setViewTreeLifecycleOwner(activity)
            decorView.setViewTreeViewModelStoreOwner(activity)
            decorView.setViewTreeSavedStateRegistryOwner(activity)
        } else {
            AppLogManager.w(
                "StagePresentation",
                "Warning: Context is not a ComponentActivity (${context.javaClass.name}). ViewTree owners not bound."
            )
        }

        presentationWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        presentationWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        presentationWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val data by presentationDataFlow.collectAsState()
                GTATheme(themeMode = AppThemeMode.AMOLED_DARK) {
                    StageTeleprompterContent(data)
                }
            }
        }
        setContentView(composeView)
    }
}

@Composable
private fun StageTeleprompterContent(data: StagePresentationData) {
    if (data.isPrivacyCurtainActive) {
        // Privacy Blackout Curtain: Pure black canvas to block Android OS screen mirror leak
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "GTAR STAGE • STANDBY",
                color = Color(0x33FFFFFF),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp
            )
        }
        return
    }

    val customColors = LocalGtaColors.current
    val song = data.song

    if (song == null) {
        // Standby Screen when no song is loaded
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF00151A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = customColors.chordAccent.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent)
                ) {
                    Text(
                        text = "GTAR STAGE TELEPROMPTER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = customColors.chordAccent,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        letterSpacing = 2.sp
                    )
                }
                Text(
                    text = "External Display Connected • Ready for Song Selection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = customColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val scrollState = rememberScrollState()

    // Smoothly mirror autoscroll and manual scroll updates in real-time
    LaunchedEffect(data.scrollFraction, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val target = (data.scrollFraction * scrollState.maxValue).toInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(target)
        }
    }

    // Reset scroll to top when song changes
    LaunchedEffect(song.title) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(customColors.canvasBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Distraction-Free Header: Clean song title & key/capo metadata
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = customColors.textPrimary,
                    fontSize = (data.fontSizeSp + 4).sp,
                    lineHeight = ((data.fontSizeSp + 4) * 1.25f).sp
                )
                if (!song.artist.isNullOrBlank()) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = customColors.textSecondary,
                        fontSize = (data.fontSizeSp - 2).sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!song.key.isNullOrBlank()) {
                    MetaBadge(label = "KEY: ${song.key}")
                }
                val effectiveCapo = if (data.activeCapo.isNotBlank() && !data.activeCapo.equals("No Capo", ignoreCase = true) && !data.activeCapo.equals("None", ignoreCase = true)) {
                    data.activeCapo
                } else song.capo

                if (!effectiveCapo.isNullOrBlank() && !effectiveCapo.equals("No Capo", ignoreCase = true) && !effectiveCapo.equals("None", ignoreCase = true)) {
                    val label = if (effectiveCapo.startsWith("Capo", ignoreCase = true) || effectiveCapo.startsWith("Fret", ignoreCase = true)) {
                        effectiveCapo.uppercase()
                    } else "CAPO: $effectiveCapo"
                    MetaBadge(label = label)
                }
            }
        }

        HorizontalDivider(color = customColors.divider, thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))

        // Fullscreen Song Body (1 or 2 Columns)
        if (data.columnCount == 2) {
            val (col1Lines, col2Lines) = remember(song.lines) {
                splitSongLinesForColumns(song.lines)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    col1Lines.forEach { line ->
                        RenderSongLine(
                            line = line,
                            fontSizeSp = data.fontSizeSp,
                            songFontStyle = data.songFontStyle,
                            onChordClick = {}
                        )
                    }
                }
                VerticalDivider(
                    color = customColors.divider.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    col2Lines.forEach { line ->
                        RenderSongLine(
                            line = line,
                            fontSizeSp = data.fontSizeSp,
                            songFontStyle = data.songFontStyle,
                            onChordClick = {}
                        )
                    }
                }
            }
        } else {
            song.lines.forEach { line ->
                RenderSongLine(
                    line = line,
                    fontSizeSp = data.fontSizeSp,
                    songFontStyle = data.songFontStyle,
                    onChordClick = {}
                )
            }
        }

        // Generous teleprompter bottom breathing room
        Spacer(modifier = Modifier.height(200.dp))
    }
}
