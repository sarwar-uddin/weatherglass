package com.github.sarwaruddin.weatherglass.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ---- local palette (BRAND.md §4.2; Theme.kt owns the core tokens) ----------------------------

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR
private val CrimsonPressed = Color(0xFFC8102E)
private val CrimsonHi = Color(0xFFFF4A63)
private val crimsonA40 = Pal.Crimson.copy(alpha = 0.40f)
private val crimsonA02 = Pal.Crimson.copy(alpha = 0.02f)
private val DewTeal = Color(0xFF3DB89A)
private val FeelsColor = CrimsonHi.copy(alpha = 0.62f)
private val humidColor = Pal.Humid.copy(alpha = 0.16f)
private val nightColor = Color(0x47000000)
private val RainTrace = Color(0xFF24466E)
private val RainLight = Color(0xFF2F6CB0)
private val RainMod = Color(0xFF4F98E8)
private val RainHeavy = Color(0xFF9AD0FF)
private val PopLine = Color(0xFF4F8FD9).copy(alpha = 0.85f)
private val WindLine = Color(0xFFB8BCC8)
private val GustBand = WindLine.copy(alpha = 0.16f)
private val GustLine = WindLine.copy(alpha = 0.55f)
private val ArrowColor = Pal.Silver.copy(alpha = 0.85f)
private val PressFillTop = Pal.Silver.copy(alpha = 0.14f)
private val PressFillBottom = Pal.Silver.copy(alpha = 0f)
private val CloudColor = Color(0xFFC9C9D1)
private val LaneEdge = Color(0xFF3A3A40)
private val PanelTop = Color(0xF4202026)
private val PanelBottom = Color(0xF40E0E12)
private val NowLine = Pal.Text.copy(alpha = 0.35f)
private val ScrubLine = Pal.Text.copy(alpha = 0.7f)
private val TempHalo = Pal.Crimson.copy(alpha = 0.35f)
private val PanelEdge = Color(0x40FFFFFF)

// ---- lane geometry (dp) -----------------------------------------------------------------------

private const val LEGEND = 16f
private const val DAYS = 16f
private const val LANE_T = 128f
private const val GAP = 8f
private const val LANE_P = 40f
private const val LANE_W = 38f
private const val LANE_H = 38f
private const val GAP_C = 6f
private const val LANE_C = 14f
private const val AXIS = 16f
private const val TOTAL = LEGEND + DAYS + LANE_T + GAP + LANE_P + GAP + LANE_W + GAP + LANE_H + GAP_C + LANE_C + AXIS

/** Chart text scales with the user's font size but is capped at 1.15× (UX review §1.7). */
private fun Density.capSp(v: Float): TextUnit = (v * minOf(fontScale, 1.15f) / fontScale).sp

/** Everything precomputed for one size + range + forecast + current hour. */
private class Gram(
    val a: Int, val z: Int, val sa: Int, val sz: Int,
    val left: Float, val right: Float, val t0: Long, val t1: Long,
    val aTop: Float, val aBot: Float, val tTop: Float, val tLo: Float, val tHi: Float,
    val bBot: Float, val dTop: Float, val eBot: Float,
    val xs: FloatArray, val yTemp: FloatArray, val yDew: FloatArray, val yFeels: FloatArray,
    val yWind: FloatArray, val yPress: FloatArray, val barTop: FloatArray,
    val panelBrush: Brush,
    val static: DrawScope.() -> Unit,
) {
    /** Readout layouts, measured lazily per hour and kept while this Gram lives. */
    val readouts = arrayOfNulls<TextLayoutResult>(z - a)

    fun xOf(t: Long) = left + (right - left) * (t - t0).toFloat() / (t1 - t0).toFloat()
    fun yT(v: Double) = tTop + (aBot - 2f - tTop) * (tHi - v.toFloat()) / (tHi - tLo)

    /** Nearest scrubbable hour under x. */
    fun indexAtX(x: Float): Int {
        var best = sa
        var bd = Float.MAX_VALUE
        for (i in sa until sz) {
            val d = abs(xs[i - a] - x)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }
}

/** One Gram per range; dropped when the size or the current hour changes (forecast: remember key). */
private class GramCache {
    private var w = -1f
    private var hgt = -1f
    private var anchor = -1
    private val grams = arrayOfNulls<Gram>(2)
    var current: Gram? = null

    fun get(scope: CacheDrawScope, f: Forecast, five: Boolean, anchor: Int, measurer: TextMeasurer): Gram? {
        if (scope.size.width != w || scope.size.height != hgt || anchor != this.anchor) {
            w = scope.size.width; hgt = scope.size.height; this.anchor = anchor
            grams[0] = null; grams[1] = null
        }
        val k = if (five) 1 else 0
        val g = grams[k] ?: scope.buildGram(f, anchor, five, measurer)?.also { grams[k] = it }
        current = g
        return g
    }
}

@Composable
fun MeteogramCard(f: Forecast, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val view = LocalView.current
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now.longValue = System.currentTimeMillis()
        }
    }
    var five by rememberSaveable { mutableStateOf(false) }
    val scrub = remember { mutableIntStateOf(-1) }
    val cache = remember(f) { GramCache() }
    val anchor = f.hourly.indexAt(now.longValue)
    val summary = remember(f, anchor) { summary24(f, now.longValue) }

    GlassCard(
        "Meteogram", modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RangeChip("48 h", !five) { five = false; scrub.intValue = -1 }
                RangeChip("5 days", five) { five = true; scrub.intValue = -1 }
            }
        },
    ) {
        Box(Modifier.fillMaxWidth().height(TOTAL.dp)) {
            // Semantics layer underneath: it has no pointer input, so touches fall through to the chart.
            A11yLayer(f, summary, scrub, cache, Modifier.matchParentSize())
            Spacer(
                Modifier
                    .matchParentSize()
                    .pointerInput(f) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dragging = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                if (!dragging) {
                                    if (ch.isConsumed) break // the list scrolled
                                    val dx = abs(ch.position.x - down.position.x)
                                    val dy = abs(ch.position.y - down.position.y)
                                    if (dy > slop && dy > dx) break // vertical: let the page scroll
                                    if (dx > slop) dragging = true
                                }
                                if (dragging) {
                                    ch.consume()
                                    val g = cache.current ?: continue
                                    val i = g.indexAtX(ch.position.x)
                                    if (i != scrub.intValue) {
                                        tick(view, f, i)
                                        scrub.intValue = i
                                    }
                                }
                            }
                            scrub.intValue = -1
                        }
                    }
                    .drawWithCache {
                        val nowMs = now.longValue
                        val g = cache.get(this, f, five, f.hourly.indexAt(nowMs), measurer)
                            ?: return@drawWithCache onDrawBehind { }
                        // Static lanes recorded once into a RenderNode; a scrub frame replays it.
                        val layer = obtainGraphicsLayer()
                        layer.record { g.static(this) }
                        val mark = nowMark(g, f, nowMs, measurer)
                        onDrawBehind {
                            drawLayer(layer)
                            mark(this)
                            val i = scrub.intValue
                            if (i >= g.sa && i < g.sz) drawScrub(g, i, f, measurer)
                        }
                    },
            )
        }
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Type.Small.copy(
                color = if (selected) Pal.Text else Pal.Sub,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            modifier = Modifier
                .clip(shape)
                .then(
                    if (selected) Modifier.background(Brush.verticalGradient(listOf(Pal.Crimson, CrimsonPressed)), shape)
                    else Modifier.border(1.dp, Pal.Edge, shape),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Semantics only: a 24-hour summary plus next/previous-hour actions that drive the scrub. */
@Composable
private fun A11yLayer(f: Forecast, summary: String, scrub: MutableIntState, cache: GramCache, modifier: Modifier) {
    val i = scrub.intValue
    val state = remember(f, i) { if (i < 0 || i >= f.hourly.size) "" else readoutText(f, i).text.replace("\n", ", ") }
    Box(
        modifier.semantics {
            contentDescription = summary
            if (state.isNotEmpty()) stateDescription = state
            customActions = listOf(
                CustomAccessibilityAction("Next hour") { step(scrub, cache, +1) },
                CustomAccessibilityAction("Previous hour") { step(scrub, cache, -1) },
                CustomAccessibilityAction("Stop inspecting") { scrub.intValue = -1; true },
            )
        },
    )
}

private fun step(scrub: MutableIntState, cache: GramCache, d: Int): Boolean {
    val g = cache.current ?: return false
    val cur = scrub.intValue
    scrub.intValue = if (cur < g.sa || cur >= g.sz) minOf(g.sz - 1, g.sa + 6) else (cur + d).coerceIn(g.sa, g.sz - 1)
    return true
}

/** A detent per hour; a firmer one at local midnight. */
private fun tick(view: View, f: Forecast, i: Int) {
    val local = Math.floorMod(f.hourly.t[i] + f.offsetSec * 1000L, DAY)
    val type = if (local < HOUR / 2) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK
    view.performHapticFeedback(type)
}

// ---- building ----------------------------------------------------------------------------------

/** Smooth curve through (xs, ys) like the QML bezier; NaN breaks it. With fillTo, each run is closed down to that y. */
private fun curve(xs: FloatArray, ys: FloatArray, n: Int, fillTo: Float = Float.NaN): Path {
    val p = Path()
    var start = -1
    var px = 0f
    var py = 0f
    for (i in 0 until n) {
        val y = ys[i]
        if (y.isNaN()) {
            if (start >= 0 && !fillTo.isNaN()) { p.lineTo(px, fillTo); p.lineTo(xs[start], fillTo); p.close() }
            start = -1
            continue
        }
        val x = xs[i]
        if (start < 0) { p.moveTo(x, y); start = i } else {
            val xm = (px + x) / 2f
            p.cubicTo(xm, py, xm, y, x, y)
        }
        px = x; py = y
    }
    if (start >= 0 && !fillTo.isNaN()) { p.lineTo(px, fillTo); p.lineTo(xs[start], fillTo); p.close() }
    return p
}

/** BRAND.md rain ramp, mm/h. Brighter = more. */
private fun rainColor(mm: Double): Color = when {
    mm < 0.5 -> RainTrace
    mm < 2.5 -> RainLight
    mm < 10.0 -> RainMod
    else -> RainHeavy
}

private fun localMidnight(t: Long, offMs: Long): Long = Math.floorDiv(t + offMs, DAY) * DAY - offMs

private fun Float.nanTo(v: Float) = if (isNaN()) v else this

private fun CacheDrawScope.buildGram(f: Forecast, anchor: Int, five: Boolean, measurer: TextMeasurer): Gram? {
    val h = f.hourly
    val off = f.offsetSec
    val offMs = off * 1000L
    if (h.size < 2) return null
    val left = 30.dp.toPx()
    val right = size.width - 34.dp.toPx()
    val plotW = right - left
    if (plotW <= 0f) return null

    // lanes, top to bottom
    var y = 0f
    val legendY = (LEGEND / 2f).dp.toPx()
    y += LEGEND.dp.toPx()
    val daysY = y + (DAYS / 2f).dp.toPx()
    y += DAYS.dp.toPx()
    val aTop = y; y += LANE_T.dp.toPx(); val aBot = y; y += GAP.dp.toPx()
    val bTop = y; y += LANE_P.dp.toPx(); val bBot = y; y += GAP.dp.toPx()
    val cTop = y; y += LANE_W.dp.toPx(); val cBot = y; y += GAP.dp.toPx()
    val dTop = y; y += LANE_H.dp.toPx(); val dBot = y; y += GAP_C.dp.toPx()
    val eTop = y; y += LANE_C.dp.toPx(); val eBot = y
    val axisY = eBot + 3.dp.toPx()

    // window: 6 h of context, then 42 h (48 h) or out to +120 h (5 days)
    val tA = h.t[anchor.coerceIn(0, h.size - 1)]
    val t0 = tA - 6 * HOUR
    val t1 = minOf(if (five) tA + 120 * HOUR else t0 + 48 * HOUR, h.t[h.size - 1])
    if (t1 - t0 < 6 * HOUR) return null
    var a = 0
    while (a < h.size && h.t[a] < t0 - HOUR) a++
    var z = a
    while (z < h.size && h.t[z] <= t1 + HOUR) z++
    val n = z - a
    if (n < 2) return null
    var sa = a
    while (sa < z && h.t[sa] < t0) sa++
    var sz = sa
    while (sz < z && h.t[sz] <= t1) sz++
    if (sz <= sa) return null
    fun xOf(t: Long) = left + plotW * (t - t0).toFloat() / (t1 - t0).toFloat()
    val xs = FloatArray(n) { xOf(h.t[a + it]) }

    // ---- scales
    var tmin = Double.MAX_VALUE
    var tmax = -Double.MAX_VALUE
    var pmin = Double.MAX_VALUE
    var pmax = -Double.MAX_VALUE
    var rainMax = 0.0
    var windMax = 0.0
    var anyPop = false
    for (i in a until z) {
        val tv = h.temp[i]
        val fv = h.feels[i]
        if (!tv.isNaN()) { tmin = minOf(tmin, tv); tmax = maxOf(tmax, tv) }
        if (!fv.isNaN()) { tmin = minOf(tmin, fv); tmax = maxOf(tmax, fv) }
        if (!h.dew[i].isNaN()) tmin = minOf(tmin, h.dew[i])
        val p = h.pressure[i]
        if (!p.isNaN()) { pmin = minOf(pmin, p); pmax = maxOf(pmax, p) }
        if (!h.precip[i].isNaN()) rainMax = maxOf(rainMax, h.precip[i])
        if (!h.wind[i].isNaN()) windMax = maxOf(windMax, h.wind[i])
        if (!h.gust[i].isNaN()) windMax = maxOf(windMax, h.gust[i])
        if (!h.pop[i].isNaN() && h.pop[i] > 0.0) anyPop = true
    }
    if (tmin == Double.MAX_VALUE) { tmin = 0.0; tmax = 10.0 }
    if (pmin == Double.MAX_VALUE) { pmin = 1010.0; pmax = 1015.0 }
    val tLo = (floor(tmin / 2) * 2).toFloat()
    var tHi = (ceil(tmax / 2) * 2).toFloat()
    if (tHi - tLo < 6f) tHi = tLo + 6f
    val tTop = aTop + 14.dp.toPx() // headroom for the daily-max labels
    fun yT(v: Double) = if (v.isNaN()) Float.NaN else tTop + (aBot - 2f - tTop) * (tHi - v.toFloat()) / (tHi - tLo)
    fun yH(v: Double) = if (v.isNaN()) Float.NaN else aBot - (aBot - aTop) * 0.45f * v.toFloat() / 100f

    val pmid = (pmin + pmax) / 2
    val pspan = maxOf(6.0, ceil((pmax - pmin) * 1.25))
    val pLo = (pmid - pspan / 2).toFloat()
    val pHi = (pmid + pspan / 2).toFloat()
    val dIn = 3.dp.toPx()
    fun yP(v: Double) = if (v.isNaN()) Float.NaN else dTop + dIn + (dBot - dTop - 2 * dIn) * (pHi - v.toFloat()) / (pHi - pLo)

    val wScale = maxOf(20.0, ceil(windMax / 10.0) * 10.0).toFloat()
    val wTop = cTop + 13.dp.toPx() // arrows ride above the speed curve
    fun yW(v: Double) = if (v.isNaN()) Float.NaN else cBot - (cBot - wTop) * (v.toFloat() / wScale).coerceIn(0f, 1f)

    val rScale = maxOf(2.0, ceil(rainMax))
    fun yR(mm: Double) = bBot - (bBot - bTop) * sqrt((mm / rScale).coerceIn(0.0, 1.0)).toFloat()
    fun yPop(p: Double) = if (p.isNaN()) Float.NaN else bBot - (bBot - bTop) * (p / 100.0).toFloat().coerceIn(0f, 1f)

    val yTemp = FloatArray(n) { yT(h.temp[a + it]) }
    val yDew = FloatArray(n) { yT(h.dew[a + it]) }
    val yFeels = FloatArray(n) { yT(h.feels[a + it]) }
    val yRh = FloatArray(n) { yH(h.rh[a + it]) }
    val yPress = FloatArray(n) { yP(h.pressure[a + it]) }
    val yWind = FloatArray(n) { yW(h.wind[a + it]) }
    val yGust = FloatArray(n) { yW(h.gust[a + it]).nanTo(yWind[it]) }
    val yPopA = FloatArray(n) { yPop(h.pop[a + it]) }

    // ---- lane A paths
    val humPath = curve(xs, yRh, n, fillTo = aBot)
    val dewPath = curve(xs, yDew, n)
    val feelsPath = curve(xs, yFeels, n)
    val tempPath = curve(xs, yTemp, n)
    val fillPath = curve(xs, yTemp, n, fillTo = aBot)
    val fillBrush = Brush.verticalGradient(listOf(crimsonA40, crimsonA02), aTop, aBot)

    // ---- lane B: bars over the preceding hour, colour by intensity, alpha by probability
    val gap = 0.6f.dp.toPx()
    val barTop = FloatArray(n) { Float.NaN }
    val barX0 = FloatArray(n)
    val barX1 = FloatArray(n)
    val barAlpha = FloatArray(n)
    val barColor = arrayOfNulls<Color>(n)
    val minBar = 1.5f.dp.toPx()
    for (k in 0 until n) {
        val mm = h.precip[a + k]
        if (mm.isNaN() || mm < 0.05) continue
        val pop = h.pop[a + k]
        barX0[k] = xOf(h.t[a + k] - HOUR) + gap
        barX1[k] = maxOf(barX0[k] + 1f, xs[k] - gap)
        barTop[k] = minOf(yR(mm), bBot - minBar)
        barAlpha[k] = if (pop.isNaN()) 0.8f else (0.35f + 0.65f * (pop / 100.0).toFloat()).coerceIn(0.35f, 1f)
        barColor[k] = rainColor(mm)
    }
    val popPath = if (anyPop) curve(xs, yPopA, n) else null

    // ---- lane C: gust band (hourly polyline), wind curve, direction arrows
    val gustBand = Path().apply {
        moveTo(xs[0], yGust[0].nanTo(cBot))
        for (k in 1 until n) lineTo(xs[k], yGust[k].nanTo(cBot))
        for (k in n - 1 downTo 0) lineTo(xs[k], yWind[k].nanTo(cBot))
        close()
    }
    val gustPath = curve(xs, yGust, n)
    val windPath = curve(xs, yWind, n)
    val arrows = Path()
    val every = if (five) 6 else 3
    val al = 9.dp.toPx()
    val arrowY = cTop + 6.dp.toPx()
    for (k in 0 until n) {
        val i = a + k
        val t = h.t[i]
        if (t < t0 || t > t1) continue
        val lh = Math.floorMod(Math.floorDiv(t + offMs, HOUR), 24L).toInt()
        if (lh % every != 0) continue
        val cx = xs[k]
        val dir = h.windDir[i]
        val spd = h.wind[i]
        if (dir.isNaN() || spd.isNaN() || spd < 2.0) { // calm: a dot
            arrows.addOval(Rect(Offset(cx, arrowY), 1.6f.dp.toPx()))
            continue
        }
        val th = Math.toRadians(dir + 180.0) // meteorological FROM → arrow points where it blows TO
        val dx = sin(th).toFloat()
        val dy = -cos(th).toFloat()
        fun px(u: Float, v: Float) = cx + u * dx - v * dy
        fun py(u: Float, v: Float) = arrowY + u * dy + v * dx
        arrows.moveTo(px(0.5f * al, 0f), py(0.5f * al, 0f))
        arrows.lineTo(px(-0.5f * al, 0.38f * al), py(-0.5f * al, 0.38f * al))
        arrows.lineTo(px(-0.22f * al, 0f), py(-0.22f * al, 0f))
        arrows.lineTo(px(-0.5f * al, -0.38f * al), py(-0.5f * al, -0.38f * al))
        arrows.close()
    }

    // ---- lane D
    val pressPath = curve(xs, yPress, n)
    val pressFill = curve(xs, yPress, n, fillTo = dBot)
    val pressBrush = Brush.verticalGradient(listOf(PressFillTop, PressFillBottom), dTop, dBot)

    // ---- lane E: cloud layers, one cell per hour centred on the hour
    val rowH = (eBot - eTop - 2f) / 3f
    val cellW = xs[1] - xs[0]

    // ---- nights (sunset → next sunrise), plus the night before the first sunrise
    val d = f.daily
    val nights = ArrayList<Float>()
    for (i in 0 until d.size) {
        val set = d.sunset[i]
        val rise = if (i + 1 < d.size) d.sunrise[i + 1] else set + 13 * HOUR
        if (set > 0 && rise > set) { nights += xOf(set).coerceIn(left, right); nights += xOf(rise).coerceIn(left, right) }
        if (i == 0 && d.sunrise[0] > 0) {
            nights += xOf(d.sunrise[0] - 11 * HOUR).coerceIn(left, right); nights += xOf(d.sunrise[0]).coerceIn(left, right)
        }
    }

    // ---- text
    val label = TextStyle(fontSize = capSp(10f), color = Pal.Sub)
    val laneLabel = TextStyle(fontSize = capSp(9f), color = Pal.Sub)
    val dayStyle = TextStyle(fontSize = capSp(10.5f), color = Pal.Silver, fontWeight = FontWeight.SemiBold)
    val extremeHi = TextStyle(fontSize = capSp(10f), color = Pal.Text, fontWeight = FontWeight.SemiBold)
    val extremeLo = TextStyle(fontSize = capSp(10f), color = Pal.Sub)

    // temperature grid
    val step = if (tHi - tLo > 12f) 4 else 2
    val gridY = ArrayList<Float>()
    val gridL = ArrayList<TextLayoutResult>()
    var v = tLo.toInt()
    while (v <= tHi.toInt()) {
        gridY += yT(v.toDouble())
        gridL += measurer.measure(Fmt.temp(v.toDouble()), label)
        v += step
    }

    // vertical grid: minor every 6 h (48 h) or 12 h (5 days); local midnight strong
    val minorEvery = if (five) 12 else 6
    val gridX = ArrayList<Float>()
    val gridXStrong = ArrayList<Boolean>()
    val xLabels = ArrayList<Pair<Float, TextLayoutResult>>()
    var t = Math.floorDiv(t0 + offMs, HOUR) * HOUR - offMs + HOUR
    while (t <= t1) {
        val lh = Math.floorMod(Math.floorDiv(t + offMs, HOUR), 24L).toInt()
        if (lh % minorEvery == 0) {
            val x = xOf(t)
            gridX += x
            gridXStrong += lh == 0
            if (lh != 0) {
                val lay = measurer.measure("%02d".format(lh), label)
                val lx = x - lay.size.width / 2f
                if (lx >= left - 4f && lx + lay.size.width <= right + 4f) xLabels += lx to lay
            }
        }
        t += HOUR
    }

    // day labels, centred on each day's visible span
    val dayLabels = ArrayList<Pair<Float, TextLayoutResult>>()
    val today = localMidnight(System.currentTimeMillis(), offMs)
    var m = localMidnight(t0, offMs)
    while (m < t1) {
        val s0 = xOf(maxOf(m, t0))
        val s1 = xOf(minOf(m + DAY, t1))
        val noon = m + 12 * HOUR
        val txt = if (m == today) "Today" else "${Fmt.weekday(noon, off)} ${Fmt.dayMonth(noon, off).substringBefore(' ')}"
        val lay = measurer.measure(txt, dayStyle)
        if (s1 - s0 >= lay.size.width + 6f) dayLabels += ((s0 + s1) / 2f - lay.size.width / 2f) to lay
        m += DAY
    }

    // daily max/min annotations on the temperature curve (true turning points only)
    val extremes = ArrayList<Pair<Offset, TextLayoutResult>>()
    val labelGap = 3.dp.toPx()
    m = localMidnight(t0, offMs)
    while (m < t1) {
        var hi = -1
        var lo = -1
        for (i in sa until sz) {
            if (h.t[i] < m || h.t[i] >= m + DAY) continue
            val tv = h.temp[i]
            if (tv.isNaN()) continue
            if (hi < 0 || tv > h.temp[hi]) hi = i
            if (lo < 0 || tv < h.temp[lo]) lo = i
        }
        for (pass in 0..1) {
            val idx = if (pass == 0) hi else lo
            val isHi = pass == 0
            if (idx <= sa || idx >= sz - 1) continue
            val tv = h.temp[idx]
            val prev = h.temp[idx - 1]
            val next = h.temp[idx + 1]
            val turning = if (isHi) tv >= prev && tv >= next else tv <= prev && tv <= next
            if (!turning) continue
            val lay = measurer.measure(Fmt.temp(tv), if (isHi) extremeHi else extremeLo)
            val x = (xs[idx - a] - lay.size.width / 2f).coerceIn(left, right - lay.size.width)
            val yy = if (isHi) yTemp[idx - a] - labelGap - lay.size.height else yTemp[idx - a] + labelGap
            if (yy < aTop || yy + lay.size.height > aBot) continue
            extremes += Offset(x, yy) to lay
        }
        m += DAY
    }

    // lane captions (left gutter) and scales (right gutter)
    val capMm = measurer.measure("mm", laneLabel)
    val capWind = measurer.measure("km/h", laneLabel)
    val capHpa = measurer.measure("hPa", laneLabel)
    val capCloud = measurer.measure("cloud", laneLabel)
    val rMaxLay = measurer.measure(Fmt.num(rScale), laneLabel.copy(color = RainMod))
    val popLay = if (anyPop) measurer.measure("100%", laneLabel.copy(color = PopLine)) else null
    val wMaxLay = measurer.measure(Fmt.num(wScale.toDouble()), laneLabel)
    val pHiLay = measurer.measure(pHi.roundToInt().toString(), laneLabel.copy(color = Pal.Silver))
    val pLoLay = measurer.measure(pLo.roundToInt().toString(), laneLabel.copy(color = Pal.Silver))
    val rowStyle = laneLabel.copy(fontSize = capSp(7.5f))
    val cloudRows = arrayOf(measurer.measure("H", rowStyle), measurer.measure("M", rowStyle), measurer.measure("L", rowStyle))

    // legend
    val legendItems = arrayOf("Temp", "Feels", "Dew", "RH").map { measurer.measure(it, label) }
    val sw = 12.dp.toPx()
    val legendX = FloatArray(legendItems.size)
    var lx = left
    for (i in legendItems.indices) {
        legendX[i] = lx
        lx += sw + 4.dp.toPx() + legendItems[i].size.width + 12.dp.toPx()
    }
    val rangeLay = measurer.measure("${Fmt.temp(tmin)} … ${Fmt.temp(tmax)}", label.copy(color = Pal.Text))
    val rangeX = size.width - rangeLay.size.width
    val showRange = rangeX >= lx

    val hair = 1.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    val dewStroke = Stroke(width = 1.5f.dp.toPx(), pathEffect = dash)
    val feelsStroke = Stroke(width = 1.dp.toPx())
    val tempStroke = Stroke(width = 2.dp.toPx())
    val pressStroke = Stroke(width = 1.5f.dp.toPx())
    val windStroke = Stroke(width = 1.4f.dp.toPx())
    val gustStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
    val popStroke = Stroke(width = 1.dp.toPx())
    val gp = 5.dp.toPx()
    val lanes = floatArrayOf(aTop, aBot, bTop, bBot, cTop, cBot, dTop, dBot, eTop, eBot)
    val legendStroke2 = 2.dp.toPx()
    val legendStroke15 = 1.5f.dp.toPx()
    val legendBox = 4.dp.toPx()
    val rhLegend = Pal.Humid.copy(alpha = 0.45f)

    val static: DrawScope.() -> Unit = {
        // nights across every lane
        for (i in 0 until nights.size / 2) {
            val x0 = nights[i * 2]
            val x1 = nights[i * 2 + 1]
            if (x1 > x0) drawRect(nightColor, Offset(x0, aTop), Size(x1 - x0, eBot - aTop))
        }
        // lane frames
        for (k in 0 until 5) {
            val t2 = lanes[k * 2]
            val b2 = lanes[k * 2 + 1]
            drawLine(LaneEdge, Offset(left, t2), Offset(left, b2), hair)
            drawLine(LaneEdge, Offset(right, t2), Offset(right, b2), hair)
            if (k == 1 || k == 2) drawLine(Pal.Grid, Offset(left, b2), Offset(right, b2), hair)
        }
        for (i in gridX.indices) {
            val x = gridX[i]
            drawLine(if (gridXStrong[i]) Pal.GridStrong else Pal.Grid, Offset(x, aTop), Offset(x, eBot), hair)
        }
        for ((x, lay) in xLabels) drawText(lay, topLeft = Offset(x, axisY))
        for ((x, lay) in dayLabels) drawText(lay, topLeft = Offset(x, daysY - lay.size.height / 2f))

        // A: temperature, feels-like, dew point, humidity wash
        for (i in gridY.indices) {
            val gy = gridY[i]
            drawLine(Pal.Grid, Offset(left, gy), Offset(right, gy), hair)
            val l = gridL[i]
            drawText(l, topLeft = Offset(left - gp - l.size.width, gy - l.size.height / 2f))
        }
        clipRect(left, aTop, right, aBot) {
            drawPath(humPath, humidColor)
            drawPath(fillPath, fillBrush)
            drawPath(dewPath, DewTeal, style = dewStroke)
            drawPath(feelsPath, FeelsColor, style = feelsStroke)
            drawPath(tempPath, Pal.Crimson, style = tempStroke)
        }
        for ((o, lay) in extremes) drawText(lay, topLeft = o)

        // B: precipitation
        clipRect(left, bTop, right, bBot) {
            for (k in 0 until n) {
                val top = barTop[k]
                if (top.isNaN()) continue
                drawRect(barColor[k]!!, Offset(barX0[k], top), Size(barX1[k] - barX0[k], bBot - top), alpha = barAlpha[k])
            }
            if (popPath != null) drawPath(popPath, PopLine, style = popStroke)
        }
        drawText(rMaxLay, topLeft = Offset(left - gp - rMaxLay.size.width, bTop - 2f))
        drawText(capMm, topLeft = Offset(left - gp - capMm.size.width, bBot - capMm.size.height))
        if (popLay != null) drawText(popLay, topLeft = Offset(right + gp, bTop - 2f))

        // C: wind and gusts
        clipRect(left, cTop, right, cBot) {
            drawPath(gustBand, GustBand)
            drawPath(gustPath, GustLine, style = gustStroke)
            drawPath(windPath, WindLine, style = windStroke)
        }
        drawPath(arrows, ArrowColor)
        drawText(capWind, topLeft = Offset(left - gp - capWind.size.width + 3.dp.toPx(), cBot - capWind.size.height))
        drawText(wMaxLay, topLeft = Offset(right + gp, wTop - wMaxLay.size.height / 2f))

        // D: pressure
        clipRect(left, dTop, right, dBot) {
            drawPath(pressFill, pressBrush)
            drawPath(pressPath, Pal.Silver, style = pressStroke)
        }
        drawText(capHpa, topLeft = Offset(left - gp - capHpa.size.width, (dTop + dBot) / 2f - capHpa.size.height / 2f))
        drawText(pHiLay, topLeft = Offset(right + gp, dTop))
        drawText(pLoLay, topLeft = Offset(right + gp, dBot - pLoLay.size.height))

        // E: cloud layers, high on top
        for (k in 0 until n) {
            val i = a + k
            val x0 = maxOf(left, xs[k] - cellW / 2f)
            val x1 = minOf(right, xs[k] + cellW / 2f)
            if (x1 <= x0) continue
            for (r in 0 until 3) {
                val c = when (r) { 0 -> h.cloudHigh[i]; 1 -> h.cloudMid[i]; else -> h.cloudLow[i] }
                if (c.isNaN() || c < 3.0) continue
                drawRect(CloudColor, Offset(x0, eTop + r * (rowH + 1f)), Size(x1 - x0, rowH), alpha = (c / 100.0).toFloat() * 0.85f)
            }
        }
        drawText(capCloud, topLeft = Offset(left - gp - capCloud.size.width + 3.dp.toPx(), (eTop + eBot) / 2f - capCloud.size.height / 2f))
        for (r in 0 until 3) {
            val lay = cloudRows[r]
            drawText(lay, topLeft = Offset(right + gp, eTop + r * (rowH + 1f) + rowH / 2f - lay.size.height / 2f))
        }

        // legend
        for (i in legendItems.indices) {
            val x = legendX[i]
            val c = Offset(x, legendY)
            when (i) {
                0 -> drawLine(Pal.Crimson, c, Offset(x + sw, legendY), legendStroke2)
                1 -> drawLine(FeelsColor, c, Offset(x + sw, legendY), hair)
                2 -> drawLine(DewTeal, c, Offset(x + sw, legendY), legendStroke15, pathEffect = dash)
                else -> drawRect(rhLegend, Offset(x, legendY - legendBox), Size(sw, legendBox * 2))
            }
            val l = legendItems[i]
            drawText(l, topLeft = Offset(x + sw + 4.dp.toPx(), legendY - l.size.height / 2f))
        }
        if (showRange) drawText(rangeLay, topLeft = Offset(rangeX, legendY - rangeLay.size.height / 2f))
    }

    val panelBrush = Brush.verticalGradient(listOf(PanelTop, PanelBottom), aTop, aBot)
    return Gram(
        a, z, sa, sz, left, right, t0, t1, aTop, aBot, tTop, tLo, tHi,
        bBot, dTop, eBot,
        xs, yTemp, yDew, yFeels, yWind, yPress, barTop, panelBrush, static,
    )
}

/** "Now": a line through every lane, the glowing dot on temperature, the 3-h pressure tendency. */
private fun CacheDrawScope.nowMark(g: Gram, f: Forecast, now: Long, measurer: TextMeasurer): DrawScope.() -> Unit {
    if (now < g.t0 || now > g.t1) return {}
    val h = f.hourly
    val nx = g.xOf(now)
    val tv = Meteo.at(h.t, h.temp, now)
    val ny = if (tv == null) Float.NaN else g.yT(tv)
    val glowR = 9.dp.toPx()
    val glow = if (ny.isNaN()) null else Brush.radialGradient(
        listOf(Pal.Crimson.copy(alpha = 0.6f), Pal.Crimson.copy(alpha = 0f)), center = Offset(nx, ny), radius = glowR,
    )
    val p = Meteo.at(h.t, h.pressure, now)
    val p3 = Meteo.at(h.t, h.pressure, now - 3 * HOUR)
    val tendLay = if (p != null && p3 != null) {
        val dp = p - p3
        measurer.measure(
            "${Meteo.tendency(dp).arrow} ${Meteo.signed(dp)}",
            TextStyle(fontSize = capSp(9.5f), color = Pal.Silver, fontWeight = FontWeight.SemiBold),
        )
    } else null
    val tendX = if (tendLay == null) 0f else {
        val x = nx + 4.dp.toPx()
        if (x + tendLay.size.width > g.right) nx - 4.dp.toPx() - tendLay.size.width else x
    }
    val hair = 1.dp.toPx()
    val dot = 3.dp.toPx()
    return {
        drawLine(NowLine, Offset(nx, g.aTop), Offset(nx, g.eBot), hair)
        if (glow != null) {
            drawCircle(glow, glowR, Offset(nx, ny))
            drawCircle(Pal.Text, dot, Offset(nx, ny))
        }
        if (tendLay != null) drawText(tendLay, topLeft = Offset(tendX, g.dTop + 1f))
    }
}

// ---- scrub -------------------------------------------------------------------------------------

private fun readoutText(f: Forecast, i: Int): AnnotatedString {
    val h = f.hourly
    val off = f.offsetSec
    val t = h.t[i]
    val dir = h.windDir[i]
    val rain = h.precip[i]
    val pop = h.pop[i]
    val feels = h.feels[i]
    return buildAnnotatedString {
        withStyle(SpanStyle(color = Pal.Text, fontWeight = FontWeight.SemiBold)) {
            append(Fmt.weekday(t, off)); append(" "); append(Fmt.time(t, off))
        }
        append("\n")
        withStyle(SpanStyle(color = CrimsonHi, fontWeight = FontWeight.SemiBold)) { append(Fmt.temp(h.temp[i])) }
        if (!feels.isNaN()) {
            withStyle(SpanStyle(color = Pal.Sub)) { append("  feels ") }
            append(Fmt.temp(feels))
        }
        append("\n")
        withStyle(SpanStyle(color = Pal.Sub)) { append("dew ") }
        withStyle(SpanStyle(color = DewTeal)) { append(Fmt.temp(h.dew[i])) }
        withStyle(SpanStyle(color = Pal.Sub)) { append("  RH ") }
        append(Fmt.num(h.rh[i])); append("%")
        append("\n")
        withStyle(SpanStyle(color = RainMod)) {
            if (rain.isNaN() || rain < 0.05) append("dry") else { append(Fmt.num(rain, 1)); append(" mm") }
            if (!pop.isNaN()) { append(" · "); append(Fmt.num(pop)); append("%") }
        }
        withStyle(SpanStyle(color = Pal.Sub)) { append("  past hour") }
        append("\n")
        withStyle(SpanStyle(color = Pal.Sub)) { append("wind ") }
        append(Fmt.num(h.wind[i]))
        if (!h.gust[i].isNaN()) {
            withStyle(SpanStyle(color = Pal.Sub)) { append(" gust ") }
            append(Fmt.num(h.gust[i]))
        }
        append(" km/h")
        if (!dir.isNaN()) { append(" "); append(Meteo.compass(dir)) }
        append("\n")
        withStyle(SpanStyle(color = Pal.Silver)) { append(Fmt.num(h.pressure[i], 1)); append(" hPa") }
        withStyle(SpanStyle(color = Pal.Sub)) { append("  cloud ") }
        append(Fmt.num(h.cloud[i])); append("%")
    }
}

private fun DrawScope.drawScrub(g: Gram, i: Int, f: Forecast, measurer: TextMeasurer) {
    val k = i - g.a
    val x = g.xs[k]
    // measured once per hour, then reused for the life of this Gram
    val lay = g.readouts[k] ?: measurer.measure(
        readoutText(f, i),
        TextStyle(fontSize = capSp(12f), color = Pal.Text, lineHeight = capSp(15.5f)),
    ).also { g.readouts[k] = it }

    drawLine(ScrubLine, Offset(x, g.aTop), Offset(x, g.eBot), 1.dp.toPx())
    val r = 3.5f.dp.toPx()
    // outline the rain bar for the hour ending here
    val bt = g.barTop[k]
    if (!bt.isNaN()) {
        val x0 = g.xOf(f.hourly.t[i] - HOUR)
        drawRect(RainHeavy, Offset(x0, bt), Size(x - x0, g.bBot - bt), style = Stroke(1.dp.toPx()))
    }
    if (!g.yWind[k].isNaN()) drawCircle(WindLine, r * 0.8f, Offset(x, g.yWind[k]))
    if (!g.yPress[k].isNaN()) drawCircle(Pal.Silver, r * 0.8f, Offset(x, g.yPress[k]))
    if (!g.yDew[k].isNaN()) drawCircle(DewTeal, r * 0.8f, Offset(x, g.yDew[k]))
    if (!g.yFeels[k].isNaN()) drawCircle(FeelsColor, r * 0.6f, Offset(x, g.yFeels[k]))
    if (!g.yTemp[k].isNaN()) {
        val c = Offset(x, g.yTemp[k])
        drawCircle(TempHalo, r * 2.4f, c)
        drawCircle(Pal.Crimson, r, c)
        drawCircle(Pal.Text, r * 0.45f, c)
    }

    val pad = 8.dp.toPx()
    val bw = lay.size.width + pad * 2
    val bh = lay.size.height + pad * 1.5f
    val gapX = 12.dp.toPx()
    var bx = x + gapX
    if (bx + bw > size.width) bx = x - gapX - bw
    bx = bx.coerceAtLeast(0f)
    val by = g.aTop + 2.dp.toPx()
    val corner = CornerRadius(9.dp.toPx())
    drawRoundRect(g.panelBrush, Offset(bx, by), Size(bw, bh), corner)
    drawRoundRect(PanelEdge, Offset(bx, by), Size(bw, bh), corner, style = Stroke(1f))
    drawRect(Pal.Crimson, Offset(bx + 1f, by + corner.x * 0.4f), Size(2.dp.toPx(), bh - corner.x * 0.8f))
    drawText(lay, topLeft = Offset(bx + pad, by + pad * 0.75f))
}

// ---- TalkBack summary --------------------------------------------------------------------------

private fun summary24(f: Forecast, now: Long): String {
    val h = f.hourly
    val off = f.offsetSec
    if (h.size < 2) return "Meteogram. No hourly data."
    val s = h.indexAt(now)
    val e = minOf(h.size, s + 25)
    var hi = -1
    var lo = -1
    var rain = 0.0
    var wetFrom = -1
    var wetTo = -1
    var popMax = 0.0
    var wind = 0.0
    var gust = 0.0
    var p0 = Double.NaN
    var p1 = Double.NaN
    for (i in s until e) {
        val tv = h.temp[i]
        if (!tv.isNaN()) {
            if (hi < 0 || tv > h.temp[hi]) hi = i
            if (lo < 0 || tv < h.temp[lo]) lo = i
        }
        if (i > s) {
            val mm = h.precip[i]
            if (!mm.isNaN()) {
                rain += mm
                if (mm >= 0.1) { if (wetFrom < 0) wetFrom = i; wetTo = i }
            }
            if (!h.pop[i].isNaN()) popMax = maxOf(popMax, h.pop[i])
        }
        if (!h.wind[i].isNaN()) wind = maxOf(wind, h.wind[i])
        if (!h.gust[i].isNaN()) gust = maxOf(gust, h.gust[i])
        val p = h.pressure[i]
        if (!p.isNaN()) { if (p0.isNaN()) p0 = p; p1 = p }
    }
    fun at(i: Int) = "${Fmt.weekday(h.t[i], off)} ${Fmt.time(h.t[i], off)}"
    return buildString {
        append("Meteogram, next 24 hours. ")
        if (hi >= 0) append("High ${Fmt.temp(h.temp[hi])} at ${at(hi)}, low ${Fmt.temp(h.temp[lo])} at ${at(lo)}. ")
        if (wetFrom < 0) {
            append("No rain expected")
            if (popMax >= 20) append(", chance up to ${Fmt.num(popMax)} percent")
            append(". ")
        } else {
            append("Rain ${Fmt.num(rain, 1)} millimetres, from ${Fmt.time(h.t[wetFrom] - HOUR, off)} to ${Fmt.time(h.t[wetTo], off)}")
            if (popMax > 0) append(", chance up to ${Fmt.num(popMax)} percent")
            append(". ")
        }
        append("Wind up to ${Fmt.num(wind)} kilometres per hour")
        if (gust > wind) append(", gusts ${Fmt.num(gust)}")
        append(". ")
        if (!p0.isNaN()) {
            val dp = p1 - p0
            val word = when {
                abs(dp) < 1.0 -> "steady"
                dp > 0 -> "rising"
                else -> "falling"
            }
            append("Pressure $word, ${Fmt.num(p0)} to ${Fmt.num(p1)} hectopascals. ")
        }
        append("Use the custom actions to step through the hours.")
    }
}
