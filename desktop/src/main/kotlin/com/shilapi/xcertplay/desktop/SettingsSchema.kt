package com.shilapi.xcertplay.desktop

/** How one setting's value is parsed, validated and written back. */
sealed interface SettingType<T> {
    fun parse(text: String): T?
    fun format(value: T): String = value.toString()

    data object Bool : SettingType<Boolean> {
        override fun parse(text: String) = text.trim().lowercase().toBooleanStrictOrNull()
    }

    data class Whole(val min: Int, val max: Int, val step: Int = 1, val unit: String = "") : SettingType<Int> {
        override fun parse(text: String) = text.trim().toIntOrNull()?.takeIf { it in min..max }
    }

    data class Decimal(val min: Double, val max: Double, val step: Double, val unit: String = "") : SettingType<Double> {
        override fun parse(text: String) = text.trim().toDoubleOrNull()?.takeIf { it in min..max }
    }

    /** One of a fixed set of values; [options] maps stored value to label. */
    data class Choice(val options: List<Pair<String, String>>) : SettingType<String> {
        override fun parse(text: String) = text.trim().takeIf { value -> options.any { it.first == value } }
    }

    data class Text(val maxLength: Int = 64) : SettingType<String> {
        override fun parse(text: String) = text.trim().takeIf { it.isNotEmpty() && it.length <= maxLength }
    }
}

/** One granular setting, declared once; the dashboard builds its control from this. */
class Setting<T>(
    val key: String,
    val tab: String,
    val section: String,
    val label: String,
    val default: T,
    val type: SettingType<T>,
    val help: String = "",
) {
    fun parse(text: String?): T = text?.let(type::parse) ?: default
}

/** The granular settings beyond the core connection fields, as stored in settings.properties. */
class SettingsValues(private val raw: Map<String, String> = emptyMap()) {
    operator fun <T> get(setting: Setting<T>): T = setting.parse(raw[setting.key])

    fun <T> with(setting: Setting<T>, value: T): SettingsValues =
        SettingsValues(raw + (setting.key to setting.type.format(value)))

    /** Every declared setting with its effective value, for saving. */
    fun entries(): List<Pair<String, String>> = SettingsSchema.ALL.map { setting -> setting.key to formatted(setting) }

    private fun <T> formatted(setting: Setting<T>): String = setting.type.format(get(setting))

    override fun equals(other: Any?) = other is SettingsValues && entries() == other.entries()
    override fun hashCode() = entries().hashCode()

    companion object {
        val DEFAULTS = SettingsValues()
    }
}

/** Every granular OpenPlay setting, grouped into dashboard tabs and sections. */
object SettingsSchema {
    private const val CARPLAY = "CarPlay"
    private const val CONNECTION = "Connection"
    private const val AUDIO = "Audio"
    private const val VIDEO = "Video"
    private const val CLUSTER = "Cluster"
    private const val VEHICLE = "Vehicle"
    private const val DIAGNOSTICS = "Diagnostics"

    // CarPlay identity and screen
    val DEVICE_NAME = Setting("carplay.deviceName", CARPLAY, "Identity", "Name shown on the iPhone", APP_NAME, SettingType.Text(32))
    val MANUFACTURER = Setting("carplay.manufacturer", CARPLAY, "Identity", "Manufacturer", APP_NAME, SettingType.Text(32))
    val MODEL = Setting("carplay.model", CARPLAY, "Identity", "Model", APP_NAME, SettingType.Text(32))
    val RIGHT_HAND_DRIVE = Setting("carplay.rightHandDrive", CARPLAY, "Layout", "Right-hand drive", false, SettingType.Bool,
        "Moves the CarPlay dock to the driver's side for right-hand-drive cars.")
    val NIGHT_MODE = Setting("carplay.nightMode", CARPLAY, "Layout", "Appearance", "system",
        SettingType.Choice(listOf("system" to "Follow Windows", "day" to "Always light", "night" to "Always dark")))
    val SCREEN_WIDTH_MM = Setting("carplay.screenWidthMm", CARPLAY, "Physical screen", "Width", 200, SettingType.Whole(40, 800, 1, "mm"),
        "The real size of the screen; CarPlay sizes icons and text from it.")
    val SCREEN_HEIGHT_MM = Setting("carplay.screenHeightMm", CARPLAY, "Physical screen", "Height", 113, SettingType.Whole(30, 600, 1, "mm"))
    val SAFE_TOP = Setting("carplay.safeAreaTop", CARPLAY, "Safe area", "Top inset", 0, SettingType.Whole(0, 600, 2, "px"),
        "Keeps CarPlay's controls away from screen edges hidden by a bezel.")
    val SAFE_BOTTOM = Setting("carplay.safeAreaBottom", CARPLAY, "Safe area", "Bottom inset", 0, SettingType.Whole(0, 600, 2, "px"))
    val SAFE_LEFT = Setting("carplay.safeAreaLeft", CARPLAY, "Safe area", "Left inset", 0, SettingType.Whole(0, 800, 2, "px"))
    val SAFE_RIGHT = Setting("carplay.safeAreaRight", CARPLAY, "Safe area", "Right inset", 0, SettingType.Whole(0, 800, 2, "px"))

    // Connection
    val AIRPLAY_PORT = Setting("connection.airplayPort", CONNECTION, "Network", "AirPlay port", 7000, SettingType.Whole(1024, 65535),
        "OpenPlay falls back to 7001-7010 when this port is busy.")
    val BLUETOOTH_ATTEMPTS = Setting("connection.bluetoothAttempts", CONNECTION, "Bluetooth", "Connection attempts", 4, SettingType.Whole(1, 20))
    val BLUETOOTH_RETRY_SECONDS = Setting("connection.bluetoothRetrySeconds", CONNECTION, "Bluetooth", "Wait between attempts", 3,
        SettingType.Whole(1, 60, 1, "s"))
    val BOOTSTRAP_TIMEOUT_SECONDS = Setting("connection.bootstrapTimeoutSeconds", CONNECTION, "Bluetooth", "Bootstrap timeout", 300,
        SettingType.Whole(30, 1800, 10, "s"), "How long the Bluetooth handshake may take before giving up.")

    // Audio
    val AUDIO_ENABLED = Setting("audio.enabled", AUDIO, "Output", "Play CarPlay audio on this PC", true, SettingType.Bool,
        "Off keeps all sound on the iPhone.")
    val AUDIO_BUFFER_MILLIS = Setting("audio.bufferMillis", AUDIO, "Output", "Output buffer", 120, SettingType.Whole(40, 1000, 10, "ms"),
        "Larger is smoother on a busy PC; smaller keeps sound closer to the picture.")
    val MICROPHONE_ENABLED = Setting("audio.microphoneEnabled", AUDIO, "Microphone", "Use this PC's microphone", true, SettingType.Bool,
        "iOS routes car audio only to receivers with a microphone; turning this off sends silence instead.")
    val MICROPHONE_BITRATE = Setting("audio.microphoneBitrate", AUDIO, "Microphone", "Opus bitrate", 48_000,
        SettingType.Whole(12_000, 128_000, 4_000, "bit/s"))

    // Video
    val DECODER_THREADS = Setting("video.decoderThreads", VIDEO, "Decoding", "Decoder threads", 2, SettingType.Whole(1, 16))
    val DECODE_QUEUE_FRAMES = Setting("video.decodeQueueFrames", VIDEO, "Decoding", "Frames buffered before catching up", 90,
        SettingType.Whole(10, 600, 10), "When decoding falls this far behind, OpenPlay skips to the next keyframe.")
    val SCALING = Setting("video.scaling", VIDEO, "Display", "Scaling quality", "bilinear",
        SettingType.Choice(listOf("nearest" to "Fastest (nearest)", "bilinear" to "Smooth (bilinear)", "bicubic" to "Sharpest (bicubic)")))
    val KEEP_ASPECT = Setting("video.keepAspect", VIDEO, "Display", "Keep aspect ratio (black bars)", true, SettingType.Bool)

    // Cluster
    val CLUSTER_WIDTH = Setting("cluster.width", CLUSTER, "Cluster display", "Width", 1280, SettingType.Whole(320, 3840, 2, "px"))
    val CLUSTER_HEIGHT = Setting("cluster.height", CLUSTER, "Cluster display", "Height", 480, SettingType.Whole(240, 2160, 2, "px"))
    val CLUSTER_FPS = Setting("cluster.fps", CLUSTER, "Cluster display", "Frame rate", 30, SettingType.Whole(10, 60, 5, "fps"))
    val CLUSTER_CONTENT = Setting("cluster.content", CLUSTER, "Cluster display", "Content at start", "instruments",
        SettingType.Choice(listOf("instruments" to "Map and turn card", "map" to "Map only", "turncard" to "Turn card only")))

    // Simulated vehicle
    val BATTERY_START = Setting("vehicle.batteryStartPercent", VEHICLE, "Battery", "Charge at start", 62.0,
        SettingType.Decimal(1.0, 100.0, 1.0, "%"))
    val BATTERY_DRAIN = Setting("vehicle.drainPercentPerHour", VEHICLE, "Battery", "Drain", 12.0, SettingType.Decimal(0.0, 100.0, 1.0, "%/h"))
    val FULL_RANGE_KM = Setting("vehicle.fullRangeKm", VEHICLE, "Battery", "Range when full", 420, SettingType.Whole(50, 1500, 10, "km"))
    val CHARGING = Setting("vehicle.charging", VEHICLE, "Battery", "Report as charging", false, SettingType.Bool)
    val SPEED_KMH = Setting("vehicle.speedKmh", VEHICLE, "Driving", "Speed", 50, SettingType.Whole(0, 250, 5, "km/h"))
    val CENTER_LATITUDE = Setting("vehicle.centerLatitude", VEHICLE, "Driving", "Loop centre latitude", 37.33490,
        SettingType.Decimal(-89.0, 89.0, 0.001, "°"))
    val CENTER_LONGITUDE = Setting("vehicle.centerLongitude", VEHICLE, "Driving", "Loop centre longitude", -122.00900,
        SettingType.Decimal(-180.0, 180.0, 0.001, "°"))
    val LOOP_METERS = Setting("vehicle.loopMeters", VEHICLE, "Driving", "Loop length", 500, SettingType.Whole(100, 20_000, 100, "m"))

    // Diagnostics
    val LOG_LEVEL = Setting("diagnostics.logLevel", DIAGNOSTICS, "Logging", "Detail", "info",
        SettingType.Choice(listOf("info" to "Normal", "debug" to "Detailed", "verbose" to "Everything (large logs)")))
    val LOG_FILES = Setting("diagnostics.logFiles", DIAGNOSTICS, "Logging", "Log files to keep", 10, SettingType.Whole(1, 100))

    val ALL: List<Setting<*>> = listOf(
        DEVICE_NAME, MANUFACTURER, MODEL, RIGHT_HAND_DRIVE, NIGHT_MODE, SCREEN_WIDTH_MM, SCREEN_HEIGHT_MM,
        SAFE_TOP, SAFE_BOTTOM, SAFE_LEFT, SAFE_RIGHT,
        AIRPLAY_PORT, BLUETOOTH_ATTEMPTS, BLUETOOTH_RETRY_SECONDS, BOOTSTRAP_TIMEOUT_SECONDS,
        AUDIO_ENABLED, AUDIO_BUFFER_MILLIS, MICROPHONE_ENABLED, MICROPHONE_BITRATE,
        DECODER_THREADS, DECODE_QUEUE_FRAMES, SCALING, KEEP_ASPECT,
        CLUSTER_WIDTH, CLUSTER_HEIGHT, CLUSTER_FPS, CLUSTER_CONTENT,
        BATTERY_START, BATTERY_DRAIN, FULL_RANGE_KM, CHARGING, SPEED_KMH, CENTER_LATITUDE, CENTER_LONGITUDE, LOOP_METERS,
        LOG_LEVEL, LOG_FILES,
    )

    /** Tabs in display order. */
    val TABS = ALL.map { it.tab }.distinct()
}
