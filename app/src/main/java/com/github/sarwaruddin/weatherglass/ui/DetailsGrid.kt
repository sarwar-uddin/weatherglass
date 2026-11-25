package com.github.sarwaruddin.weatherglass.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun DoubleArray.g(i: Int): Double = if (i in indices) this[i] else Double.NaN
private fun Double.ok(): Double? = takeUnless { it.isNaN() || it.isInfinite() }

private val Value = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Light, color = Pal.Text, fontFamily = Plex)

/** One small glossy tile; fills its share of a Row and the Row's height. */
@Composable
private fun Tile(title: String, modifier: Modifier, alert: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val m = if (alert) modifier.border(1.5.dp, Pal.Crimson, RoundedCornerShape(18.dp)) else modifier
    GlassCard(title, m.fillMaxHeight().heightIn(min = 150.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

private class TileSpec(val salience: Int, val draw: @Composable (Modifier) -> Unit)

/** Severity index of a Meteo.Band on the shared 5-step scale, by its colour. */
private fun level(b: Meteo.Band): Int = when (b.color) {
    0xFFF2DC5D -> 1
    0xFFEA7A1E -> 2
    0xFFE0203F -> 3
    0xFFA55DE0, 0xFFA8326E -> 4
    else -> 0
}

private fun dp3Of(f: Forecast, now: Long): Double? {
    val h = f.hourly
    val p = f.current.pressure.ok() ?: Meteo.at(h.t, h.pressure, now)
    val p3 = Meteo.at(h.t, h.pressure, now - 3 * 3_600_000L)
    return if (p != null && p3 != null && h.size > 0 && h.t[0] <= now - 3 * 3_600_000L) p - p3 else null
}

/**
 * Eight instrument tiles in two columns. Anything at Moderate or worse rises to the top
 * (stable sort, so the usual order holds on quiet days). Plain Rows, safe in a scrolling list.
 */
@Composable
fun DetailsGrid(f: Forecast, modifier: Modifier = Modifier) {
    val c = f.current
    val h = f.hourly
    val now = if (c.t > 0) c.t else System.currentTimeMillis()
    val hi = if (h.size > 0) h.indexAt(now) else -1
    val dp3 = dp3Of(f, now)
    val wb = c.temp.ok()?.let { t -> c.rh.ok()?.let { Meteo.wetBulb(t, it).ok() } }
    val km = c.vis.ok()?.div(1000.0)
    val bf = Meteo.beaufort(c.wind.ok()).first
    val night = !c.isDay
    val tiles = listOf(
        TileSpec(if (bf >= 8) 3 else if (bf >= 6) 2 else 0) { WindTile(f, it) },
        TileSpec(level(Meteo.comfort(c.dew.ok()))) { HumidityTile(f, now, it) },
        TileSpec(if (dp3 != null && dp3 < -1.5) 3 else 0) { PressureTile(f, now, dp3, it) },
        TileSpec(level(Meteo.heatStress(wb))) { HeatTile(f, it) },
        TileSpec(level(Meteo.cape(capePeak(f, now).first))) { ThunderTile(f, hi, now, it) },
        TileSpec(if (night) 0 else level(Meteo.uv(c.uv.ok()))) { UvTile(f, now, it) },
        TileSpec(when { km == null -> 0; km < 1 -> 3; km < 5 -> 2; km < 10 -> 1; else -> 0 }) { VisibilityTile(f, it) },
        TileSpec(0) { CloudTile(f, hi, it) },
    ).sortedByDescending { it.salience }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (k in tiles.indices step 2) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles[k].draw(Modifier.weight(1f))
                if (k + 1 < tiles.size) tiles[k + 1].draw(Modifier.weight(1f)) else Box(Modifier.weight(1f))
            }
        }
    }
}

// ---- wind --------------------------------------------------------------------

@Composable
private fun WindTile(f: Forecast, modifier: Modifier) {
    val c = f.current
    val speed = c.wind.ok()
    val dir = c.windDir.ok()?.let { ((it % 360) + 360) % 360 }
    val gust = c.gust.ok()
    val (bf, bfName) = Meteo.beaufort(speed)
    val tm = rememberTextMeasurer()
    val lab = capChartText(10f, Pal.CrimsonText).copy(fontWeight = FontWeight.Bold)
    val from = if (dir != null) "${Meteo.compass(dir)} (${dir.roundToInt() % 360}°)" else "—"
    val desc = if (speed == null) "Wind unavailable" else
        "Wind ${Fmt.num(speed)} kilometres per hour from $from, gusts ${Fmt.num(gust)}, force $bf, $bfName"
    Tile("Wind", modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(64.dp).semantics { contentDescription = desc }) {
                val r = size.minDimension / 2 - 2.dp.toPx()
                val ctr = center
                drawCircle(Brush.radialGradient(listOf(Color(0xFF1C1C21), Color(0xFF0B0B0D)), ctr, r), r, ctr)
                drawCircle(Pal.GridStrong, r, ctr, style = Stroke(1f))
                for (k in 0 until 16) {
                    val a = Math.toRadians(k * 22.5 - 90).toFloat()
                    val inner = if (k % 4 == 0) r * 0.78f else r * 0.88f
                    drawLine(
                        if (k % 4 == 0) Pal.Silver else Pal.GridStrong,
                        Offset(ctr.x + inner * cos(a), ctr.y + inner * sin(a)),
                        Offset(ctr.x + r * cos(a), ctr.y + r * sin(a)), 1f,
                    )
                }
                val nl = tm.measure("N", lab)
                drawText(nl, topLeft = Offset(ctr.x - nl.size.width / 2f, ctr.y - r * 0.78f))
                if (dir != null) {
                    // arrow flies downwind: tail on the side the wind comes from
                    rotate((dir + 180).toFloat(), ctr) {
                        val tip = Offset(ctr.x, ctr.y - r * 0.62f)
                        val tail = Offset(ctr.x, ctr.y + r * 0.55f)
                        drawLine(Pal.Text, tail, tip, 2.dp.toPx(), StrokeCap.Round)
                        val head = Path().apply {
                            moveTo(tip.x, tip.y - 4.dp.toPx())
                            lineTo(tip.x - 5.dp.toPx(), tip.y + 5.dp.toPx())
                            lineTo(tip.x + 5.dp.toPx(), tip.y + 5.dp.toPx())
                            close()
                        }
                        drawPath(head, Pal.Crimson)
                        drawCircle(Pal.Crimson, 2.5.dp.toPx(), tail)
                    }
                }
                drawCircle(Color.White.copy(alpha = 0.07f), r * 0.9f, Offset(ctr.x, ctr.y - r * 0.35f))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(Fmt.num(speed), style = Value)
                Text("km/h", style = Type.Tiny)
            }
        }
        Text("from $from", style = Type.Small.copy(color = Pal.Silver))
        Text("gusts ${Fmt.num(gust)} km/h", style = Type.Small)
        Text("F$bf · $bfName", style = Type.Tiny.copy(color = Pal.Sub))
    }
}

// ---- humidity ----------------------------------------------------------------

@Composable
private fun HumidityTile(f: Forecast, now: Long, modifier: Modifier) {
    val c = f.current
    val b = Meteo.comfort(c.dew.ok())
    val h = f.hourly
    val pts = (0 until h.size).filter { h.t[it] in (now - 24 * 3_600_000L)..now && !h.rh.g(it).isNaN() }
    Tile("Humidity", modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (c.rh.ok() == null) "—" else "${Fmt.num(c.rh)}%", style = Value)
            Text(" RH", style = Type.Tiny, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("dew point ${Fmt.temp(c.dew)}", style = Type.Small.copy(color = Pal.Silver))
        if (b.name != "—") Pill(b.name, band(b.color), Modifier.padding(top = 4.dp))
        if (b.advice.isNotEmpty()) Text(b.advice, style = Type.Tiny.copy(color = Pal.Sub))
        if (pts.size >= 3) {
            val lo = pts.minOf { h.rh[it] }
            val hiV = pts.maxOf { h.rh[it] }
            Canvas(
                Modifier.fillMaxWidth().height(24.dp).padding(top = 4.dp)
                    .semantics { contentDescription = "Humidity over the last 24 hours, ${Fmt.num(lo)} to ${Fmt.num(hiV)} percent" },
            ) {
                val t0 = now - 24 * 3_600_000L
                fun x(t: Long) = size.width * ((t - t0).toFloat() / (24 * 3_600_000f))
                fun y(v: Double) = size.height * (1f - (v / 100.0).toFloat())
                val p = Path()
                pts.forEachIndexed { k, i -> if (k == 0) p.moveTo(x(h.t[i]), y(h.rh[i])) else p.lineTo(x(h.t[i]), y(h.rh[i])) }
                drawPath(p, Pal.Humid, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            }
            Text("RH, last 24 h", style = Type.Tiny)
        }
    }
}

// ---- pressure: a small aneroid -----------------------------------------------

@Composable
private fun PressureTile(f: Forecast, now: Long, dp3: Double?, modifier: Modifier) {
    val h = f.hourly
    val p = f.current.pressure.ok() ?: Meteo.at(h.t, h.pressure, now)
    val p3 = if (p != null && dp3 != null) p - dp3 else null
    val tend = Meteo.tendency(dp3)
    val falling = dp3 != null && dp3 < -1.5
    Tile("Pressure", modifier, alert = falling) {
        if (p == null) {
            Text("—", style = Value)
            Text("No pressure reading.", style = Type.Small)
            return@Tile
        }
        AneroidDial(p, p3, Modifier.fillMaxWidth().height(116.dp))
        Text(
            if (tend.word.isEmpty()) "tendency —" else "${tend.arrow} ${tend.word}",
            style = Type.Small.copy(color = if (falling) Pal.CrimsonText else Pal.Silver, fontWeight = FontWeight.Medium),
        )
        Text("${Meteo.signed(dp3)} hPa / 3 h", style = Type.Tiny.copy(color = Pal.Sub))
        if (p3 != null) Text("silver hand: 3 h ago", style = Type.Tiny)
    }
}

@Composable
private fun AneroidDial(p: Double, p3: Double?, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val lab = capChartText(10f, Pal.Silver)
    val valueStyle = capChartText(14f, Pal.Text).copy(fontWeight = FontWeight.Medium)
    val unitStyle = capChartText(10f, Pal.Sub)
    val lo = (Math.round(p / 10.0) * 10 - 20).toDouble()
    val span = 40.0
    fun angle(v: Double) = (135.0 + 270.0 * ((v - lo) / span).coerceIn(-0.02, 1.02)).toFloat()

    // The needle wakes up once: sweeps from the dial's low end with one spring overshoot.
    val ctx = LocalContext.current
    val still = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    var shown by rememberSaveable { mutableStateOf(false) }
    val needle = remember { Animatable(if (shown || still) angle(p) else angle(lo)) }
    LaunchedEffect(p) {
        if (!shown && !still) {
            needle.animateTo(angle(p), spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessVeryLow))
        } else needle.snapTo(angle(p))
        shown = true
    }
    val desc = "Barometer dial: ${Fmt.num(p, 0)} hectopascals now" +
        (p3?.let { ", ${Fmt.num(it, 0)} three hours ago" } ?: "")

    Canvas(modifier.semantics { contentDescription = desc }) {
        val r = size.minDimension / 2 - 1.dp.toPx()
        val c = center
        // chrome rim: hard horizon break at 50 %
        val ch = Pal.Chrome
        drawCircle(
            Brush.verticalGradient(
                0f to ch[0], 0.25f to ch[1], 0.5f to ch[2], 0.5f to ch[3], 0.75f to ch[4], 1f to ch[5],
                startY = c.y - r, endY = c.y + r,
            ),
            r, c,
        )
        val face = r * 0.88f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF16161A), Color(0xFF050506)), c, face), face, c)
        // ticks
        for (k in 0..40) {
            val v = lo + k
            val a = Math.toRadians(angle(v).toDouble())
            val major = k % 5 == 0
            val outer = face * 0.95f
            val inner = face * if (k % 10 == 0) 0.78f else if (major) 0.83f else 0.88f
            drawLine(
                if (major) Pal.Silver else Pal.GridStrong,
                Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                if (major) 1.4f else 1f,
            )
            if (k % 10 == 0) {
                val l = tm.measure("${v.toInt()}", lab)
                val lr = face * 0.6f
                drawText(l, topLeft = Offset(c.x + lr * cos(a).toFloat() - l.size.width / 2f, c.y + lr * sin(a).toFloat() - l.size.height / 2f))
            }
        }
        // value window at the bottom of the face
        val vl = tm.measure(Fmt.num(p, 0), valueStyle)
        drawText(vl, topLeft = Offset(c.x - vl.size.width / 2f, c.y + face * 0.32f))
        val ul = tm.measure("hPa", unitStyle)
        drawText(ul, topLeft = Offset(c.x - ul.size.width / 2f, c.y + face * 0.32f + vl.size.height))
        // set hand (3 h ago)
        p3?.let {
            rotate(angle(it) + 90f, c) {
                drawLine(Pal.Silver.copy(alpha = 0.85f), c, Offset(c.x, c.y - face * 0.8f), 1.5.dp.toPx(), StrokeCap.Round)
                drawCircle(Pal.Silver, 2.dp.toPx(), Offset(c.x, c.y - face * 0.8f))
            }
        }
        // crimson needle (now)
        rotate(needle.value + 90f, c) {
            val w = 2.6.dp.toPx()
            val path = Path().apply {
                moveTo(c.x, c.y - face * 0.9f)
                lineTo(c.x + w, c.y)
                lineTo(c.x + w * 0.6f, c.y + face * 0.2f)
                lineTo(c.x - w * 0.6f, c.y + face * 0.2f)
                lineTo(c.x - w, c.y)
                close()
            }
            drawPath(path, Brush.horizontalGradient(listOf(Pal.CrimsonHi, Pal.Crimson, Pal.CrimsonDeep), c.x - w, c.x + w))
        }
        // chrome cap and glass highlight
        drawCircle(Brush.verticalGradient(listOf(ch[0], ch[2], ch[5]), c.y - 5.dp.toPx(), c.y + 5.dp.toPx()), 4.5.dp.toPx(), c)
        drawCircle(Color.White.copy(alpha = 0.06f), face * 0.8f, Offset(c.x - face * 0.15f, c.y - face * 0.35f))
    }
}

// ---- heat stress -------------------------------------------------------------

@Composable
private fun HeatTile(f: Forecast, modifier: Modifier) {
    val c = f.current
    val t = c.temp.ok()
    val rh = c.rh.ok()
    val wb = if (t != null && rh != null) Meteo.wetBulb(t, rh).ok() else null
    val hiC = if (t != null && rh != null) Meteo.heatIndex(t, rh).ok() else null
    val b = Meteo.heatStress(wb)
    Tile("Heat stress", modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (wb == null) "—" else Fmt.num(wb, 1) + "°", style = Value)
            Text(" wet-bulb", style = Type.Tiny, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("heat index ${Fmt.temp(hiC)}", style = Type.Small.copy(color = Pal.Silver))
        if (b.name != "—") Pill(if (b.name == "Low") "Low heat stress" else b.name, band(b.color), Modifier.padding(top = 4.dp))
        if (b.advice.isNotEmpty() && b.name != "Low") Text(b.advice, style = Type.Tiny.copy(color = Pal.Sub))
    }
}

// ---- thunder -----------------------------------------------------------------

private fun capePeak(f: Forecast, now: Long): Pair<Double?, Long> {
    val h = f.hourly
    var pk = Double.NaN
    var peakT = 0L
    for (i in 0 until h.size) {
        if (h.t[i] < now - 3_600_000L || h.t[i] > now + 24 * 3_600_000L) continue
        val v = h.cape.g(i)
        if (!v.isNaN() && (pk.isNaN() || v > pk)) { pk = v; peakT = h.t[i] }
    }
    return pk.ok() to peakT
}

@Composable
private fun ThunderTile(f: Forecast, hi: Int, now: Long, modifier: Modifier) {
    val h = f.hourly
    val capeNow = if (hi >= 0) h.cape.g(hi).ok() else null
    val (peak, peakT) = capePeak(f, now)
    val b = Meteo.cape(peak ?: capeNow)
    Tile("Thunder", modifier) {
        if (b.name == "Stable" || b.name == "—") {
            // quiet: one line, no dead zero
            Text(if (b.name == "—") "—" else "Quiet", style = Value)
            Text(
                if (peak != null) "CAPE under ${Fmt.num(maxOf(peak, 1.0))} J/kg next 24 h" else "No CAPE data.",
                style = Type.Small.copy(color = Pal.Silver),
            )
            if (b.advice.isNotEmpty()) Text(b.advice, style = Type.Tiny.copy(color = Pal.Sub))
            return@Tile
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.num(capeNow), style = Value)
            Text(" J/kg", style = Type.Tiny, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("peak ${Fmt.num(peak)} at ${Fmt.time(peakT, f.offsetSec)}", style = Type.Small.copy(color = Pal.Silver))
        Text("CAPE, next 24 h", style = Type.Tiny)
        Pill(b.name, band(b.color), Modifier.padding(top = 4.dp))
        if (b.advice.isNotEmpty()) Text(b.advice, style = Type.Tiny.copy(color = Pal.Sub))
    }
}

// ---- UV ----------------------------------------------------------------------

@Composable
private fun UvTile(f: Forecast, now: Long, modifier: Modifier) {
    val h = f.hourly
    val night = !f.current.isDay
    // next daytime peak in the coming 24 h (at night: tomorrow's)
    var pk = Double.NaN
    var pkT = 0L
    for (i in 0 until h.size) {
        if (h.t[i] < now || h.t[i] > now + 24 * 3_600_000L) continue
        val v = h.uv.g(i)
        if (!v.isNaN() && (pk.isNaN() || v > pk)) { pk = v; pkT = h.t[i] }
    }
    val peak = pk.ok()
    val uvNow = f.current.uv.ok()
    val shown = if (night) peak else uvNow
    val b = Meteo.uv(shown)
    Tile("UV index", modifier) {
        Text(Fmt.num(shown, 1), style = Value.copy(color = if (shown != null) band(b.color) else Pal.Text))
        Text(
            if (night) (if (peak != null) "next peak, ${Fmt.weekday(pkT, f.offsetSec)} ${Fmt.time(pkT, f.offsetSec)}" else "night")
            else "peak next 24 h ${Fmt.num(peak, 1)}${if (peak != null) " at " + Fmt.time(pkT, f.offsetSec) else ""}",
            style = Type.Small.copy(color = Pal.Silver),
        )
        Canvas(
            Modifier.fillMaxWidth().height(10.dp).padding(vertical = 2.dp)
                .semantics { contentDescription = "UV scale, ${Fmt.num(shown, 1)}, ${b.name}" },
        ) {
            val segs = listOf(3.0 to 0xFF3DB89AL, 6.0 to 0xFFF2DC5DL, 8.0 to 0xFFEA7A1EL, 11.0 to 0xFFE0203FL, 13.0 to 0xFFA55DE0L)
            var from = 0.0
            val w = size.width
            for ((to, col) in segs) {
                drawRect(
                    band(col).copy(alpha = 0.75f), Offset((w * from / 13).toFloat(), 0f),
                    Size((w * (to - from) / 13).toFloat() - 1f, size.height),
                )
                from = to
            }
            drawRect(Color.White.copy(alpha = 0.18f), Offset(0f, 0f), Size(w, size.height * 0.35f))
            shown?.let {
                val x = (w * (it.coerceIn(0.0, 13.0) / 13)).toFloat()
                drawCircle(Pal.Bg, 4.dp.toPx(), Offset(x, size.height / 2))
                drawCircle(Color.White, 3.dp.toPx(), Offset(x, size.height / 2))
            }
        }
        if (b.name != "—") Pill(b.name, band(b.color), Modifier.padding(top = 4.dp))
        if (b.advice.isNotEmpty() && !night) Text(b.advice, style = Type.Tiny.copy(color = Pal.Sub))
    }
}

// ---- visibility --------------------------------------------------------------

@Composable
private fun VisibilityTile(f: Forecast, modifier: Modifier) {
    val km = f.current.vis.ok()?.div(1000.0)
    val rh = f.current.rh.ok() ?: 0.0
    val (word, note, col) = when {
        km == null -> Triple("—", "", Pal.Sub)
        km < 1 -> Triple("Fog", "Under 1 km: drive with dipped lights.", Pal.Crimson)
        km < 5 && rh >= 90 -> Triple("Mist", "Water droplets, humid air.", Pal.Humid)
        km < 5 -> Triple("Haze", "Dry particles: smoke, dust or pollution.", Color(0xFFEA7A1E))
        km < 10 -> Triple("Slight haze", "Distant outlines soften.", Pal.Warn)
        km < 20 -> Triple("Good", "Clear to the horizon.", Pal.Good)
        else -> Triple("Excellent", "Very clean, dry air.", Pal.Good)
    }
    Tile("Visibility", modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.num(km, if (km != null && km < 10) 1 else 0), style = Value)
            Text(" km", style = Type.Tiny, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (word != "—") Pill(word, col, Modifier.padding(top = 4.dp))
        if (note.isNotEmpty()) Text(note, style = Type.Tiny.copy(color = Pal.Sub))
        Text("Model value.", style = Type.Tiny)
    }
}

// ---- clouds ------------------------------------------------------------------

@Composable
private fun CloudTile(f: Forecast, hi: Int, modifier: Modifier) {
    val h = f.hourly
    val layers = listOf(
        Triple("High", "cirrus", if (hi >= 0) h.cloudHigh.g(hi) else Double.NaN),
        Triple("Mid", "alto", if (hi >= 0) h.cloudMid.g(hi) else Double.NaN),
        Triple("Low", "stratus/cu", if (hi >= 0) h.cloudLow.g(hi) else Double.NaN),
    )
    val total = f.current.cloud.ok()
    val clear = (total ?: 100.0) < 5 && layers.all { (it.third.ok() ?: 0.0) < 5 }
    Tile("Clouds", modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.num(total), style = Value)
            Text(" % total", style = Type.Tiny, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (clear) {
            Text("Clear sky", style = Type.Small.copy(color = Pal.Silver))
            Text("No cloud in any layer.", style = Type.Tiny.copy(color = Pal.Sub))
            return@Tile
        }
        for ((name, kind, v) in layers) {
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "$name cloud, $kind, ${Fmt.num(v)} percent" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = Type.Tiny.copy(color = Pal.Silver), modifier = Modifier.widthIn(min = 32.dp))
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    val rr = CornerRadius(size.height / 2)
                    drawRoundRect(Pal.Grid, cornerRadius = rr)
                    val frac = (v.ok() ?: 0.0).coerceIn(0.0, 100.0).toFloat() / 100f
                    if (frac > 0f) {
                        val w = maxOf(size.width * frac, size.height)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color(0xFFE6E6EE), Color(0xFF8A8A94))),
                            size = Size(w, size.height), cornerRadius = rr,
                        )
                    }
                }
                Text(
                    if (v.isNaN()) "—" else "%.0f".format(v), style = Type.Tiny.copy(color = Pal.Sub),
                    modifier = Modifier.widthIn(min = 26.dp).padding(start = 4.dp),
                )
            }
        }
        Text(layers.joinToString(" · ") { it.second }, style = Type.Tiny, modifier = Modifier.padding(top = 2.dp))
    }
}
