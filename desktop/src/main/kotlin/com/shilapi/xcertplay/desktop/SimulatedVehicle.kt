package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.transport.CarPlayLocationFix
import com.shilapi.xcertplay.transport.Iap2LocationMessages
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.NmeaLocationEncoder
import com.shilapi.xcertplay.transport.PascdEncoder
import com.shilapi.xcertplay.transport.VehicleGear
import com.shilapi.xcertplay.transport.VehicleSpeedReading
import com.shilapi.xcertplay.transport.VehicleSpeedSample
import com.shilapi.xcertplay.transport.VehicleStatusProvider
import com.shilapi.xcertplay.transport.VehicleStatusSnapshot
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The electric car behind the "simulated vehicle data" experiment: a 60 kWh battery draining
 * slowly while the car circles a loop, so the iPhone sees an EV with range, charge, position and
 * wheel speed without a real car. The defaults are a 62 % battery at town speed around Cupertino;
 * the Vehicle settings tab changes them through [configured]. Every reading derives from the time
 * since construction as read through [clock] (epoch milliseconds), so the same clock always
 * yields the same readings and tests can step time by hand.
 */
class SimulatedVehicle(
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    private val startPercent: Double = START_PERCENT,
    private val drainPercentPerHour: Double = DRAIN_PERCENT_PER_HOUR,
    private val fullRangeKm: Int = MAX_RANGE_KM,
    val charging: Boolean = false,
    val speedKmh: Int = CRUISE_KMH,
    private val centerLatitude: Double = CENTER_LATITUDE,
    private val centerLongitude: Double = CENTER_LONGITUDE,
    private val loopRadiusMeters: Double = LOOP_RADIUS_METERS,
) : VehicleStatusProvider, Iap2LocationProvider {
    private val startMillis = clock()
    private val metersPerSecond = speedKmh / METERS_PER_SECOND_PER_KMH
    /** The battery never drains below this; a start below the usual floor keeps its own level. */
    private val floorPercent = minOf(MIN_PERCENT, startPercent)
    /** One clockwise lap of the loop at cruise speed, or 0 when the car stands still. */
    val lapMillis: Long =
        if (speedKmh > 0) (2 * PI * loopRadiusMeters / metersPerSecond * 1000).roundToLong() else 0L
    /** What the drain costs the battery while driving, in kW; 0 when parked. */
    val powerKw: Double = if (speedKmh > 0) drainPercentPerHour / 100 * MAX_CHARGE_WH / WATTS_PER_KILOWATT else 0.0
    @Volatile private var started = false
    @Volatile private var speedRequested = false
    private var nextLogMillis = startMillis

    override fun snapshot(): VehicleStatusSnapshot {
        val percent = batteryPercent(elapsedMillis())
        return VehicleStatusSnapshot(
            rangeKm = (fullRangeKm * percent / 100).roundToInt(),
            rangeWarning = percent <= LOW_CHARGE_PERCENT,
            batteryPercent = percent,
            currentChargeWh = (MAX_CHARGE_WH * percent / 100).roundToLong(),
            maxChargeWh = MAX_CHARGE_WH,
            maxRangeKm = fullRangeKm,
            charging = charging,
        )
    }

    override fun onRequested(components: Set<Int>) {
        speedRequested = Iap2LocationMessages.VEHICLE_SPEED_DATA in components
        log("vehicle: iPhone asked for location components=$components wheel-speed=$speedRequested")
    }

    override fun start(): Boolean {
        started = true
        log("vehicle: location updates started; ${describe()}")
        return true
    }

    override fun stop() {
        started = false
    }

    /** GGA + RMC for the current point of the loop, plus `$PASCD` when the iPhone asked for it. */
    override fun latestNmea(): String? {
        if (!started) return null
        val now = clock()
        val elapsed = now - startMillis
        val fix = positionAfter(elapsed).copy(timestampMillis = now)
        logProgress(now, fix)
        val speed = if (speedRequested) PascdEncoder.encode(wheelSpeedUntil(elapsed)).orEmpty() else ""
        return NmeaLocationEncoder.encode(fix) + speed
    }

    /** One line for the log: what the iPhone is about to be told. */
    fun describe(): String {
        val status = snapshot()
        return format(
            "battery %.1f%% range %d km, %d km/h loop around %.5f,%.5f",
            status.batteryPercent, status.rangeKm, speedKmh, centerLatitude, centerLongitude,
        )
    }

    /** The charge after [elapsedMillis] of driving; never below the floor, one decimal. */
    fun batteryPercent(elapsedMillis: Long): Double {
        val hours = elapsedMillis.coerceAtLeast(0) / MILLIS_PER_HOUR
        val percent = (startPercent - drainPercentPerHour * hours).coerceAtLeast(floorPercent)
        return (percent * 10).roundToInt() / 10.0
    }

    /** Where on the loop the car is after [elapsedMillis]: clockwise from north, heading along the tangent. */
    fun positionAfter(elapsedMillis: Long): CarPlayLocationFix {
        val angle = if (lapMillis == 0L) 0.0 else 2 * PI * (elapsedMillis.coerceAtLeast(0) % lapMillis) / lapMillis
        val north = loopRadiusMeters * cos(angle)
        val east = loopRadiusMeters * sin(angle)
        val metersPerDegree = Math.toRadians(1.0) * EARTH_RADIUS_METERS
        return CarPlayLocationFix(
            latitudeDegrees = centerLatitude + north / metersPerDegree,
            longitudeDegrees = centerLongitude + east / (metersPerDegree * cos(Math.toRadians(centerLatitude))),
            altitudeMeters = ALTITUDE_METERS,
            bearingDegrees = (Math.toDegrees(angle) + 90) % 360,
            speedMetersPerSecond = metersPerSecond,
            accuracyMeters = ACCURACY_METERS,
        )
    }

    /** The wheel-speed samples of the last second, as a car would have collected them. */
    fun wheelSpeedUntil(elapsedMillis: Long): VehicleSpeedReading {
        val samples = List(SPEED_SAMPLES_PER_SENTENCE) { index ->
            val at = elapsedMillis - (SPEED_SAMPLES_PER_SENTENCE - 1 - index) * SPEED_SAMPLE_MILLIS
            VehicleSpeedSample(at.coerceAtLeast(0), metersPerSecond)
        }
        return VehicleSpeedReading(VehicleGear.DRIVE, samples)
    }

    /** How far the car has driven since construction, in km. */
    fun distanceKm(): Double = speedKmh * elapsedMillis() / MILLIS_PER_HOUR

    private fun elapsedMillis(): Long = (clock() - startMillis).coerceAtLeast(0)

    private fun logProgress(now: Long, fix: CarPlayLocationFix) {
        if (now < nextLogMillis) return
        nextLogMillis = now + LOG_INTERVAL_MILLIS
        log(format(
            "vehicle: at %.5f,%.5f heading %03.0f speed %d km/h battery %.1f%%",
            fix.latitudeDegrees, fix.longitudeDegrees, fix.bearingDegrees, speedKmh, batteryPercent(now - startMillis),
        ))
    }

    companion object {
        const val START_PERCENT = 62.0
        const val MIN_PERCENT = 8.0
        const val LOW_CHARGE_PERCENT = 15.0
        /** About what a 60 kWh car spends at 50 km/h. */
        const val DRAIN_PERCENT_PER_HOUR = 12.0
        const val MAX_RANGE_KM = 420
        const val MAX_CHARGE_WH = 60_000L
        const val CRUISE_KMH = 50
        const val LOOP_RADIUS_METERS = 500.0
        // A quiet loop in Cupertino, where CarPlay comes from.
        const val CENTER_LATITUDE = 37.33490
        const val CENTER_LONGITUDE = -122.00900
        private const val METERS_PER_SECOND_PER_KMH = 3.6
        private const val MILLIS_PER_HOUR = 3_600_000.0
        private const val WATTS_PER_KILOWATT = 1000.0
        private const val ALTITUDE_METERS = 72.0
        private const val ACCURACY_METERS = 5.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val SPEED_SAMPLE_MILLIS = 250L
        private const val SPEED_SAMPLES_PER_SENTENCE = 4
        private const val LOG_INTERVAL_MILLIS = 30_000L

        /** The car the Vehicle settings tab describes. */
        fun configured(
            advanced: SettingsValues,
            log: (String) -> Unit = {},
            clock: () -> Long = System::currentTimeMillis,
        ) = SimulatedVehicle(
            clock = clock,
            log = log,
            startPercent = advanced[SettingsSchema.BATTERY_START],
            drainPercentPerHour = advanced[SettingsSchema.BATTERY_DRAIN],
            fullRangeKm = advanced[SettingsSchema.FULL_RANGE_KM],
            charging = advanced[SettingsSchema.CHARGING],
            speedKmh = advanced[SettingsSchema.SPEED_KMH],
            centerLatitude = advanced[SettingsSchema.CENTER_LATITUDE],
            centerLongitude = advanced[SettingsSchema.CENTER_LONGITUDE],
            loopRadiusMeters = advanced[SettingsSchema.LOOP_METERS].toDouble(),
        )

        private fun format(format: String, vararg arguments: Any?): String = String.format(Locale.US, format, *arguments)
    }
}
