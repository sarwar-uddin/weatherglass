package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.github.sarwaruddin.weatherglass.data.Meteo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glossy hand-drawn weather glyph for a WMO code (BRAND.md §6, tier 1: one light source top-left,
 * one body gradient + one specular highlight per object, no crimson). Static; all paths and
 * brushes are built once per size in drawWithCache, so it is cheap in long lists.
 *
 * Decorative by default. Pass [contentDescription] (e.g. `Meteo.wmo(code).text`) when the icon
 * is the only carrier of the condition on screen.
 */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val sky = Meteo.wmo(code).sky
    val a11y = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Spacer(
        modifier.then(a11y).size(size).drawWithCache {
            val s = this.size.minDimension
            val ox = (this.size.width - s) / 2f
            val oy = (this.size.height - s) / 2f
            val ops = iconOps(sky, isDay, s)
            onDrawBehind { translate(ox, oy) { for (op in ops) op() } }
        },
    )
}

private typealias Op = DrawScope.() -> Unit

private fun iconOps(sky: Meteo.Sky, day: Boolean, s: Float): List<Op> {
    val ops = ArrayList<Op>(6)
    fun orb(x: Float, y: Float, sunR: Float, moonR: Float) {
        ops += if (day) sunOp(x * s, y * s, sunR * s) else moonOp(x * s, y * s, moonR * s)
    }
    when (sky) {
        Meteo.Sky.CLEAR -> orb(0.5f, 0.5f, 0.24f, 0.30f)
        Meteo.Sky.FEW -> {
            orb(0.42f, 0.40f, 0.21f, 0.26f)
            ops += cloudOp(0.64f * s, 0.70f * s, 0.46f * s, dark = false)
        }
        Meteo.Sky.PARTLY -> {
            orb(0.36f, 0.36f, 0.19f, 0.23f)
            ops += cloudOp(0.56f * s, 0.64f * s, 0.72f * s, dark = false)
        }
        Meteo.Sky.OVERCAST -> {
            ops += cloudOp(0.62f * s, 0.46f * s, 0.60f * s, dark = true)
            ops += cloudOp(0.46f * s, 0.64f * s, 0.78f * s, dark = false)
        }
        Meteo.Sky.FOG -> {
            if (!day) ops += moonOp(0.66f * s, 0.30f * s, 0.18f * s)
            ops += cloudOp(0.48f * s, 0.42f * s, 0.70f * s, dark = false)
            ops += fogOp(s)
        }
        Meteo.Sky.DRIZZLE -> {
            ops += cloudOp(0.5f * s, 0.44f * s, 0.80f * s, dark = false)
            ops += dropsOp(s, floatArrayOf(0.34f, 0.66f, 0.52f, 0.80f, 0.70f, 0.66f), 0.08f)
        }
        Meteo.Sky.RAIN -> {
            ops += cloudOp(0.5f * s, 0.42f * s, 0.82f * s, dark = false)
            ops += dropsOp(s, floatArrayOf(0.30f, 0.64f, 0.48f, 0.74f, 0.66f, 0.64f, 0.40f, 0.86f, 0.58f, 0.84f), 0.13f)
        }
        Meteo.Sky.HEAVY_RAIN -> {
            ops += cloudOp(0.5f * s, 0.40f * s, 0.84f * s, dark = true)
            ops += dropsOp(
                s,
                floatArrayOf(0.24f, 0.62f, 0.40f, 0.66f, 0.56f, 0.62f, 0.72f, 0.66f, 0.32f, 0.82f, 0.48f, 0.86f, 0.64f, 0.82f),
                0.15f,
            )
        }
        Meteo.Sky.SNOW -> {
            ops += cloudOp(0.5f * s, 0.42f * s, 0.82f * s, dark = false)
            ops += flakesOp(s, floatArrayOf(0.32f, 0.70f, 0.56f, 0.74f, 0.42f, 0.88f, 0.70f, 0.88f))
        }
        Meteo.Sky.STORM -> {
            ops += cloudOp(0.5f * s, 0.38f * s, 0.84f * s, dark = true)
            ops += dropsOp(s, floatArrayOf(0.28f, 0.66f, 0.72f, 0.70f), 0.12f)
            ops += boltOp(0.50f * s, 0.70f * s, s)
        }
    }
    return ops
}

// ---- pieces ------------------------------------------------------------------

private fun sunOp(cx: Float, cy: Float, r: Float): Op {
    val c = Offset(cx, cy)
    val glow = Brush.radialGradient(listOf(Color(0x66FF7A3A), Color(0x00FF7A3A)), center = c, radius = r * 2.1f)
    val disc = Brush.radialGradient(
        listOf(Color(0xFFFFF6DC), Color(0xFFFFC54E), Color(0xFFF2772A), Color(0xFFD24E1C)),
        center = Offset(cx - r * 0.35f, cy - r * 0.35f), radius = r * 1.55f,
    )
    val gloss = Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x00FFFFFF)), startY = cy - r * 0.9f, endY = cy - r * 0.05f)
    val rays = FloatArray(32)
    for (i in 0 until 8) {
        val a = i * PI / 4 + PI / 8
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        rays[i * 4] = cx + ca * r * 1.3f
        rays[i * 4 + 1] = cy + sa * r * 1.3f
        rays[i * 4 + 2] = cx + ca * r * 1.68f
        rays[i * 4 + 3] = cy + sa * r * 1.68f
    }
    val rayColor = Color(0xFFFFA63A)
    val rw = r * 0.17f
    return {
        drawCircle(glow, r * 2.1f, c)
        for (i in 0 until 8) {
            drawLine(rayColor, Offset(rays[i * 4], rays[i * 4 + 1]), Offset(rays[i * 4 + 2], rays[i * 4 + 3]), rw, StrokeCap.Round)
        }
        drawCircle(disc, r, c)
        drawOval(gloss, Offset(cx - r * 0.72f, cy - r * 0.86f), Size(r * 1.1f, r * 0.72f))
    }
}

private fun moonOp(cx: Float, cy: Float, r: Float): Op {
    val a = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
    val b = Path().apply { addOval(Rect(Offset(cx + r * 0.52f, cy - r * 0.30f), r * 0.86f)) }
    val crescent = Path().apply { op(a, b, PathOperation.Difference) }
    val glow = Brush.radialGradient(listOf(Color(0x44C8D4FF), Color(0x00C8D4FF)), center = Offset(cx, cy), radius = r * 1.7f)
    val fill = Brush.radialGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFDCDCE6), Color(0xFF8C8C9C)),
        center = Offset(cx - r * 0.45f, cy - r * 0.2f), radius = r * 1.5f,
    )
    val gloss = Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x00FFFFFF)), startY = cy - r, endY = cy)
    val rim = Stroke(width = r * 0.05f)
    return {
        drawCircle(glow, r * 1.7f, Offset(cx, cy))
        drawPath(crescent, fill)
        clipPath(crescent) { drawRect(gloss, Offset(cx - r, cy - r), Size(r * 2f, r)) }
        drawPath(crescent, Color(0x55FFFFFF), style = rim)
    }
}

/** Puffy cloud: centre (cx, cy), width w; spans roughly cy-0.47w .. cy+0.17w. */
private fun cloudOp(cx: Float, cy: Float, w: Float, dark: Boolean): Op {
    val h = w * 0.34f
    val bottom = cy + h * 0.5f
    val top = bottom - w * 0.65f
    val base = Path().apply { addRoundRect(RoundRect(cx - w / 2, bottom - h, cx + w / 2, bottom, CornerRadius(h / 2, h / 2))) }
    val b1 = Path().apply { addOval(Rect(Offset(cx - w * 0.17f, bottom - h * 0.95f), w * 0.21f)) }
    val b2 = Path().apply { addOval(Rect(Offset(cx + w * 0.09f, bottom - h * 1.1f), w * 0.27f)) }
    val u = Path().apply { op(base, b1, PathOperation.Union) }
    val path = Path().apply { op(u, b2, PathOperation.Union) }
    val fill = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF9C9CA8), Color(0xFF50505C), Color(0xFF2C2C34))
        else listOf(Color(0xFFFFFFFF), Color(0xFFDADAE2), Color(0xFF8E8E9C)),
        startY = top, endY = bottom,
    )
    val gloss = Brush.linearGradient(
        listOf(Color(if (dark) 0x55FFFFFFL else 0xAAFFFFFFL), Color(0x00FFFFFF)),
        start = Offset(cx - w * 0.35f, top), end = Offset(cx - w * 0.05f, top + (bottom - top) * 0.6f),
    )
    val shadow = Brush.verticalGradient(listOf(Color(0x00000000), Color(0x30000000)), startY = bottom - h * 0.6f, endY = bottom)
    val rim = Stroke(width = w * 0.016f)
    return {
        drawPath(path, fill)
        clipPath(path) {
            drawRect(gloss, Offset(cx - w / 2, top), Size(w, (bottom - top) * 0.6f))
            drawRect(shadow, Offset(cx - w / 2, bottom - h * 0.6f), Size(w, h * 0.6f))
        }
        drawPath(path, Color(0x40FFFFFF), style = rim)
    }
}

/** Slanted drops; xy pairs as fractions of s, len as fraction. */
private fun dropsOp(s: Float, xy: FloatArray, len: Float): Op {
    val n = xy.size / 2
    val dx = -0.045f * s * (len / 0.13f)
    val dy = len * s
    val w = s * 0.055f
    val hi = Color(0xFFA8CCF5)
    return {
        for (i in 0 until n) {
            val x = xy[i * 2] * s
            val y = xy[i * 2 + 1] * s
            drawLine(Pal.Rain, Offset(x, y), Offset(x + dx, y + dy), w, StrokeCap.Round)
            drawLine(hi, Offset(x - w * 0.15f, y), Offset(x + dx * 0.4f - w * 0.15f, y + dy * 0.4f), w * 0.35f, StrokeCap.Round)
        }
    }
}

private fun flakesOp(s: Float, xy: FloatArray): Op {
    val n = xy.size / 2
    val arm = s * 0.055f
    val w = s * 0.026f
    val c = Color(0xFFEAF2FF)
    val ca = cos(PI / 3).toFloat()
    val sa = sin(PI / 3).toFloat()
    return {
        for (i in 0 until n) {
            val x = xy[i * 2] * s
            val y = xy[i * 2 + 1] * s
            drawLine(c, Offset(x - arm, y), Offset(x + arm, y), w, StrokeCap.Round)
            drawLine(c, Offset(x - arm * ca, y - arm * sa), Offset(x + arm * ca, y + arm * sa), w, StrokeCap.Round)
            drawLine(c, Offset(x - arm * ca, y + arm * sa), Offset(x + arm * ca, y - arm * sa), w, StrokeCap.Round)
        }
    }
}

private fun boltOp(cx: Float, cy: Float, s: Float): Op {
    val p = Path().apply {
        moveTo(cx + 0.06f * s, cy - 0.18f * s)
        lineTo(cx - 0.10f * s, cy + 0.04f * s)
        lineTo(cx - 0.01f * s, cy + 0.04f * s)
        lineTo(cx - 0.07f * s, cy + 0.25f * s)
        lineTo(cx + 0.12f * s, cy - 0.03f * s)
        lineTo(cx + 0.03f * s, cy - 0.03f * s)
        lineTo(cx + 0.11f * s, cy - 0.18f * s)
        close()
    }
    val fill = Brush.verticalGradient(
        listOf(Color(0xFFFFF3B0), Color(0xFFFFB23A), Color(0xFFEA7A1E)),
        startY = cy - 0.18f * s, endY = cy + 0.25f * s,
    )
    val glow = Brush.radialGradient(listOf(Color(0x55FFB23A), Color(0x00FFB23A)), center = Offset(cx, cy + 0.03f * s), radius = 0.24f * s)
    val rim = Stroke(width = s * 0.012f)
    return {
        drawCircle(glow, 0.24f * s, Offset(cx, cy + 0.03f * s))
        drawPath(p, fill)
        drawPath(p, Color(0x40FFFFFF), style = rim)
    }
}

private fun fogOp(s: Float): Op {
    val w = s * 0.07f
    val c1 = Color(0xCCD8D8E0)
    val c2 = Color(0x88D8D8E0)
    return {
        drawLine(c1, Offset(0.16f * s, 0.68f * s), Offset(0.84f * s, 0.68f * s), w, StrokeCap.Round)
        drawLine(c2, Offset(0.26f * s, 0.80f * s), Offset(0.90f * s, 0.80f * s), w, StrokeCap.Round)
        drawLine(c2, Offset(0.12f * s, 0.92f * s), Offset(0.62f * s, 0.92f * s), w, StrokeCap.Round)
    }
}
