package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import kotlin.math.abs

private const val HOUR = 3_600_000L

// local extras (Theme.kt owns the core palette)
private val RainText = Color(0xFF6FA8EC) // PoP / mm: ≥ 5:1 on Card
private val Hairline = Color(0xFF4A4A50)
private val CurveColor = Pal.Crimson.copy(alpha = 0.85f)
private val CurveFill = Brush.verticalGradient(listOf(Pal.Crimson.copy(alpha = 0.22f), Pal.Crimson.copy(alpha = 0f)))

/**
 * One cell. `yl`, `yc`, `yr` are the mini temperature curve at the cell's left edge, centre and
 * right edge, as 0..1 (1 = warmest in the strip); neighbouring cells share edge values so the
 * curve runs unbroken across the row.
 */
private sealed class HourItem(val t: Long, val temp: Double) {
    abstract val key: String
    var yl = Float.NaN
    var yc = Float.NaN
    var yr = Float.NaN
}
private class HourEntry(val i: Int, val isNow: Boolean, t: Long, temp: Double) : HourItem(t, temp) {
    override val key get() = "h$t"
}
private class SunEntry(t: Long, val rise: Boolean, temp: Double) : HourItem(t, temp) {
    override val key get() = (if (rise) "r" else "s") + t
}

private class HourList(val items: List<HourItem>, val showMm: Boolean, val showFeels: Boolean)

private fun hourItems(f: Forecast, now: Long): HourList {
    val h = f.hourly
    if (h.size == 0) return HourList(emptyList(), false, false)
    val start = h.indexAt(now)
    val end = minOf(h.size, start + 48)
    val d = f.daily
    val events = ArrayList<SunEntry>()
    val hi = h.t[end - 1] + HOUR
    for (i in 0 until d.size) {
        if (d.sunrise[i] in now until hi) events += SunEntry(d.sunrise[i], true, Meteo.at(h.t, h.temp, d.sunrise[i]) ?: Double.NaN)
        if (d.sunset[i] in now until hi) events += SunEntry(d.sunset[i], false, Meteo.at(h.t, h.temp, d.sunset[i]) ?: Double.NaN)
    }
    events.sortBy { it.t }
    val out = ArrayList<HourItem>(end - start + events.size)
    var e = 0
    var showMm = false
    var showFeels = false
    for (i in start until end) {
        val isNow = i == start
        val temp = if (isNow && !f.current.temp.isNaN()) f.current.temp else h.temp[i]
        out += HourEntry(i, isNow, h.t[i], temp)
        if (!h.precip[i].isNaN() && h.precip[i] >= 0.1) showMm = true
        val feels = if (isNow) f.current.feels else h.feels[i]
        if (!feels.isNaN() && !temp.isNaN() && abs(feels - temp) >= 3.0) showFeels = true
        val next = h.t[i] + HOUR
        while (e < events.size && events[e].t < next) { out += events[e]; e++ }
    }
    // mini curve
    var lo = Double.MAX_VALUE
    var top = -Double.MAX_VALUE
    for (it in out) if (!it.temp.isNaN()) { lo = minOf(lo, it.temp); top = maxOf(top, it.temp) }
    if (lo != Double.MAX_VALUE) {
        val span = maxOf(4.0, top - lo)
        fun norm(v: Double) = if (v.isNaN()) Float.NaN else ((v - lo) / span).toFloat()
        for (k in out.indices) {
            val c = norm(out[k].temp)
            val p = if (k > 0) norm(out[k - 1].temp) else c
            val n = if (k + 1 < out.size) norm(out[k + 1].temp) else c
            out[k].yc = c
            out[k].yl = if (p.isNaN()) c else (p + c) / 2f
            out[k].yr = if (n.isNaN()) c else (n + c) / 2f
        }
    }
    return HourList(out, showMm, showFeels)
}

@Composable
fun HourlyCard(f: Forecast, modifier: Modifier = Modifier) {
    val list = remember(f) { hourItems(f, System.currentTimeMillis()) }
    val state = rememberLazyListState()
    GlassCard("Hourly", modifier) {
        LazyRow(
            Modifier.fillMaxWidth(),
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
        ) {
            items(list.items, key = { it.key }, contentType = { if (it is HourEntry) 0 else 1 }) { item ->
                when (item) {
                    is HourEntry -> HourCell(f, item, list)
                    is SunEntry -> SunCell(f, item, list)
                }
            }
        }
    }
}

private val cellShape = RoundedCornerShape(14.dp)
private val nowBg = Brush.verticalGradient(listOf(Color(0x55E0203F), Color(0x14E0203F), Color(0x00E0203F)))
private val nowEdge = Brush.verticalGradient(listOf(Color(0x99E0203F), Color(0x22E0203F)))
private val cellBase = Modifier.widthIn(min = 54.dp)

/** Same height in hour and sun cells (and at any font scale) so the mini curve lines up. */
@Composable
private fun valueRowHeight() = with(LocalDensity.current) { 21.sp.toDp() }

/** Mini temperature curve: one segment per cell, joined to its neighbours at the edges. */
@Composable
private fun CurveSegment(item: HourItem, dot: Boolean) {
    Spacer(
        Modifier.fillMaxWidth().height(14.dp).drawWithCache {
            val w = size.width
            val pad = 3.dp.toPx()
            val hh = size.height - pad * 2
            fun y(v: Float) = pad + hh * (1f - v)
            val ok = !item.yc.isNaN()
            val line = Path()
            val fill = Path()
            if (ok) {
                val yl = y(item.yl)
                val yc = y(item.yc)
                val yr = y(item.yr)
                line.moveTo(0f, yl)
                line.quadraticTo(w * 0.25f, (yl + yc) / 2f, w / 2f, yc)
                line.quadraticTo(w * 0.75f, (yc + yr) / 2f, w, yr)
                fill.addPath(line)
                fill.lineTo(w, size.height)
                fill.lineTo(0f, size.height)
                fill.close()
            }
            val stroke = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Butt)
            val r = 2.5f.dp.toPx()
            val cy = if (ok) y(item.yc) else 0f
            onDrawBehind {
                if (!ok) return@onDrawBehind
                drawPath(fill, CurveFill)
                drawPath(line, CurveColor, style = stroke)
                if (dot) {
                    drawCircle(Pal.Crimson, r, Offset(w / 2f, cy))
                    drawCircle(Pal.Text, r * 0.45f, Offset(w / 2f, cy))
                }
            }
        },
    )
}

@Composable
private fun HourCell(f: Forecast, e: HourEntry, list: HourList) {
    val h = f.hourly
    val off = f.offsetSec
    val i = e.i
    val isNow = e.isNow
    val t = h.t[i]
    val midnight = Fmt.localHour(t, off) == 0
    val label = when {
        isNow -> "Now"
        midnight -> Fmt.weekday(t, off)
        else -> Fmt.time(t, off)
    }
    val temp = e.temp
    val code = if (isNow) f.current.code else h.code[i]
    val day = if (isNow) f.current.isDay else h.isDay[i]
    val feels = if (isNow) f.current.feels else h.feels[i]
    val showFeels = !feels.isNaN() && !temp.isNaN() && abs(feels - temp) >= 3.0
    val pop = h.pop[i]
    val mm = h.precip[i]
    val wind = h.wind[i]
    val dir = h.windDir[i]
    val spoken = remember(f, i) {
        buildString {
            append(if (isNow) "Now" else "${Fmt.weekday(t, off)} ${Fmt.time(t, off)}")
            append(", ").append(Meteo.wmo(code).text)
            append(", ").append(Fmt.temp(temp))
            if (showFeels) append(", feels like ").append(Fmt.temp(feels))
            if (!pop.isNaN() && pop >= 10.0) append(", ${Fmt.num(pop)} percent chance of rain")
            if (!mm.isNaN() && mm >= 0.1) append(", ${Fmt.num(mm, 1)} millimetres")
            if (!wind.isNaN()) {
                append(", wind ${Fmt.num(wind)} kilometres per hour")
                if (!dir.isNaN() && wind >= 2.0) append(" from ${Meteo.compass(dir)}")
            }
        }
    }
    val deco = if (isNow) cellBase.background(nowBg, cellShape).border(1.dp, nowEdge, cellShape) else cellBase
    Column(
        deco.clearAndSetSemantics { contentDescription = spoken }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = Type.Small.copy(
                color = when {
                    isNow -> Pal.Text
                    midnight -> Pal.Silver
                    else -> Pal.Sub
                },
                fontWeight = if (isNow || midnight) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        WeatherIcon(code, day, 30.dp)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(valueRowHeight()), contentAlignment = Alignment.Center) {
            Text(Fmt.temp(temp), style = Type.Body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        }
        CurveSegment(e, dot = isNow)
        if (list.showFeels) {
            Box(Modifier.height(15.dp), contentAlignment = Alignment.Center) {
                if (showFeels) Text("≈${Fmt.temp(feels)}", style = Type.Tiny.copy(color = Pal.Sub, fontSize = 11.sp))
            }
        }
        Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            if (!pop.isNaN() && pop >= 10.0) Text("${Fmt.num(pop)}%", style = Type.Tiny.copy(color = RainText, fontSize = 11.sp))
            else Text("—", style = Type.Tiny.copy(color = Hairline, fontSize = 11.sp))
        }
        if (list.showMm) {
            Box(Modifier.height(15.dp), contentAlignment = Alignment.Center) {
                if (!mm.isNaN() && mm >= 0.1) {
                    Text("${Fmt.num(mm, 1)} mm", style = Type.Tiny.copy(color = RainText, fontSize = 10.5.sp), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!dir.isNaN() && !wind.isNaN() && wind >= 2.0) WindArrow(dir.toFloat())
            else Spacer(Modifier.size(14.dp))
            Spacer(Modifier.width(2.dp))
            Text(Fmt.num(wind), style = Type.Tiny.copy(color = Pal.Sub, fontSize = 11.sp))
        }
    }
}

/**
 * Arrow pointing where the wind blows TO (meteorological direction is where it comes FROM):
 * a long shaft with a tail notch so the heading reads at a glance, unlike the v0.1 chevron.
 */
@Composable
private fun WindArrow(fromDeg: Float) {
    Spacer(
        Modifier.size(14.dp).drawWithCache {
            val s = size.minDimension
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.02f) // tip
                lineTo(s * 0.86f, s * 0.48f)
                lineTo(s * 0.60f, s * 0.42f)
                lineTo(s * 0.60f, s * 0.92f)
                lineTo(s * 0.40f, s * 0.92f)
                lineTo(s * 0.40f, s * 0.42f)
                lineTo(s * 0.14f, s * 0.48f)
                close()
            }
            onDrawBehind { rotate(fromDeg + 180f) { drawPath(p, Pal.Silver) } }
        },
    )
}

@Composable
private fun SunCell(f: Forecast, e: SunEntry, list: HourList) {
    val time = Fmt.time(e.t, f.offsetSec)
    val word = if (e.rise) "Sunrise" else "Sunset"
    Column(
        cellBase.clearAndSetSemantics { contentDescription = "$word $time" }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(time, style = Type.Small.copy(color = Pal.Warn), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Spacer(
            Modifier.size(30.dp).drawWithCache {
                val s = size.minDimension
                val horizon = s * 0.68f
                val c = Offset(s * 0.5f, horizon)
                val r = s * 0.24f
                val disc = Brush.radialGradient(
                    listOf(Color(0xFFFFF1C8), Color(0xFFFFB040), if (e.rise) Color(0xFFF0702A) else Color(0xFFD9541E)),
                    center = Offset(c.x - r * 0.3f, c.y - r * 0.5f), radius = r * 1.5f,
                )
                val glow = Brush.radialGradient(listOf(Color(0x55FF7A3A), Color(0x00FF7A3A)), center = c, radius = r * 2.2f)
                val arrow = Path().apply {
                    val ax = s * 0.5f
                    val tip = if (e.rise) s * 0.02f else s * 0.30f
                    val back = if (e.rise) s * 0.14f else s * 0.18f
                    moveTo(ax, tip)
                    lineTo(ax - s * 0.08f, back)
                    lineTo(ax + s * 0.08f, back)
                    close()
                }
                onDrawBehind {
                    drawCircle(glow, r * 2.2f, c)
                    clipRect(0f, 0f, s, horizon) { drawCircle(disc, r, c) }
                    drawLine(Pal.Silver.copy(alpha = 0.7f), Offset(s * 0.08f, horizon), Offset(s * 0.92f, horizon), s * 0.05f, StrokeCap.Round)
                    drawPath(arrow, Pal.Warn)
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(valueRowHeight()), contentAlignment = Alignment.Center) {
            Text(word, style = Type.Tiny.copy(color = Pal.Sub, fontSize = 11.sp), maxLines = 1)
        }
        CurveSegment(e, dot = false)
    }
}
