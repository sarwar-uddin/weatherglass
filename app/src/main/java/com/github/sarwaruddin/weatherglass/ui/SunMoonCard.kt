package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import com.github.sarwaruddin.weatherglass.data.Place
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToLong

private fun DoubleArray.g(i: Int): Double = if (i in indices) this[i] else Double.NaN
private fun LongArray.g(i: Int): Long = if (i in indices) this[i] else 0L
private fun localDay(ms: Long, off: Int): Long = Math.floorDiv(ms + off * 1000L, 86_400_000L)

private fun DrawScope.label(
    tm: TextMeasurer, s: String, x: Float, y: Float, style: TextStyle, ax: Float = 0f, ay: Float = 0f,
) {
    val l = tm.measure(s, style)
    drawText(l, topLeft = Offset(x - l.size.width * ax, y - l.size.height * ay))
}

private const val STEP_MS = 5 * 60_000L
private val Gold = Color(0xFFE0A050)

private class SunDay(
    val mid: Long, val alt: DoubleArray, val maxAlt: Double, val noon: Long,
    val golden: List<Pair<Long, Long>>,
)

private fun sunDay(mid: Long, lat: Double, lng: Double): SunDay {
    val n = (86_400_000L / STEP_MS).toInt() + 1
    val alt = DoubleArray(n) { Meteo.sunAltitude(mid + it * STEP_MS, lat, lng) }
    var mi = 0
    for (i in alt.indices) if (alt[i] > alt[mi]) mi = i
    val golden = ArrayList<Pair<Long, Long>>()
    var start = -1
    for (i in alt.indices) {
        val inBand = alt[i] >= -4.0 && alt[i] <= 6.0
        if (inBand && start < 0) start = i
        if ((!inBand || i == alt.lastIndex) && start >= 0) {
            val end = if (inBand) i else i - 1
            if (end > start) golden += (mid + start * STEP_MS) to (mid + end * STEP_MS)
            start = -1
        }
    }
    return SunDay(mid, alt, alt[mi], mid + mi * STEP_MS, golden)
}

private fun signedDuration(sec: Double): String {
    val s = abs(sec).roundToLong()
    val sign = if (sec > 0.5) "+" else if (sec < -0.5) "−" else "±"
    return "$sign${s / 60}m ${"%02d".format(s % 60)}s"
}

/**
 * Moon's topocentric altitude in degrees: Astronomical Almanac low-precision lunar formulae
 * (good to ~0.3°), plus the parallax correction, which is up to 1° for the Moon.
 */
private fun moonAltitude(ms: Long, lat: Double, lng: Double): Double {
    val d = ms / 86400000.0 - 10957.5
    val t = d / 36525.0
    fun s(a: Double) = sin(Math.toRadians(a))
    fun c(a: Double) = cos(Math.toRadians(a))
    val lam = 218.32 + 481267.881 * t + 6.29 * s(135.0 + 477198.87 * t) - 1.27 * s(259.3 - 413335.36 * t) +
        0.66 * s(235.7 + 890534.22 * t) + 0.21 * s(269.9 + 954397.74 * t) - 0.19 * s(357.5 + 35999.05 * t) -
        0.11 * s(186.5 + 966404.03 * t)
    val bet = 5.13 * s(93.3 + 483202.02 * t) + 0.28 * s(228.2 + 960400.89 * t) - 0.28 * s(318.3 + 6003.15 * t) -
        0.17 * s(217.6 - 407332.21 * t)
    val par = 0.9508 + 0.0518 * c(135.0 + 477198.87 * t) + 0.0095 * c(259.3 - 413335.36 * t) +
        0.0078 * c(235.7 + 890534.22 * t) + 0.0028 * c(269.9 + 954397.74 * t)
    val eps = 23.439 - 0.00000036 * d
    val l = c(bet) * c(lam)
    val m = c(eps) * c(bet) * s(lam) - s(eps) * s(bet)
    val n = s(eps) * c(bet) * s(lam) + c(eps) * s(bet)
    val ra = Math.toDegrees(atan2(m, l))
    val dec = Math.toDegrees(asin(n))
    val gmst = 280.46061837 + 360.98564736629 * d
    val ha = gmst + lng - ra
    val alt = Math.toDegrees(asin(s(lat) * s(dec) + c(lat) * c(dec) * c(ha)))
    return alt - par * c(alt)
}

/** Centre altitude at which the Moon's upper limb touches the horizon (refraction + semidiameter). */
private const val MOON_H0 = -0.83

/** Moonrise and moonset instants inside [t0, t0 + 24 h], by 10-minute scan and linear refinement. */
private fun moonEvents(t0: Long, lat: Double, lng: Double): Pair<Long?, Long?> {
    val step = 10 * 60_000L
    var rise: Long? = null
    var set: Long? = null
    var prev = moonAltitude(t0, lat, lng)
    var t = t0
    while (t < t0 + 86_400_000L) {
        val nt = t + step
        val a = moonAltitude(nt, lat, lng)
        if ((prev - MOON_H0) * (a - MOON_H0) <= 0 && prev != a) {
            val tx = t + ((MOON_H0 - prev) / (a - prev) * step).toLong()
            if (a > prev) { if (rise == null) rise = tx } else if (set == null) set = tx
        }
        prev = a
        t = nt
    }
    return rise to set
}

/**
 * Day: the sun's whole day as an altitude curve, golden hours and the moon's phase.
 * Night: tonight's moon arc (noon to noon) with moonrise and moonset, so the chart is never dead.
 */
@Composable
fun SunMoonCard(f: Forecast, place: Place, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val off = f.offsetSec
    val now = System.currentTimeMillis()
    val today = localDay(now, off)
    val mid = today * 86_400_000L - off * 1000L
    val d = f.daily
    val i = (0 until d.size).firstOrNull { localDay(d.t[it], off) == today } ?: -1
    val sun = remember(mid, place.lat, place.lng) { sunDay(mid, place.lat, place.lng) }
    val rise = d.sunrise.g(i).takeIf { it > 0 }
    val set = d.sunset.g(i).takeIf { it > 0 }
    val dlSec = d.daylight.g(i).takeUnless { it.isNaN() }
        ?: if (rise != null && set != null) (set - rise) / 1000.0 else null
    val prevSec = if (i >= 1 && localDay(d.t[i - 1], off) == today - 1) {
        d.daylight.g(i - 1).takeUnless { it.isNaN() }
            ?: d.sunset.g(i - 1).let { s -> d.sunrise.g(i - 1).let { r -> if (s > r && r > 0) (s - r) / 1000.0 else null } }
    } else null
    val nowAlt = Meteo.sunAltitude(now, place.lat, place.lng)
    val night = nowAlt < -0.833
    // night window: local noon to noon around tonight
    val wStart = if (!night) mid else if (Fmt.localHour(now, off) >= 12) mid + 43_200_000L else mid - 43_200_000L
    val nightData = remember(wStart, night, place.lat, place.lng) {
        if (!night) null else {
            val n = (86_400_000L / STEP_MS).toInt() + 1
            val moonAlt = DoubleArray(n) { moonAltitude(wStart + it * STEP_MS, place.lat, place.lng) }
            val sunAlt = DoubleArray(n) { Meteo.sunAltitude(wStart + it * STEP_MS, place.lat, place.lng) }
            Triple(moonAlt, sunAlt, moonEvents(wStart, place.lat, place.lng))
        }
    }
    val moonEv = remember(mid, place.lat, place.lng) { if (night) null else moonEvents(mid, place.lat, place.lng) }
    val (mRise, mSet) = nightData?.third ?: moonEv ?: (null to null)
    val moonNow = moonAltitude(now, place.lat, place.lng)
    val small = capChartText(10f, Pal.Sub)
    val tag = capChartText(10f, Pal.Silver)

    val chartDesc = if (night) {
        "Tonight's moon: rises ${mRise?.let { Fmt.time(it, off) } ?: "not in this window"}, " +
            "sets ${mSet?.let { Fmt.time(it, off) } ?: "not in this window"}; now ${"%.0f".format(moonNow)} degrees " +
            (if (moonNow > MOON_H0) "above" else "below") + " the horizon."
    } else {
        "Sun today: rises ${rise?.let { Fmt.time(it, off) } ?: "unknown"}, sets ${set?.let { Fmt.time(it, off) } ?: "unknown"}, " +
            "solar noon ${Fmt.time(sun.noon, off)} at ${"%.0f".format(sun.maxAlt)} degrees; now ${"%.0f".format(nowAlt)} degrees."
    }

    GlassCard(
        "Sun & Moon", modifier,
        trailing = {
            Text(
                if (night) "moon ${"%.0f".format(moonNow).replace("-", "−")}°" else "sun ${"%.0f".format(nowAlt).replace("-", "−")}°",
                style = Type.Small.copy(color = Pal.Silver),
            )
        },
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = chartDesc }) {
            val left = 4.dp.toPx()
            val right = size.width - 4.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 14.dp.toPx()
            val horizon = top + (bottom - top) * 0.70f
            val peak = if (night) maxOf(nightData!!.first.max(), 10.0) else maxOf(sun.maxAlt, 10.0)
            val k = (horizon - top) / peak.toFloat()
            fun xOf(t: Long) = left + (right - left) * ((t - wStart).toFloat() / 86_400_000f)
            fun yOf(a: Double) = (horizon - k * a.toFloat()).coerceIn(top - 4f, bottom)
            fun curve(alt: DoubleArray): Path {
                val path = Path()
                alt.forEachIndexed { n, a ->
                    val x = xOf(wStart + n * STEP_MS)
                    if (n == 0) path.moveTo(x, yOf(a)) else path.lineTo(x, yOf(a))
                }
                return path
            }

            if (!night) {
                for ((a, b) in sun.golden) {
                    drawRect(
                        Brush.verticalGradient(listOf(Gold.copy(alpha = 0.04f), Gold.copy(alpha = 0.22f), Gold.copy(alpha = 0.04f)), top, bottom),
                        Offset(xOf(a), top), androidx.compose.ui.geometry.Size(xOf(b) - xOf(a), bottom - top),
                    )
                }
            }
            // hour grid (local clock)
            for (h in 0..24 step 3) {
                val t = wStart + h * 3_600_000L
                val x = xOf(t)
                drawLine(Pal.Grid, Offset(x, top), Offset(x, bottom), 1f)
                if (h in 3..21) label(tm, "%02d".format(Fmt.localHour(t, off)), x, bottom + 2.dp.toPx(), small, 0.5f)
            }
            drawLine(Pal.GridStrong, Offset(left, horizon), Offset(right, horizon), 1.2f)

            if (!night) {
                val path = curve(sun.alt)
                drawPath(path, Pal.Dim.copy(alpha = 0.7f), style = Stroke(1.2.dp.toPx()))
                clipRect(left = left, top = 0f, right = right, bottom = horizon) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xOf(wStart + 86_400_000L), horizon + 20f)
                        lineTo(xOf(wStart), horizon + 20f)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(Gold.copy(alpha = 0.28f), Pal.Crimson.copy(alpha = 0.06f)), top, horizon))
                    drawPath(
                        path, Brush.verticalGradient(listOf(Color(0xFFFFE2A8), Gold, Pal.Crimson), top, horizon),
                        style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                rise?.let {
                    val x = xOf(it)
                    drawCircle(Gold, 2.5.dp.toPx(), Offset(x, horizon))
                    label(tm, "↑ " + Fmt.time(it, off), x, horizon + 4.dp.toPx(), tag, 0.5f)
                }
                set?.let {
                    val x = xOf(it)
                    drawCircle(Pal.Crimson, 2.5.dp.toPx(), Offset(x, horizon))
                    label(tm, "↓ " + Fmt.time(it, off), x, horizon + 4.dp.toPx(), tag, 0.5f)
                }
                val p = Offset(xOf(now), yOf(nowAlt))
                drawCircle(Brush.radialGradient(listOf(Color(0xCCFFD890), Color.Transparent), p, 14.dp.toPx()), 14.dp.toPx(), p)
                drawCircle(Color(0xFFFFF4DC), 4.5.dp.toPx(), p)
            } else {
                val (moonAlt, sunAlt, _) = nightData!!
                // the night itself: sun below the horizon, shaded
                drawPath(curve(sunAlt), Gold.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
                val path = curve(moonAlt)
                drawPath(path, Pal.Dim.copy(alpha = 0.6f), style = Stroke(1.2.dp.toPx()))
                clipRect(left = left, top = 0f, right = right, bottom = horizon) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xOf(wStart + 86_400_000L), horizon + 20f)
                        lineTo(xOf(wStart), horizon + 20f)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(Pal.Silver.copy(alpha = 0.22f), Color.Transparent), top, horizon))
                    drawPath(path, Pal.Silver, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                }
                mRise?.let {
                    val x = xOf(it)
                    drawCircle(Pal.Silver, 2.5.dp.toPx(), Offset(x, horizon))
                    label(tm, "↑ " + Fmt.time(it, off), x, horizon + 4.dp.toPx(), tag, 0.5f)
                }
                mSet?.let {
                    val x = xOf(it)
                    drawCircle(Pal.Silver, 2.5.dp.toPx(), Offset(x, horizon))
                    label(tm, "↓ " + Fmt.time(it, off), x, horizon + 4.dp.toPx(), tag, 0.5f)
                }
                val p = Offset(xOf(now), yOf(moonNow))
                if (moonNow > MOON_H0) {
                    drawCircle(Brush.radialGradient(listOf(Color(0x99E6E6EE), Color.Transparent), p, 12.dp.toPx()), 12.dp.toPx(), p)
                    drawCircle(Color(0xFFF6F4EC), 4.5.dp.toPx(), p)
                } else {
                    drawCircle(Pal.Bg, 4.dp.toPx(), p)
                    drawCircle(Pal.Sub, 4.dp.toPx(), p, style = Stroke(1.2.dp.toPx()))
                }
                label(tm, "moon tonight", left + 2.dp.toPx(), 0f, tag)
            }
            // now marker
            val nx = xOf(now)
            drawLine(Pal.Crimson.copy(alpha = 0.7f), Offset(nx, top), Offset(nx, bottom), 1.dp.toPx())
        }

        if (night) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                SunFact("Moonrise", mRise?.let { Fmt.time(it, off) } ?: "—", null, Modifier.weight(1f))
                SunFact("Moonset", mSet?.let { Fmt.time(it, off) } ?: "—", null, Modifier.weight(1f))
                SunFact("Moon now", "${"%.0f".format(moonNow).replace("-", "−")}°", if (moonNow > MOON_H0) "up" else "below horizon", Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            SunFact("Sunrise", rise?.let { Fmt.time(it, off) } ?: "—", null, Modifier.weight(1f))
            SunFact("Sunset", set?.let { Fmt.time(it, off) } ?: "—", null, Modifier.weight(1f))
            SunFact(
                "Daylight", dlSec?.let { Fmt.duration(it / 3600.0) } ?: "—",
                if (dlSec != null && prevSec != null) signedDuration(dlSec - prevSec) + " vs yesterday" else null,
                Modifier.weight(1.4f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SunFact(
                "Solar noon", Fmt.time(sun.noon, off), "${"%.0f".format(sun.maxAlt)}° up", Modifier.weight(1f),
            )
            val gh = sun.golden.joinToString("  ") { (a, b) -> "${Fmt.time(a, off)}–${Fmt.time(b, off)}" }
            SunFact("Golden hours", gh.ifEmpty { "—" }, "sun −4° to 6°", Modifier.weight(2.4f), Gold)
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Pal.Grid)

        val moon = Meteo.moon(now)
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                Modifier.size(64.dp).semantics { contentDescription = "${moon.name}, ${"%.0f".format(moon.illumination * 100)} percent lit" },
            ) { moonDisc(moon.age, moon.illumination, place.lat < 0) }
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(moon.name, style = Type.Body)
                Text(
                    "Age ${"%.1f".format(moon.age)} d · ${"%.0f".format(moon.illumination * 100)}% lit",
                    style = Type.Small,
                )
                if (!night && (mRise != null || mSet != null)) Text(
                    listOfNotNull(mRise?.let { "Rises ${Fmt.time(it, off)}" }, mSet?.let { "sets ${Fmt.time(it, off)}" }).joinToString(" · "),
                    style = Type.Small.copy(color = Pal.Silver),
                )
                Text(
                    "Full ${Fmt.weekday(moon.nextFull, off)} ${Fmt.dayMonth(moon.nextFull, off)}" +
                        "  ·  New ${Fmt.weekday(moon.nextNew, off)} ${Fmt.dayMonth(moon.nextNew, off)}",
                    style = Type.Small.copy(color = Pal.Silver),
                )
                Text("Phases ±14 h; rise/set ±5 min.", style = Type.Tiny)
            }
        }
    }
}

@Composable
private fun SunFact(label: String, value: String, note: String?, modifier: Modifier, accent: Color = Pal.Text) {
    Column(modifier) {
        Text(label.uppercase(), style = Type.Tiny.copy(color = Pal.Sub))
        Text(value, style = Type.Body.copy(color = accent))
        if (note != null) Text(note, style = Type.Tiny.copy(color = Pal.Sub))
    }
}

/** Lit part of the disc: a half-disc on the sunward side, bounded by an elliptical terminator. */
private fun DrawScope.moonDisc(age: Double, illum: Double, south: Boolean) {
    val r = size.minDimension / 2 - 3.dp.toPx()
    val c = center
    // earthshine body and a faint rim
    drawCircle(Brush.radialGradient(listOf(Color(0xFF26262C), Color(0xFF101013)), c, r), r, c)
    drawCircle(Pal.Edge, r, c, style = Stroke(1f))
    val k = illum.coerceIn(0.0, 1.0).toFloat()
    if (k < 0.01f) return
    val waxing = age < Meteo.SYNODIC / 2
    val ex = maxOf(r * abs(1f - 2f * k), 0.5f)
    val lit = Path().apply {
        moveTo(c.x, c.y - r)
        arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, 180f, false)
        arcTo(Rect(c.x - ex, c.y - r, c.x + ex, c.y + r), 90f, if (k < 0.5f) -180f else 180f, false)
        close()
    }
    val brush = Brush.radialGradient(
        listOf(Color(0xFFF6F4EC), Color(0xFFD8D8E0), Color(0xFF9A9AA2)),
        Offset(c.x + r * 0.25f, c.y - r * 0.3f), r * 1.4f,
    )
    // northern hemisphere: waxing lit on the right; mirror for waning or south of the equator
    val mirror = (!waxing) xor south
    scale(if (mirror) -1f else 1f, 1f, pivot = c) {
        drawPath(lit, brush)
    }
    // glossy specular highlight
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset(c.x - r * 0.3f, c.y - r * 0.45f), r * 0.7f),
        r, c,
    )
}
