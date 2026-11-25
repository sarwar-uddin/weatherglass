package com.github.sarwaruddin.weatherglass.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The meteorology that turns Open-Meteo numbers into readings. Pure functions,
 * unit-tested on the JVM. Ported from plasma-observatory's obs.js plus new bits
 * (heat stress, moon, sun altitude, model consensus).
 */
object Meteo {

    // ---- weather codes ---------------------------------------------------------

    enum class Sky { CLEAR, FEW, PARTLY, OVERCAST, FOG, DRIZZLE, RAIN, HEAVY_RAIN, SNOW, STORM }

    data class Condition(val text: String, val sky: Sky)

    fun wmo(code: Int?): Condition = when (code) {
        0 -> Condition("Clear", Sky.CLEAR)
        1 -> Condition("Mainly clear", Sky.FEW)
        2 -> Condition("Partly cloudy", Sky.PARTLY)
        3 -> Condition("Overcast", Sky.OVERCAST)
        45 -> Condition("Fog", Sky.FOG)
        48 -> Condition("Rime fog", Sky.FOG)
        51, 53, 55 -> Condition("Drizzle", Sky.DRIZZLE)
        56, 57 -> Condition("Freezing drizzle", Sky.DRIZZLE)
        61 -> Condition("Light rain", Sky.RAIN)
        63 -> Condition("Rain", Sky.RAIN)
        65 -> Condition("Heavy rain", Sky.HEAVY_RAIN)
        66, 67 -> Condition("Freezing rain", Sky.RAIN)
        71, 73, 75, 77 -> Condition("Snow", Sky.SNOW)
        80, 81 -> Condition("Showers", Sky.RAIN)
        82 -> Condition("Violent showers", Sky.HEAVY_RAIN)
        85, 86 -> Condition("Snow showers", Sky.SNOW)
        95 -> Condition("Thunderstorm", Sky.STORM)
        96, 99 -> Condition("Thunderstorm, hail", Sky.STORM)
        else -> Condition("—", Sky.PARTLY)
    }

    fun compass(deg: Double?): String {
        if (deg == null) return "—"
        val pts = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return pts[(((deg % 360) + 360) % 360 / 45).roundToInt() % 8]
    }

    /** Beaufort number and name for a wind speed in km/h. */
    fun beaufort(kmh: Double?): Pair<Int, String> {
        if (kmh == null) return 0 to "—"
        val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        val names = arrayOf("Calm", "Light air", "Light breeze", "Gentle breeze", "Moderate breeze",
            "Fresh breeze", "Strong breeze", "Near gale", "Gale", "Strong gale", "Storm",
            "Violent storm", "Hurricane force")
        var b = 0
        while (b < limits.size && kmh >= limits[b]) b++
        return b to names[b]
    }

    // ---- pressure --------------------------------------------------------------

    data class Tendency(val word: String, val arrow: String)

    /** Barometric tendency over three hours, in the Met Office's words. */
    fun tendency(dp3: Double?): Tendency {
        if (dp3 == null || dp3.isNaN()) return Tendency("", "")
        val a = abs(dp3)
        val up = dp3 > 0
        if (a < 0.1) return Tendency("steady", "→")
        val dir = if (up) "rising" else "falling"
        return when {
            a <= 1.5 -> Tendency("$dir slowly", if (up) "↗" else "↘")
            a <= 3.5 -> Tendency(dir, if (up) "↑" else "↓")
            a <= 6.0 -> Tendency("$dir quickly", if (up) "⇑" else "⇓")
            else -> Tendency("$dir very rapidly", if (up) "⇑" else "⇓")
        }
    }

    // ---- heat and humidity -----------------------------------------------------

    /** Wet-bulb temperature (°C) from air temperature and RH, Stull (2011). Valid ~5–99 % RH. */
    fun wetBulb(t: Double, rh: Double): Double =
        t * atan(0.151977 * sqrt(rh + 8.313659)) + atan(t + rh) - atan(rh - 1.676331) +
            0.00391838 * rh.pow(1.5) * atan(0.023101 * rh) - 4.686035

    /** NWS heat index (°C) from °C and RH, Rothfusz regression with the NWS adjustments. */
    fun heatIndex(tc: Double, rh: Double): Double {
        val t = tc * 9 / 5 + 32
        var hi = 0.5 * (t + 61.0 + (t - 68.0) * 1.2 + rh * 0.094)
        if ((hi + t) / 2 >= 80) {
            hi = -42.379 + 2.04901523 * t + 10.14333127 * rh - 0.22475541 * t * rh -
                0.00683783 * t * t - 0.05481717 * rh * rh + 0.00122874 * t * t * rh +
                0.00085282 * t * rh * rh - 0.00000199 * t * t * rh * rh
            if (rh < 13 && t in 80.0..112.0) hi -= (13 - rh) / 4 * sqrt((17 - abs(t - 95)) / 17)
            else if (rh > 85 && t in 80.0..87.0) hi += (rh - 85) / 10 * (87 - t) / 5
        }
        return (hi - 32) * 5 / 9
    }

    data class Band(val name: String, val color: Long, val advice: String)

    /** How muggy it feels, from the dew point (°C). */
    fun comfort(dew: Double?): Band = when {
        dew == null -> Band("—", 0xFF4A4A50, "")
        dew < 10 -> Band("Dry", 0xFF8FB3D9, "Crisp air.")
        dew < 16 -> Band("Comfortable", 0xFF3DB89A, "Pleasant.")
        dew < 19 -> Band("Sticky", 0xFFF2DC5D, "Noticeably humid.")
        dew < 22 -> Band("Muggy", 0xFFEA7A1E, "Sweat won't dry easily.")
        dew < 25 -> Band("Oppressive", 0xFFE0203F, "Heavy, tropical air.")
        else -> Band("Miserable", 0xFFA55DE0, "Dangerously humid air.")
    }

    /** Heat stress from wet-bulb temperature (°C). 35 °C is the theoretical survivability limit. */
    fun heatStress(wb: Double?): Band = when {
        wb == null -> Band("—", 0xFF4A4A50, "")
        wb < 21 -> Band("Low", 0xFF3DB89A, "No heat stress.")
        wb < 25 -> Band("Moderate", 0xFFF2DC5D, "Drink water, take shade breaks in heavy work.")
        wb < 28 -> Band("High", 0xFFEA7A1E, "Hard labour outdoors gets risky. Rest often.")
        wb < 31 -> Band("Very high", 0xFFE0203F, "Limit exertion outdoors; watch for heat exhaustion.")
        else -> Band("Extreme", 0xFFA55DE0, "Dangerous. Even resting in shade can overheat the body.")
    }

    /** Thunderstorm potential from CAPE (J/kg). */
    fun cape(j: Double?): Band = when {
        j == null -> Band("—", 0xFF4A4A50, "")
        j < 300 -> Band("Stable", 0xFF3DB89A, "Little energy for storms.")
        j < 1000 -> Band("Marginal", 0xFFF2DC5D, "Weak storms possible.")
        j < 2500 -> Band("Moderate", 0xFFEA7A1E, "Storms can grow if something sets them off.")
        j < 3500 -> Band("Strong", 0xFFE0203F, "Strong storms possible: lightning, gusts, hail.")
        else -> Band("Extreme", 0xFFA55DE0, "Explosive instability: kalbaishakhi-type storms.")
    }

    fun uv(i: Double?): Band = when {
        i == null -> Band("—", 0xFF4A4A50, "")
        i < 3 -> Band("Low", 0xFF3DB89A, "No protection needed.")
        i < 6 -> Band("Moderate", 0xFFF2DC5D, "Shade around midday.")
        i < 8 -> Band("High", 0xFFEA7A1E, "Cover up; sunscreen.")
        i < 11 -> Band("Very high", 0xFFE0203F, "Avoid the midday sun.")
        else -> Band("Extreme", 0xFFA55DE0, "Unprotected skin burns in minutes.")
    }

    // ---- air quality -----------------------------------------------------------

    /** US EPA categories, colours muted to sit in the black/crimson palette. */
    val AQI_CATS = listOf(
        Triple(50, Band("Good", 0xFF3DB89A, "Air is clean. Open the windows."), 0),
        Triple(100, Band("Moderate", 0xFFF2DC5D, "Fine for most; unusually sensitive people may notice it."), 1),
        Triple(150, Band("Unhealthy for sensitive groups", 0xFFEA7A1E, "Children, older people and anyone with asthma or heart disease should ease off long exertion outdoors."), 2),
        Triple(200, Band("Unhealthy", 0xFFE0203F, "Everyone may feel it. Cut long outdoor exertion; a fitted N95 helps."), 3),
        Triple(300, Band("Very unhealthy", 0xFFA55DE0, "Health alert. Stay in where you can, keep windows shut, run a purifier."), 4),
        Triple(Int.MAX_VALUE, Band("Hazardous", 0xFFA8326E, "Emergency conditions. Avoid going outside."), 5),
    )

    fun aqiCat(aqi: Double?): Band {
        if (aqi == null || aqi.isNaN()) return Band("—", 0xFF4A4A50, "")
        return AQI_CATS.first { aqi <= it.first }.second
    }

    /** PM2.5 (µg/m³) to category, EPA 2024 breakpoints. */
    private val PM25_BREAKS = doubleArrayOf(9.0, 35.4, 55.4, 125.4, 225.4)
    fun pm25Cat(c: Double?): Band {
        if (c == null || c.isNaN()) return aqiCat(null)
        var i = 0
        while (i < PM25_BREAKS.size && c > PM25_BREAKS[i]) i++
        return AQI_CATS[i].second
    }

    /** 24-hour guideline values (µg/m³): WHO 2021 and Bangladesh's Air Pollution Control Rules 2022. */
    val WHO_24H = mapOf("pm25" to 15.0, "pm10" to 45.0, "no2" to 25.0, "so2" to 40.0, "o3" to 100.0)
    val BD_24H = mapOf("pm25" to 65.0, "pm10" to 150.0, "no2" to 80.0, "so2" to 80.0)

    // ---- interpolation ---------------------------------------------------------

    /** Linear interpolation of a value series (times in ms) at instant t. */
    fun at(times: LongArray, values: DoubleArray, t: Long): Double? {
        if (times.isEmpty()) return null
        if (t <= times[0]) return values[0].takeUnless { it.isNaN() }
        for (i in 1 until times.size) {
            if (times[i] >= t) {
                val a = values[i - 1]
                val b = values[i]
                if (a.isNaN() || b.isNaN()) return (if (!a.isNaN()) a else b).takeUnless { it.isNaN() }
                return a + (b - a) * (t - times[i - 1]) / (times[i] - times[i - 1])
            }
        }
        return values.last().takeUnless { it.isNaN() }
    }

    // ---- sun ------------------------------------------------------------------

    private const val RAD = PI / 180

    /** Solar altitude in degrees (NOAA low-precision formulas, good to ~0.1°). */
    fun sunAltitude(ms: Long, lat: Double, lng: Double): Double {
        val d = ms / 86400000.0 - 10957.5 // days since J2000.0
        val g = (357.529 + 0.98560028 * d) * RAD
        val q = 280.459 + 0.98564736 * d
        val l = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * RAD
        val e = (23.439 - 0.00000036 * d) * RAD
        val dec = asin(sin(e) * sin(l))
        val ra = kotlin.math.atan2(cos(e) * sin(l), cos(l))
        val gmst = (18.697374558 + 24.06570982441908 * d) % 24
        val ha = (gmst * 15 + lng) * RAD - ra
        val phi = lat * RAD
        return asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(ha)) / RAD
    }

    // ---- moon -----------------------------------------------------------------

    const val SYNODIC = 29.530588853
    private const val NEW_MOON_REF = 947182440000L // 2000-01-06 18:14 UTC

    data class Moon(val age: Double, val illumination: Double, val name: String,
                    val nextFull: Long, val nextNew: Long)

    /** Moon phase by mean synodic month, with the main periodic correction (±~14 h). */
    fun moon(ms: Long): Moon {
        val days = (ms - NEW_MOON_REF) / 86400000.0
        var age = days % SYNODIC
        if (age < 0) age += SYNODIC
        val frac = age / SYNODIC
        val illum = (1 - cos(2 * PI * frac)) / 2
        val name = when {
            age < 1.0 || age > SYNODIC - 1.0 -> "New moon"
            age < 6.4 -> "Waxing crescent"
            age < 8.4 -> "First quarter"
            age < 13.8 -> "Waxing gibbous"
            age < 15.8 -> "Full moon"
            age < 21.1 -> "Waning gibbous"
            age < 23.1 -> "Last quarter"
            else -> "Waning crescent"
        }
        val dayMs = 86400000.0
        val toFull = ((SYNODIC / 2 - age) % SYNODIC + SYNODIC) % SYNODIC
        val toNew = SYNODIC - age
        return Moon(age, illum, name, ms + (toFull * dayMs).toLong(), ms + (toNew * dayMs).toLong())
    }

    // ---- model consensus ------------------------------------------------------

    data class Consensus(val mean: Double, val min: Double, val max: Double, val spread: Double)

    fun consensus(values: List<Double>): Consensus? {
        val v = values.filter { !it.isNaN() }
        if (v.isEmpty()) return null
        val mn = v.min()
        val mx = v.max()
        return Consensus(v.average(), mn, mx, mx - mn)
    }

    /**
     * 0–100 agreement score for one day: temperature-high spread (°C) and how far
     * apart the models are on rain (mm), each mapped onto a soft scale.
     */
    fun confidence(tempSpread: Double, rainValues: List<Double>): Int {
        val r = rainValues.filter { !it.isNaN() }
        val tScore = exp(-tempSpread / 3.0)
        val rScore = if (r.isEmpty()) 1.0 else {
            val wet = r.count { it >= 1.0 }.toDouble() / r.size // share of models saying "rain"
            val yesNo = 1 - 2 * minOf(wet, 1 - wet)               // 1 = all agree, 0 = split
            val amounts = r.max() - r.min()
            yesNo * 0.7 + exp(-amounts / 15.0) * 0.3
        }
        return ((tScore * 0.5 + rScore * 0.5) * 100).roundToInt().coerceIn(0, 100)
    }

    // ---- history ----------------------------------------------------------------

    data class History(
        val normalHi: Double?, val normalLo: Double?,
        val recHi: Double?, val recHiYear: Int, val recLo: Double?, val recLoYear: Int,
        val series: List<Pair<Int, Double>>, val exact: List<Triple<Int, Double, Double>>,
        val trendPerDecade: Double, val slope: Double, val intercept: Double,
    )

    /**
     * Climatology for calendar date (month, day) from an ERA5 daily archive:
     * normal high/low (1991–2020, ±3 days), the date's record high/low, per-year
     * ±3-day mean high and its linear trend.
     */
    fun history(dates: List<String>, hi: DoubleArray, lo: DoubleArray, month: Int, day: Int): History {
        val window = HashSet<String>()
        val ref = java.time.LocalDate.of(2001, month, day)
        for (k in -3..3) {
            val t = ref.plusDays(k.toLong())
            window += "%02d-%02d".format(t.monthValue, t.dayOfMonth)
        }
        val key = "%02d-%02d".format(month, day)
        var nh = 0.0; var nl = 0.0; var nn = 0
        var recHi: Double? = null; var recHiYear = 0
        var recLo: Double? = null; var recLoYear = 0
        val byYear = HashMap<Int, Pair<Double, Int>>()
        val exact = ArrayList<Triple<Int, Double, Double>>()
        for (i in dates.indices) {
            val md = dates[i].substring(5, 10)
            if (md !in window) continue
            val y = dates[i].substring(0, 4).toInt()
            val h = hi[i]; val l = lo[i]
            if (h.isNaN() || l.isNaN()) continue
            if (y in 1991..2020) { nh += h; nl += l; nn++ }
            val (s, n) = byYear[y] ?: (0.0 to 0)
            byYear[y] = (s + h) to (n + 1)
            if (md == key) {
                exact += Triple(y, h, l)
                if (recHi == null || h > recHi) { recHi = h; recHiYear = y }
                if (recLo == null || l < recLo) { recLo = l; recLoYear = y }
            }
        }
        val series = byYear.filter { it.value.second >= 5 }
            .map { it.key to it.value.first / it.value.second }.sortedBy { it.first }
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for ((x, v) in series) { sx += x; sy += v; sxx += x.toDouble() * x; sxy += x * v }
        val n = series.size
        val slope = if (n > 2) (n * sxy - sx * sy) / (n * sxx - sx * sx) else 0.0
        return History(
            if (nn > 0) nh / nn else null, if (nn > 0) nl / nn else null,
            recHi, recHiYear, recLo, recLoYear, series, exact,
            slope * 10, slope, if (n > 0) (sy - slope * sx) / n else 0.0,
        )
    }

    data class Rank(val rank: Int, val of: Int, val since: Int)

    /** Where a high ranks on this date (1 = warmest); `since` = last year that was warmer (0 = never). */
    fun rankHigh(h: History, hi: Double?): Rank? {
        if (hi == null) return null
        var rank = 1
        var since = 0
        for ((y, eh, _) in h.exact) if (eh > hi) { rank++; since = maxOf(since, y) }
        return Rank(rank, h.exact.size + 1, since)
    }

    fun ordinal(n: Int): String {
        val v = n % 100
        val suf = if (v in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suf"
    }

    fun signed(v: Double?, digits: Int = 1): String {
        if (v == null || v.isNaN()) return "—"
        val s = "%.${digits}f".format(v)
        val zero = s.toDouble() == 0.0
        return (if (v > 0 && !zero) "+" else "") + s.replace("-", "−")
    }

    /** Daylight hours on a given latitude/day-of-year, for the day-length trend (CBM model). */
    fun dayLength(lat: Double, doy: Int): Double {
        val theta = 0.2163108 + 2 * atan(0.9671396 * kotlin.math.tan(0.00860 * (doy - 186)))
        val phi = asin(0.39795 * cos(theta))
        val p = 0.8333
        val x = (sin(p * RAD) + sin(lat * RAD) * sin(phi)) / (cos(lat * RAD) * cos(phi))
        return 24 - 24 / PI * acos(x.coerceIn(-1.0, 1.0))
    }

}
