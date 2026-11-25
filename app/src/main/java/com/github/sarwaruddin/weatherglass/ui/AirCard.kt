package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.sarwaruddin.weatherglass.data.Air
import com.github.sarwaruddin.weatherglass.data.Meteo
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private fun Double.ok(): Double? = takeUnless { it.isNaN() || it.isInfinite() }

private fun DrawScope.label(
    tm: TextMeasurer, s: String, x: Float, y: Float, style: TextStyle, ax: Float = 0f, ay: Float = 0f,
) {
    val l = tm.measure(s, style)
    drawText(l, topLeft = Offset(x - l.size.width * ax, y - l.size.height * ay))
}

/** EPA AQI breakpoints; each category gets an equal slice of the gauge. */
private val AQI_BOUNDS = doubleArrayOf(0.0, 50.0, 100.0, 150.0, 200.0, 300.0, 500.0)

private fun aqiFrac(v: Double): Float {
    val x = v.coerceIn(0.0, 500.0)
    var i = 0
    while (i < 5 && x > AQI_BOUNDS[i + 1]) i++
    val f = (x - AQI_BOUNDS[i]) / (AQI_BOUNDS[i + 1] - AQI_BOUNDS[i])
    return ((i + f) / 6.0).toFloat()
}

private class Pollutant(val name: String, val key: String, val value: Double)

/**
 * US AQI gauge with the EPA category, pollutant concentrations against the WHO 2021
 * 24-hour guidelines (and Bangladesh's 2022 standard), and the hourly PM2.5 trace.
 */
@Composable
fun AirCard(a: Air, offsetSec: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val now = System.currentTimeMillis()
    val aqi = a.now.aqi.ok()
    val cat = Meteo.aqiCat(aqi)
    val catColor = band(cat.color)

    // trailing 24-h mean PM2.5, the number the guidelines are written against
    var s = 0.0
    var n = 0
    for (i in a.t.indices) {
        if (a.t[i] in (now - 86_400_000L)..now) {
            val v = a.pm25.getOrNull(i) ?: Double.NaN
            if (!v.isNaN()) { s += v; n++ }
        }
    }
    val pm24 = if (n >= 6) s / n else null

    val allMissing = aqi == null && a.pm25.all { it.isNaN() }
    if (allMissing) {
        GlassCard("Air", modifier) { Text("No air-quality data for this place right now.", style = Type.Small) }
        return
    }
    // cleanest 3 h in the next 24 h (UX review P3): lowest mean PM2.5
    val clean = remember(a, now / 3_600_000L) {
        val idx = a.t.indices.filter { a.t[it] in now..(now + 86_400_000L) }
        var best = -1
        var bestMean = Double.MAX_VALUE
        for (k in 0..idx.size - 3) {
            val v = (0 until 3).map { a.pm25.getOrNull(idx[k + it]) ?: Double.NaN }
            if (v.any { it.isNaN() } || idx[k + 2] - idx[k] != 2) continue
            val m = v.average()
            if (m < bestMean) { bestMean = m; best = idx[k] }
        }
        if (best < 0) null else best to bestMean
    }

    GlassCard("Air", modifier, trailing = { Text("US AQI", style = Type.Small.copy(color = Pal.Silver)) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 150.dp, height = 88.dp), contentAlignment = Alignment.BottomCenter) {
                Canvas(
                    Modifier.size(width = 150.dp, height = 88.dp).semantics {
                        contentDescription = "US AQI ${aqi?.let { "%.0f".format(it) } ?: "unavailable"}, ${cat.name}"
                    },
                ) { gauge(aqi) }
                Text(
                    aqi?.let { "%.0f".format(it) } ?: "—",
                    style = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Light, color = Pal.Text),
                    modifier = Modifier.padding(bottom = 0.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(cat.name, catColor)
                if (cat.advice.isNotEmpty()) Text(cat.advice, style = Type.Small)
                if (pm24 != null) {
                    val who = Meteo.WHO_24H["pm25"] ?: 15.0
                    Text(
                        "Last 24-h mean PM2.5: ${"%.0f".format(pm24)} µg/m³ = ${"%.1f".format(pm24 / who)}× the WHO 24-h limit",
                        style = Type.Small.copy(color = Pal.Silver),
                    )
                }
            }
        }

        val pols = listOf(
            Pollutant("PM2.5", "pm25", a.now.pm25), Pollutant("PM10", "pm10", a.now.pm10),
            Pollutant("NO₂", "no2", a.now.no2), Pollutant("O₃", "o3", a.now.o3),
            Pollutant("SO₂", "so2", a.now.so2), Pollutant("CO", "co", a.now.co),
            Pollutant("Dust", "dust", a.now.dust),
        )
        var maxMult = 1.0
        // WHO 2021 CO 24-h guideline is 4 mg/m³; Open-Meteo reports µg/m³
        fun whoOf(k: String) = if (k == "co") 4000.0 else Meteo.WHO_24H[k]
        for (p in pols) {
            val w = whoOf(p.key) ?: continue
            p.value.ok()?.let { maxMult = maxOf(maxMult, it / w) }
            Meteo.BD_24H[p.key]?.let { maxMult = maxOf(maxMult, it / w * 1.05) }
        }
        val scale = ceil(maxMult + 0.3).coerceIn(3.0, 15.0)

        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) {
            Text("NOW", style = Type.Tiny.copy(color = Pal.Sub), modifier = Modifier.widthIn(min = 48.dp))
            Text("µg/m³", style = Type.Tiny.copy(color = Pal.Sub, textAlign = TextAlign.End), modifier = Modifier.widthIn(min = 52.dp))
            Text(
                "now ÷ WHO 24-h limit", style = Type.Tiny.copy(color = Pal.Sub, textAlign = TextAlign.End),
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }
        for (p in pols) {
            val who = whoOf(p.key)
            val bd = Meteo.BD_24H[p.key]
            val v = p.value.ok()
            val rowDesc = "${p.name} now ${v?.let { Fmt.num(it, 1) } ?: "unavailable"} micrograms per cubic metre" +
                (if (who != null && v != null) ", ${"%.1f".format(v / who)} times the WHO 24-hour limit" else ", no guideline")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) { contentDescription = rowDesc },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.name, style = Type.Small.copy(color = Pal.Silver), modifier = Modifier.widthIn(min = 48.dp))
                Text(
                    v?.let { Fmt.num(it, if (it < 10) 1 else 0) } ?: "—",
                    style = Type.Mono.copy(textAlign = TextAlign.End),
                    modifier = Modifier.widthIn(min = 52.dp),
                )
                if (who == null) {
                    Canvas(Modifier.weight(1f).height(12.dp).padding(start = 10.dp)) { multBar(null, null, scale) }
                    Text(
                        "no limit", style = Type.Tiny.copy(color = Pal.Sub, textAlign = TextAlign.End),
                        modifier = Modifier.widthIn(min = 40.dp),
                    )
                } else {
                    val mult = v?.div(who)
                    Canvas(Modifier.weight(1f).height(12.dp).padding(start = 10.dp)) {
                        multBar(mult, bd?.div(who), scale)
                    }
                    Text(
                        mult?.let { "${"%.1f".format(it)}×" } ?: "",
                        style = Type.Tiny.copy(
                            color = if ((mult ?: 0.0) > 1) Pal.Text else Pal.Sub, textAlign = TextAlign.End,
                        ),
                        modifier = Modifier.widthIn(min = 40.dp),
                    )
                }
            }
        }
        LimitLegend()

        clean?.let { (i, m) ->
            Text(
                "Cleanest air in the next 24 h: ${Fmt.time(a.t[i], offsetSec)}–${Fmt.time(a.t[i] + 3 * 3_600_000L, offsetSec)}, " +
                    "PM2.5 about ${"%.0f".format(m)} µg/m³.",
                style = Type.Small.copy(color = Pal.Silver), modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer8()
        Pm25Chart(a, now, offsetSec, tm)
        Text(
            "CAMS model, US AQI. O₃ guideline is WHO's 8-h peak value. BD = Bangladesh Air Pollution " +
                "Control Rules 2022. Chart: past hours solid, forecast faded; crimson = above the BD limit.",
            style = Type.Tiny, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Spacer8() = Box(Modifier.height(14.dp))

/** Explains the tick marks on the bars, with the ticks themselves. */
@Composable
private fun LimitLegend() {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 6.dp, height = 12.dp)) {
            drawLine(Pal.Silver, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5.dp.toPx())
        }
        Text(" WHO 24-h limit", style = Type.Tiny.copy(color = Pal.Sub))
        Box(Modifier.size(12.dp))
        Canvas(Modifier.size(width = 6.dp, height = 12.dp)) {
            drawLine(Pal.Crimson, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5.dp.toPx())
        }
        Text(" Bangladesh limit", style = Type.Tiny.copy(color = Pal.Sub))
    }
}

private fun DrawScope.gauge(aqi: Double?) {
    val stroke = 13.dp.toPx()
    val r = minOf(size.width / 2, size.height) - stroke / 2 - 2.dp.toPx()
    val c = Offset(size.width / 2, size.height - 2.dp.toPx())
    val tl = Offset(c.x - r, c.y - r)
    val sz = Size(r * 2, r * 2)
    val active = aqi?.let { aqiFrac(it) }
    val gap = 1.2f
    for (i in 0 until 6) {
        val col = band(Meteo.AQI_CATS[i].second.color)
        val start = 180f + i * 30f + gap / 2
        val segFrac0 = i / 6f
        val lit = active != null && active >= segFrac0
        drawArc(
            col.copy(alpha = if (lit) 0.9f else 0.25f), start, 30f - gap, false, tl, sz,
            style = Stroke(stroke),
        )
    }
    // glass: a bright inner rim and a soft highlight on the upper half
    drawArc(
        Color.White.copy(alpha = 0.18f), 182f, 176f, false,
        Offset(c.x - r + stroke * 0.3f, c.y - r + stroke * 0.3f), Size(2 * (r - stroke * 0.3f), 2 * (r - stroke * 0.3f)),
        style = Stroke(stroke * 0.18f, cap = StrokeCap.Round),
    )
    drawArc(
        Color.Black.copy(alpha = 0.35f), 180f, 180f, false,
        Offset(c.x - r - stroke / 2, c.y - r - stroke / 2), Size(2 * r + stroke, 2 * r + stroke),
        style = Stroke(1f),
    )
    if (active != null) {
        val ang = (PI + PI * active).toFloat()
        val p = Offset(c.x + r * cos(ang), c.y + r * sin(ang))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), p, stroke * 1.2f), stroke * 1.2f, p)
        drawCircle(Pal.Bg, stroke * 0.55f, p)
        drawCircle(Color.White, stroke * 0.38f, p)
    }
}

private fun DrawScope.multBar(mult: Double?, bdMult: Double?, scale: Double) {
    val w = size.width
    val h = 6.dp.toPx()
    val y = (size.height - h) / 2
    val rr = CornerRadius(h / 2)
    fun x(m: Double) = (w * (m / scale)).toFloat().coerceIn(0f, w)
    drawRoundRect(Pal.Grid, Offset(0f, y), Size(w, h), rr)
    if (mult != null) {
        val col = when {
            mult <= 1.0 -> Pal.Good
            bdMult != null && mult <= bdMult -> Pal.Warn
            mult <= 2.0 -> Pal.Warn
            else -> Pal.Crimson
        }
        val bw = maxOf(x(mult), h)
        drawRoundRect(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.7f)), y, y + h), Offset(0f, y), Size(bw, h), rr)
        drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(1f, y + 0.5f), Size(maxOf(bw - 2f, 0f), h * 0.35f), rr)
        if (mult > scale) {
            // overflow notch at the end
            drawLine(Pal.Text, Offset(w - 2f, y - 2f), Offset(w - 2f, y + h + 2f), 2f)
        }
    }
    if (mult == null && bdMult == null) return
    val wx = x(1.0)
    drawLine(Pal.Silver, Offset(wx, 0f), Offset(wx, size.height), 1.5.dp.toPx())
    if (bdMult != null) {
        val bx = x(bdMult)
        drawLine(Pal.Crimson, Offset(bx, 0f), Offset(bx, size.height), 1.5.dp.toPx())
    }
}

@Composable
private fun Pm25Chart(a: Air, now: Long, off: Int, tm: TextMeasurer) {
    val idx = a.t.indices.filter { !(a.pm25.getOrNull(it) ?: Double.NaN).isNaN() }
    if (idx.size < 2) {
        Text("No hourly PM2.5.", style = Type.Small)
        return
    }
    val small = capChartText(10f, Pal.Sub)
    val nowStyle = capChartText(10f, Pal.CrimsonText)
    var pk = idx.first()
    for (i in idx) if (a.pm25[i] > a.pm25[pk]) pk = i
    val nowV = Meteo.at(a.t, a.pm25, now)
    val desc = "PM2.5 hourly chart. Now ${nowV?.let { "%.0f".format(it) } ?: "unknown"} micrograms per cubic metre; " +
        "peak ${"%.0f".format(a.pm25[pk])} on ${Fmt.weekday(a.t[pk], off)} at ${Fmt.time(a.t[pk], off)}. " +
        "WHO 24-hour limit 15, Bangladesh 65."
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = desc }) {
        val left = 30.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val pw = right - left
        val ph = bottom - top
        val t0 = a.t[idx.first()]
        val t1 = a.t[idx.last()] + 3_600_000L
        fun xOf(t: Long) = left + pw * ((t - t0).toFloat() / (t1 - t0).toFloat())
        var peak = 0.0
        for (i in idx) peak = maxOf(peak, a.pm25[i])
        val vmax = maxOf(80.0, ceil(peak * 1.1 / 25) * 25)
        fun yOf(v: Double) = (bottom - ph * (minOf(v, vmax) / vmax)).toFloat()

        val step = if (vmax > 300) 100.0 else if (vmax > 150) 50.0 else 25.0
        var v = 0.0
        while (v <= vmax) {
            val y = yOf(v)
            drawLine(if (v == 0.0) Pal.GridStrong else Pal.Grid, Offset(left, y), Offset(right, y), 1f)
            label(tm, "%.0f".format(v), left - 4.dp.toPx(), y, small, 1f, 0.5f)
            v += step
        }
        // day boundaries and labels at local noon
        var t = Math.floorDiv(t0, 3_600_000L) * 3_600_000L
        while (t <= t1) {
            val hr = Fmt.localHour(t, off)
            if (hr == 0) {
                val x = xOf(t)
                drawLine(Pal.GridStrong, Offset(x, top), Offset(x, bottom), 1f)
            } else if (hr == 12) {
                label(tm, Fmt.weekday(t, off) + " " + Fmt.dayMonth(t, off).substringBefore(' '), xOf(t), bottom + 3.dp.toPx(), small, 0.5f)
            }
            t += 3_600_000L
        }
        // bars: neutral below the BD limit, crimson above it (the labelled line carries the meaning)
        val bw = maxOf(1f, pw / idx.size - 0.6f)
        val bdY = yOf(Meteo.BD_24H["pm25"] ?: 65.0)
        for (i in idx) {
            val pv = a.pm25[i]
            val y = yOf(pv)
            val alpha = if (a.t[i] > now) 0.38f else 0.9f
            val x = xOf(a.t[i])
            drawRect(Color(0xFFAEB0BC).copy(alpha = alpha), Offset(x, maxOf(y, bdY)), Size(bw, bottom - maxOf(y, bdY)))
            if (y < bdY) drawRect(Pal.Crimson.copy(alpha = alpha), Offset(x, y), Size(bw, bdY - y))
        }
        // guidelines
        fun guide(gv: Double, text: String, col: Color) {
            if (gv > vmax) return
            val y = yOf(gv)
            drawLine(col, Offset(left, y), Offset(right, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            val l = tm.measure(text, small.copy(color = col))
            drawRect(Color(0xD90D0D0F), Offset(right - l.size.width - 6f, y - l.size.height / 2f), Size(l.size.width + 4f, l.size.height.toFloat()))
            drawText(l, topLeft = Offset(right - l.size.width - 4f, y - l.size.height / 2f))
        }
        guide(Meteo.WHO_24H["pm25"] ?: 15.0, "WHO ${"%.0f".format(Meteo.WHO_24H["pm25"] ?: 15.0)}", Pal.Silver.copy(alpha = 0.8f))
        guide(Meteo.BD_24H["pm25"] ?: 65.0, "BD ${"%.0f".format(Meteo.BD_24H["pm25"] ?: 65.0)}", Pal.Crimson)
        // now
        if (now in t0..t1) {
            val nx = xOf(now)
            drawLine(Pal.Text.copy(alpha = 0.6f), Offset(nx, top), Offset(nx, bottom), 1.5f)
            label(tm, "now", nx + 3.dp.toPx(), top + 1.dp.toPx(), nowStyle)
        }
        label(tm, "PM2.5 µg/m³, hourly", left, 2.dp.toPx(), small)
    }
}
