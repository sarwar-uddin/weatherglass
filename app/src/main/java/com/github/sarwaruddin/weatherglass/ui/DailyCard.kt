package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import kotlin.math.ceil
import kotlin.math.max

private fun DoubleArray.g(i: Int): Double = if (i in indices) this[i] else Double.NaN
private fun LongArray.g(i: Int): Long = if (i in indices) this[i] else 0L
private fun IntArray.g(i: Int): Int = if (i in indices) this[i] else -1
private fun Double.ok(): Double? = takeUnless { it.isNaN() || it.isInfinite() }
private fun localDay(ms: Long, off: Int): Long = Math.floorDiv(ms + off * 1000L, 86_400_000L)

/** Cool silver at the bottom of the period's range, crimson at the top. */
private val RangeColors = listOf(Color(0xFF8FA6BF), Color(0xFFD8D8E0), Color(0xFFE0A050), Color(0xFFE0203F))

/** Days 1–7 have real skill; 8–16 are shown on request, flagged as low confidence. */
private const val SURE_DAYS = 7

/**
 * Day rows: weekday, icon with rain odds, and every day's low–high on one shared scale so a
 * hot spell reads at a glance. Tap a row for that day's hourly temperature and rain plus sun,
 * UV and wind.
 */
@Composable
fun DailyCard(f: Forecast, modifier: Modifier = Modifier) {
    val d = f.daily
    val off = f.offsetSec
    val now = if (f.current.t > 0) f.current.t else System.currentTimeMillis()
    val today = localDay(now, off)
    val rows = remember(d, today) { (0 until d.size).filter { localDay(d.t[it], off) >= today } }
    if (rows.isEmpty()) {
        GlassCard("16 days", modifier) { Text("No daily forecast.", style = Type.Small) }
        return
    }
    val curTemp = f.current.temp.ok()
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    for (i in rows) {
        d.min.g(i).ok()?.let { lo = minOf(lo, it) }
        d.max.g(i).ok()?.let { hi = maxOf(hi, it) }
    }
    curTemp?.let { lo = minOf(lo, it); hi = maxOf(hi, it) }
    if (!lo.isFinite() || !hi.isFinite()) { lo = 0.0; hi = 1.0 }
    if (hi - lo < 1.0) { hi += 0.5; lo -= 0.5 }
    var open by rememberSaveable { mutableIntStateOf(-1) }
    var showLate by rememberSaveable { mutableStateOf(false) }
    val sure = rows.take(SURE_DAYS)
    val late = rows.drop(SURE_DAYS)

    GlassCard(
        "${rows.size} days", modifier,
        trailing = { Text("${Fmt.temp(lo)} … ${Fmt.temp(hi)}", style = Type.Small.copy(color = Pal.Silver)) },
    ) {
        sure.forEachIndexed { n, i ->
            if (n > 0) HorizontalDivider(thickness = 1.dp, color = Pal.Grid.copy(alpha = 0.6f))
            DayRow(f, i, today, lo, hi, curTemp, open == i, faded = false) { open = if (open == i) -1 else i }
        }
        if (late.isNotEmpty()) {
            HorizontalDivider(thickness = 1.dp, color = Pal.Grid.copy(alpha = 0.6f))
            val lateLabel = "Days ${SURE_DAYS + 1}–${rows.size} · low confidence"
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = if (showLate) "hide" else "show") { showLate = !showLate }
                    .semantics(mergeDescendants = true) {
                        contentDescription = lateLabel
                        stateDescription = if (showLate) "expanded" else "collapsed"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lateLabel, style = Type.Small.copy(color = Pal.Silver), modifier = Modifier.weight(1f))
                Chevron(showLate)
            }
            if (showLate) late.forEach { i ->
                HorizontalDivider(thickness = 1.dp, color = Pal.Grid.copy(alpha = 0.6f))
                DayRow(f, i, today, lo, hi, null, open == i, faded = true) { open = if (open == i) -1 else i }
            }
        }
    }
}

@Composable
private fun Chevron(open: Boolean, modifier: Modifier = Modifier) {
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Canvas(modifier.size(12.dp).rotate(rot)) {
        val p = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.35f)
            lineTo(size.width * 0.5f, size.height * 0.7f)
            lineTo(size.width * 0.85f, size.height * 0.35f)
        }
        drawPath(p, Pal.Sub, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun DayRow(
    f: Forecast, i: Int, today: Long, lo: Double, hi: Double, curTemp: Double?,
    isOpen: Boolean, faded: Boolean, onToggle: () -> Unit,
) {
    val d = f.daily
    val off = f.offsetSec
    val isToday = localDay(d.t[i], off) == today
    val name = if (isToday) "Today" else Fmt.weekday(d.t[i], off)
    val cond = Meteo.wmo(d.code.g(i)).text
    val pop = d.popMax.g(i).ok()
    val alpha = if (faded) 0.75f else 1f
    val desc = buildString {
        append(if (isToday) "Today" else Fmt.fullDate(d.t[i], off))
        append(": $cond, low ${Fmt.temp(d.min.g(i))}, high ${Fmt.temp(d.max.g(i))}")
        if (pop != null && pop >= 15) append(", rain chance ${Fmt.num(pop)} percent")
        if (faded) append(", low confidence")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (isOpen) "hide details" else "show hourly detail", onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = desc
                    stateDescription = if (isOpen) "expanded" else "collapsed"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.widthIn(min = 54.dp)) {
                Text(
                    name,
                    style = Type.Body.copy(
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = (if (isToday) Pal.Text else Pal.Silver).copy(alpha = alpha),
                    ),
                )
                Text(Fmt.dayMonth(d.t[i], off), style = Type.Tiny.copy(color = Pal.Sub))
            }
            Column(Modifier.widthIn(min = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherIcon(code = d.code.g(i), isDay = true, size = 26.dp)
                if (pop != null && pop >= 15) Text("${Fmt.num(pop)}%", style = Type.Tiny.copy(color = Pal.Rain))
            }
            Text(
                Fmt.temp(d.min.g(i)), style = Type.Small.copy(color = Pal.Sub.copy(alpha = alpha), textAlign = TextAlign.End),
                modifier = Modifier.widthIn(min = 32.dp).padding(start = 4.dp),
            )
            RangeBar(
                lo, hi, d.min.g(i), d.max.g(i), if (isToday) curTemp else null, faded,
                Modifier.weight(1f).height(14.dp).padding(horizontal = 8.dp),
            )
            Text(
                Fmt.temp(d.max.g(i)), style = Type.Body.copy(color = Pal.Text.copy(alpha = alpha), textAlign = TextAlign.End),
                modifier = Modifier.widthIn(min = 34.dp),
            )
            Chevron(isOpen, Modifier.padding(start = 6.dp))
        }
        if (isOpen) {
            DayHours(f, i, Modifier.padding(top = 6.dp))
            DayDetail(f, i, Modifier.padding(top = 8.dp, start = 4.dp))
        }
    }
}

@Composable
private fun RangeBar(lo: Double, hi: Double, mn: Double, mx: Double, cur: Double?, faded: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val cy = size.height / 2
        val th = 5.dp.toPx()
        fun x(v: Double) = (w * ((v - lo) / (hi - lo))).toFloat().coerceIn(0f, w)
        // track at GridStrong-equivalent contrast (≥3:1 for a meaningful graphic)
        drawLine(Color(0xFF3A3A40), Offset(0f, cy), Offset(w, cy), th, StrokeCap.Round)
        val a = mn.ok()
        val b = mx.ok()
        if (a != null || b != null) {
            val x0 = x(a ?: b!!)
            val x1 = maxOf(x(b ?: a!!), x0 + 0.5f)
            val brush = Brush.horizontalGradient(RangeColors, startX = 0f, endX = w)
            drawLine(brush, Offset(x0, cy), Offset(x1, cy), th, StrokeCap.Round, alpha = if (faded) 0.6f else 1f)
            drawLine(
                Color.White.copy(alpha = 0.28f), Offset(x0, cy - th * 0.22f), Offset(x1, cy - th * 0.22f),
                th * 0.3f, StrokeCap.Round,
            )
        }
        if (cur != null) {
            val cx = x(cur)
            drawCircle(Pal.Bg, radius = th * 0.95f, center = Offset(cx, cy))
            drawCircle(Color.White, radius = th * 0.7f, center = Offset(cx, cy))
        }
    }
}

/** That day's hours: temperature line over rain bars, 00–24 local. */
@Composable
private fun DayHours(f: Forecast, di: Int, modifier: Modifier) {
    val h = f.hourly
    val off = f.offsetSec
    val day = localDay(f.daily.t[di], off)
    val idx = remember(h, day) { (0 until h.size).filter { localDay(h.t[it], off) == day } }
    val temps = idx.mapNotNull { h.temp.g(it).ok() }
    if (idx.size < 2 || temps.size < 2) {
        Text("Hourly detail isn't available this far out.", style = Type.Small, modifier = modifier)
        return
    }
    val tm = rememberTextMeasurer()
    val small = capChartText(10f, Pal.Sub)
    val hiStyle = capChartText(10f, Pal.Text)
    val rainStyle = capChartText(10f, Pal.Rain)
    var tLo = Double.MAX_VALUE; var tHi = -Double.MAX_VALUE; var iLo = idx[0]; var iHi = idx[0]
    var rainSum = 0.0; var rainPeak = 0.0; var firstWet = -1; var lastWet = -1
    for (i in idx) {
        h.temp.g(i).ok()?.let { if (it < tLo) { tLo = it; iLo = i }; if (it > tHi) { tHi = it; iHi = i } }
        val r = h.precip.g(i).ok() ?: 0.0
        rainSum += r; rainPeak = max(rainPeak, r)
        if (r >= 0.1) { if (firstWet < 0) firstWet = i; lastWet = i }
    }
    val desc = buildString {
        append("Hourly for ${Fmt.fullDate(f.daily.t[di], off)}: low ${Fmt.temp(tLo)} at ${Fmt.time(h.t[iLo], off)}, ")
        append("high ${Fmt.temp(tHi)} at ${Fmt.time(h.t[iHi], off)}. ")
        if (firstWet < 0) append("No rain.")
        else append("Rain ${Fmt.num(rainSum, 1)} millimetres, ${Fmt.time(h.t[firstWet], off)} to ${Fmt.time(h.t[lastWet] + 3_600_000L, off)}.")
    }
    val now = System.currentTimeMillis()
    Canvas(modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = desc }) {
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 14.dp.toPx()
        val pw = right - left
        val dayStart = day * 86_400_000L - off * 1000L
        fun xOf(t: Long) = left + pw * ((t - dayStart).toFloat() / 86_400_000f)
        val span = max(tHi - tLo, 3.0)
        val mid = (tHi + tLo) / 2
        val lo = mid - span / 2
        val tTop = top
        val tBottom = top + (bottom - top) * 0.72f
        fun yOf(v: Double) = (tBottom - (tBottom - tTop) * ((v - lo) / span)).toFloat()

        for (hr in 0..24 step 6) {
            val x = xOf(dayStart + hr * 3_600_000L)
            drawLine(Pal.Grid, Offset(x, top), Offset(x, bottom), 1f)
            if (hr in 6..18) {
                val l = tm.measure("%02d".format(hr), small)
                drawText(l, topLeft = Offset(x - l.size.width / 2f, bottom + 1.dp.toPx()))
            }
        }
        drawLine(Pal.GridStrong, Offset(left, bottom), Offset(right, bottom), 1f)
        // rain bars, own scale, along the bottom
        val rainMax = max(2.0, ceil(rainPeak))
        val bw = pw / 24f - 1f
        for (i in idx) {
            val r = h.precip.g(i).ok() ?: continue
            if (r < 0.05) continue
            val bh = ((bottom - tBottom) * 1.3f * (r / rainMax)).toFloat().coerceAtMost(bottom - top)
            drawRect(Pal.Rain.copy(alpha = 0.85f), Offset(xOf(h.t[i] - 3_600_000L), bottom - bh), Size(bw, bh))
        }
        // temperature
        val p = Path()
        var started = false
        for (i in idx) {
            val v = h.temp.g(i).ok() ?: continue
            val x = xOf(h.t[i]); val y = yOf(v)
            if (!started) { p.moveTo(x, y); started = true } else p.lineTo(x, y)
        }
        drawPath(p, Pal.Crimson, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // hi / lo labels
        fun tag(i: Int, v: Double, above: Boolean) {
            val l = tm.measure(Fmt.temp(v), hiStyle)
            val x = xOf(h.t[i]).coerceIn(left + l.size.width / 2f, right - l.size.width / 2f)
            val y = yOf(v)
            drawCircle(Pal.Text, 2.5.dp.toPx(), Offset(xOf(h.t[i]), y))
            drawText(l, topLeft = Offset(x - l.size.width / 2f, if (above) y - l.size.height - 2.dp.toPx() else y + 3.dp.toPx()))
        }
        tag(iHi, tHi, true)
        if (iLo != iHi) tag(iLo, tLo, false)
        if (rainSum >= 0.1) {
            val l = tm.measure("${Fmt.num(rainSum, 1)} mm", rainStyle)
            drawText(l, topLeft = Offset(right - l.size.width, 0f))
        }
        if (now in dayStart..(dayStart + 86_400_000L)) {
            val nx = xOf(now)
            drawLine(Pal.Crimson.copy(alpha = 0.8f), Offset(nx, top), Offset(nx, bottom), 1.dp.toPx())
        }
    }
}

@Composable
private fun DayDetail(f: Forecast, i: Int, modifier: Modifier) {
    val d = f.daily
    val off = f.offsetSec
    val rise = d.sunrise.g(i)
    val set = d.sunset.g(i)
    val daylightH = d.daylight.g(i).ok()?.div(3600.0)
        ?: if (rise > 0 && set > rise) (set - rise) / 3_600_000.0 else null
    val uvMax = d.uvMax.g(i).ok()
    val uvBand = Meteo.uv(uvMax)
    val wind = d.windMax.g(i).ok()
    val dir = d.windDir.g(i).ok()
    val mm = d.precip.g(i).ok()
    val pop = d.popMax.g(i).ok()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Meteo.wmo(d.code.g(i)).text, style = Type.Small.copy(color = Pal.Silver))
        Row(Modifier.fillMaxWidth()) {
            Fact("Rain", mm?.let { "${Fmt.num(it, if (it < 10) 1 else 0)} mm" } ?: "—", Modifier.weight(1f), Pal.Rain)
            Fact("Chance", pop?.let { "${Fmt.num(it)}%" } ?: "—", Modifier.weight(1f))
            Fact("Daylight", daylightH?.let { Fmt.duration(it) } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Fact("Sunrise", if (rise > 0) Fmt.time(rise, off) else "—", Modifier.weight(1f))
            Fact("Sunset", if (set > 0) Fmt.time(set, off) else "—", Modifier.weight(1f))
            Fact("UV max", uvMax?.let { "${Fmt.num(it)} · ${uvBand.name}" } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Fact(
                "Wind max",
                wind?.let { "${Fmt.num(it)} km/h ${if (dir != null) Meteo.compass(dir) else ""}".trim() } ?: "—",
                Modifier.weight(1f),
            )
            Fact("Beaufort", wind?.let { "F${Meteo.beaufort(it).first} · ${Meteo.beaufort(it).second}" } ?: "—", Modifier.weight(2f))
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier, accent: Color = Pal.Text) {
    Column(modifier) {
        Text(label.uppercase(), style = Type.Tiny.copy(color = Pal.Sub))
        Text(value, style = Type.Small.copy(color = accent))
    }
}
