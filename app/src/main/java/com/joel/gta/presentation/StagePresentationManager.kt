package com.joel.gta.presentation

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.joel.gta.data.model.ParsedSong
import com.joel.gta.ui.theme.SongFontStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class StagePresentationManager(private val context: Context) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _availableDisplays = MutableStateFlow<List<Display>>(emptyList())
    val availableDisplays: StateFlow<List<Display>> = _availableDisplays.asStateFlow()

    private val _isProjecting = MutableStateFlow(false)
    val isProjecting: StateFlow<Boolean> = _isProjecting.asStateFlow()

    private val _presentationData = MutableStateFlow(StagePresentationData())
    val presentationData: StateFlow<StagePresentationData> = _presentationData.asStateFlow()

    private var activePresentation: StagePresentation? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshDisplays()
            if (activePresentation?.display?.displayId == displayId) {
                stopProjection()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplays()
        }
    }

    init {
        displayManager.registerDisplayListener(displayListener, null)
        refreshDisplays()
    }

    fun refreshDisplays() {
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList()
        val secondaryDisplays = if (presentationDisplays.isNotEmpty()) {
            presentationDisplays
        } else {
            displayManager.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        }
        _availableDisplays.value = secondaryDisplays
    }

    fun startProjection(display: Display? = null): Boolean {
        refreshDisplays()
        val targetDisplay = display ?: _availableDisplays.value.firstOrNull() ?: return false
        stopProjection()
        return try {
            val presentation = StagePresentation(context, targetDisplay, _presentationData)
            presentation.setOnDismissListener {
                _isProjecting.value = false
                activePresentation = null
            }
            presentation.show()
            activePresentation = presentation
            _isProjecting.value = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _isProjecting.value = false
            activePresentation = null
            false
        }
    }

    fun stopProjection() {
        try {
            activePresentation?.dismiss()
        } catch (_: Exception) {}
        activePresentation = null
        _isProjecting.value = false
    }

    fun toggleProjection(): Boolean {
        return if (_isProjecting.value) {
            stopProjection()
            false
        } else {
            startProjection()
        }
    }

    fun updateSong(song: ParsedSong?, activeCapo: String = "No Capo") {
        _presentationData.update {
            it.copy(song = song, activeCapo = activeCapo)
        }
    }

    fun updateScrollFraction(fraction: Float) {
        _presentationData.update {
            it.copy(scrollFraction = fraction)
        }
    }

    fun updateFormatting(fontSizeSp: Float, fontStyle: SongFontStyle, columnCount: Int) {
        _presentationData.update {
            it.copy(
                fontSizeSp = (fontSizeSp + 4f).coerceIn(16f, 38f),
                songFontStyle = fontStyle,
                columnCount = columnCount
            )
        }
    }

    fun cleanup() {
        stopProjection()
        displayManager.unregisterDisplayListener(displayListener)
    }
}
