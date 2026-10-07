package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.transport.Iap2LocationMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedVehicleTest {
    private var nowMillis = 1_760_000_000_000L
    private val logged = mutableListOf<String>()
    private val vehicle = SimulatedVehicle(clock = { nowMillis }, log = logged::add)

    @Test fun startsAtSixtyTwoPercentAndDrainsSlowly() {
        val fresh = vehicle.snapshot()
        assertEquals(62.0, fresh.batteryPercent, 0.0)
        assertEquals(260, fresh.rangeKm)
        assertEquals(37_200L, fresh.currentChargeWh)
        assertEquals(60_000L, fresh.maxChargeWh)
        assertEquals(420, fresh.maxRangeKm)
        assertFalse(fresh.charging)
        assertFalse(fresh.rangeWarning)

        nowMillis += HOUR_MILLIS
        assertEquals(50.0, vehicle.snapshot().batteryPercent, 0.0)

        nowMillis += 10 * HOUR_MILLIS
        val drained = vehicle.snapshot()
        assertEquals(SimulatedVehicle.MIN_PERCENT, drained.batteryPercent, 0.0)
        assertTrue(drained.rangeWarning)
    }

    @Test fun reportsNothingUntilTheIphoneSubscribes() {
        assertNull(vehicle.latestNmea())
        assertTrue(vehicle.start())
        assertTrue(vehicle.latestNmea()!!.startsWith("\$GPGGA,"))
        vehicle.stop()
        assertNull(vehicle.latestNmea())
    }

    @Test fun drivesTheLoopAtTownSpeed() {
        vehicle.start()

        val sentences = vehicle.latestNmea()!!.trimEnd().split("\r\n")

        assertEquals(2, sentences.size)
        val rmc = sentences[1].split(",")
        assertEquals("\$GPRMC", rmc[0])
        assertEquals("A", rmc[2])
        assertEquals("27.00", rmc[7]) // 50 km/h in knots
        assertEquals("90.00", rmc[8]) // heading east at the top of a clockwise loop
    }

    @Test fun positionReturnsToTheStartAfterOneLap() {
        val start = SimulatedVehicle.positionAfter(0)
        val quarter = SimulatedVehicle.positionAfter(SimulatedVehicle.LAP_MILLIS / 4)
        val lap = SimulatedVehicle.positionAfter(SimulatedVehicle.LAP_MILLIS)

        assertEquals(start.latitudeDegrees, lap.latitudeDegrees, 1e-9)
        assertEquals(start.longitudeDegrees, lap.longitudeDegrees, 1e-9)
        assertEquals(180.0, quarter.bearingDegrees!!, 0.01)
        // A 500 m radius spans about 0.0045 degrees of latitude.
        assertEquals(0.0045, start.latitudeDegrees - SimulatedVehicle.CENTER_LATITUDE, 0.0001)
        assertTrue(quarter.longitudeDegrees > SimulatedVehicle.CENTER_LONGITUDE)
    }

    @Test fun addsWheelSpeedOnlyWhenRequested() {
        vehicle.onRequested(setOf(1, 2))
        vehicle.start()
        assertFalse(vehicle.latestNmea()!!.contains("\$PASCD"))

        vehicle.onRequested(setOf(1, 2, Iap2LocationMessages.VEHICLE_SPEED_DATA))
        nowMillis += 1_000
        val sentences = vehicle.latestNmea()!!.trimEnd().split("\r\n")

        assertEquals(3, sentences.size)
        assertTrue(
            sentences[2],
            sentences[2].startsWith("\$PASCD,0.250,C,D,0,4,0.00,13.889,0.25,13.889,0.50,13.889,0.75,13.889*"),
        )
    }

    @Test fun isDeterministicForTheSameClock() {
        val twin = SimulatedVehicle(clock = { nowMillis })
        vehicle.start()
        twin.start()

        nowMillis += 90_000

        assertEquals(vehicle.latestNmea(), twin.latestNmea())
        assertEquals(vehicle.snapshot(), twin.snapshot())
    }

    @Test fun logsWithTheVehiclePrefix() {
        vehicle.onRequested(setOf(1, 2, Iap2LocationMessages.VEHICLE_SPEED_DATA))
        vehicle.start()
        vehicle.latestNmea()
        vehicle.latestNmea() // Within the 30 s log interval: no second position line.

        assertEquals(3, logged.size)
        assertTrue(logged.all { it.startsWith("vehicle: ") })
        assertTrue(logged[0].contains("wheel-speed=true"))
        assertTrue(logged[1].contains("battery 62.0% range 260 km"))
        assertTrue(logged[2].contains("heading 090 speed 50 km/h"))
    }

    private companion object {
        const val HOUR_MILLIS = 60 * 60_000L
    }
}