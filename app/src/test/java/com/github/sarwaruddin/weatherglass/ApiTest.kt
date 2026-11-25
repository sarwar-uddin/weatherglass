package com.github.sarwaruddin.weatherglass

import com.github.sarwaruddin.weatherglass.data.Api
import com.github.sarwaruddin.weatherglass.data.Meteo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses real Open-Meteo responses captured for Dhaka on 2026-11-24. */
class ApiTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }.readText()

    @Test fun forecast() {
        val f = Api.parseForecast(fixture("forecast.json"), fetched = 42L)
        assertEquals(42L, f.fetched)
        assertEquals(21600, f.offsetSec)
        assertEquals(9.0, f.elevation, 1e-9)

        assertEquals(1_795_535_100_000L, f.current.t) // seconds -> ms
        assertEquals(20.6, f.current.temp, 1e-9)
        assertEquals(79.0, f.current.rh, 1e-9)
        assertEquals(1018.4, f.current.pressure, 1e-9)
        assertEquals(0, f.current.code)
        assertFalse(f.current.isDay)

        assertEquals(408, f.hourly.size)
        assertEquals(1_795_370_400_000L, f.hourly.t[0])
        assertEquals(3_600_000L, f.hourly.t[1] - f.hourly.t[0])
        assertEquals(408, f.hourly.isDay.size)
        assertTrue(f.hourly.temp.none { it.isNaN() })

        assertEquals(17, f.daily.size)
        assertEquals(1_795_370_400_000L, f.daily.t[0]) // local midnight, UTC+6
        assertEquals(0L, (f.daily.t[0] + f.offsetSec * 1000L) % 86_400_000L)
        assertEquals(1_795_393_099_000L, f.daily.sunrise[0])

        assertEquals(12, f.nowcast.t.size)
        assertEquals(12, f.nowcast.precip.size)
        assertEquals(1_795_535_100_000L, f.nowcast.t[0])
    }

    @Test fun models() {
        val m = Api.parseModels(fixture("models.json"))
        assertEquals(10, m.t.size)
        assertEquals(listOf("ECMWF", "GFS", "ICON", "GEM", "JMA"), m.runs.map { it.label })
        for (r in m.runs) {
            assertEquals(10, r.max.size); assertEquals(10, r.min.size); assertEquals(10, r.precip.size)
        }
        assertEquals(27.7, m.runs[0].max[0], 1e-9)
        assertEquals(3, m.runs.first { it.label == "ICON" }.max.count { it.isNaN() }) // nulls -> NaN
    }

    @Test fun modelsAllNullAreDropped() {
        val json = """{"daily":{"time":[1,2],"temperature_2m_max_gfs_seamless":[20.0,21.0],
            "temperature_2m_max_jma_seamless":[null,null],"temperature_2m_min_jma_seamless":[null,null],
            "precipitation_sum_jma_seamless":[null,null]}}"""
        val m = Api.parseModels(json)
        assertEquals(listOf("GFS"), m.runs.map { it.label })
        assertEquals(2000L, m.t[1])
    }

    @Test fun air() {
        val a = Api.parseAir(fixture("air.json"))
        assertEquals(131.0, a.now.aqi, 1e-9)
        assertEquals(91.3, a.now.pm25, 1e-9)
        assertEquals(706.0, a.now.co, 1e-9)
        assertEquals(120, a.t.size)
        assertEquals(120, a.pm25.size)
        assertEquals(120, a.aqi.size)
    }

    @Test fun search() {
        val hits = Api.parseSearch(fixture("search.json"))
        assertTrue(hits.isNotEmpty())
        with(hits[0]) {
            assertEquals("Sylhet", name)
            assertEquals("Sylhet Division", region)
            assertEquals("Bangladesh", country)
            assertEquals(24.89904, lat, 1e-9)
        }
        assertTrue(Api.parseSearch("""{"generationtime_ms":0.1}""").isEmpty())
    }

    @Test fun archive() {
        val a = Api.parseArchive(fixture("archive_sample.json"), fetched = 7L)
        assertEquals(1827, a.dates.size)
        assertEquals("1940-01-01", a.dates[0])
        assertEquals(26.0, a.hi[0], 1e-9)
        assertEquals(13.9, a.lo[0], 1e-9)
        val h = Meteo.history(a.dates, a.hi, a.lo, 11, 24)
        assertNotNull(h.recHi)
        assertNotNull(h.recLo)
        assertTrue(h.exact.isNotEmpty())
    }

    @Test fun urls() {
        val u = Api.forecastUrl(23.8061, 90.4194)
        assertTrue(u.contains("latitude=23.81&longitude=90.42"))
        assertTrue(u.endsWith("timezone=auto&timeformat=unixtime"))
        assertTrue(Api.archiveUrl(1.0, 2.0, "2026-11-18").contains("end_date=2026-11-18"))
        assertTrue(Api.searchUrl("Cox's Bazar").contains("name=Cox%27s+Bazar"))
        assertTrue(Api.modelsUrl(1.0, 2.0).contains("models=ecmwf_ifs025,gfs_seamless,icon_seamless,gem_seamless,jma_seamless"))
    }
}
