package com.shilapi.xcertplay.desktop

import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * What the instrument panel shows at one moment, in metric units; [ClusterUnits] converts for
 * display. Taken from the same [SimulatedVehicle] the iPhone gets its vehicle data from, so the
 * dials and CarPlay's own range and battery readouts agree.
 */
data class ClusterReadings(
    val speedKmh: Double,
    val powerKw: Double,
    val gear: Char,
    val batteryPercent: Double,
    val rangeKm: Int,
    val rangeWarning: Boolean,
    val charging: Boolean,
    val tripKm: Double,
    val odometerKm: Double,
    val outsideC: Int,
    val time: LocalTime,
) {
    companion object {
        fun of(car: SimulatedVehicle, values: SettingsValues, time: LocalTime = LocalTime.now()): ClusterReadings {
            val status = car.snapshot()
            val trip = car.distanceKm()
            return ClusterReadings(
                speedKmh = car.speedKmh.toDouble(),
                powerKw = car.powerKw,
                gear = if (car.speedKmh > 0) 'D' else 'P',
                batteryPercent = status.batteryPercent,
                rangeKm = status.rangeKm,
                rangeWarning = status.rangeWarning,
                charging = status.charging,
                tripKm = trip,
                odometerKm = values[SettingsSchema.ODOMETER_START_KM] + trip,
                outsideC = values[SettingsSchema.OUTSIDE_TEMP_C],
                time = time,
            )
        }
    }
}

/** A dial's range and tick spacing, in display units. */
data class DialScale(val min: Double, val max: Double, val majorStep: Double, val minorStep: Double) {
    /** How far along the dial [value] sits, 0 at [min] and 1 at [max]. */
    fun fraction(value: Double): Double = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

/** The Cluster tab's units choice: how speeds, distances and temperatures are shown. */
enum class ClusterUnits(
    val speedLabel: String,
    val distanceLabel: String,
    val temperatureLabel: String,
    private val perKm: Double,
    val speedScale: DialScale,
) {
    IMPERIAL("mph", "mi", "°F", 1 / KM_PER_MILE, DialScale(0.0, 160.0, 20.0, 10.0)),
    METRIC("km/h", "km", "°C", 1.0, DialScale(0.0, 240.0, 40.0, 20.0));

    fun speed(kmh: Double): Double = kmh * perKm
    fun distance(km: Double): Double = km * perKm
    fun temperature(celsius: Int): Int = if (this == IMPERIAL) (celsius * 9 / 5.0 + 32).roundToInt() else celsius

    companion object {
        fun of(setting: String): ClusterUnits = if (setting == "metric") METRIC else IMPERIAL
    }
}

private const val KM_PER_MILE = 1.609344
