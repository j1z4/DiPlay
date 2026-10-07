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
 * slowly from 62 % while the car circles a short loop at town speed, so the iPhone sees an EV
 * with range, charge, position and wheel speed without a real car. Every reading derives from
 * the time since construction as read through [clock] (epoch milliseconds), so the same clock
 * always yields the same readings and tests can step time by hand.
 */
class SimulatedVehicle(
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) : VehicleStatusProvider, Iap2LocationProvider {
    private val startMillis = clock()
    @Volatile private var started = false
    @Volatile private var speedRequested = false
    private var nextLogMillis = startMillis

    override fun snapshot(): VehicleStatusSnapshot {
        val percent = batteryPercent(elapsedMillis())
        return VehicleStatusSnapshot(
            rangeKm = (MAX_RANGE_KM * percent / 100).roundToInt(),
            rangeWarning = percent <= LOW_CHARGE_PERCENT,
            batteryPercent = percent,
            currentChargeWh = (MAX_CHARGE_WH * percent / 100).roundToLong(),
            maxChargeWh = MAX_CHARGE_WH,
            maxRangeKm = MAX_RANGE_KM,
            charging = false,
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
            status.batteryPercent, status.rangeKm, CRUISE_KMH, CENTER_LATITUDE, CENTER_LONGITUDE,
        )
    }

    private fun elapsedMillis(): Long = (clock() - startMillis).coerceAtLeast(0)

    private fun logProgress(now: Long, fix: CarPlayLocationFix) {
        if (now < nextLogMillis) return
        nextLogMillis = now + LOG_INTERVAL_MILLIS
        log(format(
            "vehicle: at %.5f,%.5f heading %03.0f speed %d km/h battery %.1f%%",
            fix.latitudeDegrees, fix.longitudeDegrees, fix.bearingDegrees, CRUISE_KMH, batteryPercent(now - startMillis),
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
        const val CRUISE_METERS_PER_SECOND = CRUISE_KMH / 3.6
        const val LOOP_RADIUS_METERS = 500.0
        /** One clockwise lap of the loop at cruise speed. */
        val LAP_MILLIS: Long = (2 * PI * LOOP_RADIUS_METERS / CRUISE_METERS_PER_SECOND * 1000).roundToLong()
        // A quiet loop in Cupertino, where CarPlay comes from.
        const val CENTER_LATITUDE = 37.33490
        const val CENTER_LONGITUDE = -122.00900
        private const val ALTITUDE_METERS = 72.0
        private const val ACCURACY_METERS = 5.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val SPEED_SAMPLE_MILLIS = 250L
        private const val SPEED_SAMPLES_PER_SENTENCE = 4
        private const val LOG_INTERVAL_MILLIS = 30_000L

        /** The charge after [elapsedMillis] of driving; never below [MIN_PERCENT], one decimal. */
        fun batteryPercent(elapsedMillis: Long): Double {
            val hours = elapsedMillis.coerceAtLeast(0) / 3_600_000.0
            val percent = (START_PERCENT - DRAIN_PERCENT_PER_HOUR * hours).coerceAtLeast(MIN_PERCENT)
            return (percent * 10).roundToInt() / 10.0
        }

        /** Where on the loop the car is after [elapsedMillis]: clockwise from north, heading along the tangent. */
        fun positionAfter(elapsedMillis: Long): CarPlayLocationFix {
            val angle = 2 * PI * (elapsedMillis.coerceAtLeast(0) % LAP_MILLIS) / LAP_MILLIS
            val north = LOOP_RADIUS_METERS * cos(angle)
            val east = LOOP_RADIUS_METERS * sin(angle)
            val metersPerDegree = Math.toRadians(1.0) * EARTH_RADIUS_METERS
            return CarPlayLocationFix(
                latitudeDegrees = CENTER_LATITUDE + north / metersPerDegree,
                longitudeDegrees = CENTER_LONGITUDE + east / (metersPerDegree * cos(Math.toRadians(CENTER_LATITUDE))),
                altitudeMeters = ALTITUDE_METERS,
                bearingDegrees = (Math.toDegrees(angle) + 90) % 360,
                speedMetersPerSecond = CRUISE_METERS_PER_SECOND,
                accuracyMeters = ACCURACY_METERS,
            )
        }

        /** The wheel-speed samples of the last second, as a car would have collected them. */
        fun wheelSpeedUntil(elapsedMillis: Long): VehicleSpeedReading {
            val samples = List(SPEED_SAMPLES_PER_SENTENCE) { index ->
                val at = elapsedMillis - (SPEED_SAMPLES_PER_SENTENCE - 1 - index) * SPEED_SAMPLE_MILLIS
                VehicleSpeedSample(at.coerceAtLeast(0), CRUISE_METERS_PER_SECOND)
            }
            return VehicleSpeedReading(VehicleGear.DRIVE, samples)
        }

        private fun format(format: String, vararg arguments: Any?): String = String.format(Locale.US, format, *arguments)
    }
}