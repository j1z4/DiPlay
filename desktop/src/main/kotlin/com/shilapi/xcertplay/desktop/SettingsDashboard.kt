package com.shilapi.xcertplay.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.concurrent.thread

/**
 * OpenPlay's dashboard: every setting in one window, the live connection state, and
 * Start/Stop for CarPlay. Settings are saved to %APPDATA%\OpenPlay\settings.properties.
 */
class SettingsDashboard(private val store: DesktopStore, private val log: (String) -> Unit) {
    private val session = CarPlaySession(store, log) { state, message ->
        SwingUtilities.invokeLater { showState(state, message) }
    }

    private val statusDot = JLabel("●")
    private val statusText = JLabel("Stopped")
    private val startStop = JButton("Start CarPlay")
    private val problemsLabel = JLabel(" ")

    private val iphone = JComboBox<PairedDevice>()
    private val wifiNetwork = JLabel("Checking Wi-Fi...")
    private val passphrase = JPasswordField(22)
    private val identityFolder = JTextField(26)
    private val identityStatus = JLabel(" ")
    private val resolution = JComboBox(RESOLUTIONS.map { it.label }.toTypedArray())
    private val customWidth = JSpinner(SpinnerNumberModel(1280, 320, 3840, 2))
    private val customHeight = JSpinner(SpinnerNumberModel(720, 320, 2160, 2))
    private val fps = JComboBox(arrayOf(30, 60))
    private val fullscreen = JCheckBox("Fullscreen (kiosk for an in-car PC)")
    private val autoStart = JCheckBox("Start CarPlay automatically when OpenPlay opens")
    private val cluster = JCheckBox("Instrument cluster display (second screen)")
    private val vehicleData = JCheckBox("Simulated vehicle data (EV range, charging, speed, location)")

    private val frame = JFrame("$APP_NAME Dashboard").apply {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(event: java.awt.event.WindowEvent) {
                session.stop()
                Runtime.getRuntime().halt(0)
            }
        })
    }

    fun show(startNow: Boolean) {
        frame.contentPane = JScrollPane(content()).apply { border = null }
        frame.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
        frame.pack()
        frame.setLocationRelativeTo(null)
        load(store.loadSettings())
        refreshDevices()
        refreshWifi()
        frame.isVisible = true
        if (startNow) startCarPlay()
    }

    private fun content(): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)
        add(header())
        add(section("iPhone", row(iphone, button("Refresh") { refreshDevices() }),
            hint("Pair the iPhone in Windows Settings > Bluetooth & devices first.")))
        add(section("Wi-Fi", wifiNetwork, row(JLabel("Password"), passphrase, showToggle()),
            hint("The network this PC and the iPhone share. Leave blank for an open network.")))
        add(section("Accessory identity", row(identityFolder, button("Browse...") { chooseFolder() }), identityStatus))
        add(section("Display", row(JLabel("Resolution"), resolution, customWidth, JLabel("x"), customHeight),
            row(JLabel("Frame rate"), fps), fullscreen, autoStart))
        add(section("Experimental: CarPlay Ultra", cluster, vehicleData,
            hint("Next-generation CarPlay building blocks. Full CarPlay Ultra is limited by Apple to approved vehicles.")))
        add(footer())
    }

    private fun header(): JComponent = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(0, 0, PAD, 0)
        add(JLabel(APP_NAME).apply { font = font.deriveFont(Font.BOLD, TITLE_SIZE) }, BorderLayout.WEST)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, GAP, 0)).apply {
            isOpaque = false
            statusDot.font = statusDot.font.deriveFont(STATUS_DOT_SIZE)
            add(statusDot)
            add(statusText)
            startStop.addActionListener { if (session.isRunning) session.stop() else startCarPlay() }
            add(startStop)
        }, BorderLayout.EAST)
        showState(SessionState.STOPPED, "Stopped")
    }

    private fun footer(): JComponent = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(PAD, 0, 0, 0)
        problemsLabel.foreground = ERROR_COLOR
        add(problemsLabel, BorderLayout.CENTER)
        add(button("Save") { save() }, BorderLayout.EAST)
    }

    private fun startCarPlay() {
        val settings = save() ?: return
        session.start(settings)
    }

    /** Saves the form; returns the settings when they are complete enough to start CarPlay. */
    private fun save(): DesktopSettings? {
        val settings = current()
        store.saveSettings(settings)
        val problems = settings.problems()
        problemsLabel.text = if (problems.isEmpty()) {
            if (session.isRunning) "Saved. Changes apply the next time CarPlay starts." else "Saved."
        } else problems.joinToString("  •  ")
        problemsLabel.foreground = if (problems.isEmpty()) OK_COLOR else ERROR_COLOR
        updateIdentityStatus()
        return settings.takeIf { problems.isEmpty() }
    }

    private fun current(): DesktopSettings {
        val preset = RESOLUTIONS[resolution.selectedIndex]
        return DesktopSettings(
            iphoneAddress = (iphone.selectedItem as? PairedDevice)?.address.orEmpty(),
            wifiPassphrase = String(passphrase.password),
            mfiDirectory = File(identityFolder.text.trim()),
            width = preset.width ?: (customWidth.value as Int),
            height = preset.height ?: (customHeight.value as Int),
            fps = fps.selectedItem as Int,
            fullscreen = fullscreen.isSelected,
            autoStart = autoStart.isSelected,
            clusterDisplay = cluster.isSelected,
            vehicleData = vehicleData.isSelected,
        )
    }

    private fun load(settings: DesktopSettings) {
        passphrase.text = settings.wifiPassphrase
        identityFolder.text = settings.mfiDirectory.path
        val preset = RESOLUTIONS.indexOfFirst { it.width == settings.width && it.height == settings.height }
        resolution.selectedIndex = if (preset >= 0) preset else RESOLUTIONS.lastIndex
        customWidth.value = settings.width
        customHeight.value = settings.height
        resolution.addActionListener { updateCustomSize() }
        updateCustomSize()
        fps.selectedItem = if (settings.fps <= 30) 30 else 60
        fullscreen.isSelected = settings.fullscreen
        autoStart.isSelected = settings.autoStart
        cluster.isSelected = settings.clusterDisplay
        vehicleData.isSelected = settings.vehicleData
        iphone.putClientProperty(SAVED_ADDRESS, settings.iphoneAddress)
        updateIdentityStatus()
    }

    private fun refreshDevices() {
        val saved = (iphone.selectedItem as? PairedDevice)?.address ?: iphone.getClientProperty(SAVED_ADDRESS) as? String
        iphone.removeAllItems()
        PairedBluetoothDevices.list().forEach(iphone::addItem)
        (0 until iphone.itemCount).map { iphone.getItemAt(it) }
            .firstOrNull { it.address.equals(saved, ignoreCase = true) }?.let { iphone.selectedItem = it }
    }

    private fun refreshWifi() = thread(isDaemon = true, name = "dashboard-wifi") {
        val text = runCatching { WindowsWlanInfo.current() }.getOrNull()?.let {
            "Connected to ${it.ssid}  •  ${it.band.ifBlank { "Wi-Fi" }}, channel ${it.channel}"
        } ?: "This PC is not connected to Wi-Fi"
        SwingUtilities.invokeLater { wifiNetwork.text = text }
    }

    private fun chooseFolder() {
        val chooser = JFileChooser(identityFolder.text).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            identityFolder.text = chooser.selectedFile.path
            updateIdentityStatus()
        }
    }

    private fun updateIdentityStatus() {
        val folder = File(identityFolder.text.trim())
        val ok = File(folder, "identity.pk8").isFile && File(folder, "certificate.p7b").isFile
        identityStatus.text = if (ok) "✔ identity.pk8 and certificate.p7b found" else "✖ identity files not found in this folder"
        identityStatus.foreground = if (ok) OK_COLOR else ERROR_COLOR
    }

    private fun updateCustomSize() {
        val custom = RESOLUTIONS[resolution.selectedIndex].width == null
        customWidth.isVisible = custom
        customHeight.isVisible = custom
        frame.contentPane?.revalidate()
    }

    private fun showState(state: SessionState, message: String) {
        statusText.text = message
        statusDot.foreground = when (state) {
            SessionState.STOPPED -> IDLE_COLOR
            SessionState.CONNECTING -> CONNECTING_COLOR
            SessionState.CONNECTED -> OK_COLOR
        }
        startStop.text = if (state == SessionState.STOPPED) "Start CarPlay" else "Stop"
    }

    private fun showToggle() = JToggleButton("Show").apply {
        addActionListener { passphrase.echoChar = if (isSelected) 0.toChar() else '•' }
    }

    private fun section(title: String, vararg rows: JComponent): JComponent = JPanel(GridBagLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP),
        )
        rows.forEachIndexed { index, row ->
            add(row, GridBagConstraints().apply {
                gridy = index
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                insets = Insets(2, 0, 2, 0)
            })
        }
        alignmentX = 0f
    }

    private fun row(vararg parts: JComponent): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, GAP, 0)).apply {
        parts.forEach(::add)
    }

    private fun hint(text: String) = JLabel(text).apply { foreground = HINT_COLOR }

    private fun button(text: String, action: () -> Unit) = JButton(text).apply { addActionListener { action() } }

    private data class Resolution(val label: String, val width: Int?, val height: Int?)

    private companion object {
        const val PAD = 16
        const val GAP = 8
        const val MIN_WIDTH = 620
        const val MIN_HEIGHT = 640
        const val TITLE_SIZE = 24f
        const val STATUS_DOT_SIZE = 18f
        const val SAVED_ADDRESS = "openplay.savedAddress"
        val OK_COLOR = Color(0x2E, 0xA0, 0x43)
        val CONNECTING_COLOR = Color(0xD2, 0x99, 0x22)
        val IDLE_COLOR = Color(0x8B, 0x94, 0x9E)
        val ERROR_COLOR = Color(0xCF, 0x22, 0x2E)
        val HINT_COLOR = Color(0x8B, 0x94, 0x9E)
        val RESOLUTIONS = listOf(
            Resolution("800 x 480", 800, 480),
            Resolution("1024 x 600", 1024, 600),
            Resolution("1280 x 720", 1280, 720),
            Resolution("1920 x 720 (wide)", 1920, 720),
            Resolution("1920 x 1080", 1920, 1080),
            Resolution("Custom", null, null),
        )
    }
}
