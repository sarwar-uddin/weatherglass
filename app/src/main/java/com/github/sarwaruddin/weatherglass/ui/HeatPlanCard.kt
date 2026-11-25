package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Heat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Text style for labels drawn inside a Canvas: never under 10 sp, and capped at 1.15× font
 * scale so charts keep their geometry at large font sizes (UX review §1.7).
 */
@Composable
internal fun capChartText(sizeSp: Float = 10f, color: Color = Pal.Sub): TextStyle {
    val fs = LocalDensity.current.fontScale
    return Type.Tiny.copy(fontSize = (sizeSp * min(fs, 1.15f) / fs).sp, color = color)
}

private fun localDayOf(ms: Long, off: Int): Long = Math.floorDiv(ms + off * 1000L, 86_400_000L)

private fun DrawScope.chartLabel(
    tm: TextMeasurer, s: String, x: Float, y: Float, style: TextStyle, ax: Float = 0f, ay: Float = 0f,
) {
    val l = tm.measure(s, style)
    drawText(l, topLeft = Offset(x - l.size.width * ax, y - l.size.height * ay))
}

private fun deg(v: Double) = Fmt.num(v, 0) + "°"

/** "Wed 11:00–15:00" for hourly indices [a, b] inclusive (end = start of hour b + 1 h). */
private fun span(t: LongArray, a: Int, b: Int, off: Int, today: Long): String {
    val day = localDayOf(t[a], off)
    val prefix = when (day - today) { 0L -> "today"; 1L -> "tomorrow"; else -> Fmt.weekday(t[a], off) }
    return "$prefix ${Fmt.time(t[a], off)}–${Fmt.time(t[b] + 3_600_000L, off)}"
}

/**
 * "Heat & work": hourly WBGT in full sun for the next 48 h, turned into ACGIH work/rest
 * guidance for light, moderate or heavy work. The physics lives in [Heat].
 */
@Composable
fun HeatPlanCard(f: Forecast, modifier: Modifier = Modifier) {
    val s = remember(f) { Heat.series(f) }
    val off = f.offsetSec
    val now = if (f.current.t > 0) f.current.t else System.currentTimeMillis()
    val today = localDayOf(now, off)
    val title = "Heat & work"
    if (s.size == 0) {
        GlassCard(title, modifier) { Text("No hourly forecast to estimate heat stress.", style = Type.Small) }
        return
    }
    val i0 = f.hourly.indexAt(now)
    val i48 = min(s.size, i0 + 48)
    if ((i0 until i48).all { s.sun[it].isNaN() }) {
        GlassCard(title, modifier) {
            Text("Heat estimate unavailable: the forecast is missing temperature or humidity.", style = Type.Small)
        }
        return
    }

    if (Heat.quiet(s.sun, i0, i48)) {
        QuietHeat(f, s, i0, i48, today, modifier)
        return
    }

    var workIdx by rememberSaveable { mutableIntStateOf(Heat.Work.HEAVY.ordinal) }
    val work = Heat.Work.entries[workIdx]
    val levels = remember(s, work) { s.levels(work) }
    val wNow = s.sun[i0]
    val shadeNow = s.shade[i0]
    val rNow = if (wNow.isNaN()) null else Heat.regime(wNow, work)

    GlassCard(
        title, modifier,
        trailing = { if (!wNow.isNaN()) Text("WBGT ${deg(wNow)}", style = Type.Small.copy(color = Pal.Silver)) },
    ) {
        WorkSelector(work) { workIdx = it.ordinal }
        Text(
            "${work.label}: ${work.example}", style = Type.Tiny.copy(color = Pal.Sub),
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )

        // now
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (wNow.isNaN()) "—" else deg(wNow), style = Type.Big)
                    Text(" WBGT in sun", style = Type.Small, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (!shadeNow.isNaN()) Text("${deg(shadeNow)} in shade", style = Type.Small.copy(color = Pal.Silver))
            }
            Box(Modifier.weight(1f))
            rNow?.let { Pill("${it.short} · ${it.name}", band(it.color)) }
        }
        rNow?.let {
            Text(
                "Now, ${work.label.lowercase()} work: ${it.advice}.",
                style = Type.Body.copy(color = Pal.Text), modifier = Modifier.padding(top = 6.dp),
            )
        }

        HeatChart(f, s, levels, work, i0, i48, now, today, Modifier.padding(top = 12.dp))
        HeatLegend(work)

        // worst window and best hours
        val worst = Heat.worstWindow(s.sun, levels, i0, i48)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (worst == null) {
                Text(
                    "${work.label} work: steady work stays within limits for the next 48 h.",
                    style = Type.Small.copy(color = Pal.Silver),
                )
            } else {
                val r = Heat.regime(worst.peak, work)
                Row(verticalAlignment = Alignment.Top) {
                    Pill(r.short, band(r.color))
                    Text(
                        "Worst: ${span(s.t, worst.range.first, worst.range.last, off, today)}, WBGT up to ${deg(worst.peak)}. " +
                            "${work.label} work: ${r.advice}.",
                        style = Type.Small.copy(color = Pal.Text),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            BestHours(s, work, i0, off, today)
        }
        Text(
            "WBGT (wet-bulb globe temperature) in full sun, estimated from model temperature, humidity, " +
                "wind and sunshine (Liljegren et al. 2008). Work/rest per ACGIH TLV for acclimatised workers; " +
                "people new to the heat need more rest. An estimate, not an on-site measurement.",
            style = Type.Tiny, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun WorkSelector(work: Heat.Work, onPick: (Heat.Work) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Heat.Work.entries.forEach { w ->
            val sel = w == work
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .selectable(selected = sel, role = Role.Tab, onClick = { onPick(w) })
                    .semantics { contentDescription = "${w.label} work" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(shape)
                        .background(
                            if (sel) Brush.verticalGradient(listOf(Pal.Crimson, Pal.CrimsonPressed, Pal.CrimsonDeep))
                            else Brush.verticalGradient(listOf(Pal.CardTop, Pal.Card)),
                        )
                        .border(1.dp, if (sel) Color(0x66FF4A63) else Pal.Edge, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        w.label,
                        style = Type.Small.copy(
                            color = if (sel) Pal.Text else Pal.Sub,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

private val LEVEL_COLORS = longArrayOf(0xFF3DB89A, 0xFFF2DC5D, 0xFFEA7A1E, 0xFFE0203F, 0xFFA55DE0)
private val LEVEL_SHORT = arrayOf("Steady", "45/15", "30/30", "15/45", "Stop")

@Composable
private fun HeatLegend(work: Heat.Work) {
    val lim = Heat.limits(work)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (k in 0..4) {
            if (work == Heat.Work.LIGHT && k == 1) continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(band(LEVEL_COLORS[k])))
                Text(" ${LEVEL_SHORT[k]}", style = Type.Tiny.copy(color = Pal.Silver))
            }
        }
    }
    Text(
        "Solid line: sun. Dashed: shade. Minutes of work / rest each hour; steady up to ${Fmt.num(lim[0], 1)}°, stop above ${Fmt.num(lim[3], 1)}°.",
        style = Type.Tiny, modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun BestHours(s: Heat.Series, work: Heat.Work, i0: Int, off: Int, today: Long) {
    fun range(day: Long): Pair<Int, Int> {
        var a = -1
        var b = -1
        for (i in i0 until s.size) {
            val d = localDayOf(s.t[i], off)
            if (d < day) continue
            if (d > day) break
            val hr = Fmt.localHour(s.t[i], off)
            if (hr in 5..19) { if (a < 0) a = i; b = i + 1 }
        }
        return a to b
    }
    var (a, b) = range(today)
    var whenWord = "today"
    if (a < 0 || b - a < 3) { val r = range(today + 1); a = r.first; b = r.second; whenWord = "tomorrow" }
    if (a < 0 || b - a < 3) return
    val st = Heat.coolestStretch(s.sun, a, b, 3)
    if (st < 0) return
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (k in st until st + 3) { lo = min(lo, s.sun[k]); hi = max(hi, s.sun[k]) }
    val r = Heat.regime(hi, work)
    val rangeText = if (deg(lo) == deg(hi)) deg(hi) else "${Fmt.num(lo, 0)}–${deg(hi)}"
    Row(verticalAlignment = Alignment.Top) {
        Pill(r.short, band(r.color))
        Text(
            "Best 3 h for ${work.label.lowercase()} work $whenWord: ${Fmt.time(s.t[st], off)}–${Fmt.time(s.t[st + 2] + 3_600_000L, off)}, " +
                "WBGT $rangeText. ${r.advice.replaceFirstChar { it.uppercase() }}.",
            style = Type.Small.copy(color = Pal.Text),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Plain-language summary of the 48 h for TalkBack. */
private fun chartSummary(s: Heat.Series, levels: IntArray, work: Heat.Work, i0: Int, i48: Int, off: Int, today: Long): String {
    var pk = i0
    var lo = i0
    for (i in i0 until i48) {
        if (s.sun[i].isNaN()) continue
        if (s.sun[pk].isNaN() || s.sun[i] > s.sun[pk]) pk = i
        if (s.sun[lo].isNaN() || s.sun[i] < s.sun[lo]) lo = i
    }
    val sb = StringBuilder("Heat chart, next 48 hours, ${work.label.lowercase()} work. ")
    sb.append("WBGT in sun peaks at ${Fmt.num(s.sun[pk], 0)} degrees ${span(s.t, pk, pk, off, today).substringBefore('–')}, ")
    sb.append("lowest ${Fmt.num(s.sun[lo], 0)} degrees ${span(s.t, lo, lo, off, today).substringBefore('–')}. ")
    val rs = Heat.runs(levels, i0, i48, 1)
    if (rs.isEmpty()) sb.append("Steady work within limits throughout.")
    else sb.append("Rest breaks needed ").append(rs.joinToString(" and ") { span(s.t, it.first, it.last, off, today).replace("–", " to ") }).append('.')
    return sb.toString()
}

@Composable
private fun HeatChart(
    f: Forecast, s: Heat.Series, levels: IntArray, work: Heat.Work,
    i0: Int, i48: Int, now: Long, today: Long, modifier: Modifier,
) {
    val tm = rememberTextMeasurer()
    val off = f.offsetSec
    val small = capChartText(10f, Pal.Sub)
    val tag = capChartText(10f, Pal.Silver)
    val nowStyle = capChartText(10f, Pal.CrimsonText)
    val lim = Heat.limits(work)
    val worst = remember(s, levels, i0) { Heat.worstWindow(s.sun, levels, i0, i48) }
    val summary = remember(s, levels, work, i0) { chartSummary(s, levels, work, i0, i48, off, today) }

    Canvas(
        modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = summary },
    ) {
        val left = 26.dp.toPx()
        val right = size.width - 38.dp.toPx()
        val top = 16.dp.toPx()
        val strip = 6.dp.toPx()
        val bottom = size.height - 18.dp.toPx() - strip
        val pw = right - left
        val ph = bottom - top
        val n = 48
        val t0 = s.t[i0]
        fun xOfIdx(i: Int) = left + pw * ((i - i0).toFloat() / n)

        var vmin = Double.MAX_VALUE
        var vmax = -Double.MAX_VALUE
        for (i in i0 until i48) {
            if (!s.sun[i].isNaN()) { vmin = min(vmin, s.sun[i]); vmax = max(vmax, s.sun[i]) }
            if (!s.shade[i].isNaN()) vmin = min(vmin, s.shade[i])
        }
        var lo = floor(min(vmin, lim[0]) - 1)
        var hi = ceil(max(vmax, lim[0] + 2) + 1)
        if (hi - lo < 8) { val m = (hi + lo) / 2; lo = floor(m - 4); hi = ceil(m + 4) }
        fun yOf(v: Double) = (bottom - ph * ((v - lo) / (hi - lo))).toFloat().coerceIn(top - 2f, bottom)

        // risk-coloured columns and the solid risk strip
        for (i in i0 until i48) {
            val lv = levels.getOrElse(i) { -1 }
            if (lv < 0) continue
            val c = band(LEVEL_COLORS[lv])
            val x = xOfIdx(i)
            val w = pw / n + 0.5f
            drawRect(c.copy(alpha = if (lv == 0) 0.06f else 0.16f), Offset(x, top), Size(w, ph))
            drawRect(c.copy(alpha = 0.9f), Offset(x, bottom + 2f), Size(w, strip))
        }

        // y grid
        var g = ceil(lo / 2) * 2
        while (g <= hi) {
            val y = yOf(g)
            drawLine(Pal.Grid, Offset(left, y), Offset(right, y), 1f)
            chartLabel(tm, "${g.toInt()}°", left - 4.dp.toPx(), y, small, 1f, 0.5f)
            g += 2
        }

        // regime limits, labelled on the right (colour is never the only cue)
        var lastLabelY = Float.MAX_VALUE
        val lineH = tm.measure("0", small).size.height.toFloat()
        val seen = HashSet<Double>()
        for (k in 3 downTo 0) {
            val v = lim[k]
            if (!seen.add(v) || v > hi || v < lo) continue
            val y = yOf(v)
            drawLine(
                band(LEVEL_COLORS[k + 1]).copy(alpha = 0.7f), Offset(left, y), Offset(right, y), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
            )
            // label the regime that starts above this line
            val above = if (work == Heat.Work.LIGHT && k == 0) 2 else k + 1
            val labelY = y - lineH / 2
            if (lastLabelY - labelY >= lineH * 0.95f || lastLabelY == Float.MAX_VALUE) {
                chartLabel(tm, LEVEL_SHORT[above], right + 4.dp.toPx(), y, tag, 0f, 0.5f)
                lastLabelY = labelY
            }
        }

        // days and hours
        for (i in i0..i48) {
            if (i >= s.size) break
            val hr = Fmt.localHour(s.t[i], off)
            val x = xOfIdx(i)
            if (hr == 0) drawLine(Pal.GridStrong, Offset(x, top), Offset(x, bottom + strip), 1f)
            if (hr % 6 == 0 && i > i0) {
                val text = if (hr == 0) Fmt.weekday(s.t[i], off) else "%02d".format(hr)
                chartLabel(tm, text, x, bottom + strip + 3.dp.toPx(), if (hr == 0) tag else small, 0.5f)
            }
        }

        // shade (dashed) and sun (solid) WBGT
        fun line(v: DoubleArray, col: Color, width: Float, dash: Boolean) {
            val p = Path()
            var started = false
            for (i in i0 until i48) {
                if (v[i].isNaN()) { started = false; continue }
                val x = xOfIdx(i) + pw / n / 2
                if (!started) { p.moveTo(x, yOf(v[i])); started = true } else p.lineTo(x, yOf(v[i]))
            }
            drawPath(
                p, col,
                style = Stroke(width, cap = StrokeCap.Round, pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(7f, 6f)) else null),
            )
        }
        line(s.shade, Pal.Silver.copy(alpha = 0.6f), 1.3.dp.toPx(), true)
        line(s.sun, Pal.Text, 2.dp.toPx(), false)

        // worst window bracket
        worst?.let { w ->
            val xa = xOfIdx(w.range.first)
            val xb = xOfIdx(w.range.last + 1)
            val y = top - 4.dp.toPx()
            val col = band(LEVEL_COLORS[w.level])
            drawLine(col, Offset(xa, y), Offset(xb, y), 2.dp.toPx(), StrokeCap.Round)
            drawLine(col, Offset(xa, y), Offset(xa, y + 4.dp.toPx()), 1.5f)
            drawLine(col, Offset(xb, y), Offset(xb, y + 4.dp.toPx()), 1.5f)
            val px = xOfIdx(w.peakIndex) + pw / n / 2
            drawCircle(Pal.Bg, 4.dp.toPx(), Offset(px, yOf(w.peak)))
            drawCircle(col, 3.dp.toPx(), Offset(px, yOf(w.peak)))
            chartLabel(tm, "worst", (xa + xb) / 2, y - 1.dp.toPx(), tag, 0.5f, 1f)
        }

        // now
        val nx = left + pw * ((now - t0).toFloat() / (n * 3_600_000f)).coerceIn(0f, 1f)
        drawLine(Pal.Crimson, Offset(nx, top), Offset(nx, bottom + strip), 1.5.dp.toPx())
        if (worst == null || xOfIdx(worst.range.first) - nx > 40.dp.toPx()) {
            chartLabel(tm, "now", nx + 3.dp.toPx(), top - 13.dp.toPx(), nowStyle)
        }
    }
}

@Composable
private fun QuietHeat(f: Forecast, s: Heat.Series, i0: Int, i48: Int, today: Long, modifier: Modifier) {
    val off = f.offsetSec
    var pk = -1
    for (i in i0 until i48) if (!s.sun[i].isNaN() && (pk < 0 || s.sun[i] > s.sun[pk])) pk = i
    val next = Heat.nextHeat(s.sun, i48)
    val days = ((s.t.last() - s.t[i0]) / 86_400_000L + 1).toInt().coerceAtLeast(2)
    val line1 = "Low heat stress for all work, including heavy work, for the next 48 h."
    val line2 = if (next >= 0) {
        "Rest breaks for heavy work return around ${Fmt.weekday(s.t[next], off)} ${Fmt.dayMonth(s.t[next], off)}" +
            " (WBGT ${deg(s.sun[next])} at ${Fmt.time(s.t[next], off)})."
    } else "No heat stress for heavy work in the $days-day outlook."
    val peak = if (pk >= 0) "Peak WBGT ${deg(s.sun[pk])} in sun, ${span(s.t, pk, pk, off, today).substringBefore('–')}." else ""
    GlassCard(
        "Heat & work",
        modifier.semantics(mergeDescendants = true) {},
        trailing = { if (pk >= 0) Text("peak ${deg(s.sun[pk])}", style = Type.Small.copy(color = Pal.Silver)) },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Pill("Low", Pal.Good)
            Text(line1, style = Type.Body, modifier = Modifier.padding(start = 8.dp))
        }
        Text("$peak $line2".trim(), style = Type.Small, modifier = Modifier.padding(top = 6.dp))
    }
}
