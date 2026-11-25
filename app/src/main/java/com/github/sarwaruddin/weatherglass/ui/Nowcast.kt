package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.data.Forecast
import kotlin.math.sqrt

private const val WET = 0.05 // mm per 15 min (0.2 mm/h) counts as rain
private const val TOP_MMH = 16.0 // top of the bar scale, mm/h (sqrt scale)

private class NowcastInfo(val t: LongArray, val v: DoubleArray, val headline: String, val dry: Boolean, val total: Double)

private fun nowcastInfo(f: Forecast, now: Long): NowcastInfo {
    val nc = f.nowcast
    var first = 0
    while (first < nc.t.size && nc.t[first] < now - 900_000L) first++
    val n = minOf(12, nc.t.size - first).coerceAtLeast(0)
    val t = LongArray(n) { nc.t[first + it] }
    val v = DoubleArray(n) { nc.precip[first + it].let { x -> if (x.isNaN()) 0.0 else x } }
    val off = f.offsetSec
    var total = 0.0
    var peak = 0.0
    for (x in v) { total += x; if (x > peak) peak = x }
    val dry = peak < 0.02
    val word = when {
        peak * 4 < 2.5 -> "Light rain"
        peak * 4 < 7.6 -> "Rain"
        else -> "Heavy rain"
    }
    val headline = when {
        n == 0 -> "No nowcast available"
        dry -> "No rain for the next 3 hours"
        v[0] >= WET -> {
            var stop = -1
            for (i in 1 until n) if (v[i] < WET) { stop = i; break }
            if (stop > 0) "$word easing by ${Fmt.time(t[stop], off)}" else "$word for the next 3 hours"
        }
        else -> {
            var start = -1
            for (i in 0 until n) if (v[i] >= WET) { start = i; break }
            if (start >= 0) "$word starting around ${Fmt.time(t[start], off)}" else "Spots of drizzle possible"
        }
    }
    return NowcastInfo(t, v, headline, dry || n == 0, total)
}

/** The next-3-hours headline for the hero; [wet] decides whether the full card is worth showing. */
class NowcastBrief(val headline: String, val wet: Boolean, val available: Boolean)

fun nowcastBrief(f: Forecast, now: Long = System.currentTimeMillis()): NowcastBrief {
    val info = nowcastInfo(f, now)
    val available = info.t.isNotEmpty()
    return NowcastBrief(info.headline, available && !info.dry, available)
}

/** Shown on the Now page only when rain is expected; a dry nowcast is one line in the hero. */
@Composable
fun NowcastCard(f: Forecast, modifier: Modifier = Modifier) {
    val info = remember(f) { nowcastInfo(f, System.currentTimeMillis()) }
    val desc = remember(info) {
        if (info.total >= 0.05) "Next 3 hours: ${info.headline}, about ${"%.1f".format(info.total)} millimetres." else "Next 3 hours: ${info.headline}."
    }
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val trailing: (@Composable () -> Unit)? =
        if (info.total >= 0.05) { { Text("%.1f mm".format(info.total), style = Type.Small.copy(color = Pal.Rain)) } } else null
    GlassCard("Next 3 hours", modifier.clearAndSetSemantics { contentDescription = desc }, trailing = trailing) {
        Text(info.headline, style = Type.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium))
        if (info.dry) {
            Spacer(
                Modifier.fillMaxWidth().height(14.dp).drawWithCache {
                    val y = size.height * 0.6f
                    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
                    val sw = 1.5f.dp.toPx()
                    onDrawBehind {
                        drawLine(Pal.GridStrong, Offset(0f, y), Offset(size.width, y), sw, StrokeCap.Round, dash)
                    }
                },
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Spacer(
                Modifier.fillMaxWidth().height(86.dp).drawWithCache {
                    val w = size.width
                    val h = size.height
                    val labelStyle = Type.Tiny.copy(fontSize = 10.sp, color = Pal.Sub)
                    val right = w - 30.dp.toPx()
                    val top = 4.dp.toPx()
                    val bottom = h - 16.dp.toPx()
                    val plotH = bottom - top
                    val n = info.v.size
                    val slot = right / n.coerceAtLeast(1)
                    val bw = slot * 0.68f
                    fun yOf(mmh: Double) = bottom - plotH * sqrt((mmh / TOP_MMH).coerceIn(0.0, 1.0)).toFloat()
                    val barTop = FloatArray(n) { yOf(info.v[it] * 4.0) }
                    val barBrush = Brush.verticalGradient(listOf(Color(0xFF8CB8F0), Pal.Rain, Pal.RainDeep), top, bottom)
                    val gloss = Color(0x30FFFFFF)
                    val corner = CornerRadius(2.5f.dp.toPx())
                    // intensity guides: moderate 2.5, heavy 7.6 mm/h
                    val guides = floatArrayOf(yOf(2.5), yOf(7.6))
                    val guideLabels: List<TextLayoutResult> = listOf(
                        measurer.measure("mod", labelStyle), measurer.measure("heavy", labelStyle),
                    )
                    val ticks = ArrayList<Pair<Float, TextLayoutResult>>()
                    for (i in 0 until n) {
                        val label = when {
                            i == 0 -> "Now"
                            Fmt.localHour(info.t[i], f.offsetSec) != Fmt.localHour(info.t[i - 1], f.offsetSec) -> Fmt.time(info.t[i], f.offsetSec)
                            else -> null
                        } ?: continue
                        val lay = measurer.measure(label, labelStyle)
                        val x = (i * slot + slot / 2 - lay.size.width / 2f).coerceIn(0f, right - lay.size.width)
                        ticks += x to lay
                    }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                    val hair = 1.dp.toPx()
                    onDrawBehind {
                        for (g in guides.indices) {
                            drawLine(Pal.Grid, Offset(0f, guides[g]), Offset(right, guides[g]), hair, pathEffect = dash)
                            val l = guideLabels[g]
                            drawText(l, topLeft = Offset(right + 4.dp.toPx(), guides[g] - l.size.height / 2f))
                        }
                        drawLine(Pal.GridStrong, Offset(0f, bottom), Offset(right, bottom), hair)
                        for (i in 0 until n) {
                            if (info.v[i] <= 0.0) continue
                            val x = i * slot + (slot - bw) / 2
                            val yt = minOf(barTop[i], bottom - 2.dp.toPx())
                            drawRoundRect(barBrush, Offset(x, yt), Size(bw, bottom - yt), corner)
                            drawRoundRect(gloss, Offset(x + bw * 0.12f, yt + 1f), Size(bw * 0.3f, bottom - yt - 1f), corner)
                        }
                        for ((x, lay) in ticks) drawText(lay, topLeft = Offset(x, bottom + 3.dp.toPx()))
                    }
                },
            )
        }
    }
}
