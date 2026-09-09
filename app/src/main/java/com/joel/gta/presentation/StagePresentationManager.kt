package com.joel.gta.presentation

import android.content.Context
import com.joel.gta.data.logger.AppLogManager
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
        AppLogManager.d(
            "StagePresentationManager",
            "refreshDisplays: ${presentationDisplays.size} category-presentation displays, ${secondaryDisplays.size} total secondary displays available."
        )
    }

    fun startProjection(display: Display? = null): Boolean {
        refreshDisplays()
        val targetDisplay = display ?: _availableDisplays.value.firstOrNull()
        if (targetDisplay == null) {
            AppLogManager.logPresentationEvent(
                action = "START_PROJECTION",
                details = "Aborted: No external or presentation displays available. Total system displays: ${displayManager.displays.size}",
                success = false
            )
            return false
        }

        val displayInfo = "Display ID=${targetDisplay.displayId}, Name='${targetDisplay.name}', Valid=${targetDisplay.isValid}, State=${targetDisplay.state}, Flags=0x${Integer.toHexString(targetDisplay.flags)}, RefreshRate=${targetDisplay.refreshRate}Hz"
        AppLogManager.logPresentationEvent(
            action = "START_PROJECTION_ATTEMPT",
            details = "Initiating projection on $displayInfo",
            success = true
        )

        stopProjection(disconnectRoute = false)
        return try {
            val hostActivity = context.findComponentActivity()
            if (hostActivity != null) {
                AppLogManager.i("StagePresentationManager", "Host ComponentActivity found (${hostActivity.javaClass.simpleName}) for ViewTree owner binding.")
            } else {
                AppLogManager.w("StagePresentationManager", "Warning: Context ${context.javaClass.name} could not be resolved to ComponentActivity.")
            }
            val presentation = StagePresentation(context, targetDisplay, _presentationData, hostActivity)
            presentation.setOnDismissListener {
                AppLogManager.logPresentationEvent(
                    action = "DISMISS",
                    details = "StagePresentation on Display ID=${targetDisplay.displayId} dismissed by system or user",
                    success = true
                )
                val wasProjecting = _isProjecting.value
                _isProjecting.value = false
                activePresentation = null
                if (wasProjecting) {
                    disconnectMediaRoutes()
                }
            }
            presentation.show()
            activePresentation = presentation
            _isProjecting.value = true
            AppLogManager.logPresentationEvent(
                action = "SHOW_SUCCESS",
                details = "Successfully projected to $displayInfo",
                success = true
            )
            true
        } catch (e: Exception) {
            AppLogManager.logPresentationEvent(
                action = "SHOW_FAILED",
                details = "Failed to project to $displayInfo: ${e.message}",
                success = false,
                error = e
            )
            e.printStackTrace()
            _isProjecting.value = false
            activePresentation = null
            false
        }
    }

    fun stopProjection(disconnectRoute: Boolean = true) {
        if (activePresentation != null) {
            AppLogManager.logPresentationEvent(
                action = "STOP_PROJECTION",
                details = "Stopping projection on Display ID=${activePresentation?.display?.displayId}",
                success = true
            )
            try {
                activePresentation?.dismiss()
            } catch (e: Exception) {
                AppLogManager.w("StagePresentationManager", "Error while dismissing presentation: ${e.message}", e)
            }
        }
        activePresentation = null
        _isProjecting.value = false

        if (disconnectRoute) {
            disconnectMediaRoutes()
        }
    }

    /**
     * Completely disconnects active media route / cast session back to the default internal route.
     * Prevents Android OS from dropping back into screen mirroring mode on the external display.
     */
    private fun disconnectMediaRoutes() {
        val disconnectRunnable = Runnable {
            try {
                val mediaRouter = androidx.mediarouter.media.MediaRouter.getInstance(context)
                val defaultRoute = mediaRouter.defaultRoute
                val selectedRoute = mediaRouter.selectedRoute
                if (selectedRoute != defaultRoute) {
                    AppLogManager.i(
                        "StagePresentationManager",
                        "Disconnecting active MediaRouter route '${selectedRoute.name}' -> selecting default route '${defaultRoute.name}'"
                    )
                    mediaRouter.selectRoute(defaultRoute)
                }
            } catch (e: Throwable) {
                AppLogManager.w("StagePresentationManager", "AndroidX MediaRouter route disconnect failed: ${e.message}", e)
            }

            try {
                val systemRouter = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? android.media.MediaRouter
                if (systemRouter != null) {
                    val defaultRoute = systemRouter.getDefaultRoute()
                    val liveVideoRoute = systemRouter.getSelectedRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
                    if (liveVideoRoute != defaultRoute) {
                        AppLogManager.i(
                            "StagePresentationManager",
                            "Disconnecting system MediaRouter live video route '${liveVideoRoute?.name}' -> selecting default route '${defaultRoute.name}'"
                        )
                        systemRouter.selectRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_VIDEO, defaultRoute)
                    }
                }
            } catch (e: Throwable) {
                AppLogManager.w("StagePresentationManager", "System MediaRouter route disconnect failed: ${e.message}", e)
            }
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            disconnectRunnable.run()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(disconnectRunnable)
        }
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
