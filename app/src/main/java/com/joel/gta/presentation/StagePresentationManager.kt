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

    private val _showDisconnectGuide = MutableStateFlow(false)
    val showDisconnectGuide: StateFlow<Boolean> = _showDisconnectGuide.asStateFlow()

    private val _presentationData = MutableStateFlow(StagePresentationData())
    val presentationData: StateFlow<StagePresentationData> = _presentationData.asStateFlow()

    private var activePresentation: StagePresentation? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshDisplays()
            val addedDisplay = displayManager.getDisplay(displayId)
            if (addedDisplay != null && addedDisplay.displayId != Display.DEFAULT_DISPLAY) {
                autoEngageCurtainForDisplay(addedDisplay)
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshDisplays()
            if (activePresentation?.display?.displayId == displayId) {
                AppLogManager.logPresentationEvent(
                    action = "DISPLAY_REMOVED",
                    details = "External Display ID=$displayId physically removed or disconnected. Cleaning up presentation.",
                    success = true
                )
                try {
                    activePresentation?.dismiss()
                } catch (e: Exception) {
                    AppLogManager.w("StagePresentationManager", "Error dismissing on display removed: ${e.message}", e)
                }
                activePresentation = null
                _isProjecting.value = false
                _isPrivacyCurtainActive.value = false
                _showDisconnectGuide.value = false
                _presentationData.update { it.copy(isPrivacyCurtainActive = false) }
                disconnectMediaRoutes()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplays()
        }
    }

    init {
        displayManager.registerDisplayListener(displayListener, null)
        refreshDisplays()
        val firstDisplay = _availableDisplays.value.firstOrNull()
        if (firstDisplay != null && activePresentation == null) {
            autoEngageCurtainForDisplay(firstDisplay)
        }
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

    /**
     * Preemptively instantiates and shows StagePresentation with isPrivacyCurtainActive = true and song = null
     * the moment an external display is detected by the OS.
     * This forces the TV to transition directly from Miracast connecting to pure black,
     * completely eliminating any initial tablet desktop/home screen mirror leak!
     */
    fun autoEngageCurtainForDisplay(targetDisplay: Display) {
        if (activePresentation != null && activePresentation?.display?.displayId == targetDisplay.displayId && activePresentation?.isShowing == true) {
            return
        }

        AppLogManager.logPresentationEvent(
            action = "AUTO_CURTAIN_PREEMPTIVE_LOCK",
            details = "Display ID=${targetDisplay.displayId} connected. Instantly presenting opaque blackout curtain to preempt desktop mirror.",
            success = true
        )

        _presentationData.update {
            it.copy(
                isPrivacyCurtainActive = true,
                song = null
            )
        }
        _isPrivacyCurtainActive.value = true
        _isProjecting.value = false

        try {
            val hostActivity = context.findComponentActivity()
            val presentation = StagePresentation(context, targetDisplay, _presentationData, hostActivity)
            presentation.setOnDismissListener {
                AppLogManager.logPresentationEvent(
                    action = "DISMISS",
                    details = "StagePresentation on Display ID=${targetDisplay.displayId} dismissed",
                    success = true
                )
                if (activePresentation == presentation) {
                    activePresentation = null
                    _isProjecting.value = false
                    _isPrivacyCurtainActive.value = false
                    _showDisconnectGuide.value = false
                    _presentationData.update { it.copy(isPrivacyCurtainActive = false) }
                }
            }
            presentation.engageBlackoutCanvas()
            presentation.show()
            activePresentation = presentation
        } catch (e: Exception) {
            AppLogManager.w("StagePresentationManager", "Failed to auto-engage curtain for display ${targetDisplay.displayId}: ${e.message}", e)
        }
    }

    fun startProjection(
        display: Display? = null,
        song: ParsedSong? = null,
        activeCapo: String? = null
    ): Boolean {
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

        // Ensure active song payload is not wiped and is applied immediately
        if (song != null) {
            _presentationData.update {
                it.copy(
                    song = song,
                    activeCapo = activeCapo ?: it.activeCapo,
                    isPrivacyCurtainActive = false
                )
            }
        } else {
            _presentationData.update { it.copy(isPrivacyCurtainActive = false) }
        }

        // Initial State on startProjection: Privacy Curtain MUST be FALSE by default
        _isPrivacyCurtainActive.value = false
        _showDisconnectGuide.value = false

        // If active presentation is already showing on this target display (e.g. held under Privacy Curtain),
        // lift the curtain to resume live lyrics and chords!
        if (activePresentation != null && activePresentation?.display?.displayId == targetDisplay.displayId && activePresentation?.isShowing == true) {
            _isProjecting.value = true
            activePresentation?.setCurtainOverlayVisible(false)
            AppLogManager.logPresentationEvent(
                action = "LIFT_PRIVACY_CURTAIN",
                details = "Resumed stage projection on Display ID=${targetDisplay.displayId} (Song='${_presentationData.value.song?.title}')",
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

        if (activePresentation != null && activePresentation?.display?.displayId != targetDisplay.displayId) {
            try {
                activePresentation?.dismiss()
            } catch (e: Exception) {
                AppLogManager.w("StagePresentationManager", "Error dismissing previous display presentation: ${e.message}", e)
            }
            activePresentation = null
        }

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
                    details = "StagePresentation on Display ID=${targetDisplay.displayId} dismissed",
                    success = true
                )
                if (activePresentation == presentation) {
                    activePresentation = null
                    _isProjecting.value = false
                    _isPrivacyCurtainActive.value = false
                    _showDisconnectGuide.value = false
                    _presentationData.update { it.copy(isPrivacyCurtainActive = false) }
                }
            }
            presentation.show()
            presentation.setCurtainOverlayVisible(false)
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
     * Toggles or sets the Privacy Curtain state directly.
     */
    fun setPrivacyCurtain(active: Boolean) {
        _isPrivacyCurtainActive.value = active
        _presentationData.update { it.copy(isPrivacyCurtainActive = active) }
        activePresentation?.setCurtainOverlayVisible(active)
        if (active) {
            _isProjecting.value = false
        }
    }

    /**
     * Stops live projection without dismissing the secondary window.
     * Keeps the presentation alive and engages a permanent blackout screen
     * (Privacy Curtain with pure black), preventing Android from falling back
     * to mirroring the tablet desktop/apps to the TV.
     *
     * Keeps the user inside GTAR app so the Presentation window stays foregrounded
     * and actively obscuring the mirror. Sets showDisconnectGuide = true so the UI
     * displays an in-app banner instructing the user to disconnect Cast via quick settings.
     */
    fun stopProjection() {
        if (activePresentation != null && activePresentation?.isShowing == true) {
            AppLogManager.logPresentationEvent(
                action = "PRIVACY_CURTAIN_ENABLED",
                details = "Engaging permanent Privacy Curtain on Display ID=${activePresentation?.display?.displayId}. Screen held pure black to prevent screen mirror leak.",
                success = true
            )
            setPrivacyCurtain(true)
            activePresentation?.engageBlackoutCanvas()
            _showDisconnectGuide.value = true
        } else {
            _isProjecting.value = false
            _isPrivacyCurtainActive.value = false
            _showDisconnectGuide.value = false
        }
    }

    fun dismissDisconnectGuide() {
        _showDisconnectGuide.value = false
    }

    /**
     * Completely terminates the presentation session and sets privacy curtain to black.
     */
    fun endCastSession(context: Context? = null) {
        stopProjection()
    }

    fun openCastSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    fun toggleProjection(song: ParsedSong? = null, activeCapo: String? = null): Boolean {
        return if (_isProjecting.value) {
            stopProjection()
            false
        } else {
            startProjection(song = song, activeCapo = activeCapo)
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
        displayManager.unregisterDisplayListener(displayListener)
        try {
            activePresentation?.dismiss()
        } catch (e: Exception) {
            AppLogManager.w("StagePresentationManager", "Error dismissing on cleanup: ${e.message}", e)
        }
        activePresentation = null
        _isProjecting.value = false
        _isPrivacyCurtainActive.value = false
        _showDisconnectGuide.value = false
        _presentationData.update { it.copy(isPrivacyCurtainActive = false) }
        disconnectMediaRoutes()
    }
}
