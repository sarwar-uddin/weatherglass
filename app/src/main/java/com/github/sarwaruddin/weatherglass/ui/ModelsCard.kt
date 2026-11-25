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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.weatherglass.data.Meteo
import com.github.sarwaruddin.weatherglass.data.ModelRun
import com.github.sarwaruddin.weatherglass.data.Models
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

private fun DoubleArray.g(i: Int): Double = if (i in indices) this[i] else Double.NaN
private fun localDay(ms: Long, off: Int): Long = Math.floorDiv(ms + off * 1000L, 86_400_000L)

/** One fixed colour per model family, so a dot means the same model on every row. */
private fun modelColor(run: ModelRun, index: Int): Color {
    val k = (run.id + " " + run.label).uppercase(Locale.ROOT)
    return when {
        "ECMWF" in k || "IFS" in k -> Color(0xFFE0203F)
        "GFS" in k || "NCEP" in k -> Color(0xFF3F8FE8)
        "ICON" in k || "DWD" in k -> Color(0xFFF2DC5D)
        "GEM" in k || "CMC" in k -> Color(0xFF3DB89A)
        "JMA" in k -> Color(0xFFCDB8FF)
        else -> listOf(Color(0xFFD8D8E0), Color(0xFFEA7A1E), Color(0xFF6FC3C3), Color(0xFFB08A6A))[index % 4]
    }
}

/** Shape per model so dots survive colour blindness: ● ECMWF, ■ GFS, ▲ ICON, ◆ GEM, ✚ JMA. */
private fun modelShape(run: ModelRun, index: Int): Int {
    val k = (run.id + " " + run.label).uppercase(Locale.ROOT)
    return when {
        "ECMWF" in k || "IFS" in k -> 0
        "GFS" in k || "NCEP" in k -> 1
        "ICON" in k || "DWD" in k -> 2
        "GEM" in k || "CMC" in k -> 3
        "JMA" in k -> 4
        else -> index % 5
    }
}

private fun DrawScope.marker(shape: Int, c: Offset, r: Float, color: Color) {
    when (shape) {
        0 -> drawCircle(color, r, c)
        1 -> drawRect(color, Offset(c.x - r * 0.85f, c.y - r * 0.85f), Size(r * 1.7f, r * 1.7f))
        2 -> drawPath(Path().apply { moveTo(c.x, c.y - r * 1.1f); lineTo(c.x + r, c.y + r * 0.8f); lineTo(c.x - r, c.y + r * 0.8f); close() }, color)
        3 -> drawPath(Path().apply { moveTo(c.x, c.y - r * 1.15f); lineTo(c.x + r * 1.15f, c.y); lineTo(c.x, c.y + r * 1.15f); lineTo(c.x - r * 1.15f, c.y); close() }, color)
        else -> {
            val w = r * 0.55f
            drawRect(color, Offset(c.x - w / 2, c.y - r), Size(w, r * 2))
            drawRect(color, Offset(c.x - r, c.y - w / 2), Size(r * 2, w))
        }
    }
}

private fun shortName(run: ModelRun): String {
    val k = (run.id + " " + run.label).uppercase(Locale.ROOT)
    return listOf("ECMWF", "GFS", "ICON", "GEM", "JMA").firstOrNull { it in k } ?: run.label
}

/** Green when the models agree, amber when they wobble, crimson when they're split. */
private fun confColor(c: Int): Color {
    val t = c / 100f
    return if (t >= 0.5f) lerp(Pal.Warn, Pal.Good, (t - 0.5f) / 0.5f)
    else lerp(Pal.CrimsonText, Pal.Warn, t / 0.5f)
}

private class DayRow(
    val t: Long, val highs: List<Double>, val cons: Meteo.Consensus?,
    val wet: Int, val voters: Int, val conf: Int,
)

private fun weekdayName(ms: Long, off: Int, rel: Int): String = when (rel) {
    0 -> "today"
    1 -> "tomorrow"
    else -> Instant.ofEpochMilli(ms).atOffset(ZoneOffset.ofTotalSeconds(off)).dayOfWeek
        .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
}

private fun headline(rows: List<DayRow>, off: Int): String {
    if (rows.isEmpty()) return ""
    val bad = rows.indexOfFirst { (it.cons?.spread ?: 0.0) > 3.0 || it.conf < 55 }
    val maxIdx = rows.indices.maxByOrNull { rows[it].cons?.spread ?: -1.0 } ?: 0
    val maxSpread = rows[maxIdx].cons?.spread ?: 0.0
    val sp = "%.0f°".format(maxSpread)
    return when {
        bad < 0 -> "Models agree all ${rows.size} days; highs within ${"%.0f°".format(ceil(maxSpread))} throughout."
        bad == 0 -> "Models split from the start; spread reaches $sp by ${weekdayName(rows[maxIdx].t, off, maxIdx)}."
        else -> {
            val until = weekdayName(rows[bad - 1].t, off, bad - 1)
            val peakIdx = (bad until rows.size).maxByOrNull { rows[it].cons?.spread ?: -1.0 } ?: bad
            val peak = "%.0f°".format(rows[peakIdx].cons?.spread ?: 0.0)
            val tail = if ((rows[peakIdx].cons?.spread ?: 0.0) > 3.0) "spread widens to $peak by ${weekdayName(rows[peakIdx].t, off, peakIdx)}"
            else "rain signal splits by ${weekdayName(rows[bad].t, off, bad)}"
            "Models agree through $until; $tail."
        }
    }
}

/**
 * Five global models side by side: each day's high from every model as a dot on a
 * shared scale, the consensus tick, spread, how many call it wet, and a 0–100 score.
 */
@Composable
fun ModelsCard(m: Models, offsetSec: Int, modifier: Modifier = Modifier) {
    val today = localDay(System.currentTimeMillis(), offsetSec)
    val runs = m.runs
    val rows = remember(m, today) {
        m.t.indices.filter { localDay(m.t[it], offsetSec) >= today }.take(10).map { i ->
            val highs = runs.map { it.max.g(i) }
            val cons = Meteo.consensus(highs)
            val rain = runs.map { it.precip.g(i) }.filter { !it.isNaN() }
            DayRow(
                m.t[i], highs, cons, rain.count { it >= 1.0 }, rain.size,
                if (cons == null) 0 else Meteo.confidence(cons.spread, rain),
            )
        }.filter { it.cons != null }
    }
    if (rows.isEmpty() || runs.isEmpty()) {
        GlassCard("Model consensus", modifier) { Text("Model runs unavailable.", style = Type.Small) }
        return
    }
    var lo = rows.minOf { it.cons!!.min }
    var hi = rows.maxOf { it.cons!!.max }
    lo = floor(lo - 0.5); hi = ceil(hi + 0.5)
    val colors = runs.mapIndexed { i, r -> modelColor(r, i) }
    val shapes = runs.mapIndexed { i, r -> modelShape(r, i) }
    val anyWet = rows.any { it.wet > 0 }
    val meanConf = rows.map { it.conf }.average()

    GlassCard(
        "Model consensus", modifier,
        trailing = { Text("agreement ${"%.0f".format(meanConf)}/100", style = Type.Small.copy(color = Pal.Silver)) },
    ) {
        Text(headline(rows, offsetSec), style = Type.Body.copy(color = Pal.Silver))
        // legend
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            runs.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(9.dp)) { marker(shapes[i], center, size.minDimension / 2.3f, colors[i]) }
                    Text(" " + shortName(r), style = Type.Tiny.copy(color = Pal.Silver))
                }
            }
        }
        // header
        val head = Type.Tiny.copy(color = Pal.Sub, textAlign = TextAlign.End)
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.widthIn(min = 40.dp))
            Text("HIGHS ${Fmt.temp(lo)}–${Fmt.temp(hi)}", style = Type.Tiny.copy(color = Pal.Sub), modifier = Modifier.weight(1f).padding(start = 6.dp))
            Text("SPREAD", style = head, modifier = Modifier.widthIn(min = 46.dp))
            if (anyWet) Text("WET", style = head, modifier = Modifier.widthIn(min = 40.dp))
            Text("AGREE", style = head, modifier = Modifier.widthIn(min = 46.dp))
        }
        rows.forEachIndexed { n, r ->
            val dayName = if (n == 0 && localDay(r.t, offsetSec) == today) "Today" else Fmt.weekday(r.t, offsetSec)
            val c0 = r.cons!!
            val rowDesc = "$dayName: model highs ${Fmt.temp(c0.min)} to ${Fmt.temp(c0.max)}, mean ${Fmt.temp(c0.mean)}, " +
                "spread ${"%.1f".format(c0.spread)} degrees, ${r.wet} of ${r.voters} models wet, agreement ${r.conf} of 100"
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = rowDesc },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dayName,
                    style = Type.Small.copy(color = if (n == 0) Pal.Text else Pal.Silver),
                    modifier = Modifier.widthIn(min = 40.dp),
                )
                Canvas(Modifier.weight(1f).height(16.dp).padding(horizontal = 6.dp)) {
                    val w = size.width
                    val cy = size.height / 2
                    fun x(v: Double) = (w * (v - lo) / (hi - lo)).toFloat().coerceIn(0f, w)
                    // degree grid
                    var g = ceil(lo)
                    while (g <= hi) {
                        val gx = x(g)
                        val strong = g.toInt() % 5 == 0
                        drawLine(
                            if (strong) Pal.GridStrong.copy(alpha = 0.6f) else Pal.Grid.copy(alpha = 0.5f),
                            Offset(gx, if (strong) 0f else cy - 3.dp.toPx()),
                            Offset(gx, if (strong) size.height else cy + 3.dp.toPx()), 1f,
                        )
                        g += 1.0
                    }
                    val c = r.cons!!
                    // spread band
                    val bx0 = x(c.min)
                    val bx1 = maxOf(x(c.max), bx0 + 1f)
                    drawRoundRect(
                        confColor(r.conf).copy(alpha = 0.18f),
                        topLeft = Offset(bx0 - 4.dp.toPx(), cy - 5.dp.toPx()),
                        size = Size(bx1 - bx0 + 8.dp.toPx(), 10.dp.toPx()),
                        cornerRadius = CornerRadius(5.dp.toPx()),
                    )
                    // model dots
                    r.highs.forEachIndexed { k, v ->
                        if (!v.isNaN()) {
                            val p = Offset(x(v), cy)
                            marker(shapes[k], p, 4.2.dp.toPx(), Pal.Bg)
                            marker(shapes[k], p, 3.2.dp.toPx(), colors[k])
                        }
                    }
                    // consensus tick
                    val mx = x(c.mean)
                    drawLine(Color.White, Offset(mx, cy - 7.dp.toPx()), Offset(mx, cy + 7.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                }
                val c = r.cons!!
                Text(
                    "${"%.1f".format(c.spread)}°",
                    style = Type.Small.copy(
                        color = when { c.spread < 1.5 -> Pal.Sub; c.spread < 3 -> Pal.Warn; else -> Pal.CrimsonText },
                        textAlign = TextAlign.End,
                    ),
                    modifier = Modifier.widthIn(min = 46.dp),
                )
                if (anyWet) Text(
                    if (r.voters == 0) "—" else "${r.wet}/${r.voters}",
                    style = Type.Small.copy(
                        color = if (r.voters > 0 && r.wet * 2 >= r.voters) Pal.Rain else Pal.Sub,
                        textAlign = TextAlign.End,
                    ),
                    modifier = Modifier.widthIn(min = 40.dp),
                )
                Text(
                    "${r.conf}",
                    style = Type.Small.copy(color = confColor(r.conf), fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
                    modifier = Modifier.widthIn(min = 46.dp),
                )
            }
        }
        Text(
            "Markers: each model's daily high. White tick: consensus mean. Wet = models giving ≥1 mm. " +
                "Agreement (0–100) blends high-temperature spread with rain yes/no agreement.",
            style = Type.Tiny, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
