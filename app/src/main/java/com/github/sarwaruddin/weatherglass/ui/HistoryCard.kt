package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.sarwaruddin.weatherglass.data.Archive
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.floor

private fun localDay(ms: Long, off: Int): Long = Math.floorDiv(ms + off * 1000L, 86_400_000L)

private fun DrawScope.label(
    tm: TextMeasurer, s: String, x: Float, y: Float, style: TextStyle, ax: Float = 0f, ay: Float = 0f,
) {
    val l = tm.measure(s, style)
    drawText(l, topLeft = Offset(x - l.size.width * ax, y - l.size.height * ay))
}

private fun anomalyColor(d: Double?): Color = when {
    d == null || d.isNaN() -> Pal.Sub
    d >= 3 -> Pal.CrimsonText
    d >= 1 -> Color(0xFFEA7A1E)
    d <= -3 -> Color(0xFF5A8CF0)
    d <= -1 -> Pal.Rain
    else -> Pal.Silver
}

/** Today against the same calendar date in every year of ERA5 since 1940. */
@Composable
fun HistoryCard(a: Archive, f: Forecast, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val off = f.offsetSec
    val now = System.currentTimeMillis()
    val date = Instant.ofEpochMilli(now).atOffset(ZoneOffset.ofTotalSeconds(off)).toLocalDate()
    val month = date.monthValue
    val day = date.dayOfMonth
    val year = date.year
    val h = remember(a, month, day) {
        runCatching { Meteo.history(a.dates, a.hi, a.lo, month, day) }.getOrNull()
    }
    val today = localDay(now, off)
    val ti = (0 until f.daily.size).firstOrNull { localDay(f.daily.t[it], off) == today }
    val todayHi = ti?.let { f.daily.max.getOrNull(it) }?.takeUnless { it.isNaN() }
    val dm = Fmt.dayMonth(now, off)

    if (h == null || h.series.size < 5) {
        GlassCard("On this date", modifier) { Text("Not enough archive data for $dm.", style = Type.Small) }
        return
    }
    val normal = h.normalHi
    val diff = if (todayHi != null && normal != null) todayHi - normal else null
    val rank = Meteo.rankHigh(h, todayHi)

    GlassCard(
        "On this date", modifier,
        trailing = { Text("$dm · since ${h.series.first().first}", style = Type.Small.copy(color = Pal.Silver)) },
    ) {
        Text("TODAY'S FORECAST HIGH", style = Type.Tiny.copy(color = Pal.Sub))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.temp(todayHi), style = Type.Big)
            Text(
                " ${Meteo.signed(diff)}°",
                style = Type.Body.copy(color = anomalyColor(diff), fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
            )
            Text(
                "  vs normal high ${Fmt.num(h.normalHi, 1)}°", style = Type.Small,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        rank?.let { r ->
            val text = when {
                r.of <= 1 -> null
                r.rank == 1 -> "Warmest $dm in ${r.of} years of record"
                r.rank * 2 <= r.of -> {
                    val base = "${Meteo.ordinal(r.rank)} warmest of ${r.of} years"
                    if (r.since in 1 until year - 1) "$base · warmest $dm since ${r.since}" else base
                }
                r.rank == r.of -> "Coolest $dm in ${r.of} years of record"
                else -> "${Meteo.ordinal(r.of - r.rank + 1)} coolest of ${r.of} years"
            }
            if (text != null) Text(
                text, style = Type.Body.copy(color = if (r.rank * 2 <= r.of) Color(0xFFF0B0B8) else Color(0xFFB0C8F0)),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HistFact(
                "Record high ▲", "${Fmt.num(h.recHi, 1)}°", if (h.recHiYear > 0) "${h.recHiYear}" else "", Pal.CrimsonText,
                Modifier.weight(1f),
            )
            HistFact(
                "Record low ▼", "${Fmt.num(h.recLo, 1)}°", if (h.recLoYear > 0) "${h.recLoYear}" else "", Pal.Rain,
                Modifier.weight(1f),
            )
            HistFact(
                "Trend", "${Meteo.signed(h.trendPerDecade, 2)}°", "per decade", anomalyColor(h.trendPerDecade * 10),
                Modifier.weight(1f),
            )
        }

        val small = capChartText(10f, Pal.Sub)
        val s = h.series
        val warmest = s.maxByOrNull { it.second }
        val coolest = s.minByOrNull { it.second }
        val desc = "Mean high around $dm each year since ${s.first().first}. Trend ${Meteo.signed(h.trendPerDecade, 2)} degrees per decade. " +
            "Normal ${Fmt.num(h.normalHi, 1)}. Warmest ${warmest?.first} at ${Fmt.num(warmest?.second, 1)}, " +
            "coolest ${coolest?.first} at ${Fmt.num(coolest?.second, 1)}. Today's forecast high ${Fmt.temp(todayHi)}."
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp).semantics { contentDescription = desc }) {
            val left = 28.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 4.dp.toPx()
            val bottom = size.height - 14.dp.toPx()
            val pw = right - left
            val ph = bottom - top
            val y0 = s.first().first
            val y1 = maxOf(year, s.last().first + 1)
            var lo = s.minOf { it.second }
            var hi = s.maxOf { it.second }
            if (todayHi != null) { lo = minOf(lo, todayHi); hi = maxOf(hi, todayHi) }
            lo = floor(lo - 0.5); hi = ceil(hi + 0.5)
            fun xOf(y: Double) = left + pw * ((y - y0) / (y1 - y0)).toFloat()
            fun yOf(v: Double) = top + ph * ((hi - v) / (hi - lo)).toFloat()

            val step = if (hi - lo > 12) 4 else if (hi - lo > 6) 2 else 1
            var v = lo
            while (v <= hi) {
                val y = yOf(v)
                drawLine(Pal.Grid, Offset(left, y), Offset(right, y), 1f)
                label(tm, "%.0f°".format(v).replace("-", "−"), left - 4.dp.toPx(), y, small, 1f, 0.5f)
                v += step
            }
            var yr = (ceil(y0 / 20.0) * 20).toInt()
            while (yr <= y1) {
                val x = xOf(yr.toDouble())
                drawLine(Pal.Grid, Offset(x, top), Offset(x, bottom), 1f)
                label(tm, "$yr", x, bottom + 2.dp.toPx(), small, 0.5f)
                yr += 20
            }
            // 1991–2020 normal period and value
            h.normalHi?.let { nh ->
                drawRect(
                    Color(0x0FD8D8E0), Offset(xOf(1991.0), top),
                    Size(xOf(2020.0) - xOf(1991.0), ph),
                )
                drawLine(
                    Pal.Silver.copy(alpha = 0.5f), Offset(left, yOf(nh)), Offset(right, yOf(nh)), 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
            // yearly marks, tinted and shaped by anomaly: ▲ warm, ▼ cool, ● near normal
            val r = 2.4.dp.toPx()
            for ((y, mv) in s) {
                val an = h.normalHi?.let { mv - it }
                val p = Offset(xOf(y.toDouble()), yOf(mv))
                when {
                    an != null && an > 1.5 -> drawPath(
                        Path().apply { moveTo(p.x, p.y - r); lineTo(p.x + r, p.y + r * 0.8f); lineTo(p.x - r, p.y + r * 0.8f); close() },
                        Pal.Crimson.copy(alpha = 0.75f),
                    )
                    an != null && an < -1.5 -> drawPath(
                        Path().apply { moveTo(p.x, p.y + r); lineTo(p.x + r, p.y - r * 0.8f); lineTo(p.x - r, p.y - r * 0.8f); close() },
                        Pal.Rain.copy(alpha = 0.8f),
                    )
                    else -> drawCircle(Pal.Silver.copy(alpha = 0.55f), 1.9.dp.toPx(), p)
                }
            }
            // 10-year running mean
            if (s.size >= 12) {
                val p = Path()
                var started = false
                for (i in 4 until s.size - 5) {
                    var sum = 0.0
                    for (k in i - 4..i + 5) sum += s[k].second
                    val x = xOf(s[i].first + 0.5)
                    val y = yOf(sum / 10)
                    if (!started) { p.moveTo(x, y); started = true } else p.lineTo(x, y)
                }
                drawPath(p, Pal.Silver.copy(alpha = 0.85f), style = Stroke(1.5.dp.toPx()))
            }
            // linear trend
            drawLine(
                Pal.Crimson, Offset(xOf(y0.toDouble()), yOf(h.intercept + h.slope * y0)),
                Offset(xOf(y1.toDouble()), yOf(h.intercept + h.slope * y1)), 1.6.dp.toPx(),
            )
            // today
            if (todayHi != null) {
                val c = Offset(xOf(year.toDouble()), yOf(todayHi))
                drawCircle(Brush.radialGradient(listOf(Pal.Crimson.copy(alpha = 0.75f), Color.Transparent), c, 10.dp.toPx()), 10.dp.toPx(), c)
                drawCircle(Color.White, 3.dp.toPx(), c)
            }
        }
        Text(
            "Marks: mean high of ${dm} ±3 days each year (▲ >1.5° above normal, ▼ >1.5° below). Line: 10-yr mean. Crimson: linear trend " +
                "(${Meteo.signed(h.trendPerDecade, 2)}°/decade). Dashed: 1991–2020 normal.",
            style = Type.Tiny, modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "ERA5 reanalysis: a ~28 km cell average, not a station.",
            style = Type.Tiny, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun HistFact(label: String, value: String, note: String, accent: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = Type.Tiny)
        Text(value, style = Type.Body.copy(color = accent))
        if (note.isNotEmpty()) Text(note, style = Type.Tiny.copy(color = Pal.Sub))
    }
}
