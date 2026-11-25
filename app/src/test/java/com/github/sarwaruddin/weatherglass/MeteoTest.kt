package com.github.sarwaruddin.weatherglass

import com.github.sarwaruddin.weatherglass.data.Meteo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MeteoTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun heatIndex() = assertEquals(45.0, Meteo.heatIndex(35.0, 60.0), 1.5)

    /** Stull (2011): 20 °C at 50 % RH gives a wet-bulb of 13.7 °C. */
    @Test fun wetBulb() = assertEquals(13.7, Meteo.wetBulb(20.0, 50.0), 0.3)

    @Test fun tendency() {
        assertEquals("steady", Meteo.tendency(0.05).word)
        assertEquals("rising slowly", Meteo.tendency(1.0).word)
        assertEquals("falling", Meteo.tendency(-2.0).word)
        assertEquals("rising quickly", Meteo.tendency(4.0).word)
        assertEquals("falling very rapidly", Meteo.tendency(-7.0).word)
        assertEquals("", Meteo.tendency(Double.NaN).word)
    }

    @Test fun compass() {
        assertEquals("N", Meteo.compass(350.0))
        assertEquals("E", Meteo.compass(95.0))
        assertEquals("SW", Meteo.compass(-140.0))
    }

    /** Full moon on 2026-11-24 14:53 UTC. */
    @Test fun moonFull() {
        val m = Meteo.moon(ms("2026-11-24T14:53:00Z"))
        assertTrue("illumination ${m.illumination}", m.illumination > 0.95)
        assertEquals("Full moon", m.name)
    }

    /** Equinox noon in Dhaka (23.81 N): altitude ≈ 90 − 23.8 = 66°. Solar noon ≈ 06:06 UTC. */
    @Test fun sunAltitudeEquinox() =
        assertEquals(66.0, Meteo.sunAltitude(ms("2026-03-20T06:06:00Z"), 23.81, 90.41), 1.5)
}
