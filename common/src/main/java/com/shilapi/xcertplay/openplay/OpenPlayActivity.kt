package com.shilapi.xcertplay.openplay

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.PhoneBluetoothReceiver

/**
 * The OpenPlay launcher screen: splash, Home and Settings, driving the real CarPlay session and
 * the real settings. The session itself runs in CarPlayHostActivity, whose waiting screen is the
 * OpenPlay loading screen. Flows that need DiPlay's own dialogs (setup guide, connection setup,
 * car hotspot, ADB tools) open the classic DiPlayActivity.
 */
internal class OpenPlayActivity : ComponentActivity(), OpenPlayHost {
    override val activity: Activity get() = this
    private lateinit var state: AppState
    private var permissionResult: ((Boolean) -> Unit)? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionResult?.invoke(granted)
        permissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // As DiPlayActivity: the launcher icon goes straight back to a running CarPlay session.
        if (savedInstanceState == null && isLauncherIntent(intent) && CarPlayBackgroundSession.hasSession()) {
            OpenPlaySession.openCarPlay(this); finish(); return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyWindow()
        val catalog = loadCatalog(this)
        val page = intent.getStringExtra("page")
        val start = when {
            page == "settings" -> Screen.Settings
            savedInstanceState != null || page != null -> Screen.Home
            else -> Screen.Splash
        }
        state = AppState(catalog, RealSettings(this, catalog), this, start)
        state.splashDone = ::afterSplash
        if (savedInstanceState == null) {
            OpenPlaySession.onAppOpened(this)
            OpenPlaySession.startCarHotspotOnLaunch(this)
            handleAutoConnectIntent(intent)
        }
        setContent { OpenPlayApp(state) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isLauncherIntent(intent) && CarPlayBackgroundSession.hasSession()) {
            state.home(); OpenPlaySession.openCarPlay(this); return
        }
        when (intent.getStringExtra("page")) {
            "settings" -> { state.back.clear(); state.back.add(Screen.Home); state.screen = Screen.Settings }
            "home", null -> if (state.screen == Screen.Splash) Unit else state.home()
        }
        handleAutoConnectIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Back from Android settings, DiPlay's classic screens or CarPlay: show what is saved now.
        if (::state.isInitialized) { state.settings.reload(); state.revision++ }
        applyWindow()
    }

    /** "Connect when OpenPlay opens": as DiPlayActivity's first resume, after the splash. */
    private fun afterSplash() {
        if (!DiPlayPreferences.autoConnect(this) || CarPlayBackgroundSession.hasSession()) return
        Thread {
            val setupFailed = OpenPlaySession.setupFailed(this)
            runOnUiThread { if (!setupFailed && !isFinishing) startSession(DiPlayPreferences.autoConnectWireless(this)) }
        }.start()
    }

    /** "Connect when the selected iPhone reconnects" sends this extra (PhoneBluetoothReceiver). */
    private fun handleAutoConnectIntent(intent: Intent) {
        val requested = intent.getBooleanExtra(PhoneBluetoothReceiver.EXTRA_AUTO_CONNECT, false)
        intent.removeExtra(PhoneBluetoothReceiver.EXTRA_AUTO_CONNECT)
        if (requested && DiPlayPreferences.connectOnPhoneBluetooth(this) && DiPlayPreferences.phoneAddress(this) != null &&
            !CarPlayBackgroundSession.hasSession() && !OpenPlaySession.setupFailed(this)) {
            startSession(true)
        }
    }

    private fun isLauncherIntent(intent: Intent?) =
        intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.getStringExtra("page") == null

    private fun startSession(wireless: Boolean) {
        OpenPlaySession.connect(this, wireless, OpenPlaySession.setupFailed(this)) {
            choosePhone { startSession(wireless) }
        }
    }

    /* ---------- OpenPlayHost ---------- */

    override fun connect() = startSession(AirPlayPersistence.loadWirelessEnabled(this))

    override fun disconnect() {
        OpenPlaySession.disconnect { runOnUiThread { state.revision++ } }
    }

    override fun phones(): List<OpenPlaySession.Phone>? = OpenPlaySession.pairedPhones(this)

    override fun choosePhone(then: () -> Unit) {
        if (OpenPlaySession.needsBluetoothPermission(this)) {
            requestPermission(android.Manifest.permission.BLUETOOTH_CONNECT) { granted ->
                if (granted) choosePhone(then) else OpenPlaySession.handOff(this, OpenPlaySession.ACTION_CHOOSE_PHONE)
            }
            return
        }
        if (phones() == null) {
            // Bluetooth missing, off or nothing paired: DiPlay explains each case.
            OpenPlaySession.handOff(this, OpenPlaySession.ACTION_CHOOSE_PHONE)
            return
        }
        state.afterPhone = then
        if (state.screen != Screen.Phones) state.go(Screen.Phones)
    }

    override fun say(text: String) = state.say(text)

    override fun confirmReconnect(label: String, apply: () -> Unit) {
        if (CarPlayBackgroundSession.hasSession()) state.confirm = label to { apply(); state.settings.reload() } else apply()
    }

    override fun reconnectIfRunning() = OpenPlaySession.reconnectIfRunning(this) { choosePhone { connect() } }

    override fun requestPermission(permission: String, result: (Boolean) -> Unit) {
        permissionResult = result
        this.permission.launch(permission)
    }

    override fun textInput(title: String, value: String, done: (String) -> Unit) {
        val field = EditText(this).apply { setText(value); setSelection(value.length); isSingleLine = true }
        val frame = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(field)
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton(android.R.string.ok) { _, _ -> done(field.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Full screen follows "Hide the status bar" / "Hide the navigation bar", as in DiPlay. */
    override fun applyWindow() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (AirPlayPersistence.loadHideTopBar(this)) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        if (AirPlayPersistence.loadHideBottomBar(this)) controller.hide(WindowInsetsCompat.Type.navigationBars()) else controller.show(WindowInsetsCompat.Type.navigationBars())
    }

    override fun refresh() {
        if (::state.isInitialized) state.revision++
    }

    override fun openClassic(page: String, category: String?) {
        if (page == "adb-check") Toast.makeText(this, "Checking ADB access on the head unit…", Toast.LENGTH_SHORT).show()
        OpenPlaySession.openClassic(this, page, category)
    }
}
