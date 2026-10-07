package com.shilapi.xcertplay.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.time.LocalTime
import kotlin.math.abs

class InstrumentPanelTest {
    private var nowMillis = 1_000_000L
    private val gauges = SettingsValues.DEFAULTS.with(SettingsSchema.CLUSTER_STYLE, "gauges")

    @Test fun gaugeStyleStreamsOnlyTheCentreMapWindowAndShowsTheWholePanel() {
        val display = ClusterDisplay.config(gauges)

        // 34 % of a 1920x720 panel, 78 % of its height, both on multiples of 8.
        assertEquals(656, display.widthPixels)
        assertEquals(560, display.heightPixels)
        assertEquals(Dimension(1920, 720), ClusterDisplay.windowSize(gauges))
    }

    @Test fun mapWindowSitsCentredBelowTheTopBar() {
        assertEquals(Rectangle(632, 94, 656, 560), ClusterDisplay.mapSlot(1920, 720, 34))
        assertEquals(Rectangle(424, 62, 432, 376), ClusterDisplay.mapSlot(1280, 480, 34))
    }

    @Test fun panelSettingsResizeTheMapWindow() {
        val values = gauges
            .with(SettingsSchema.CLUSTER_PANEL_WIDTH, 1280)
            .with(SettingsSchema.CLUSTER_PANEL_HEIGHT, 480)
            .with(SettingsSchema.CLUSTER_MAP_SHARE, 50)

        val display = ClusterDisplay.config(values)

        assertEquals(640, display.widthPixels)
        assertEquals(376, display.heightPixels)
        assertEquals(Dimension(1280, 480), ClusterDisplay.windowSize(values))
    }

    @Test fun readingsFollowTheSimulatedCar() {
        val car = SimulatedVehicle(clock = { nowMillis }, speedKmh = 80, drainPercentPerHour = 10.0)
        val values = SettingsValues.DEFAULTS
            .with(SettingsSchema.ODOMETER_START_KM, 1000)
            .with(SettingsSchema.OUTSIDE_TEMP_C, 21)
        nowMillis += 30 * 60 * 1000L

        val readings = ClusterReadings.of(car, values, LocalTime.of(14, 5))

        assertEquals(80.0, readings.speedKmh, 0.0)
        assertEquals('D', readings.gear)
        // 10 %/h of a 60 kWh battery.
        assertEquals(6.0, readings.powerKw, 1e-9)
        assertEquals(40.0, readings.tripKm, 1e-9)
        assertEquals(1040.0, readings.odometerKm, 1e-9)
        assertEquals(57.0, readings.batteryPercent, 0.0)
        assertEquals(21, readings.outsideC)
        assertEquals(LocalTime.of(14, 5), readings.time)
    }

    @Test fun parkedCarShowsParkAndNoPower() {
        val parked = SimulatedVehicle(clock = { nowMillis }, speedKmh = 0)

        val readings = ClusterReadings.of(parked, SettingsValues.DEFAULTS, LocalTime.NOON)

        assertEquals('P', readings.gear)
        assertEquals(0.0, readings.powerKw, 0.0)
    }

    @Test fun unitsConvertSpeedDistanceAndTemperature() {
        assertEquals(50.0, ClusterUnits.IMPERIAL.speed(80.4672), 1e-6)
        assertEquals(80.4672, ClusterUnits.METRIC.speed(80.4672), 0.0)
        assertEquals(100.0, ClusterUnits.IMPERIAL.distance(160.9344), 1e-6)
        assertEquals(72, ClusterUnits.IMPERIAL.temperature(22))
        assertEquals(22, ClusterUnits.METRIC.temperature(22))
        assertEquals("mph", ClusterUnits.IMPERIAL.speedLabel)
        assertEquals("km/h", ClusterUnits.METRIC.speedLabel)
        assertEquals(ClusterUnits.METRIC, ClusterUnits.of("metric"))
        assertEquals(ClusterUnits.IMPERIAL, ClusterUnits.of("anything"))
    }

    @Test fun dialFractionClampsToTheScale() {
        val scale = DialScale(min = -50.0, max = 200.0, majorStep = 50.0, minorStep = 10.0)

        assertEquals(0.2, scale.fraction(0.0), 1e-9)
        assertEquals(0.0, scale.fraction(-80.0), 0.0)
        assertEquals(1.0, scale.fraction(500.0), 0.0)
    }

    @Test fun renderedPanelDrawsTheMapInsideItsWindow() {
        val readings = ClusterReadings.of(SimulatedVehicle(clock = { nowMillis }), gauges, LocalTime.NOON)
        val red = BufferedImage(656, 560, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run { color = Color.RED; fillRect(0, 0, 656, 560); dispose() }
        }

        val withMap = InstrumentPanelPainter(gauges).render(1920, 720, readings, red, "")
        val waiting = InstrumentPanelPainter(gauges).render(1920, 720, readings, null, "Waiting for the iPhone map")

        assertEquals(1920, withMap.width)
        assertEquals(720, withMap.height)
        assertEquals(Color.RED.rgb, withMap.getRGB(960, 374))
        assertNotEquals(Color.RED.rgb, waiting.getRGB(960, 374))
        // Outside the map window the panel is the painted instrument background, not the map.
        assertNotEquals(Color.RED.rgb, withMap.getRGB(10, 10))
        assertTrue(accentPixels(withMap) > 200)
    }

    /** Pixels in the left third (the speed dial) close to the accent colour: the value arc was drawn. */
    private fun accentPixels(image: BufferedImage): Int {
        val accent = ClusterTheme.accent(gauges[SettingsSchema.CLUSTER_ACCENT])
        var count = 0
        for (y in 0 until image.height step 2) for (x in 0 until image.width / 3 step 2) {
            val pixel = Color(image.getRGB(x, y))
            val close = abs(pixel.red - accent.red) < 24 && abs(pixel.green - accent.green) < 24 &&
                abs(pixel.blue - accent.blue) < 24
            if (close) count++
        }
        return count
    }
}
