package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.weatherglass.data.Air
import com.github.sarwaruddin.weatherglass.data.Bundle
import com.github.sarwaruddin.weatherglass.data.Daily
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import com.github.sarwaruddin.weatherglass.data.Place
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// =================================================================================
// Hero: live sky behind the current conditions
// =================================================================================

private val heroShadow = Shadow(Color(0x99000000), Offset(0f, 2f), 14f)

/** Amber for "this data is getting old". Local so Theme.kt stays the brand engineer's. */
internal val StaleAmber = Color(0xFFF0A040)

/** "12 min ago", "3 h ago". */
internal fun ageText(ms: Long): String {
    val m = (ms / 60_000L).coerceAtLeast(0L)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / 1440} d ago"
    }
}

/** The phone's own UTC offset (seconds), for "Updated hh:mm", which is about the phone, not the place. */
internal fun phoneOffsetSec(ms: Long): Int = java.util.TimeZone.getDefault().getOffset(ms) / 1000

internal const val STALE_MS = 3_600_000L

/** AQI band, or null when there is no usable reading (aqiCat can't take NaN). */
internal fun aqiBand(aqi: Double?): Meteo.Band? =
    if (aqi == null || aqi.isNaN() || aqi < 0) null else Meteo.aqiCat(aqi)

/**
 * Live sky behind the current conditions.
 *
 * [active] gates the motion clock: the caller turns it off when the hero is scrolled under the
 * header, another tab is showing, or the system asks for reduced motion. [topPad] is the height of
 * the sticky header that floats over the sky. [scrollPx] is how far the hero has scrolled up; it
 * only drives graphicsLayer (parallax and fade), so scrolling never recomposes the hero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Hero(
    b: Bundle,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    topPad: Dp = 0.dp,
    offline: Boolean = false,
    onAqi: () -> Unit = {},
    scrollPx: () -> Float = { 0f },
) {
    val f = b.forecast
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val minute = now / 60_000L
    val scene = remember(b.place, f, minute) { skyScene(b.place, f, minute * 60_000L) }
    // The air note is left out here: the AQI has its own chip right next to the summary.
    val line = remember(f, minute / 5) { if (f == null) "" else summary(f, null, now) }
    val brief = remember(f, minute) { f?.let { nowcastBrief(it, now) } }
    val aqi = b.air?.now?.aqi
    val aqiBand = remember(aqi) { aqiBand(aqi) }

    // One shared clock for every ambient motion; ticks ~30 fps for precipitation, ~15 fps otherwise.
    // It resumes where it stopped, so pausing (collapsed hero, other tab) never makes the sky jump.
    val clock = remember { mutableFloatStateOf(0f) }
    if (scene.animated && active) {
        val frameMs = if (scene.fast) 30L else 62L
        LaunchedEffect(frameMs) {
            val base = clock.floatValue
            val start = withFrameNanos { it }
            while (true) {
                val n = withFrameNanos { it }
                clock.floatValue = ((base + (n - start) / 1_000_000_000.0) % 7200.0).toFloat()
                delay(frameMs)
            }
        }
    }

    Box(modifier.fillMaxWidth().heightIn(min = 340.dp).clipToBounds()) {
        Box(Modifier.matchParentSize().graphicsLayer { translationY = scrollPx() * 0.5f }) {
            SkyBackdrop(scene, Modifier.fillMaxSize())
            SkyMotion(scene, clock, Modifier.fillMaxSize())
        }
        SkyScrim(Modifier.matchParentSize())

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = topPad + 12.dp, bottom = 16.dp)
                .graphicsLayer {
                    // Fade the readout as it slides under the header; the compact strip takes over.
                    val fade = size.height * 0.75f
                    alpha = if (fade <= 0f) 1f else (1f - scrollPx() / fade).coerceIn(0f, 1f)
                },
        ) {
            if (f == null) {
                HeroShimmer()
            } else {
                val c = f.current
                val di = dayIndexOf(f.daily, now, f.offsetSec)
                val hi = if (di >= 0) f.daily.max[di] else Double.NaN
                val lo = if (di >= 0) f.daily.min[di] else Double.NaN
                val cond = Meteo.wmo(c.code).text
                val desc = remember(c, hi, lo, aqiBand, cond) {
                    buildString {
                        append("Now ${Fmt.temp(c.temp)}, $cond. Feels like ${Fmt.temp(c.feels)}. ")
                        append("High ${Fmt.temp(hi)}, low ${Fmt.temp(lo)}.")
                        if (aqiBand != null && aqi != null) append(" Air quality index ${aqi.roundToInt()}, ${aqiBand.name}.")
                    }
                }
                // Hero numeral and condition are capped at 1.15× font scale; body lines scale freely.
                val cap = minOf(1f, 1.15f / LocalDensity.current.fontScale)
                Column(Modifier.clearAndSetSemantics { contentDescription = desc; heading() }) {
                    Text(Fmt.temp(c.temp), style = Type.Huge.copy(fontSize = 92.sp * cap, shadow = heroShadow), maxLines = 1)
                    Text(cond, style = Type.Big.copy(fontSize = 20.sp * cap, shadow = heroShadow))
                    Text(
                        "Feels ${Fmt.temp(c.feels)}  ·  H ${Fmt.temp(hi)}  L ${Fmt.temp(lo)}",
                        style = Type.Body.copy(color = Pal.Silver, shadow = heroShadow),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (brief != null && brief.available && !brief.wet) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier.size(7.dp).drawBehind {
                                drawCircle(Pal.Rain, size.minDimension / 2, style = Stroke(1.2.dp.toPx()))
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(brief.headline, style = Type.Small.copy(color = Pal.Silver, shadow = heroShadow))
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (line.isNotEmpty()) SummaryChip(line, Modifier.align(Alignment.CenterVertically))
                    if (aqiBand != null && aqi != null) AqiChip(aqi, aqiBand, onAqi, Modifier.align(Alignment.CenterVertically))
                }
                UpdatedLine(f.fetched, now, offline, Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** "Updated 22:05 · 12 min ago"; amber once the data is over an hour old. */
@Composable
internal fun UpdatedLine(fetched: Long, now: Long, offline: Boolean, modifier: Modifier = Modifier) {
    val age = now - fetched
    val stale = age > STALE_MS
    val text = buildString {
        append("Updated ").append(Fmt.time(fetched, phoneOffsetSec(fetched))).append(" · ").append(ageText(age))
        if (offline) append(" · offline")
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (stale) {
            Spacer(Modifier.size(6.dp).drawBehind { drawCircle(StaleAmber) })
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Type.Small.copy(color = if (stale) StaleAmber else Pal.Sub, shadow = heroShadow))
    }
}

private val chipShape = RoundedCornerShape(50)
private val chipFill = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x22000000), Color(0x55000000)))
private val chipEdge = Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x14FFFFFF)))

@Composable
private fun SummaryChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(chipFill, chipShape)
            .border(1.dp, chipEdge, chipShape)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier.size(8.dp).drawBehind {
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8090), Pal.Crimson, Pal.CrimsonDeep), center = Offset(r * 0.7f, r * 0.7f), radius = r * 1.4f), r)
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.Body.copy(fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "● AQI 135 · Unhealthy for sensitive groups ›" in the band colour; opens the Air tab. */
@Composable
private fun AqiChip(aqi: Double, band: Meteo.Band, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = band(band.color)
    // Short label on the chip (the full band name goes to TalkBack and the Air tab).
    val short = if (band.name == "Unhealthy for sensitive groups") "Unhealthy for some" else band.name
    val label = "AQI ${aqi.roundToInt()} · $short"
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(chipShape)
            .background(c.copy(alpha = 0.20f))
            .border(1.dp, c.copy(alpha = 0.75f), chipShape)
            .clickable(onClickLabel = "Open air quality", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Air quality index ${aqi.roundToInt()}, ${band.name}" }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(8.dp).drawBehind { drawCircle(c) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = Type.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("  ›", style = Type.Body.copy(fontSize = 14.sp, color = Pal.Sub))
    }
}

@Composable
private fun HeroShimmer() {
    val tr = rememberInfiniteTransition(label = "heroShimmer")
    val a = tr.animateFloat(0.08f, 0.24f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(Modifier.semantics { contentDescription = "Loading the forecast" }) {
        // Alpha is read in the draw phase only: the pulse redraws, it never recomposes.
        @Composable
        fun block(w: Int, h: Int, top: Int) {
            Spacer(
                Modifier.padding(top = top.dp).width(w.dp).height(h.dp).drawBehind {
                    drawRoundRect(Color.White.copy(alpha = a.value), cornerRadius = CornerRadius(8.dp.toPx()))
                },
            )
        }
        block(150, 80, 0)
        block(120, 20, 12)
        block(200, 16, 10)
        block(230, 30, 14)
    }
}

// =================================================================================
// Smart one-liner
// =================================================================================

/** One-line plain-language summary of what matters right now. Pure (pass `now` for tests). */
fun summary(f: Forecast, air: Air?, now: Long = System.currentTimeMillis()): String {
    val h = f.hourly
    val off = f.offsetSec
    val c = f.current
    val skyNow = Meteo.wmo(c.code).sky
    val wetSky = skyNow == Meteo.Sky.DRIZZLE || skyNow == Meteo.Sky.RAIN || skyNow == Meteo.Sky.HEAVY_RAIN
    var main: String? = null

    val n = h.size
    val i0 = if (n > 0) h.indexAt(now) else 0
    val end = minOf(n, i0 + 13)
    fun wet(i: Int): Boolean = h.precip[i] >= 0.2 && !(h.pop[i] < 40.0)

    // nowcast, 15-minute slots
    val nc = f.nowcast
    var ncFirstWet = -1
    var ncNowWet = false
    for (k in nc.t.indices) {
        if (nc.t[k] < now - 900_000L) continue
        val v = nc.precip[k]
        if (v >= 0.05) {
            if (ncFirstWet < 0) ncFirstWet = k
            if (nc.t[k] <= now) ncNowWet = true
        }
    }
    val rainingNow = ncNowWet || (c.precip > 0.05 && skyNow != Meteo.Sky.SNOW) || (wetSky && c.precip > 0)

    // 1. storms
    if (skyNow == Meteo.Sky.STORM) {
        main = "Thunderstorm overhead"
    } else {
        for (i in i0 until minOf(n, i0 + 9)) {
            if (Meteo.wmo(h.code[i]).sky == Meteo.Sky.STORM) {
                main = if (i == i0) "Thunderstorms nearby" else "Thunderstorms likely around ${Fmt.time(h.t[i], off)}"
                break
            }
        }
    }
    // 2. rain now / soon
    if (main == null && n > 0) {
        if (rainingNow) {
            var stop = -1
            for (i in i0 + 1 until end) if (!wet(i)) { stop = i; break }
            main = if (stop > 0) "Rain easing by ${Fmt.time(h.t[stop], off)}" else "Rain set in for the next 12 hours"
        } else if (ncFirstWet >= 0) {
            main = "Rain starting around ${Fmt.time(nc.t[ncFirstWet], off)}"
        } else {
            var likely = -1
            var chance = -1
            for (i in i0 until end) {
                if (likely < 0 && wet(i)) likely = i
                if (chance < 0 && h.pop[i] >= 30.0 && h.precip[i] > 0.0) chance = i
            }
            if (likely >= 0) main = "Rain likely after ${Fmt.time(h.t[likely], off)}"
            else if (chance >= 0) main = "Chance of showers after ${Fmt.time(h.t[chance], off)}"
            else if (skyNow == Meteo.Sky.SNOW) main = "Snow falling"
        }
    }
    // 3. heat
    if (main == null && !c.feels.isNaN() && c.feels >= 40.0) main = "Dangerous heat, feels like ${Fmt.temp(c.feels)}"
    // 4. wind
    if (main == null && n > 0) {
        var g = 0.0
        var gi = -1
        for (i in i0 until end) if (!h.gust[i].isNaN() && h.gust[i] > g) { g = h.gust[i]; gi = i }
        if (g >= 50.0 && gi >= 0) main = "Gusts to ${g.roundToInt()} km/h around ${Fmt.time(h.t[gi], off)}"
    }
    // 5. remarkable days
    val d = f.daily
    val di = dayIndexOf(d, now, off)
    if (main == null && di >= 0 && !d.max[di].isNaN()) {
        val m = d.max[di]
        var back = 0
        var j = di - 1
        while (j >= 0 && !d.max[j].isNaN() && d.max[j] < m) { back++; j-- }
        var fwd = 0
        j = di + 1
        while (j < d.size && !d.max[j].isNaN() && d.max[j] < m) { fwd++; j++ }
        var coolBack = 0
        j = di - 1
        while (j >= 0 && !d.max[j].isNaN() && d.max[j] > m) { coolBack++; j-- }
        val tomorrow = if (di + 1 < d.size) d.max[di + 1] - m else Double.NaN
        main = when {
            back >= 4 -> "Hottest day in ${back + 1} days"
            fwd >= 5 && di + fwd == d.size - 1 -> "Warmest day of the week ahead"
            coolBack >= 4 -> "Coolest day in ${coolBack + 1} days"
            !tomorrow.isNaN() && tomorrow >= 4 -> "Much warmer tomorrow"
            !tomorrow.isNaN() && tomorrow <= -4 -> "Much cooler tomorrow"
            else -> null
        }
    }
    // 6. the sky itself
    if (main == null) {
        main = when (skyNow) {
            Meteo.Sky.CLEAR -> if (c.isDay) "Clear skies, dry" else "Clear night"
            Meteo.Sky.FEW -> if (c.isDay) "Mostly sunny, dry" else "Mostly clear night"
            Meteo.Sky.PARTLY -> "Partly cloudy, dry for now"
            Meteo.Sky.OVERCAST -> "Grey and dry"
            Meteo.Sky.FOG -> if (!c.vis.isNaN()) "Fog, visibility ${Fmt.num(c.vis / 1000.0, 1)} km" else "Foggy"
            else -> "Dry for the next 12 hours"
        }
    }
    val aqi = air?.now?.aqi
    val airNote = when {
        aqi == null || aqi.isNaN() || aqi <= 100 -> null
        aqi <= 150 -> "AQI unhealthy for some"
        aqi <= 200 -> "AQI unhealthy"
        aqi <= 300 -> "AQI very unhealthy"
        else -> "AQI hazardous"
    }
    val text = main ?: "—"
    return if (airNote != null) "$text, $airNote" else text
}

/** Index of today's entry in the daily series (place's local day), or -1. */
private fun dayIndexOf(d: Daily, now: Long, off: Int): Int {
    if (d.size == 0) return -1
    val offMs = off * 1000L
    val start = Math.floorDiv(now + offMs, 86_400_000L) * 86_400_000L - offMs
    var best = -1
    var bd = Long.MAX_VALUE
    for (i in 0 until d.size) {
        val dd = abs(d.t[i] - start)
        if (dd < bd) { bd = dd; best = i }
    }
    return if (bd < 43_200_000L) best else -1
}

// =================================================================================
// Scene model
// =================================================================================

@Immutable
private data class SkyScene(
    val alt: Float,                 // sun altitude, degrees
    val cloud: Float, val low: Float, val mid: Float, val high: Float, // 0..1
    val wind: Float,                // km/h
    val sky: Meteo.Sky,
    val precip: Float,              // mm/h
    val sunX: Float, val sunY: Float, // fractions of the box
    val moonUp: Boolean, val moonX: Float, val moonY: Float,
    val moonLit: Float, val litLeft: Boolean,
) {
    val rainy get() = sky == Meteo.Sky.DRIZZLE || sky == Meteo.Sky.RAIN || sky == Meteo.Sky.HEAVY_RAIN || sky == Meteo.Sky.STORM
    val fast get() = rainy || sky == Meteo.Sky.SNOW
    val starAlpha get() = (((-alt - 4f) / 10f).coerceIn(0f, 1f)) * (1f - cloud).coerceIn(0f, 1f)
    val animated get() = fast || sky == Meteo.Sky.FOG || starAlpha > 0.05f || low + mid + high > 0.03f
}

private fun pct(v: Double?, fallback: Float): Float =
    if (v == null || v.isNaN()) fallback else (v / 100.0).toFloat().coerceIn(0f, 1f)

private fun skyScene(place: Place, f: Forecast?, now: Long): SkyScene {
    val alt = Meteo.sunAltitude(now, place.lat, place.lng).toFloat()
    val solarHour = ((now / 3_600_000.0 + place.lng / 15.0) % 24.0 + 24.0) % 24.0
    var dayFrac = (solarHour - 6.0) / 12.0
    var cloud = 0.25f
    var low = Float.NaN
    var mid = Float.NaN
    var high = Float.NaN
    var wind = 8f
    var sky = Meteo.Sky.FEW
    var precip = 0f
    if (f != null) {
        val c = f.current
        sky = Meteo.wmo(c.code).sky
        cloud = pct(c.cloud, cloud)
        val h = f.hourly
        if (h.size > 0) {
            low = pct(Meteo.at(h.t, h.cloudLow, now), Float.NaN)
            mid = pct(Meteo.at(h.t, h.cloudMid, now), Float.NaN)
            high = pct(Meteo.at(h.t, h.cloudHigh, now), Float.NaN)
        }
        if (!c.wind.isNaN()) wind = c.wind.toFloat()
        var p = if (c.precip.isNaN()) 0.0 else c.precip
        val nc = f.nowcast
        for (k in nc.t.indices) {
            if (nc.t[k] >= now - 900_000L) { if (!nc.precip[k].isNaN()) p = maxOf(p, nc.precip[k] * 4.0); break }
        }
        precip = p.toFloat()
        val di = dayIndexOf(f.daily, now, f.offsetSec)
        if (di >= 0) {
            val r = f.daily.sunrise[di]
            val s = f.daily.sunset[di]
            if (r > 0 && s > r) dayFrac = (now - r).toDouble() / (s - r)
        }
    }
    if (low.isNaN()) low = cloud * 0.6f
    if (mid.isNaN()) mid = cloud * 0.4f
    if (high.isNaN()) high = cloud * 0.3f
    // Precipitating but the model says "few clouds"? The sky must look like it can rain.
    if (sky == Meteo.Sky.RAIN || sky == Meteo.Sky.HEAVY_RAIN || sky == Meteo.Sky.STORM) low = maxOf(low, 0.75f)
    if (sky == Meteo.Sky.OVERCAST) cloud = maxOf(cloud, 0.85f)

    val sunX = (0.12 + 0.76 * dayFrac.coerceIn(-0.08, 1.08)).toFloat()
    val sunY = 0.80f - 0.62f * (alt.coerceIn(-12f, 75f) / 75f)

    // The moon trails the sun by its age: transit ≈ 12h + age/synodic·24h local solar time.
    val m = Meteo.moon(now)
    val transit = 12.0 + m.age / Meteo.SYNODIC * 24.0
    val mh = ((solarHour - (transit - 6.0)) % 24.0 + 24.0) % 24.0
    val mf = mh / 12.0
    val moonUp = mf <= 1.0 && alt < 3f && m.illumination > 0.03
    val waxing = m.age < Meteo.SYNODIC / 2
    return SkyScene(
        alt = alt, cloud = cloud, low = low, mid = mid, high = high, wind = wind, sky = sky, precip = precip,
        sunX = sunX, sunY = sunY,
        moonUp = moonUp, moonX = (0.12 + 0.76 * mf.coerceIn(0.0, 1.0)).toFloat(),
        moonY = (0.80 - 0.56 * sin(PI * mf.coerceIn(0.0, 1.0))).toFloat(),
        moonLit = m.illumination.toFloat(),
        litLeft = waxing == (place.lat < 0), // waxing is lit on the right in the north
    )
}

// ---- sky colour by solar altitude ----------------------------------------------

private val ALT_STOPS = floatArrayOf(-18f, -12f, -6f, -2f, 2f, 8f, 20f)
private val TOP_STOPS = longArrayOf(0xFF020308, 0xFF04061A, 0xFF0A0D2A, 0xFF141A3A, 0xFF1C2A4A, 0xFF22385A, 0xFF1E3A5E)
private val MID_STOPS = longArrayOf(0xFF04060E, 0xFF080A22, 0xFF1A1236, 0xFF3A1838, 0xFF3E3A58, 0xFF3E5A7A, 0xFF3E6688)
private val HOR_STOPS = longArrayOf(0xFF060812, 0xFF120C26, 0xFF4A1026, 0xFFA0202E, 0xFFC0503A, 0xFF8A7A78, 0xFF6A8AA6)

private fun stopColor(arr: LongArray, alt: Float): Color {
    if (alt <= ALT_STOPS[0]) return Color(arr[0])
    for (i in 1 until ALT_STOPS.size) {
        if (alt <= ALT_STOPS[i]) {
            val k = (alt - ALT_STOPS[i - 1]) / (ALT_STOPS[i] - ALT_STOPS[i - 1])
            return lerp(Color(arr[i - 1]), Color(arr[i]), k)
        }
    }
    return Color(arr[arr.size - 1])
}

private fun dayK(alt: Float) = ((alt + 6f) / 14f).coerceIn(0f, 1f)
private fun twilight(alt: Float): Float { val z = (alt + 2.5f) / 5f; return exp(-(z * z)) }

private fun gloom(s: SkyScene): Float {
    var k = s.cloud.pow(1.3f) * 0.7f
    k += when (s.sky) {
        Meteo.Sky.STORM -> 0.3f
        Meteo.Sky.HEAVY_RAIN -> 0.25f
        Meteo.Sky.RAIN -> 0.15f
        Meteo.Sky.FOG -> 0.2f
        else -> 0f
    }
    return k.coerceIn(0f, 0.92f)
}

// =================================================================================
// Static layer: gradient, horizon glow, stratus deck
// =================================================================================

@Composable
private fun SkyBackdrop(s: SkyScene, modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val g = gloom(s)
            val dk = dayK(s.alt)
            val grey = lerp(Color(0xFF08090C), Color(0xFF34383F), dk)
            val top = lerp(stopColor(TOP_STOPS, s.alt), grey, g)
            val mid = lerp(stopColor(MID_STOPS, s.alt), grey, g)
            val hor = lerp(stopColor(HOR_STOPS, s.alt), lerp(grey, Color(0xFF4A4E56), dk * 0.5f), g * 0.85f)
            val bg = Brush.verticalGradient(listOf(top, mid, hor), startY = 0f, endY = h * 0.86f)
            val tw = twilight(s.alt) * (1f - s.cloud * 0.6f)
            val gc = Offset(s.sunX.coerceIn(0.05f, 0.95f) * w, h * 0.80f)
            val gr = w * 0.9f
            val glow = Brush.radialGradient(
                listOf(Pal.Crimson.copy(alpha = 0.55f * tw), Color(0xFFFF8A3A).copy(alpha = 0.16f * tw), Color.Transparent),
                center = gc, radius = gr,
            )
            val deckA = ((s.cloud - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val deckC = lerp(Color(0xFF14161C), Color(0xFF5A5F68), dk)
            val deck = Brush.verticalGradient(
                listOf(deckC.copy(alpha = 0.85f * deckA), deckC.copy(alpha = 0.55f * deckA), Color.Transparent),
                startY = 0f, endY = h * 0.75f,
            )
            onDrawBehind {
                drawRect(bg)
                if (tw > 0.01f) withTransform({ scale(1f, 0.45f, gc) }) { drawCircle(glow, gr, gc) }
                if (deckA > 0.01f) drawRect(deck)
            }
        },
    )
}

@Composable
private fun SkyScrim(modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val b = Brush.verticalGradient(
                0f to Color(0x66000000),
                0.22f to Color.Transparent,
                0.55f to Color.Transparent,
                0.80f to Pal.Bg.copy(alpha = 0.6f),
                1f to Pal.Bg,
            )
            onDrawBehind { drawRect(b) }
        },
    )
}

// =================================================================================
// Animated layer: stars, sun/moon, clouds, precipitation, lightning, fog
// =================================================================================

private const val LOW_MAX = 5
private const val MID_MAX = 4
private const val HIGH_MAX = 4

private fun hash01(i: Int): Float {
    var x = i * -0x61c88647
    x = x xor (x ushr 15)
    x *= 0x2c1b3c6d
    x = x xor (x ushr 12)
    x *= 0x297a2d39
    x = x xor (x ushr 15)
    return (x and 0xFFFF) / 65535f
}

private fun puffPath(rnd: Random, pw: Float, ph: Float, cirrus: Boolean): Path {
    if (cirrus) {
        val a = Path().apply { addOval(Rect(0f, ph * 0.2f, pw, ph)) }
        val b = Path().apply { addOval(Rect(pw * 0.25f, 0f, pw * 0.95f, ph * 0.7f)) }
        return Path().apply { op(a, b, PathOperation.Union) }
    }
    var p = Path().apply { addOval(Rect(0f, ph * 0.45f, pw, ph)) }
    val n = 3 + rnd.nextInt(2)
    for (k in 0 until n) {
        val cx = pw * (0.18f + 0.64f * (k + 0.3f + rnd.nextFloat() * 0.4f) / n)
        val r = ph * (0.30f + rnd.nextFloat() * 0.22f)
        val cy = ph * 0.64f - r * 0.35f
        val bump = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
        val u = Path()
        u.op(p, bump, PathOperation.Union)
        p = u
    }
    return p
}

/** Lit portion of a moon of radius r centred on the origin, lit on the right. */
private fun litPath(r: Float, k: Float): Path {
    val p = Path()
    if (k <= 0.02f) return p
    if (k >= 0.98f) { p.addOval(Rect(Offset.Zero, r)); return p }
    val e = r * abs(1f - 2f * k)
    p.moveTo(0f, -r)
    p.arcTo(Rect(-r, -r, r, r), -90f, 180f, false)
    p.arcTo(Rect(-e, -r, e, r), 90f, if (k < 0.5f) -180f else 180f, false)
    p.close()
    return p
}

@Composable
private fun SkyMotion(s: SkyScene, clock: MutableFloatState, modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val rnd = Random(1977)
            val dk = dayK(s.alt)
            val tw = twilight(s.alt)
            val g = gloom(s)

            // ---- stars: 4 twinkle groups, each a single drawPoints call
            val starA = s.starAlpha
            val groups = 4
            val perGroup = 24
            val stars = Array(groups) { FloatArray(perGroup * 2) }
            for (gi in 0 until groups) for (k in 0 until perGroup) {
                stars[gi][k * 2] = rnd.nextFloat() * w
                stars[gi][k * 2 + 1] = rnd.nextFloat().pow(1.4f) * h * 0.72f
            }
            val starPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val starW = floatArrayOf(1.2f.dp.toPx(), 1.6f.dp.toPx(), 2.0f.dp.toPx(), 2.6f.dp.toPx())
            val twSpeed = floatArrayOf(1.7f, 2.3f, 1.1f, 0.6f)

            // ---- sun
            val showSun = s.alt > -5f
            val sunC = Offset(s.sunX * w, s.sunY * h)
            val sunR = 16.dp.toPx()
            val low = (s.alt / 12f).coerceIn(0f, 1f)
            val sunVis = (1f - s.cloud * 0.85f).coerceIn(0.08f, 1f)
            val halo = Brush.radialGradient(
                listOf(lerp(Color(0xFFFF7040), Color(0xFFFFF0D0), low).copy(alpha = 0.45f * sunVis), Color.Transparent),
                center = sunC, radius = sunR * 6f,
            )
            val disc = Brush.radialGradient(
                listOf(lerp(Color(0xFFFFB070), Color(0xFFFFF8EA), low), lerp(Pal.Crimson, Color(0xFFFFD890), low)),
                center = Offset(sunC.x - sunR * 0.3f, sunC.y - sunR * 0.3f), radius = sunR * 1.4f,
            )

            // ---- moon
            val moonR = 13.dp.toPx()
            val moonC = Offset(s.moonX * w, s.moonY * h)
            val lit = litPath(moonR, s.moonLit)
            val moonVis = (1f - s.cloud * 0.8f).coerceIn(0.1f, 1f) * (1f - dk * 0.6f)
            val moonFill = Brush.radialGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFD8DAE6), Color(0xFF9A9CAE)),
                center = Offset(-moonR * 0.3f, -moonR * 0.35f), radius = moonR * 1.5f,
            )
            val moonHalo = Brush.radialGradient(
                listOf(Color(0xFFC8D4FF).copy(alpha = 0.30f * s.moonLit * moonVis), Color.Transparent),
                center = moonC, radius = moonR * 5f,
            )

            // ---- clouds
            val litTop = lerp(lerp(Color(0xFF262A36), Color(0xFFD6DAE2), dk), Color(0xFFE8A0A0), tw * 0.35f)
            val shade0 = lerp(lerp(Color(0xFF101219), Color(0xFF7C8494), dk), Color(0xFF6A2A36), tw * 0.55f)
            val shade = lerp(shade0, Color(0xFF2A2D35).copy(alpha = 1f), (g * 0.8f).coerceIn(0f, 0.8f))
            val litC = lerp(litTop, shade, g * 0.55f)
            val nLow = (s.low * LOW_MAX).roundToInt().coerceIn(0, LOW_MAX)
            val nMid = (s.mid * MID_MAX).roundToInt().coerceIn(0, MID_MAX)
            val nHigh = (s.high * HIGH_MAX).roundToInt().coerceIn(0, HIGH_MAX)
            val nC = nLow + nMid + nHigh
            val cPath = arrayOfNulls<Path>(nC)
            val cBrush = arrayOfNulls<Brush>(nC)
            val cW = FloatArray(nC)
            val cY = FloatArray(nC)
            val cX0 = FloatArray(nC)
            val cSpeed = FloatArray(nC)
            val cAlpha = FloatArray(nC)
            val baseSpeed = w * (0.004f + s.wind / 100f * 0.02f)
            var ci = 0
            // far to near so low clouds overlap the higher ones
            for (k in 0 until nHigh) {
                val pw = w * (0.45f + rnd.nextFloat() * 0.3f)
                val ph = h * (0.035f + rnd.nextFloat() * 0.03f)
                cPath[ci] = puffPath(rnd, pw, ph, cirrus = true)
                cBrush[ci] = Brush.verticalGradient(listOf(Color.Transparent, litTop.copy(alpha = 0.55f), Color.Transparent), 0f, ph)
                cW[ci] = pw; cY[ci] = h * (0.06f + rnd.nextFloat() * 0.22f)
                cX0[ci] = (k + rnd.nextFloat() * 0.5f) / nHigh; cSpeed[ci] = baseSpeed * 0.35f; cAlpha[ci] = 0.7f
                ci++
            }
            for (k in 0 until nMid) {
                val pw = w * (0.28f + rnd.nextFloat() * 0.14f)
                val ph = pw * 0.42f
                cPath[ci] = puffPath(rnd, pw, ph, cirrus = false)
                cBrush[ci] = Brush.verticalGradient(listOf(litC, lerp(litC, shade, 0.6f)), 0f, ph)
                cW[ci] = pw; cY[ci] = h * (0.20f + rnd.nextFloat() * 0.22f)
                cX0[ci] = (k + rnd.nextFloat() * 0.6f) / nMid; cSpeed[ci] = baseSpeed * 0.6f; cAlpha[ci] = 0.72f
                ci++
            }
            for (k in 0 until nLow) {
                val pw = w * (0.48f + rnd.nextFloat() * 0.22f)
                val ph = pw * 0.40f
                cPath[ci] = puffPath(rnd, pw, ph, cirrus = false)
                cBrush[ci] = Brush.verticalGradient(listOf(litC, shade, shade), 0f, ph)
                cW[ci] = pw; cY[ci] = h * (0.36f + rnd.nextFloat() * 0.26f) - ph * 0.5f
                cX0[ci] = (k + rnd.nextFloat() * 0.7f) / nLow; cSpeed[ci] = baseSpeed; cAlpha[ci] = 0.93f
                ci++
            }

            // ---- precipitation
            val snow = s.sky == Meteo.Sky.SNOW
            val pCount = when (s.sky) {
                Meteo.Sky.DRIZZLE -> 45
                Meteo.Sky.RAIN -> 90
                Meteo.Sky.HEAVY_RAIN -> 150
                Meteo.Sky.STORM -> 120
                Meteo.Sky.SNOW -> 70
                else -> if (s.precip > 0.1f) 40 else 0
            }
            val px0 = FloatArray(pCount)
            val py0 = FloatArray(pCount)
            val pSpd = FloatArray(pCount)
            val pLen = FloatArray(pCount)
            val drizzle = s.sky == Meteo.Sky.DRIZZLE
            val slant = (s.wind / 80f).coerceIn(0.05f, 0.35f)
            for (k in 0 until pCount) {
                px0[k] = if (snow) rnd.nextFloat() * (w * 1.3f) - w * 0.15f
                else rnd.nextFloat() * (w * 1.2f + h * slant) - w * 0.1f
                py0[k] = rnd.nextFloat() * h
                pSpd[k] = h * when {
                    snow -> 0.08f + rnd.nextFloat() * 0.08f
                    drizzle -> 0.6f + rnd.nextFloat() * 0.3f
                    else -> 1.1f + rnd.nextFloat() * 0.6f
                }
                pLen[k] = if (snow) rnd.nextFloat() * 6.28f else (if (drizzle) 6.dp.toPx() else 12.dp.toPx() + rnd.nextFloat() * 10.dp.toPx())
            }
            val pBuf = FloatArray(pCount * if (snow) 2 else 4)
            val pPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeWidth = if (snow) 3.dp.toPx() else if (drizzle) 1.dp.toPx() else 1.3f.dp.toPx()
                color = if (snow) 0xD8F0F4FF.toInt() else Color(0xFFB8CCE8).copy(alpha = if (drizzle) 0.38f else 0.5f).toArgb()
            }
            val sway = 7.dp.toPx()

            // ---- lightning
            val storm = s.sky == Meteo.Sky.STORM
            val bolts = if (storm) Array(2) { bi ->
                Path().apply {
                    val br = Random(42 + bi)
                    var x = w * (0.3f + 0.4f * br.nextFloat())
                    var y = 0f
                    moveTo(x, y)
                    while (y < h * 0.62f) {
                        y += h * (0.04f + br.nextFloat() * 0.06f)
                        x += w * (br.nextFloat() - 0.5f) * 0.12f
                        lineTo(x, y)
                    }
                }
            } else emptyArray<Path>()
            val boltGlow = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            val boltCore = Stroke(width = 1.6f.dp.toPx(), cap = StrokeCap.Round)

            // ---- fog
            val fog = s.sky == Meteo.Sky.FOG
            val fogC = lerp(Color(0xFF30333A), Color(0xFFB8BCC4), dk)
            val bandH = h * 0.14f
            val fogBand = Brush.verticalGradient(listOf(Color.Transparent, fogC.copy(alpha = 0.42f), Color.Transparent), 0f, bandH)
            val fogVeil = fogC.copy(alpha = 0.22f)
            val bob = 6.dp.toPx()

            onDrawBehind {
                val t = clock.floatValue

                // stars
                if (starA > 0.02f) drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    for (gi in 0 until groups) {
                        val tws = 0.55f + 0.45f * sin(t * twSpeed[gi] + gi * 1.9f)
                        starPaint.alpha = (255 * starA * (if (gi == 3) 0.95f else tws)).toInt().coerceIn(0, 255)
                        starPaint.strokeWidth = starW[gi]
                        nc.drawPoints(stars[gi], starPaint)
                    }
                }

                // moon
                if (s.moonUp) {
                    drawCircle(moonHalo, moonR * 5f, moonC)
                    drawCircle(Color(0xFF1C2030).copy(alpha = 0.85f * moonVis), moonR, moonC)
                    translate(moonC.x, moonC.y) {
                        if (s.litLeft) scale(-1f, 1f, Offset.Zero) { drawPath(lit, moonFill, alpha = moonVis) }
                        else drawPath(lit, moonFill, alpha = moonVis)
                    }
                }

                // sun
                if (showSun) {
                    drawCircle(halo, sunR * 6f, sunC)
                    drawCircle(disc, sunR, sunC, alpha = sunVis)
                }

                // clouds, drifting with the wind and wrapping around
                for (k in 0 until nC) {
                    val span = w + cW[k]
                    val x = ((cX0[k] * span + t * cSpeed[k]) % span) - cW[k]
                    translate(x, cY[k]) { drawPath(cPath[k]!!, cBrush[k]!!, alpha = cAlpha[k]) }
                }

                // fog
                if (fog) {
                    drawRect(fogVeil)
                    for (k in 0 until 4) {
                        val y = h * (0.34f + k * 0.12f) + sin(t * 0.25f + k * 1.7f) * bob
                        translate(0f, y) { drawRect(fogBand, size = Size(w, bandH)) }
                    }
                }

                // rain / snow, one drawLines/drawPoints call
                if (pCount > 0) {
                    if (snow) {
                        for (k in 0 until pCount) {
                            val y = (py0[k] + t * pSpd[k]) % h
                            pBuf[k * 2] = ((px0[k] + w * 0.15f + t * slant * 10.dp.toPx()) % (w * 1.3f)) - w * 0.15f + sin(t * 1.2f + pLen[k]) * sway
                            pBuf[k * 2 + 1] = y
                        }
                        drawIntoCanvas { it.nativeCanvas.drawPoints(pBuf, pPaint) }
                    } else {
                        for (k in 0 until pCount) {
                            val len = pLen[k]
                            val y = (py0[k] + t * pSpd[k]) % (h + len) - len
                            val x = px0[k] - (y + len) * slant
                            pBuf[k * 4] = x
                            pBuf[k * 4 + 1] = y
                            pBuf[k * 4 + 2] = x - len * slant
                            pBuf[k * 4 + 3] = y + len
                        }
                        drawIntoCanvas { it.nativeCanvas.drawLines(pBuf, pPaint) }
                    }
                }

                // lightning: deterministic schedule, ~60 % of 5.5 s slots get a double flash
                if (storm) {
                    val seg = (t / 5.5f).toInt()
                    val r = hash01(seg)
                    if (r < 0.6f) {
                        val local = t - seg * 5.5f - (0.4f + r * 4f)
                        val env = when {
                            local in 0f..0.12f -> 1f - local / 0.12f
                            local in 0.18f..0.45f -> 0.7f * (1f - (local - 0.18f) / 0.27f)
                            else -> 0f
                        }
                        if (env > 0f) {
                            drawRect(Color(0xFFE8ECFF).copy(alpha = 0.30f * env))
                            if (r < 0.35f) {
                                val bp = bolts[seg and 1]
                                val dx = (hash01(seg * 7 + 3) - 0.5f) * w * 0.5f
                                translate(dx, 0f) {
                                    drawPath(bp, Color(0xFFB0B8FF).copy(alpha = 0.35f * env), style = boltGlow)
                                    drawPath(bp, Color.White.copy(alpha = env), style = boltCore)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
