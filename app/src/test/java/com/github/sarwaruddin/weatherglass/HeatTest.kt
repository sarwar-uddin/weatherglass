package com.github.sarwaruddin.weatherglass

import com.github.sarwaruddin.weatherglass.data.Heat
import com.github.sarwaruddin.weatherglass.data.Heat.Work
import com.github.sarwaruddin.weatherglass.data.Meteo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.cos

class HeatTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    /** Night, no sun: WBGT ≈ 0.7·Tw + 0.3·Ta (globe ≈ air, natural ≈ psychrometric wet-bulb). */
    @Test fun shadeAtNightMatchesPsychrometricWbgt() {
        val ta = 28.0; val rh = 80.0
        val w = Heat.wbgt(ta, rh, 1.5, 0.0, -0.3)
        val tw = Meteo.wetBulb(ta, rh)
        assertEquals(0.7 * tw + 0.3 * ta, w.wbgt, 1.0)
        assertTrue("globe ${w.tg} should sit within 1.5° of air", kotlin.math.abs(w.tg - ta) < 1.5)
        assertEquals(tw, w.tnw, 1.0)
    }

    /**
     * Dhaka pre-monsoon midday: 34 °C, 60 % RH, 850 W/m², light breeze. Measured WBGT in such
     * conditions runs 31–34 °C (Liljegren-type models and wearable studies of rickshaw pullers).
     */
    @Test fun hotSunnyMiddayIsInTheDangerRange() {
        val w = Heat.wbgt(34.0, 60.0, 1.5, 850.0, cos(Math.toRadians(20.0)))
        assertTrue("WBGT ${w.wbgt}", w.wbgt in 31.0..34.5)
        assertTrue("globe ${w.tg} should be well above air", w.tg - 34.0 in 6.0..18.0)
        assertTrue("natural wet-bulb ${w.tnw} ≥ psychrometric", w.tnw >= Meteo.wetBulb(34.0, 60.0) - 0.2)
    }

    @Test fun sunRaisesAndWindLowersWbgt() {
        val cz = cos(Math.toRadians(30.0))
        val shade = Heat.wbgt(32.0, 55.0, 2.0, 0.0, cz).wbgt
        val sun = Heat.wbgt(32.0, 55.0, 2.0, 800.0, cz).wbgt
        val sunCalm = Heat.wbgt(32.0, 55.0, 0.3, 800.0, cz).wbgt
        val sunWindy = Heat.wbgt(32.0, 55.0, 6.0, 800.0, cz).wbgt
        assertTrue("sun $sun > shade $shade", sun - shade in 1.0..5.0)
        assertTrue("calm $sunCalm > breezy $sun > windy $sunWindy", sunCalm > sun && sun > sunWindy)
    }

    @Test fun humidityRaisesWbgt() {
        val cz = 0.8
        assertTrue(Heat.wbgt(32.0, 85.0, 2.0, 600.0, cz).wbgt > Heat.wbgt(32.0, 40.0, 2.0, 600.0, cz).wbgt + 3)
    }

    @Test fun directFraction() {
        assertEquals(0.0, Heat.directFraction(0.0, 0.8), 1e-9)
        assertEquals(0.0, Heat.directFraction(300.0, -0.1), 1e-9)
        val clear = Heat.directFraction(950.0, 0.9)
        val overcast = Heat.directFraction(150.0, 0.9)
        assertTrue("clear $clear", clear in 0.6..0.9)
        assertTrue("overcast $overcast", overcast < 0.1)
    }

    @Test fun dewRoundTrip() {
        val dew = Heat.dewPoint(30.0, 70.0)
        assertEquals(23.9, dew, 0.3)
        assertEquals(70.0, Heat.rhFromDew(30.0, dew), 0.5)
    }

    @Test fun wind2m() {
        assertEquals(10 / 3.6 * Math.pow(0.2, 0.15), Heat.wind2m(10.0, 800.0), 1e-9)
        assertEquals(0.13, Heat.wind2m(0.0, 0.0), 1e-9)
    }

    /** Equinox solar noon at 23.8 N: zenith = latitude. */
    @Test fun cosZenithAtNoon() {
        val noon = ms("2026-03-20T06:06:00Z")
        assertEquals(cos(Math.toRadians(23.8)), Heat.cosZenith(noon, 23.8, noon), 0.01)
        // six hours later the sun is on the horizon
        assertEquals(0.0, Heat.cosZenith(noon + 6 * 3_600_000L, 23.8, noon), 0.03)
    }

    @Test fun fitLatitudeRecoversPlace() {
        for (lat in listOf(23.8, -33.9, 51.5, 5.0)) {
            val doys = IntArray(16) { 328 + it } // late November
            val hours = DoubleArray(16) { Meteo.dayLength(lat, doys[it]) }
            assertEquals(lat, Heat.fitLatitude(doys, hours)!!, 0.6)
        }
        assertNull(Heat.fitLatitude(intArrayOf(1, 2), doubleArrayOf(10.0, 10.0)))
    }

    /** ACGIH TLV table, acclimatised. */
    @Test fun regimes() {
        assertEquals(0, Heat.regime(25.9, Work.HEAVY).level)
        assertEquals(1, Heat.regime(27.0, Work.HEAVY).level)
        assertEquals(45, Heat.regime(27.0, Work.HEAVY).workMin)
        assertEquals(2, Heat.regime(28.5, Work.HEAVY).level)
        assertEquals(3, Heat.regime(30.0, Work.HEAVY).level)
        assertEquals(4, Heat.regime(31.0, Work.HEAVY).level)
        assertEquals(0, Heat.regime(28.0, Work.MODERATE).level)
        assertEquals(1, Heat.regime(28.5, Work.MODERATE).level)
        assertEquals(4, Heat.regime(32.0, Work.MODERATE).level)
        // light: no 45/15 step; straight from steady to 30/30
        assertEquals(0, Heat.regime(31.0, Work.LIGHT).level)
        assertEquals(2, Heat.regime(31.5, Work.LIGHT).level)
        assertEquals(4, Heat.regime(33.0, Work.LIGHT).level)
        assertEquals("30 min work / 30 min rest; drink 1 L/h", Heat.regime(28.5, Work.HEAVY).advice)
        assertEquals("steady work; drink to thirst", Heat.regime(20.0, Work.HEAVY).advice)
        assertFalse(Heat.regime(35.0, Work.HEAVY).advice.contains("safe", ignoreCase = true))
    }

    @Test fun windows() {
        val w = doubleArrayOf(24.0, 26.5, 28.0, 30.0, 31.0, 30.0, 28.0, 26.0, 24.0, 30.0, 30.2, 24.0)
        val lv = IntArray(w.size) { Heat.regime(w[it], Work.HEAVY).level }
        assertEquals(listOf(1..6, 9..10), Heat.runs(lv, 0, w.size, 1))
        val worst = Heat.worstWindow(w, lv, 0, w.size)
        assertNotNull(worst)
        assertEquals(4..4, worst!!.range)
        assertEquals(4, worst.level)
        // excluding the peak, the 15/45 stretch with the hottest hour wins
        val later = Heat.worstWindow(w, lv, 5, w.size)!!
        assertEquals(9..10, later.range)
        assertEquals(0, Heat.coolestStretch(w, 0, 6, 2))
        assertEquals(7, Heat.coolestStretch(w, 5, 9, 2))
        assertNull(Heat.worstWindow(DoubleArray(4) { 20.0 }, IntArray(4), 0, 4))
    }

    @Test fun quietAndNextHeat() {
        val w = doubleArrayOf(20.0, 22.0, Double.NaN, 21.0, 27.0, 22.0)
        assertTrue(Heat.quiet(w, 0, 4))
        assertFalse(Heat.quiet(w, 0, 5))
        assertEquals(4, Heat.nextHeat(w, 0))
        assertEquals(-1, Heat.nextHeat(w, 5))
    }
}
