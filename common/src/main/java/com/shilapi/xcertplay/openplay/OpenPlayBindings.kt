package com.shilapi.xcertplay.openplay

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.AmbientLightThreshold
import com.shilapi.xcertplay.AppAppearance
import com.shilapi.xcertplay.AppLocale
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.CarPlayDock
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.CarPlayNightMode
import com.shilapi.xcertplay.CarPlayNightSchedule
import com.shilapi.xcertplay.CarPlayRotation
import com.shilapi.xcertplay.ClusterMusicSettings
import com.shilapi.xcertplay.DefaultConnectionMode
import com.shilapi.xcertplay.DiLink51ClusterLayout
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.InterfaceSize
import com.shilapi.xcertplay.LauncherBubble
import com.shilapi.xcertplay.NavigationWheelSettings
import com.shilapi.xcertplay.telecom.PhoneAudioRoute
import com.shilapi.xcertplay.SettingsLayoutPreferences
import com.shilapi.xcertplay.SidePanelSettings
import com.shilapi.xcertplay.SplitScreenSettings
import com.shilapi.xcertplay.UsbAutoConfirmService
import com.shilapi.xcertplay.UsbChargingCurrent
import com.shilapi.xcertplay.UsbPermissionSetup
import com.shilapi.xcertplay.WheelKeyService
import com.shilapi.xcertplay.WheelZoomSettings
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydNavigationOutputs
import com.shilapi.xcertplay.hud.BydOemClusterHold
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.media.AmbientColorMode
import com.shilapi.xcertplay.media.AmbientColorSource
import com.shilapi.xcertplay.media.AmbientColorSpeed
import com.shilapi.xcertplay.media.AmbientIdleBehavior
import com.shilapi.xcertplay.media.AmbientMusicController
import com.shilapi.xcertplay.media.AmbientMusicSettings
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.transport.EvChargingConnectors
import com.shilapi.xcertplay.update.UpdateAvailability

/**
 * What OpenPlay's Settings screen needs from the activity hosting it: dialogs, permission
 * requests and the session controls behind the app's reconnect rules.
 */
internal interface SettingsHost {
    val activity: Activity
    fun say(text: String)
    /** "Apply and reconnect" while CarPlay runs; [apply] alone otherwise. */
    fun confirmReconnect(label: String, apply: () -> Unit)
    fun reconnectIfRunning()
    fun requestPermission(permission: String, result: (Boolean) -> Unit)
    fun choosePhone(then: () -> Unit)
    fun textInput(title: String, value: String, done: (String) -> Unit)
    fun applyWindow()
    fun refresh()
    /** Runs a classic DiPlay settings category ("connection", "vehicle", "advanced", ...). */
    fun openClassic(page: String, category: String? = null)
}

/**
 * Every catalog setting bound to the app's real storage, with the same follow-up the classic
 * screen runs after saving (DiPlayActivity.allSettingsSections()). Values cross this boundary
 * in the catalog's terms: Boolean, Double, the option label, or a list of colour numbers.
 */
internal object OpenPlayBindings {

    private fun label(options: List<String>, values: List<Any>, value: Any, fallback: String) =
        options.getOrNull(values.indexOf(value)) ?: fallback

    private fun <T> pick(options: List<String>, values: List<T>, label: String): T? = values.getOrNull(options.indexOf(label))

    /** The leading number of an option such as "300 ms · default" or "10 %". */
    private fun lead(label: String) = Regex("-?\\d+").find(label)?.value?.toInt()

    private fun minuteLabel(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    private fun minuteOf(label: String) = label.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

    private val NIGHT = listOf(CarPlayNightMode.SYSTEM, CarPlayNightMode.AMBIENT, CarPlayNightMode.DAY, CarPlayNightMode.NIGHT, CarPlayNightMode.SCHEDULE)
    private val SIZE_MM = listOf(250, 300, 350)
    private val INTERFACE = listOf(0, 100, 125, 150, 200)
    private val LANGS = listOf("system", "en", "zh", "zh-TW", "ar", "ru", "es", "uk", "ja", "it")
    private val CONTENT = listOf(
        CarPlayClusterDisplay.Content.MAP, CarPlayClusterDisplay.Content.TURN_CARD,
        CarPlayClusterDisplay.Content.INSTRUMENTS, CarPlayClusterDisplay.Content.MAP_WITH_CUSTOM_CARD,
    )
    private val MAP_SCALE = listOf(100, 83, 67, 125)
    private val AMBIENT_MODE = listOf(AmbientColorMode.ENERGY, AmbientColorMode.BEAT, AmbientColorMode.TEMPO, AmbientColorMode.BASS, AmbientColorMode.SMART)
    private val AMBIENT_SPEED = listOf(AmbientColorSpeed.SLOW, AmbientColorSpeed.STANDARD, AmbientColorSpeed.FAST)
    private val AMBIENT_IDLE = listOf(AmbientIdleBehavior.STEADY, AmbientIdleBehavior.CAR, AmbientIdleBehavior.OFF)

    /** Rows the classic screen never shows on this head unit stay hidden here too. */
    fun available(context: Context, key: String): Boolean = when (key) {
        "channelMapping" -> context.resources.getBoolean(R.bool.config_advanced_audio_channel_mapping)
        "hideBydCall" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && BydOutputSettings.navigationAvailable(context)
        "fceGuidance" -> BydOutputSettings.fceClusterAvailable()
        "hudSong" -> BydOutputSettings.standaloneHudAvailable(context)
        "squarePicture" -> CarPlayRotation.enabled(context)
        else -> true
    }

    /** The real value of [key], expressed in the catalog's terms for [row]. */
    fun read(c: Context, key: String, row: Row): Any? {
        val o = row.options
        return when (key) {
            "connectOnOpen" -> DiPlayPreferences.autoConnect(c)
            "nightMode" -> label(o, NIGHT, AirPlayPersistence.loadCarPlayNightMode(c), o[0])
            "fullScreen" -> AirPlayPersistence.loadHideTopBar(c) && AirPlayPersistence.loadHideBottomBar(c)
            "reportLocation" -> AirPlayPersistence.loadLocationReportingEnabled(c)
            "mfiTarget" -> label(o, listOf(MfiTarget.LOCAL, MfiTarget.USB_CH341), AirPlayPersistence.loadMfiTarget(c), o[0])
            "connectOnReturn" -> DiPlayPreferences.connectOnPhoneBluetooth(c)
            "connectionMode" -> label(o, listOf(DefaultConnectionMode.LAST_USED, DefaultConnectionMode.WIRELESS, DefaultConnectionMode.USB), DiPlayPreferences.defaultConnectionMode(c), o[0])
            "openAfterStart" -> AirPlayPersistence.loadAutoStartOnBoot(c)
            "autoUsb" -> UsbPermissionSetup.Permission.ACCESSIBILITY.granted(c)
            "iphone" -> DiPlayPreferences.phoneAddress(c)?.let { DiPlayPreferences.phoneName(c) } ?: "Choose"
            "charging" -> label(o, listOf(UsbChargingCurrent.NORMAL, UsbChargingCurrent.REDUCED, UsbChargingCurrent.LOW), AirPlayPersistence.loadUsbChargingCurrent(c), o[0])
            "autoHotspot", "bydAutoHotspot" -> CarHotspotSettings.enabled(c)
            "appearance" -> label(o, listOf(AppAppearance.DARK, AppAppearance.LIGHT, AppAppearance.AUTO), AirPlayPersistence.loadAppAppearance(c), o[0])
            "luxThreshold" -> AirPlayPersistence.loadAmbientLightThreshold(c).lux.toDouble()
            "nightDelay" -> AirPlayPersistence.loadAmbientDelaySeconds(c).toDouble()
            "nightStart" -> minuteLabel(AirPlayPersistence.loadCarPlayNightSchedule(c).startMinute)
            "nightEnd" -> minuteLabel(AirPlayPersistence.loadCarPlayNightSchedule(c).endMinute)
            "carplaySize" -> label(o, SIZE_MM, AirPlayPersistence.loadWidthPhysicalMm(c), o[1])
            "resolution" -> AirPlayPersistence.loadDisplayScalePercent(c).toDouble()
            "fps" -> if (AirPlayPersistence.loadFps(c) >= 60) o[1] else o[0]
            "dock" -> label(o, listOf(CarPlayDock.AUTOMATIC, CarPlayDock.DRIVER_SIDE, CarPlayDock.BOTTOM), CarPlayDock.load(c), o[0])
            "hideStatusBar" -> AirPlayPersistence.loadHideTopBar(c)
            "hideNavBar" -> AirPlayPersistence.loadHideBottomBar(c)
            "adaptMultiWindow" -> AirPlayPersistence.loadAdaptPipResolution(c)
            "interfaceSize" -> label(o, INTERFACE, InterfaceSize.preference(c), o[0])
            "forceFullSettings" -> SettingsLayoutPreferences.forceFull(c)
            "mediaStream" -> o.getOrNull(AirPlayPersistence.loadMediaAudioChannel(c)) ?: o[0]
            "navStream" -> o.getOrNull(AirPlayPersistence.loadNavigationAudioChannel(c)) ?: o[0]
            "navVolume" -> AirPlayPersistence.loadNavigationAudioAttenuationDb(c).toDouble()
            "bufferWireless" -> o.firstOrNull { lead(it) == AirPlayPersistence.loadMediaBufferMillis(c, true) } ?: o[0]
            "bufferUsb" -> o.firstOrNull { lead(it) == AirPlayPersistence.loadMediaBufferMillis(c, false) } ?: o[4]
            "speakerCalls" -> AirPlayPersistence.loadCallSpeakerphone(c)
            "bydNav" -> BydOutputSettings.enabled(c)
            "fceGuidance" -> BydOutputSettings.fceCluster(c)
            "hudSong" -> BydOutputSettings.hudSong(c)
            "clusterSong" -> BydOutputSettings.clusterSong(c)
            "clusterSongChange" -> BydOutputSettings.clusterSongOnChange(c)
            "clusterKeys" -> BydOutputSettings.carPlayCallControls(c)
            "clusterCalls" -> BydOutputSettings.carPlayCalls(c)
            "fingers" -> AirPlayPersistence.loadSettingsGestureFingers(c).let { f -> if (f == 0) o[0] else o.firstOrNull { lead(it) == f } ?: o[2] }
            "rhd" -> AirPlayPersistence.loadRightHandDrive(c)
            "siriKey" -> WheelZoomSettings.siriKey(c)
            "siriDouble" -> WheelZoomSettings.siriDoublePress(c)
            "siriInterval" -> o.firstOrNull { lead(it) == WheelZoomSettings.siriDoublePressIntervalMillis(c) } ?: o[3]
            "siriDelay" -> o.firstOrNull { lead(it) == WheelZoomSettings.siriTriggerDelayMillis(c) } ?: o[3]
            "playKey" -> WheelZoomSettings.mediaPlayPause(c)
            "siriLongOnly" -> WheelZoomSettings.siriLongPressOnly(c)
            "externalController" -> AirPlayPersistence.loadExternalController(c)
            "wheelZoom" -> WheelZoomSettings.enabled(c)
            "modeKey" -> label(o, listOf(WheelZoomSettings.Behaviour.TOGGLE, WheelZoomSettings.Behaviour.TIMED), WheelZoomSettings.behaviour(c), o[0])
            "joystick" -> WheelZoomSettings.joystick(c)
            "joystickAutoOff" -> WheelZoomSettings.joystickAutoOff(c)
            "carName" -> AirPlayPersistence.loadOemLabel(c)
            "language" -> label(o, LANGS, AppLocale.preference(c), o[0])
            "fpsCounter" -> AirPlayPersistence.loadFpsCounter(c)
            "harmanFull" -> AirPlayPersistence.loadHarmanFullscreen(c)
            "splitScreen" -> SplitScreenSettings.enabled(c)
            "rotate" -> CarPlayRotation.enabled(c)
            "squarePicture" -> label(o, listOf(CarPlayRotation.Picture.SMOOTHER, CarPlayRotation.Picture.SHARPER), CarPlayRotation.picture(c), o[0])
            "sidePanel" -> SidePanelSettings.enabled(c)
            "hideBydCall" -> AirPlayPersistence.loadHideBydCallPopup(c)
            "hevc" -> AirPlayPersistence.loadHevcEnabled(c)
            "mtkLowLatency" -> AirPlayPersistence.loadMtkDecoderTuning(c)
            "smoothVideo" -> AirPlayPersistence.loadSmoothVideo(c)
            "directVideo" -> AirPlayPersistence.loadDirectVideoOutput(c)
            "lowLatencyDecode" -> AirPlayPersistence.loadLowLatencyDecoder(c)
            "echoCancel" -> AirPlayPersistence.loadCallEchoCancellation(c)
            "clearVoices" -> AirPlayPersistence.loadCallVoiceFilter(c)
            "carPhoneAudio" -> BydOutputSettings.phoneAudioRoute(c)
            "voipAudio" -> BydOutputSettings.phoneAudioRouteApps(c)
            "noMediaFocus" -> AirPlayPersistence.loadDisableMediaKeyAudioFocus(c)
            "audioFocus" -> AirPlayPersistence.loadAudioFocusEnabled(c)
            "muteOnLoss" -> AirPlayPersistence.loadAudioFocusAutoYield(c)
            "channelMapping" -> AirPlayPersistence.loadAdvancedAudioChannelMapping(c)
            "bufferedMusic" -> AirPlayPersistence.loadMainBufferedAudio(c)
            "clearNavVoice" -> AirPlayPersistence.loadSpeechWithout8And16Khz(c)
            "btAudio" -> AirPlayPersistence.loadCarBluetoothAudio(c)
            "wheelNavVolume" -> NavigationWheelSettings.enabled(c)
            "carRhythm" -> AmbientMusicSettings.load(c).carRhythm
            "rhythmData" -> AmbientMusicSettings.load(c).carRhythmFeed
            "bubble" -> AirPlayPersistence.loadLauncherBubble(c)
            "bubbleSize" -> o.firstOrNull { lead(it) == AirPlayPersistence.loadLauncherBubbleSize(c) } ?: o[1]
            "dilink4Adb" -> AirPlayPersistence.loadAdbClusterEnabled(c)
            "platform21" -> AirPlayPersistence.loadLegacyClusterEnabled(c)
            "hideStockMap" -> label(o, listOf(BydOemClusterHold.OFF, BydOemClusterHold.COMPONENT, BydOemClusterHold.PACKAGE), BydOutputSettings.oemClusterHold(c), o[0])
            "clusterMap" -> AirPlayPersistence.loadClusterMapEnabled(c)
            "speedometer" -> AirPlayPersistence.loadClusterSpeedometerEnabled(c)
            "speedX" -> AirPlayPersistence.loadClusterSpeedometerX(c).toDouble()
            "speedY" -> AirPlayPersistence.loadClusterSpeedometerY(c).toDouble()
            "speedSize" -> AirPlayPersistence.loadClusterSpeedometerSize(c).toDouble()
            "speedOpacity" -> AirPlayPersistence.loadClusterSpeedometerOpacity(c).toDouble()
            "clusterMusic" -> ClusterMusicSettings.enabled(c)
            "coverVisibility" -> if (ClusterMusicSettings.permanent(c)) o[1] else o[0]
            "musicX" -> ClusterMusicSettings.x(c).toDouble()
            "musicY" -> ClusterMusicSettings.y(c).toDouble()
            "musicSize" -> ClusterMusicSettings.size(c).toDouble()
            "centreCard" -> AirPlayPersistence.loadCenterMapOverlay(c)
            "centreSync" -> AirPlayPersistence.loadCenterMapFollowsDashboard(c)
            "centreAutoHide" -> AirPlayPersistence.loadCenterMapAutoHide(c)
            "shareMap" -> AirPlayPersistence.loadLauncherMapSharing(c)
            "followTheme" -> DiLink51ClusterLayout.automatic(c)
            "instrumentTheme" -> DiLink51ClusterLayout.theme(c).label
            "instrumentContrast" -> DiLink51ClusterLayout.contrast(c).label
            "dashboardShows" -> label(o, CONTENT, AirPlayPersistence.loadClusterContent(c), o[0])
            "turnSize" -> AirPlayPersistence.loadClusterTurnCardOverlaySizePercent(c).toDouble()
            "turnOpacity" -> AirPlayPersistence.loadClusterTurnCardOpacityPercent(c).toDouble()
            "turnX" -> AirPlayPersistence.loadClusterTurnCardOverlayXPercent(c).toDouble()
            "turnY" -> AirPlayPersistence.loadClusterTurnCardOverlayYPercent(c).toDouble()
            "mapSize" -> label(o, MAP_SCALE, AirPlayPersistence.loadClusterMapScalePercent(c), o[1])
            "markerX" -> AirPlayPersistence.loadClusterMarkerXPercent(c).toDouble()
            "markerY" -> AirPlayPersistence.loadClusterMarkerYPercent(c).toDouble()
            "smallWindow" -> o.getOrNull(AirPlayPersistence.loadClusterSmallWindowMode(c)) ?: o[0]
            "smallX" -> AirPlayPersistence.loadClusterSmallWindowMarkerXPercent(c).toDouble()
            "smallY" -> AirPlayPersistence.loadClusterSmallWindowMarkerYPercent(c).toDouble()
            "smallTurnTheme" -> o.getOrNull(AirPlayPersistence.loadClusterSmallWindowCardTheme(c)) ?: o[0]
            "naviOnly" -> BydOutputSettings.clusterStreamPause(c)
            "backgroundUpdates" -> UpdateAvailability.backgroundChecksEnabled(c)
            "ambientOn" -> AmbientMusicSettings.load(c).enabled
            "ambientFollow" -> AmbientMusicSettings.load(c).music
            "ambientMode" -> label(o, AMBIENT_MODE, AmbientMusicSettings.load(c).colorMode, o[1])
            "ambientSpeed" -> label(o, AMBIENT_SPEED, AmbientMusicSettings.load(c).speed, o[1])
            "ambientIdle" -> label(o, AMBIENT_IDLE, AmbientMusicSettings.load(c).idle, o[0])
            "ambientAlbum" -> AmbientMusicSettings.load(c).colorSource == AmbientColorSource.ALBUM
            "ambientColors" -> AmbientMusicSettings.load(c).selectedColors
            "ambientBrightness" -> AmbientMusicSettings.load(c).brightness.toDouble()
            "ambientArea" -> o.getOrNull(AmbientMusicSettings.load(c).area - 1) ?: o[2]
            "vehicleMode" -> if (BydOutputSettings.legacyVehicleProbe(c)) o[1] else o[0]
            "btPause" -> AirPlayPersistence.loadBtSuspendDuringCarplay(c)
            "btPauseDelay" -> o.firstOrNull { lead(it) == AirPlayPersistence.loadBtSuspendDelaySeconds(c) } ?: o[1]
            "carBattery" -> BydOutputSettings.batteryToIphone(c)
            "connectors" -> label(o, listOf(EvChargingConnectors.CCS2_TYPE2, EvChargingConnectors.GB_T, EvChargingConnectors.CCS1_J1772), BydOutputSettings.chargingConnectors(c), o[0])
            "lowCharge" -> o.firstOrNull { lead(it) == BydOutputSettings.lowChargePercent(c) } ?: o[2]
            "wheelSpeed" -> BydOutputSettings.wheelSpeedToIphone(c)
            "parkedVideo" -> BydOutputSettings.videoWhileParked(c)
            else -> row.default
        }
    }

    private fun bool(v: Any?) = v as? Boolean ?: false
    private fun int(v: Any?) = (v as? Number)?.toInt() ?: 0
    private fun text(v: Any?) = v?.toString().orEmpty()

    /**
     * Saves [value] for [key] and runs the classic screen's follow-up. Some settings first need
     * a permission, a support check or DiPlay's ADB flow; they save when that succeeds.
     */
    fun write(h: SettingsHost, key: String, row: Row, value: Any?) {
        val c: Context = h.activity
        val o = row.options
        val running = CarPlayBackgroundSession.hasSession()
        fun next() = OpenPlaySession.markReconnectNeeded()
        fun now() = h.reconnectIfRunning()
        fun reconnecting(apply: () -> Unit) = h.confirmReconnect(row.label.removeSuffix(" (experimental)")) { apply(); now() }
        when (key) {
            "connectOnOpen" -> DiPlayPreferences.saveAutoConnect(c, bool(value))
            "nightMode" -> pick(o, NIGHT, text(value))?.let { AirPlayPersistence.saveCarPlayNightMode(c, it) }
            "fullScreen" -> {
                AirPlayPersistence.saveHideTopBar(c, bool(value))
                AirPlayPersistence.saveHideBottomBar(c, bool(value))
                h.applyWindow()
            }
            "reportLocation" -> {
                val on = bool(value)
                val apply = {
                    AirPlayPersistence.saveLocationReportingEnabled(c, on)
                    if (running) now() else h.say(c.getString(R.string.settings_reconnect_pending))
                }
                if (on && c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    h.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION) { granted -> if (granted) apply() else h.refresh() }
                } else apply()
            }
            "mfiTarget" -> pick(o, listOf(MfiTarget.LOCAL, MfiTarget.USB_CH341), text(value))?.let { t ->
                reconnecting { AirPlayPersistence.saveMfiTarget(c, t) }
            }
            "connectOnReturn" -> {
                val on = bool(value)
                when {
                    !on -> DiPlayPreferences.saveConnectOnPhoneBluetooth(c, false)
                    OpenPlaySession.needsBluetoothPermission(c) -> h.requestPermission(Manifest.permission.BLUETOOTH_CONNECT) { granted ->
                        if (granted) { DiPlayPreferences.saveConnectOnPhoneBluetooth(c, true); if (DiPlayPreferences.phoneAddress(c) == null) h.choosePhone {} }
                        h.refresh()
                    }
                    else -> { DiPlayPreferences.saveConnectOnPhoneBluetooth(c, true); if (DiPlayPreferences.phoneAddress(c) == null) h.choosePhone {} }
                }
            }
            "connectionMode" -> pick(o, listOf(DefaultConnectionMode.LAST_USED, DefaultConnectionMode.WIRELESS, DefaultConnectionMode.USB), text(value))
                ?.let { DiPlayPreferences.saveDefaultConnectionMode(c, it) }
            "openAfterStart" -> {
                // With the car hotspot started by OpenPlay, boot launch needs DiPlay's ADB grant.
                if (bool(value) && CarHotspotSettings.enabled(c)) h.openClassic("settings", "connection")
                else AirPlayPersistence.saveAutoStartOnBoot(c, bool(value))
            }
            "autoUsb" -> UsbAutoConfirmService.openSettings(c)
            "iphone" -> h.choosePhone {}
            "charging" -> pick(o, listOf(UsbChargingCurrent.NORMAL, UsbChargingCurrent.REDUCED, UsbChargingCurrent.LOW), text(value))?.let {
                AirPlayPersistence.saveUsbChargingCurrent(c, it)
                if (!AirPlayPersistence.loadWirelessEnabled(c)) next()
            }
            "autoHotspot" -> {
                CarHotspotSettings.setEnabled(c, bool(value))
                if (bool(value) && !CarHotspotTethering.permitted(c)) {
                    h.activity.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}")))
                }
            }
            "bydAutoHotspot" -> if (bool(value)) h.openClassic("settings", "connection") else CarHotspotSettings.setEnabled(c, false)
            "appearance" -> pick(o, listOf(AppAppearance.DARK, AppAppearance.LIGHT, AppAppearance.AUTO), text(value))?.let { AirPlayPersistence.saveAppAppearance(c, it) }
            "luxThreshold" -> int(value).takeIf { AmbientLightThreshold.isValid(it) }?.let { AirPlayPersistence.saveAmbientLightThreshold(c, AmbientLightThreshold(it)) }
            "nightDelay" -> AirPlayPersistence.saveAmbientDelaySeconds(c, int(value).coerceIn(0, 60))
            "nightStart", "nightEnd" -> {
                val now = AirPlayPersistence.loadCarPlayNightSchedule(c)
                val next = if (key == "nightStart") now.copy(startMinute = minuteOf(text(value))) else now.copy(endMinute = minuteOf(text(value)))
                if (next.startMinute == next.endMinute) h.say(c.getString(R.string.carplay_night_schedule_same_time))
                else AirPlayPersistence.saveCarPlayNightSchedule(c, next)
            }
            "carplaySize" -> pick(o, SIZE_MM, text(value))?.let { mm -> reconnecting { AirPlayPersistence.saveWidthPhysicalMm(c, mm) } }
            "resolution" -> {
                val percent = int(value).coerceIn(30, 160)
                if (percent != AirPlayPersistence.loadDisplayScalePercent(c)) { AirPlayPersistence.saveDisplayScalePercent(c, percent); now() }
            }
            "fps" -> (lead(text(value)) ?: 30).let { fps -> reconnecting { AirPlayPersistence.saveFps(c, fps) } }
            "dock" -> pick(o, listOf(CarPlayDock.AUTOMATIC, CarPlayDock.DRIVER_SIDE, CarPlayDock.BOTTOM), text(value))?.let { CarPlayDock.save(c, it); next() }
            "hideStatusBar" -> { AirPlayPersistence.saveHideTopBar(c, bool(value)); h.applyWindow() }
            "hideNavBar" -> { AirPlayPersistence.saveHideBottomBar(c, bool(value)); h.applyWindow() }
            "adaptMultiWindow" -> AirPlayPersistence.saveAdaptPipResolution(c, bool(value))
            "interfaceSize" -> pick(o, INTERFACE, text(value))?.let { InterfaceSize.save(c, it) }
            "forceFullSettings" -> SettingsLayoutPreferences.saveForceFull(c, bool(value))
            "mediaStream" -> o.indexOf(text(value)).takeIf { it >= 0 }?.let { AirPlayPersistence.saveMediaAudioChannel(c, it); now() }
            "navStream" -> o.indexOf(text(value)).takeIf { it >= 0 }?.let { AirPlayPersistence.saveNavigationAudioChannel(c, it); now() }
            "navVolume" -> int(value).coerceIn(-20, 0).let { db ->
                AirPlayPersistence.saveNavigationAudioAttenuationDb(c, db)
                CarPlayBackgroundSession.snapshot()?.sink?.setNavigationAttenuationDb(db)
            }
            "bufferWireless" -> lead(text(value))?.let { ms ->
                AirPlayPersistence.saveMediaBufferMillis(c, true, ms)
                if (AirPlayPersistence.loadWirelessEnabled(c)) now()
            }
            "bufferUsb" -> lead(text(value))?.let { ms ->
                AirPlayPersistence.saveMediaBufferMillis(c, false, ms)
                if (!AirPlayPersistence.loadWirelessEnabled(c)) now()
            }
            "speakerCalls" -> AirPlayPersistence.saveCallSpeakerphone(c, bool(value))
            "bydNav" -> BydOutputSettings.setEnabled(c, bool(value))
            "fceGuidance" -> BydOutputSettings.setFceCluster(c, bool(value))
            "hudSong" -> BydOutputSettings.setHudSong(c, bool(value))
            "clusterSong" -> {
                BydOutputSettings.setClusterSong(c, bool(value))
                BydNavigationOutputs.clusterSongChanged(bool(value))
                if (bool(value)) h.openClassic("adb-check")
            }
            "clusterSongChange" -> { BydOutputSettings.setClusterSongOnChange(c, bool(value)); BydNavigationOutputs.clusterSongOnChangeChanged() }
            "clusterKeys" -> {
                BydOutputSettings.setCarPlayCallControls(c, bool(value))
                if (bool(value) && !WheelKeyService.connected()) Thread { WheelKeyService.enableOverAdb(c.applicationContext) }.start()
            }
            "clusterCalls" -> {
                BydOutputSettings.setCarPlayCalls(c, bool(value))
                BydNavigationOutputs.carPlayCallsChanged(bool(value))
                if (bool(value)) h.openClassic("adb-check")
            }
            "fingers" -> AirPlayPersistence.saveSettingsGestureFingers(c, if (text(value) == o[0]) 0 else lead(text(value)) ?: 3)
            "rhd" -> { AirPlayPersistence.saveRightHandDrive(c, bool(value)); next() }
            "siriKey" -> WheelZoomSettings.setSiriKey(c, bool(value))
            "siriDouble" -> WheelZoomSettings.setSiriDoublePress(c, bool(value))
            "siriInterval" -> lead(text(value))?.let { WheelZoomSettings.setSiriDoublePressIntervalMillis(c, it) }
            "siriDelay" -> lead(text(value))?.let { WheelZoomSettings.setSiriTriggerDelayMillis(c, it) }
            "playKey" -> WheelZoomSettings.setMediaPlayPause(c, bool(value))
            "siriLongOnly" -> WheelZoomSettings.setSiriLongPressOnly(c, bool(value))
            "externalController" -> AirPlayPersistence.saveExternalController(c, bool(value))
            "wheelZoom" -> WheelZoomSettings.setEnabled(c, bool(value))
            "modeKey" -> pick(o, listOf(WheelZoomSettings.Behaviour.TOGGLE, WheelZoomSettings.Behaviour.TIMED), text(value))?.let { WheelZoomSettings.setBehaviour(c, it) }
            "joystick" -> WheelZoomSettings.setJoystick(c, bool(value))
            "joystickAutoOff" -> WheelZoomSettings.setJoystickAutoOff(c, bool(value))
            "carName" -> h.textInput(row.label, AirPlayPersistence.loadOemLabel(c)) { name ->
                AirPlayPersistence.saveOemLabel(c, name)
                next()
                h.say(c.getString(R.string.car_button_saved_next_connection))
                h.refresh()
            }
            "language" -> pick(o, LANGS, text(value))?.let { AppLocale.save(c, it); h.activity.recreate() }
            "fpsCounter" -> AirPlayPersistence.saveFpsCounter(c, bool(value))
            "harmanFull" -> AirPlayPersistence.saveHarmanFullscreen(c, bool(value))
            "splitScreen" -> { SplitScreenSettings.setEnabled(c, bool(value)); next() }
            "rotate" -> { CarPlayRotation.setEnabled(c, bool(value)); next() }
            "squarePicture" -> pick(o, listOf(CarPlayRotation.Picture.SMOOTHER, CarPlayRotation.Picture.SHARPER), text(value))?.let { CarPlayRotation.setPicture(c, it); next() }
            "sidePanel" -> { SidePanelSettings.setEnabled(c, bool(value)); next() }
            "hideBydCall" -> AirPlayPersistence.saveHideBydCallPopup(c, bool(value))
            "hevc" -> { AirPlayPersistence.saveHevcEnabled(c, bool(value)); next() }
            "mtkLowLatency" -> { AirPlayPersistence.saveMtkDecoderTuning(c, bool(value)); next() }
            "smoothVideo" -> { AirPlayPersistence.saveSmoothVideo(c, bool(value)); now() }
            "directVideo" -> { AirPlayPersistence.saveDirectVideoOutput(c, bool(value)); now() }
            "lowLatencyDecode" -> { AirPlayPersistence.saveLowLatencyDecoder(c, bool(value)); next() }
            "echoCancel" -> { AirPlayPersistence.saveCallEchoCancellation(c, bool(value)); next() }
            "clearVoices" -> { AirPlayPersistence.saveCallVoiceFilter(c, bool(value)); next() }
            "carPhoneAudio" -> { BydOutputSettings.setPhoneAudioRoute(c, bool(value)); PhoneAudioRoute.settingsChanged(c) }
            "voipAudio" -> {
                BydOutputSettings.setPhoneAudioRouteApps(c, bool(value))
                PhoneAudioRoute.settingsChanged(c)
                if (bool(value) && !WheelKeyService.connected()) Thread { WheelKeyService.enableOverAdb(c.applicationContext) }.start()
            }
            "noMediaFocus" -> { AirPlayPersistence.saveDisableMediaKeyAudioFocus(c, bool(value)); next() }
            "audioFocus" -> { AirPlayPersistence.saveAudioFocusEnabled(c, bool(value)); next() }
            "muteOnLoss" -> { AirPlayPersistence.saveAudioFocusAutoYield(c, bool(value)); next() }
            "channelMapping" -> { AirPlayPersistence.saveAdvancedAudioChannelMapping(c, bool(value)); next() }
            "bufferedMusic" -> { AirPlayPersistence.saveMainBufferedAudio(c, bool(value)); now() }
            "clearNavVoice" -> { AirPlayPersistence.saveSpeechWithout8And16Khz(c, bool(value)); now() }
            "btAudio" -> { AirPlayPersistence.saveCarBluetoothAudio(c, bool(value)); now() }
            "wheelNavVolume" -> NavigationWheelSettings.setEnabled(c, bool(value))
            "carRhythm" -> if (!bool(value)) {
                AmbientMusicSettings.save(c, AmbientMusicSettings.load(c).copy(carRhythm = false))
            } else {
                // As the classic screen: save only when the car can run its rhythm.
                com.shilapi.xcertplay.hud.BydCarMusicRhythm.checkSupport(c.applicationContext).whenComplete { ok, _ ->
                    h.activity.runOnUiThread {
                        if (ok == true) AmbientMusicSettings.save(c, AmbientMusicSettings.load(c).copy(carRhythm = true))
                        else h.say(c.getString(R.string.settings_ambient_car_rhythm_unavailable))
                        h.refresh()
                    }
                }
            }
            "rhythmData" -> AmbientMusicSettings.save(c, AmbientMusicSettings.load(c).copy(carRhythmFeed = bool(value)))
            "bubble" -> {
                AirPlayPersistence.saveLauncherBubble(c, bool(value))
                if (!bool(value)) LauncherBubble.hide()
                else if (!Settings.canDrawOverlays(c)) h.activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")))
            }
            "bubbleSize" -> lead(text(value))?.let { AirPlayPersistence.saveLauncherBubbleSize(c, it) }
            "dilink4Adb" -> h.confirmReconnect(row.label.removeSuffix(" (experimental)")) {
                AirPlayPersistence.saveAdbClusterEnabled(c, bool(value)); now()
            }
            "platform21" -> h.confirmReconnect(row.label.removeSuffix(" (experimental)")) {
                AirPlayPersistence.saveLegacyClusterEnabled(c, bool(value)); now()
            }
            "hideStockMap" -> pick(o, listOf(BydOemClusterHold.OFF, BydOemClusterHold.COMPONENT, BydOemClusterHold.PACKAGE), text(value))?.let { hold ->
                reconnecting { BydOutputSettings.setOemClusterHold(c, hold); next() }
            }
            "clusterMap" -> h.confirmReconnect(row.label.removeSuffix(" (experimental)")) {
                AirPlayPersistence.saveClusterMapEnabled(c, bool(value)); now()
            }
            "speedometer" -> AirPlayPersistence.saveClusterSpeedometerEnabled(c, bool(value))
            "speedX" -> AirPlayPersistence.saveClusterSpeedometerX(c, int(value))
            "speedY" -> AirPlayPersistence.saveClusterSpeedometerY(c, int(value))
            "speedSize" -> AirPlayPersistence.saveClusterSpeedometerSize(c, int(value))
            "speedOpacity" -> AirPlayPersistence.saveClusterSpeedometerOpacity(c, int(value))
            "clusterMusic" -> ClusterMusicSettings.saveEnabled(c, bool(value))
            "coverVisibility" -> ClusterMusicSettings.savePermanent(c, text(value) == o[1])
            "musicX" -> ClusterMusicSettings.save(c, "x", int(value))
            "musicY" -> ClusterMusicSettings.save(c, "y", int(value))
            "musicSize" -> ClusterMusicSettings.save(c, "size", int(value))
            "centreCard" -> {
                AirPlayPersistence.saveCenterMapOverlay(c, bool(value))
                if (bool(value) && !Settings.canDrawOverlays(c)) h.activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")))
            }
            "centreSync" -> AirPlayPersistence.saveCenterMapFollowsDashboard(c, bool(value))
            "centreAutoHide" -> AirPlayPersistence.saveCenterMapAutoHide(c, bool(value))
            "shareMap" -> AirPlayPersistence.saveLauncherMapSharing(c, bool(value))
            "followTheme" -> { DiLink51ClusterLayout.saveAutomatic(c, bool(value)); next() }
            "instrumentTheme" -> DiLink51ClusterLayout.Theme.entries.firstOrNull { it.label == text(value) }?.let { t ->
                reconnecting { DiLink51ClusterLayout.saveTheme(c, t); next() }
            }
            "instrumentContrast" -> DiLink51ClusterLayout.Contrast.entries.firstOrNull { it.label == text(value) }?.let { t ->
                reconnecting { DiLink51ClusterLayout.saveContrast(c, t); next() }
            }
            "dashboardShows" -> pick(o, CONTENT, text(value))?.let { AirPlayPersistence.saveClusterContent(c, it); next() }
            "turnSize" -> AirPlayPersistence.saveClusterTurnCardOverlaySizePercent(c, int(value))
            "turnOpacity" -> AirPlayPersistence.saveClusterTurnCardOpacityPercent(c, int(value))
            "turnX" -> AirPlayPersistence.saveClusterTurnCardOverlayXPercent(c, int(value))
            "turnY" -> AirPlayPersistence.saveClusterTurnCardOverlayYPercent(c, int(value))
            "mapSize" -> pick(o, MAP_SCALE, text(value))?.let { p -> reconnecting { AirPlayPersistence.saveClusterMapScalePercent(c, p) } }
            "markerX" -> { AirPlayPersistence.saveClusterMarkerXPercent(c, int(value)); next() }
            "markerY" -> { AirPlayPersistence.saveClusterMarkerYPercent(c, int(value)); next() }
            "smallWindow" -> o.indexOf(text(value)).takeIf { it >= 0 }?.let { m -> reconnecting { AirPlayPersistence.saveClusterSmallWindowMode(c, m); next() } }
            "smallX" -> { AirPlayPersistence.saveClusterSmallWindowMarkerXPercent(c, int(value)); next() }
            "smallY" -> { AirPlayPersistence.saveClusterSmallWindowMarkerYPercent(c, int(value)); next() }
            "smallTurnTheme" -> o.indexOf(text(value)).takeIf { it >= 0 }?.let { AirPlayPersistence.saveClusterSmallWindowCardTheme(c, it) }
            "naviOnly" -> { BydOutputSettings.setClusterStreamPause(c, bool(value)); if (bool(value)) h.openClassic("adb-check") }
            "backgroundUpdates" -> UpdateAvailability.saveBackgroundChecksEnabled(c, bool(value))
            // Ambient lighting: one draft saved whole, like the classic dialog's Save.
            "ambientOn" -> if (!bool(value)) saveAmbient(c) { it.copy(enabled = false) } else {
                AmbientMusicController.checkSupport(c.applicationContext).whenComplete { ok, _ ->
                    h.activity.runOnUiThread {
                        if (ok == true) saveAmbient(c) { it.copy(enabled = true) }
                        else h.say(c.getString(R.string.settings_ambient_unavailable))
                        h.refresh()
                    }
                }
            }
            "ambientFollow" -> saveAmbient(c) { v ->
                if (bool(value)) v.copy(music = true) else v.copy(music = false, selectedColors = v.selectedColors.take(1))
            }
            "ambientMode" -> pick(o, AMBIENT_MODE, text(value))?.let { m -> saveAmbient(c) { it.copy(colorMode = m) } }
            "ambientSpeed" -> pick(o, AMBIENT_SPEED, text(value))?.let { s -> saveAmbient(c) { it.copy(speed = s) } }
            "ambientIdle" -> pick(o, AMBIENT_IDLE, text(value))?.let { i -> saveAmbient(c) { it.copy(idle = i) } }
            "ambientAlbum" -> saveAmbient(c) { it.copy(colorSource = if (bool(value)) AmbientColorSource.ALBUM else AmbientColorSource.SELECTED) }
            "ambientColors" -> (value as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.sorted()?.takeIf { it.isNotEmpty() }?.let { colors ->
                saveAmbient(c) { it.copy(selectedColors = colors, color = colors.first()) }
            }
            "ambientBrightness" -> saveAmbient(c) { it.copy(brightness = int(value).coerceIn(0, 6)) }
            "ambientArea" -> o.indexOf(text(value)).takeIf { it >= 0 }?.let { a -> saveAmbient(c) { it.copy(area = a + 1) } }
            // Advanced vehicle data runs ADB probes and validation that live in DiPlay.
            "vehicleMode", "carBattery", "wheelSpeed", "parkedVideo" -> h.openClassic("settings", "advanced")
            "btPause" -> { AirPlayPersistence.saveBtSuspendDuringCarplay(c, bool(value)); next(); if (bool(value)) h.openClassic("adb-check") }
            "btPauseDelay" -> lead(text(value))?.let { AirPlayPersistence.saveBtSuspendDelaySeconds(c, it); next() }
            "connectors" -> pick(o, listOf(EvChargingConnectors.CCS2_TYPE2, EvChargingConnectors.GB_T, EvChargingConnectors.CCS1_J1772), text(value))?.let { k ->
                reconnecting { BydOutputSettings.setChargingConnectors(c, k) }
            }
            "lowCharge" -> lead(text(value))?.let { BydOutputSettings.setLowChargePercent(c, it) }
        }
        h.refresh()
    }

    private fun saveAmbient(c: Context, change: (AmbientMusicSettings.Values) -> AmbientMusicSettings.Values) {
        val next = change(AmbientMusicSettings.load(c))
        AmbientMusicSettings.save(c, next.copy(colorCycle = next.music))
    }

    /** Buttons and links: the real action behind each catalog label. */
    fun action(h: SettingsHost, row: Row) {
        val a = h.activity
        when (row.label) {
            "Open connection setup", "Wireless connection help" -> h.openClassic("connection")
            "Set up USB and map permissions", "Boot auto-start · ADB repair (experimental)" -> h.openClassic("settings", "connection")
            "Allow access" -> a.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${a.packageName}")))
            "App permissions" -> a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
            "Bluetooth settings" -> a.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "Picture adjustments" -> a.startActivity(Intent(a, CarPlayHostActivity::class.java).putExtra("picture_controls", true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            "Open accessibility settings" -> UsbAutoConfirmService.openSettings(a)
            "Turn on the wheel key service · ADB" -> Thread { WheelKeyService.enableOverAdb(a.applicationContext) }.start().also { h.say(row.label) }
            "Reset custom turn card position" -> {
                AirPlayPersistence.saveClusterTurnCardOverlayXPercent(a, 76)
                AirPlayPersistence.saveClusterTurnCardOverlayYPercent(a, 30)
            }
            "Reset car marker to centre" -> {
                AirPlayPersistence.saveClusterMarkerXPercent(a, 50)
                AirPlayPersistence.saveClusterMarkerYPercent(a, 45)
                OpenPlaySession.markReconnectNeeded()
            }
            "Setup guide" -> h.openClassic("setup")
            "Back up or restore on a phone", "Get the report on a phone" -> h.openClassic("backup")
            "Save diagnostic report", "Choose save location" -> h.openClassic("settings", "diagnostics")
            "Check for updates" -> h.openClassic("settings", "about")
            "Choose image" -> h.openClassic("settings", "vehicle")
            else -> when {
                row.label.startsWith("Key for") -> h.openClassic("settings", "vehicle")
                else -> h.openClassic("settings", "advanced")
            }
        }
        h.refresh()
    }
}
