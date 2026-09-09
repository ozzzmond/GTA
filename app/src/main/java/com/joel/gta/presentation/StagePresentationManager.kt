package com.joel.gta.presentation

import android.content.Context
import android.content.Intent
import android.provider.Settings
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

    private val _isPrivacyCurtainActive = MutableStateFlow(false)
    val isPrivacyCurtainActive: StateFlow<Boolean> = _isPrivacyCurtainActive.asStateFlow()

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
                stopProjection(hardDismiss = true)
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

        // If active presentation is already showing on this target display (e.g. held under Privacy Curtain),
        // lift the curtain to resume live lyrics and chords!
        if (activePresentation != null && activePresentation?.display?.displayId == targetDisplay.displayId && activePresentation?.isShowing == true) {
            _presentationData.update { it.copy(isPrivacyCurtainActive = false) }
            _isPrivacyCurtainActive.value = false
            _isProjecting.value = true
            AppLogManager.logPresentationEvent(
                action = "LIFT_PRIVACY_CURTAIN",
                details = "Resumed stage projection on Display ID=${targetDisplay.displayId}",
                success = true
            )
            return true
        }

        val displayInfo = "Display ID=${targetDisplay.displayId}, Name='${targetDisplay.name}', Valid=${targetDisplay.isValid}, State=${targetDisplay.state}, Flags=0x${Integer.toHexString(targetDisplay.flags)}, RefreshRate=${targetDisplay.refreshRate}Hz"
        AppLogManager.logPresentationEvent(
            action = "START_PROJECTION_ATTEMPT",
            details = "Initiating projection on $displayInfo",
            success = true
        )

        stopProjection(hardDismiss = true, disconnectRoute = false)
        return try {
            val hostActivity = context.findComponentActivity()
            if (hostActivity != null) {
                AppLogManager.i("StagePresentationManager", "Host ComponentActivity found (${hostActivity.javaClass.simpleName}) for ViewTree owner binding.")
            } else {
                AppLogManager.w("StagePresentationManager", "Warning: Context ${context.javaClass.name} could not be resolved to ComponentActivity.")
            }
            _presentationData.update { it.copy(isPrivacyCurtainActive = false) }
            val presentation = StagePresentation(context, targetDisplay, _presentationData, hostActivity)
            presentation.setOnDismissListener {
                AppLogManager.logPresentationEvent(
                    action = "DISMISS",
                    details = "StagePresentation on Display ID=${targetDisplay.displayId} dismissed by system or user",
                    success = true
                )
                val wasActive = _isProjecting.value || _isPrivacyCurtainActive.value
                _isProjecting.value = false
                _isPrivacyCurtainActive.value = false
                activePresentation = null
                if (wasActive) {
                    disconnectMediaRoutes()
                }
            }
            presentation.show()
            activePresentation = presentation
            _isProjecting.value = true
            _isPrivacyCurtainActive.value = false
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
            _isPrivacyCurtainActive.value = false
            activePresentation = null
            false
        }
    }

    /**
     * Stops live projection.
     * By default (hardDismiss = false), activates Privacy Blackout Curtain:
     * Keeps the secondary window attached to the external display and blanks it to solid pure black (FLAG_SECURE),
     * preventing Android 15 from aggressively falling back to mirroring the tablet desktop/private apps to the TV.
     *
     * If hardDismiss = true, completely dismisses the secondary window and disconnects media routes.
     */
    fun stopProjection(hardDismiss: Boolean = false, disconnectRoute: Boolean = true) {
        if (!hardDismiss && activePresentation != null && activePresentation?.isShowing == true) {
            AppLogManager.logPresentationEvent(
                action = "PRIVACY_CURTAIN_ENABLED",
                details = "Covering external display ID=${activePresentation?.display?.displayId} with pure black privacy curtain to prevent screen mirror leak.",
                success = true
            )
            _presentationData.update { it.copy(isPrivacyCurtainActive = true) }
            _isProjecting.value = false
            _isPrivacyCurtainActive.value = true
            return
        }

        if (activePresentation != null) {
            AppLogManager.logPresentationEvent(
                action = "STOP_PROJECTION",
                details = "Dismissing Presentation dialog on Display ID=${activePresentation?.display?.displayId}",
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
        _isPrivacyCurtainActive.value = false
        _presentationData.update { it.copy(isPrivacyCurtainActive = false) }

        if (disconnectRoute) {
            disconnectMediaRoutes()
        }
    }

    /**
     * Completely terminates the presentation and opens Android Cast / Display settings
     * so the user can disconnect the OS-level wireless display session with 1 tap.
     */
    fun endCastSession(context: Context) {
        stopProjection(hardDismiss = true, disconnectRoute = true)
        openCastSettings(context)
    }

    fun openCastSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Completely disconnects active media route / cast session back to the default internal route.
     * Prevents Android OS from dropping back into screen mirroring mode on the external display.
     */
    private fun disconnectMediaRoutes() {
        val disconnectRunnable = Runnable {
            AppLogManager.i("StagePresentationManager", "Hard disconnecting active MediaRoute to terminate OS mirroring.")

            // 1. AndroidX MediaRouter hard unselect & default route switch
            try {
                val mediaRouter = androidx.mediarouter.media.MediaRouter.getInstance(context)
                val defaultRoute = mediaRouter.defaultRoute

                try {
                    mediaRouter.unselect(androidx.mediarouter.media.MediaRouter.UNSELECT_REASON_STOPPED)
                } catch (e: Throwable) {
                    AppLogManager.w("StagePresentationManager", "MediaRouter.unselect failed: ${e.message}")
                }

                try {
                    mediaRouter.selectRoute(defaultRoute)
                } catch (e: Throwable) {
                    AppLogManager.w("StagePresentationManager", "MediaRouter.selectRoute(defaultRoute) failed: ${e.message}")
                }
            } catch (e: Throwable) {
                AppLogManager.w("StagePresentationManager", "AndroidX MediaRouter hard disconnect failed: ${e.message}", e)
            }

            // 2. Framework android.media.MediaRouter fallback
            try {
                val systemRouter = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? android.media.MediaRouter
                val defaultRoute = systemRouter?.getDefaultRoute()
                if (systemRouter != null && defaultRoute != null) {
                    systemRouter.selectRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_VIDEO, defaultRoute)
                    systemRouter.selectRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_AUDIO, defaultRoute)
                }
            } catch (e: Throwable) {
                AppLogManager.w("StagePresentationManager", "System MediaRouter hard disconnect failed: ${e.message}", e)
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
        stopProjection(hardDismiss = true, disconnectRoute = true)
        displayManager.unregisterDisplayListener(displayListener)
    }
}
