package com.github.sarwaruddin.weatherglass.data

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Outdoor heat stress for work planning: WBGT and ACGIH work/rest regimes. Pure functions.
 *
 * WBGT method: Liljegren, Carhart, Lawday, Tschopp & Sharp (2008), "Modeling the Wet Bulb Globe
 * Temperature Using Standard Meteorological Measurements", J. Occup. Environ. Hyg. 5(10):645–655.
 * Outdoor WBGT = 0.7·Tnw + 0.2·Tg + 0.1·Ta (ISO 7243 / Yaglou & Minard). The natural wet-bulb
 * (wick cylinder) and black globe (50.8 mm, Liljegren's standard globe) are each solved from
 * their own heat balance (convection, evaporation, long-wave exchange with sky and ground,
 * direct + diffuse short-wave) with Liljegren's direct-beam fraction from the clearness index. We solve each balance by bisection rather than relaxation (same fixed point, guaranteed
 * convergence). Simplifications vs. the reference code: 10 m → 2 m wind by a stability-dependent
 * power law instead of full Pasquill classes; ground temperature = air temperature (as Liljegren).
 *
 * Work/rest: ACGIH TLV® for heat stress (2007–2022 screening table, acclimatised workers, light
 * summer clothing), the table ISO 7243 also points to. ACGIH gives no continuous-work value for
 * heavy work; we use 26.0 °C, one step (1.5 °C) below its 75 %-work limit.
 * Water: US Army TB MED 507 fluid guidance (≈0.5–1 L/h; never more than ~1.4 L/h).
 */
object Heat {

    // ---- constants (Liljegren 2008, Table 1) -----------------------------------------------
    private const val SIGMA = 5.6696e-8
    private const val CP = 1003.5
    private const val M_AIR = 28.97
    private const val M_H2O = 18.015
    private const val R_AIR = 8314.34 / M_AIR
    private val PR = CP / (CP + 1.25 * R_AIR)
    private const val D_GLOBE = 0.0508
    private const val EMIS_GLOBE = 0.95
    private const val ALB_GLOBE = 0.05
    private const val D_WICK = 0.007
    private const val L_WICK = 0.0254
    private const val EMIS_WICK = 0.95
    private const val ALB_WICK = 0.4
    private const val EMIS_SFC = 0.999
    private const val ALB_SFC = 0.45
    private const val SOLAR_CONST = 1367.0
    private const val MIN_SPEED = 0.13
    private const val K0 = 273.15

    /** Saturation vapour pressure over water, hPa (Buck 1981). */
    fun esat(tC: Double): Double = 6.1121 * 1.0007 * exp(17.502 * tC / (240.97 + tC))

    private fun emisAtm(tK: Double, rh: Double): Double = 0.575 * (rh * esat(tK - K0)).pow(0.143)

    private fun viscosity(tK: Double): Double {
        val omega = (tK / 97.0 - 2.9) / 0.4 * (-0.034) + 1.048
        return 0.0000026693 * sqrt(M_AIR * tK) / (3.617 * 3.617 * omega)
    }

    private fun conductivity(tK: Double): Double = (CP + 1.25 * R_AIR) * viscosity(tK)

    private fun diffusivity(tK: Double, pHpa: Double): Double {
        val pcrit13 = (36.4 * 218.0).pow(1.0 / 3)
        val tcrit512 = (132.0 * 647.3).pow(5.0 / 12)
        val tcrit12 = sqrt(132.0 * 647.3)
        val mmix = sqrt(1.0 / M_AIR + 1.0 / M_H2O)
        return 3.640e-4 * (tK / tcrit12).pow(2.334) * pcrit13 * tcrit512 * mmix / (pHpa / 1013.25) * 1e-4
    }

    private fun evap(tK: Double): Double = (313.15 - tK) / 30.0 * (-71100.0) + 2.4073e6

    private fun density(tK: Double, pHpa: Double) = pHpa * 100 / (R_AIR * tK)

    private fun hSphere(tK: Double, pHpa: Double, u: Double): Double {
        val re = max(u, MIN_SPEED) * density(tK, pHpa) * D_GLOBE / viscosity(tK)
        val nu = 2.0 + 0.6 * sqrt(re) * PR.pow(0.3333)
        return nu * conductivity(tK) / D_GLOBE
    }

    private fun hCylinder(tK: Double, pHpa: Double, u: Double): Double {
        val re = max(u, MIN_SPEED) * density(tK, pHpa) * D_WICK / viscosity(tK)
        val nu = 0.281 * re.pow(1 - 0.4) * PR.pow(1 - 0.56)
        return nu * conductivity(tK) / D_WICK
    }

    private inline fun bisect(lo0: Double, hi0: Double, f: (Double) -> Double): Double {
        var lo = lo0
        var hi = hi0
        repeat(60) {
            val m = (lo + hi) / 2
            if (f(m) > 0) hi = m else lo = m
            if (hi - lo < 1e-4) return (lo + hi) / 2
        }
        return (lo + hi) / 2
    }

    /**
     * Direct-beam fraction of global horizontal irradiance, Liljegren (2008) eq. 13, from the
     * clearness index s* = S / (S0·cosZ), capped at 0.85. Returns 0 with the sun under 89.5°.
     */
    fun directFraction(solar: Double, cosZ: Double): Double {
        if (solar <= 0 || cosZ <= cos(89.5 * PI / 180)) return 0.0
        val sStar = min(solar / (SOLAR_CONST * cosZ), 0.85)
        if (sStar <= 0) return 0.0
        return exp(3 - 1.34 * sStar - 1.65 / sStar).coerceIn(0.0, 0.9)
    }

    /** GHI capped to what the clearness cap allows (Liljegren does the same). */
    private fun capSolar(solar: Double, cosZ: Double): Double =
        if (cosZ <= 0) min(max(solar, 0.0), 100.0) else min(max(solar, 0.0), 0.85 * SOLAR_CONST * cosZ).let { max(it, 0.0) }

    /** Black globe temperature, °C. `u` is 2 m wind (m/s), `solar` GHI W/m², `rh` 0–100. */
    fun globeTemp(ta: Double, rh: Double, u: Double, solar: Double, cosZ: Double, pHpa: Double = 1010.0): Double {
        val tK = ta + K0
        val r = rh / 100
        val s = capSolar(solar, cosZ)
        val fdb = directFraction(s, cosZ)
        val cz = max(cosZ, 0.01)
        val lw = 0.5 * (emisAtm(tK, r) * tK.pow(4) + EMIS_SFC * tK.pow(4))
        val sw = s / (2 * SIGMA * EMIS_GLOBE) * (1 - ALB_GLOBE) * (fdb * (1 / (2 * cz) - 1) + 1 + ALB_SFC)
        val tg = bisect(tK - 30, tK + 70) { g ->
            val h = hSphere(0.5 * (g + tK), pHpa, u)
            g.pow(4) - (lw - h / (SIGMA * EMIS_GLOBE) * (g - tK) + sw)
        }
        return tg - K0
    }

    /** Natural (unventilated, sun-exposed) wet-bulb temperature, °C. */
    fun naturalWetBulb(ta: Double, rh: Double, u: Double, solar: Double, cosZ: Double, pHpa: Double = 1010.0): Double {
        val tK = ta + K0
        val r = rh / 100
        val eAir = r * esat(ta)
        val s = capSolar(solar, cosZ)
        val fdb = directFraction(s, cosZ)
        val cz = cosZ.coerceIn(0.01, 1.0)
        val tanZ = sqrt(1 - cz * cz) / cz
        val ea = emisAtm(tK, r)
        val dew = dewPoint(ta, rh)
        val tw = bisect(dew + K0 - 5, tK + 20) { w ->
            val tRef = 0.5 * (w + tK)
            val h = hCylinder(tRef, pHpa, u)
            val fatm = SIGMA * EMIS_WICK * (0.5 * (ea * tK.pow(4) + EMIS_SFC * tK.pow(4)) - w.pow(4)) +
                (1 - ALB_WICK) * s * ((1 - fdb) * (1 + 0.25 * D_WICK / L_WICK) +
                    fdb * (tanZ / PI + 0.25 * D_WICK / L_WICK) + ALB_SFC)
            val eW = esat(w - K0)
            val sc = viscosity(tRef) / (density(tRef, pHpa) * diffusivity(tRef, pHpa))
            val rhs = tK - evap(tRef) / (CP * M_AIR / M_H2O) * (eW - eAir) / (pHpa - eW) * (PR / sc).pow(0.56) + fatm / h
            w - rhs
        }
        return tw - K0
    }

    fun dewPoint(ta: Double, rh: Double): Double {
        val g = ln(max(rh, 1.0) / 100) + 17.502 * ta / (240.97 + ta)
        return 240.97 * g / (17.502 - g)
    }

    fun rhFromDew(ta: Double, dew: Double): Double = (100 * esat(dew) / esat(ta)).coerceIn(1.0, 100.0)

    /** 10 m wind (km/h) to 2 m (m/s) by power law; steeper profile at night (stable air). */
    fun wind2m(kmh10: Double, solar: Double): Double {
        val p = when {
            solar > 300 -> 0.15
            solar > 0 -> 0.25
            else -> 0.35
        }
        return max(kmh10 / 3.6 * (2.0 / 10.0).pow(p), MIN_SPEED)
    }

    data class Wbgt(val wbgt: Double, val tnw: Double, val tg: Double)

    /** Outdoor WBGT, °C, ISO 7243 weighting. */
    fun wbgt(ta: Double, rh: Double, u2: Double, solar: Double, cosZ: Double, pHpa: Double = 1010.0): Wbgt {
        val tg = globeTemp(ta, rh, u2, solar, cosZ, pHpa)
        val tnw = naturalWetBulb(ta, rh, u2, solar, cosZ, pHpa)
        return Wbgt(0.7 * tnw + 0.2 * tg + 0.1 * ta, tnw, tg)
    }

    // ---- sun geometry without a longitude ---------------------------------------------------

    /** Solar declination, radians (NOAA low-precision, as Meteo.sunAltitude). */
    fun declination(ms: Long): Double {
        val d = ms / 86400000.0 - 10957.5
        val rad = PI / 180
        val g = (357.529 + 0.98560028 * d) * rad
        val q = 280.459 + 0.98564736 * d
        val l = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * rad
        val e = (23.439 - 0.00000036 * d) * rad
        return asin(sin(e) * sin(l))
    }

    /** cos(zenith) from latitude, the instant and that day's solar noon (epoch ms). */
    fun cosZenith(ms: Long, latDeg: Double, solarNoon: Long): Double {
        val dec = declination(ms)
        val ha = 2 * PI * ((ms - solarNoon) / 86_400_000.0)
        val phi = latDeg * PI / 180
        return sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(ha)
    }

    /**
     * Latitude that best explains a run of day lengths (hours) on given days-of-year, by grid
     * search over Meteo.dayLength (same −0.833° sunrise definition as Open-Meteo). The forecast
     * doesn't carry coordinates, and day length plus its trend pins latitude well except within
     * a few days of an equinox, where it degrades gracefully (cos-zenith errors of a few %).
     * Null with fewer than 3 usable days or when every day is polar (0 or 24 h).
     */
    fun fitLatitude(doys: IntArray, hours: DoubleArray): Double? {
        val idx = doys.indices.filter { !hours[it].isNaN() && hours[it] > 0.2 && hours[it] < 23.8 }
        if (idx.size < 3) return null
        var best = 0.0
        var bestErr = Double.MAX_VALUE
        var lat = -66.0
        while (lat <= 66.0) {
            var e = 0.0
            for (i in idx) { val d = Meteo.dayLength(lat, doys[i]) - hours[i]; e += d * d }
            if (e < bestErr - 1e-12) { bestErr = e; best = lat }
            lat += 0.1
        }
        return best
    }

    // ---- work/rest -------------------------------------------------------------------------

    enum class Work(val label: String, val example: String) {
        LIGHT("Light", "driving, sorting, light hand work"),
        MODERATE("Moderate", "walking with loads, market and garment work, weeding"),
        HEAVY("Heavy", "rickshaw pulling, digging, carrying bricks, running"),
    }

    /** ACGIH TLV upper WBGT limits (°C) for 75–100 %, 50–75 %, 25–50 %, 0–25 % work per hour. */
    private val LIMITS = mapOf(
        Work.LIGHT to doubleArrayOf(31.0, 31.0, 32.0, 32.5),
        Work.MODERATE to doubleArrayOf(28.0, 29.0, 30.0, 31.5),
        Work.HEAVY to doubleArrayOf(26.0, 27.5, 29.0, 30.5),
    )

    fun limits(w: Work): DoubleArray = LIMITS.getValue(w).copyOf()

    data class Regime(
        /** 0 Low … 4 Extreme, the app's 5-step severity scale. */
        val level: Int,
        val name: String,
        val workMin: Int,
        val restMin: Int,
        /** Litres per hour to drink; 0 = drink to thirst. */
        val waterLph: Double,
        val color: Long,
    ) {
        /** "45/15" style short label. */
        val short: String get() = when (level) { 0 -> "Steady"; 4 -> "Stop"; else -> "$workMin/$restMin" }
        val advice: String get() = when (level) {
            0 -> "steady work" + water
            4 -> "stop this work; only essential tasks, with shade and cooling" + water
            else -> "$workMin min work / $restMin min rest" + water
        }
        private val water: String get() = when {
            waterLph <= 0 -> "; drink to thirst"
            waterLph < 0.6 -> "; drink 0.5 L/h"
            waterLph < 0.9 -> "; drink 0.75 L/h"
            else -> "; drink 1 L/h"
        }
    }

    private val COLORS = longArrayOf(0xFF3DB89A, 0xFFF2DC5D, 0xFFEA7A1E, 0xFFE0203F, 0xFFA55DE0)
    private val NAMES = arrayOf("Low", "Moderate", "High", "Very high", "Extreme")

    fun regime(wbgt: Double, w: Work): Regime {
        val lim = LIMITS.getValue(w)
        var level = 0
        while (level < 4 && wbgt > lim[level]) level++
        // Light work has no separate 45/15 step in the ACGIH table (both limits 31.0).
        if (w == Work.LIGHT && level == 1) level = 2
        val (work, rest) = when (level) { 0 -> 60 to 0; 1 -> 45 to 15; 2 -> 30 to 30; 3 -> 15 to 45; else -> 0 to 60 }
        val water = when {
            level == 0 && wbgt < 25.0 -> 0.0
            level == 0 -> 0.5
            level == 1 -> 0.75
            else -> 1.0
        }
        return Regime(level, NAMES[level], work, rest, water, COLORS[level])
    }

    // ---- hourly series ---------------------------------------------------------------------

    class Series(
        val t: LongArray,
        /** WBGT in full sun, °C (NaN where inputs are missing). */
        val sun: DoubleArray,
        /** WBGT in shade (same air, no short-wave), °C. */
        val shade: DoubleArray,
        val lat: Double?,
    ) {
        val size get() = t.size
        fun levels(w: Work): IntArray = IntArray(size) { if (sun[it].isNaN()) -1 else regime(sun[it], w).level }
    }

    /** Fallback when latitude can't be inferred: Dhaka, the app's home. */
    const val DEFAULT_LAT = 23.8

    fun series(f: Forecast): Series {
        val h = f.hourly
        val d = f.daily
        val off = f.offsetSec
        val doys = IntArray(d.size) {
            java.time.Instant.ofEpochMilli(d.t[it]).atOffset(java.time.ZoneOffset.ofTotalSeconds(off)).dayOfYear
        }
        val hours = DoubleArray(d.size) { d.daylight.getOrElse(it) { Double.NaN } / 3600.0 }
        val lat = fitLatitude(doys, hours)
        // solar noon per local day: midpoint of sunrise and sunset; else clock noon
        val noonByDay = HashMap<Long, Long>()
        for (i in 0 until d.size) {
            val r = d.sunrise.getOrElse(i) { 0L }
            val s = d.sunset.getOrElse(i) { 0L }
            if (r > 0 && s > r) noonByDay[Math.floorDiv(d.t[i] + off * 1000L, 86_400_000L)] = (r + s) / 2
        }
        val pFactor = if (f.elevation.isNaN()) 1.0 else exp(-f.elevation / 8434.0)
        val n = h.size
        val sun = DoubleArray(n) { Double.NaN }
        val shade = DoubleArray(n) { Double.NaN }
        for (i in 0 until n) {
            val ta = h.temp.getOrElse(i) { Double.NaN }
            if (ta.isNaN()) continue
            var rh = h.rh.getOrElse(i) { Double.NaN }
            if (rh.isNaN()) {
                val dew = h.dew.getOrElse(i) { Double.NaN }
                if (!dew.isNaN()) rh = rhFromDew(ta, dew)
            }
            if (rh.isNaN()) continue
            rh = rh.coerceIn(1.0, 100.0)
            val solar = h.solar.getOrElse(i) { Double.NaN }.let { if (it.isNaN()) 0.0 else max(it, 0.0) }
            val windKmh = h.wind.getOrElse(i) { Double.NaN }.let { if (it.isNaN()) 5.0 else it }
            val pMsl = h.pressure.getOrElse(i) { Double.NaN }.let { if (it.isNaN()) 1010.0 else it }
            val p = pMsl * pFactor
            // irradiance is the preceding hour's mean: take the sun at the half hour
            val tMid = h.t[i] - 1_800_000L
            val day = Math.floorDiv(tMid + off * 1000L, 86_400_000L)
            val noon = noonByDay[day] ?: (day * 86_400_000L - off * 1000L + 43_200_000L)
            val cz = cosZenith(tMid, lat ?: DEFAULT_LAT, noon)
            val u = wind2m(windKmh, solar)
            sun[i] = wbgt(ta, rh, u, solar, cz, p).wbgt
            shade[i] = wbgt(ta, rh, wind2m(windKmh, 0.0), 0.0, cz, p).wbgt
        }
        return Series(h.t, sun, shade, lat)
    }

    // ---- windows ---------------------------------------------------------------------------

    /** Contiguous runs of indices in [from, to) whose level is at least `minLevel`. */
    fun runs(levels: IntArray, from: Int, to: Int, minLevel: Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        var s = -1
        val end = min(to, levels.size)
        for (i in max(from, 0) until end) {
            if (levels[i] >= minLevel) { if (s < 0) s = i } else if (s >= 0) { out += s until i; s = -1 }
        }
        if (s >= 0) out += s until end
        return out
    }

    data class Window(val range: IntRange, val level: Int, val peak: Double, val peakIndex: Int)

    /**
     * The worst stretch in [from, to): the contiguous run at the highest level reached, choosing
     * the run that holds the hottest hour. Null when nothing reaches level 1.
     */
    fun worstWindow(wbgt: DoubleArray, levels: IntArray, from: Int, to: Int): Window? {
        val end = min(to, levels.size)
        var top = 0
        for (i in max(from, 0) until end) top = max(top, levels[i])
        if (top < 1) return null
        val rs = runs(levels, from, end, top)
        var best: Window? = null
        for (r in rs) {
            var pk = Double.NEGATIVE_INFINITY
            var pi = r.first
            for (i in r) if (!wbgt[i].isNaN() && wbgt[i] > pk) { pk = wbgt[i]; pi = i }
            if (best == null || pk > best.peak) best = Window(r, top, pk, pi)
        }
        return best
    }

    /** Start index of the `len`-hour stretch in [from, to) with the lowest mean WBGT, or -1. */
    fun coolestStretch(wbgt: DoubleArray, from: Int, to: Int, len: Int): Int {
        var best = -1
        var bestMean = Double.MAX_VALUE
        val end = min(to, wbgt.size)
        var i = max(from, 0)
        while (i + len <= end) {
            var s = 0.0
            var ok = true
            for (k in i until i + len) { if (wbgt[k].isNaN()) { ok = false; break }; s += wbgt[k] }
            if (ok && s / len < bestMean - 1e-9) { bestMean = s / len; best = i }
            i++
        }
        return best
    }

    /** True when no hour in [from, to) puts even heavy work above the steady-work limit. */
    fun quiet(wbgt: DoubleArray, from: Int, to: Int): Boolean {
        for (i in max(from, 0) until min(to, wbgt.size)) {
            if (!wbgt[i].isNaN() && regime(wbgt[i], Work.HEAVY).level >= 1) return false
        }
        return true
    }

    /** First index ≥ from where heavy work needs rest breaks, or -1. */
    fun nextHeat(wbgt: DoubleArray, from: Int): Int {
        for (i in max(from, 0) until wbgt.size) {
            if (!wbgt[i].isNaN() && regime(wbgt[i], Work.HEAVY).level >= 1) return i
        }
        return -1
    }
}
