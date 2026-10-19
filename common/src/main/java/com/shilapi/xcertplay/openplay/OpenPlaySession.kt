package com.shilapi.xcertplay.openplay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.DiPlayBootstrap
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.OwnScreenStart
import com.shilapi.xcertplay.PendingReconnect
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * OpenPlay's link to the real CarPlay session. Everything here mirrors DiPlayActivity
 * (connect(), openProjection(), refreshStatus(), markReconnectNeeded(), reconnectIfRunning())
 * and reads the same in-process singletons and preferences, so both screens always agree.
 *
 * The common path (setup fine, hotspot ready, iPhone chosen) starts CarPlayHostActivity
 * directly; its waiting screen is the OpenPlay loading screen (OpenPlayLoading.kt). Anything
 * that needs DiPlay's dialogs (car hotspot off, details missing, Bluetooth, setup error) is
 * handed to DiPlayActivity, which runs its own connect() / choosePhone() unchanged.
 */
internal object OpenPlaySession {
    const val EXTRA_ACTION = "com.shihab.diplay.OPENPLAY_ACTION"
    const val ACTION_CONNECT_WIRELESS = "connect_wireless"
    const val ACTION_CONNECT_USB = "connect_usb"
    const val ACTION_CHOOSE_PHONE = "choose_phone"
    const val ACTION_ADB_CHECK = "adb_check"
    /** Opens DiPlay's Settings on this category (SettingsCategory name, any case). */
    const val EXTRA_SETTINGS_CATEGORY = "com.shihab.diplay.OPENPLAY_SETTINGS_CATEGORY"

    /** The last failure the CarPlay host reported, until the next attempt (see OpenPlayLoading). */
    @Volatile var lastFailure: String? = null

    enum class State { SetupError, Connected, ReconnectNeeded, Connecting, Failed, Ready, NoPhone }

    class Status(val state: State, val phone: String?)

    fun setupFailed(context: Context): Boolean =
        runCatching { DiPlayBootstrap.ensure(context, AirPlayPersistence.loadMfiTarget(context)) }.isFailure

    /** Same order as DiPlayActivity.refreshStatus(). */
    fun status(context: Context, setupFailed: Boolean): Status {
        val phone = DiPlayPreferences.phoneAddress(context)?.let { DiPlayPreferences.phoneName(context) }
        val controller = CarPlayBackgroundSession.snapshot()?.controller
        val state = when {
            setupFailed -> State.SetupError
            CarPlayBackgroundSession.active && PendingReconnect.isPending(controller) -> State.ReconnectNeeded
            CarPlayBackgroundSession.active -> State.Connected
            CarPlayBackgroundSession.hasSession() -> State.Connecting
            lastFailure != null -> State.Failed
            phone != null || !wireless(context) -> State.Ready
            else -> State.NoPhone
        }
        return Status(state, phone)
    }

    fun wireless(context: Context) = AirPlayPersistence.loadWirelessEnabled(context)
    fun setWireless(context: Context, wireless: Boolean) = AirPlayPersistence.saveWirelessEnabled(context, wireless)

    /**
     * Connect phone / Open CarPlay. Returns true when CarPlay (and its loading screen) is
     * starting, false when the request went to DiPlay's dialogs or to the iPhone picker
     * ([choosePhone] is called back).
     */
    fun connect(activity: Activity, wireless: Boolean, setupFailed: Boolean, choosePhone: () -> Unit): Boolean {
        if (CarPlayBackgroundSession.active) {
            openCarPlay(activity)
            return true
        }
        val needsClassic = setupFailed || (wireless && (hotspotDetailsMissing(activity) || carHotspotOff(activity)))
        if (needsClassic) {
            handOff(activity, if (wireless) ACTION_CONNECT_WIRELESS else ACTION_CONNECT_USB)
            return false
        }
        if (wireless && DiPlayPreferences.phoneAddress(activity) == null) {
            choosePhone()
            return false
        }
        lastFailure = null
        val open = {
            AirPlayPersistence.saveWirelessEnabled(activity, wireless)
            openCarPlay(activity)
        }
        if (CarPlayBackgroundSession.hasSession()) CarPlayBackgroundSession.stop { activity.runOnUiThread { open() } } else open()
        return true
    }

    fun openCarPlay(activity: Activity) {
        OwnScreenStart.start(activity, Intent(activity, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    fun disconnect(onDone: () -> Unit) = CarPlayBackgroundSession.stop(onDone)

    /** Same as DiPlayActivity.markReconnectNeeded(): the "Reconnect now" bar, session keeps running. */
    fun markReconnectNeeded() {
        CarPlayBackgroundSession.snapshot()?.controller?.let(PendingReconnect::mark)
    }

    /** Same as DiPlayActivity.reconnectIfRunning(). */
    fun reconnectIfRunning(activity: Activity, choosePhone: () -> Unit) {
        if (CarPlayBackgroundSession.hasSession()) connect(activity, wireless(activity), setupFailed(activity), choosePhone)
    }

    fun running() = CarPlayBackgroundSession.hasSession()

    /**
     * Opens a classic DiPlay page ("connection", "setup", "backup", "settings" + a category),
     * or runs its ADB access check ("adb-check") for features that need it.
     */
    fun openClassic(activity: Activity, page: String, category: String? = null) {
        if (page == "adb-check") return handOff(activity, ACTION_ADB_CHECK)
        val intent = Intent(activity, DiPlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra("page", page)
        if (category != null) intent.putExtra(EXTRA_SETTINGS_CATEGORY, category)
        activity.startActivity(intent)
    }

    /** What DiPlayActivity does on its first launch: start the car hotspot when that is set up. */
    fun startCarHotspotOnLaunch(context: Context) {
        val app = context.applicationContext
        if (!com.shilapi.xcertplay.CarHotspotSetup.shouldStartOnLaunch(app, CarPlayBackgroundSession.hasSession())) return
        Thread({
            CarHotspotTethering.enable(
                app,
                isCancelled = { !com.shilapi.xcertplay.CarHotspotSetup.shouldStartOnLaunch(app, CarPlayBackgroundSession.hasSession()) },
                log = { android.util.Log.i("OpenPlay-Hotspot", it) },
            )
        }, "openplay-hotspot-startup").start()
    }

    /** App-level work DiPlayActivity.onCreate does, so it still runs with OpenPlay as the launcher. */
    fun onAppOpened(context: Context) {
        val app = context.applicationContext
        runCatching { com.shilapi.xcertplay.update.BackgroundUpdateScheduler.schedule(app) }
        com.shilapi.xcertplay.hud.BydNavigationOutputs.onAppOpened(app)
        com.shilapi.xcertplay.telecom.PhoneAudioRoute.attach(app)
    }

    fun handOff(activity: Activity, action: String) {
        activity.startActivity(
            Intent(activity, DiPlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra("page", "home")
                .putExtra(EXTRA_ACTION, action),
        )
    }

    private fun hotspotDetailsMissing(context: Context) =
        AirPlayPersistence.loadWirelessHotspotMode(context) == WirelessHotspotMode.MANUAL &&
            ManualHotspotValidation.error(
                AirPlayPersistence.loadManualHotspotSsid(context),
                AirPlayPersistence.loadManualHotspotPassphrase(context),
            ) != null

    private fun carHotspotOff(context: Context) =
        AirPlayPersistence.loadWirelessHotspotMode(context) == WirelessHotspotMode.MANUAL &&
            CarHotspotSettings.reportedOff(context, com.shilapi.xcertplay.network.CarHotspotStatus.isEnabled(context)) &&
            !(CarHotspotSettings.enabled(context) && CarHotspotTethering.permitted(context))

    /* ---------- Paired iPhones ---------- */

    class Phone(val address: String, val name: String)

    fun needsBluetoothPermission(context: Context) =
        Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

    /**
     * Bonded devices, as DiPlayActivity.choosePhone() lists them. Null when Bluetooth is
     * missing, off or has nothing paired: DiPlay's own dialogs handle those cases.
     */
    @SuppressLint("MissingPermission")
    fun pairedPhones(context: Context): List<Phone>? {
        if (needsBluetoothPermission(context)) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) return null
        val devices = runCatching { adapter.bondedDevices.sortedBy { it.name ?: "" } }.getOrDefault(emptyList())
        if (devices.isEmpty()) return null
        return devices.map { d ->
            val name = d.name ?: "iPhone"
            val label = if (devices.count { it.name == d.name } > 1) "$name · ${d.address.takeLast(5)}" else name
            Phone(d.address, label)
        }
    }

    fun savePhone(context: Context, phone: Phone) =
        DiPlayPreferences.savePhone(context, phone.address, phone.name.substringBefore(" · "))
}
