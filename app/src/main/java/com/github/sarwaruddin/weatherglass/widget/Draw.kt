package com.github.sarwaruddin.weatherglass.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.github.sarwaruddin.weatherglass.data.Meteo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Brand colours as ARGB ints, for android.graphics (bitmaps, share cards, widgets). */
object Ink {
    const val BG = 0xFF070708.toInt()
    const val CARD = 0xFF131316.toInt()
    const val CARD_TOP = 0xFF1C1C21.toInt()
    const val EDGE = 0xFF26262C.toInt()
    const val GRID = 0xFF2A2A2E.toInt()
    const val TEXT = 0xFFF2F2F5.toInt()
    const val SUB = 0xFF9A9AA2.toInt()
    const val DIM = 0xFF8A8A93.toInt()
    const val CRIMSON = 0xFFE0203F.toInt()
    const val CRIMSON_HI = 0xFFFF4A63.toInt()
    const val CRIMSON_DEEP = 0xFF8A1028.toInt()
    const val SILVER = 0xFFD8D8E0.toInt()
    const val RAIN = 0xFF4F8FD9.toInt()

    /** Rain intensity scale (mm/h), brighter = more: trace, light, moderate, heavy. */
    fun rain(mmh: Double): Int = when {
        mmh < 0.5 -> 0xFF24466E.toInt()
        mmh < 2.5 -> 0xFF2F6CB0.toInt()
        mmh < 10.0 -> 0xFF4F98E8.toInt()
        else -> 0xFF9AD0FF.toInt()
    }

    fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * k).toInt() shl s
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}

/** Shared typefaces; system Roboto family weights. */
object Faces {
    val thin: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    fun paint(face: Typeface, px: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face
            textSize = px
            this.color = color
            textAlign = align
            letterSpacing = spacing
            fontFeatureSettings = "tnum"
        }
}

/**
 * The app's glossy weather glyphs (ui/WeatherIcon.kt), ported to android.graphics so they
 * can go into bitmaps: widgets and the share card.
 */
object Glyph {

    fun bitmap(code: Int, isDay: Boolean, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), code, isDay, 0f, 0f, px.toFloat())
        return bmp
    }

    /** Draw the glyph for WMO [code] in the square (x, y, x+s, y+s). */
    fun draw(c: Canvas, code: Int, isDay: Boolean, x: Float, y: Float, s: Float) {
        c.save()
        c.translate(x, y)
        fun orb(fx: Float, fy: Float, sunR: Float, moonR: Float) {
            if (isDay) sun(c, fx * s, fy * s, sunR * s) else moon(c, fx * s, fy * s, moonR * s)
        }
        when (Meteo.wmo(code).sky) {
            Meteo.Sky.CLEAR -> orb(0.5f, 0.5f, 0.24f, 0.30f)
            Meteo.Sky.FEW -> {
                orb(0.42f, 0.40f, 0.21f, 0.26f)
                cloud(c, 0.64f * s, 0.70f * s, 0.46f * s, false)
            }
            Meteo.Sky.PARTLY -> {
                orb(0.36f, 0.36f, 0.19f, 0.23f)
                cloud(c, 0.56f * s, 0.64f * s, 0.72f * s, false)
            }
            Meteo.Sky.OVERCAST -> {
                cloud(c, 0.62f * s, 0.46f * s, 0.60f * s, true)
                cloud(c, 0.46f * s, 0.64f * s, 0.78f * s, false)
            }
            Meteo.Sky.FOG -> {
                if (!isDay) moon(c, 0.66f * s, 0.30f * s, 0.18f * s)
                cloud(c, 0.48f * s, 0.42f * s, 0.70f * s, false)
                fog(c, s)
            }
            Meteo.Sky.DRIZZLE -> {
                cloud(c, 0.5f * s, 0.44f * s, 0.80f * s, false)
                drops(c, s, floatArrayOf(0.34f, 0.66f, 0.52f, 0.80f, 0.70f, 0.66f), 0.08f)
            }
            Meteo.Sky.RAIN -> {
                cloud(c, 0.5f * s, 0.42f * s, 0.82f * s, false)
                drops(c, s, floatArrayOf(0.30f, 0.64f, 0.48f, 0.74f, 0.66f, 0.64f, 0.40f, 0.86f, 0.58f, 0.84f), 0.13f)
            }
            Meteo.Sky.HEAVY_RAIN -> {
                cloud(c, 0.5f * s, 0.40f * s, 0.84f * s, true)
                drops(
                    c, s,
                    floatArrayOf(0.24f, 0.62f, 0.40f, 0.66f, 0.56f, 0.62f, 0.72f, 0.66f, 0.32f, 0.82f, 0.48f, 0.86f, 0.64f, 0.82f),
                    0.15f,
                )
            }
            Meteo.Sky.SNOW -> {
                cloud(c, 0.5f * s, 0.42f * s, 0.82f * s, false)
                flakes(c, s, floatArrayOf(0.32f, 0.70f, 0.56f, 0.74f, 0.42f, 0.88f, 0.70f, 0.88f))
            }
            Meteo.Sky.STORM -> {
                cloud(c, 0.5f * s, 0.38f * s, 0.84f * s, true)
                drops(c, s, floatArrayOf(0.28f, 0.66f, 0.72f, 0.70f), 0.12f)
                bolt(c, 0.50f * s, 0.70f * s, s)
            }
        }
        c.restore()
    }

    private fun fill() = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun stroke(w: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; this.color = color; strokeCap = Paint.Cap.ROUND
    }

    private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx, cy, r * 2.1f, fill().apply {
            shader = RadialGradient(cx, cy, r * 2.1f, 0x66FF7A3A, 0x00E0203F, Shader.TileMode.CLAMP)
        })
        val ray = stroke(r * 0.17f, 0xFFFFA63A.toInt())
        for (i in 0 until 8) {
            val a = i * PI / 4 + PI / 8
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            c.drawLine(cx + ca * r * 1.3f, cy + sa * r * 1.3f, cx + ca * r * 1.68f, cy + sa * r * 1.68f, ray)
        }
        c.drawCircle(cx, cy, r, fill().apply {
            shader = RadialGradient(
                cx - r * 0.35f, cy - r * 0.35f, r * 1.55f,
                intArrayOf(0xFFFFF6DC.toInt(), 0xFFFFC54E.toInt(), 0xFFF2772A.toInt(), 0xFFD02A3A.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        })
        c.drawOval(RectF(cx - r * 0.6f, cy - r * 0.88f, cx + r * 0.6f, cy - r * 0.10f), fill().apply {
            shader = LinearGradient(0f, cy - r * 0.9f, 0f, cy - r * 0.05f, 0xCCFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        val crescent = circle(cx, cy, r).apply { op(circle(cx + r * 0.52f, cy - r * 0.30f, r * 0.86f), Path.Op.DIFFERENCE) }
        c.drawCircle(cx, cy, r * 1.7f, fill().apply {
            shader = RadialGradient(cx, cy, r * 1.7f, 0x44C8D4FF, 0x00C8D4FF, Shader.TileMode.CLAMP)
        })
        c.drawPath(crescent, fill().apply {
            shader = RadialGradient(
                cx - r * 0.45f, cy - r * 0.2f, r * 1.5f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFDCDCE6.toInt(), 0xFF8C8C9C.toInt()), null, Shader.TileMode.CLAMP,
            )
        })
        c.save()
        c.clipPath(crescent)
        c.drawRect(cx - r, cy - r, cx + r, cy, fill().apply {
            shader = LinearGradient(0f, cy - r, 0f, cy, 0x99FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        c.restore()
        c.drawPath(crescent, stroke(r * 0.05f, 0x55FFFFFF))
    }

    private fun cloud(c: Canvas, cx: Float, cy: Float, w: Float, dark: Boolean) {
        val h = w * 0.34f
        val bottom = cy + h * 0.5f
        val top = bottom - w * 0.65f
        val path = Path().apply {
            addRoundRect(RectF(cx - w / 2, bottom - h, cx + w / 2, bottom), h / 2, h / 2, Path.Direction.CW)
            op(circle(cx - w * 0.17f, bottom - h * 0.95f, w * 0.21f), Path.Op.UNION)
            op(circle(cx + w * 0.09f, bottom - h * 1.1f, w * 0.27f), Path.Op.UNION)
        }
        val colors = if (dark) intArrayOf(0xFF9C9CA8.toInt(), 0xFF50505C.toInt(), 0xFF2C2C34.toInt())
        else intArrayOf(0xFFFFFFFF.toInt(), 0xFFDADAE2.toInt(), 0xFF8E8E9C.toInt())
        c.drawPath(path, fill().apply { shader = LinearGradient(0f, top, 0f, bottom, colors, null, Shader.TileMode.CLAMP) })
        c.save()
        c.clipPath(path)
        val gEnd = top + (bottom - top) * 0.55f
        c.drawRect(cx - w / 2, top, cx + w / 2, gEnd, fill().apply {
            shader = LinearGradient(0f, top, 0f, gEnd, if (dark) 0x55FFFFFF else 0xAAFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        c.drawRect(cx - w / 2, bottom - h * 0.6f, cx + w / 2, bottom, fill().apply {
            shader = LinearGradient(0f, bottom - h * 0.6f, 0f, bottom, 0x00000000, 0x30000000, Shader.TileMode.CLAMP)
        })
        c.restore()
        c.drawPath(path, stroke(w * 0.016f, 0x40FFFFFF))
    }

    private fun drops(c: Canvas, s: Float, xy: FloatArray, len: Float) {
        val dx = -0.045f * s * (len / 0.13f)
        val dy = len * s
        val w = s * 0.055f
        val main = stroke(w, Ink.RAIN)
        val hi = stroke(w * 0.35f, 0xFFA8CCF5.toInt())
        for (i in 0 until xy.size / 2) {
            val x = xy[i * 2] * s
            val y = xy[i * 2 + 1] * s
            c.drawLine(x, y, x + dx, y + dy, main)
            c.drawLine(x - w * 0.15f, y, x + dx * 0.4f - w * 0.15f, y + dy * 0.4f, hi)
        }
    }

    private fun flakes(c: Canvas, s: Float, xy: FloatArray) {
        val arm = s * 0.055f
        val p = stroke(s * 0.026f, 0xFFEAF2FF.toInt())
        val ca = cos(PI / 3).toFloat()
        val sa = sin(PI / 3).toFloat()
        for (i in 0 until xy.size / 2) {
            val x = xy[i * 2] * s
            val y = xy[i * 2 + 1] * s
            c.drawLine(x - arm, y, x + arm, y, p)
            c.drawLine(x - arm * ca, y - arm * sa, x + arm * ca, y + arm * sa, p)
            c.drawLine(x - arm * ca, y + arm * sa, x + arm * ca, y - arm * sa, p)
        }
    }

    private fun bolt(c: Canvas, cx: Float, cy: Float, s: Float) {
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
        c.drawCircle(cx, cy + 0.03f * s, 0.24f * s, fill().apply {
            shader = RadialGradient(cx, cy + 0.03f * s, 0.24f * s, 0x55FFB23A, 0x00FFB23A, Shader.TileMode.CLAMP)
        })
        c.drawPath(p, fill().apply {
            shader = LinearGradient(
                0f, cy - 0.18f * s, 0f, cy + 0.25f * s,
                intArrayOf(0xFFFFF3B0.toInt(), 0xFFFFB23A.toInt(), 0xFFE0203F.toInt()), null, Shader.TileMode.CLAMP,
            )
        })
        c.drawPath(p, stroke(s * 0.012f, 0x88FFFFFF.toInt()).apply { strokeJoin = Paint.Join.ROUND })
    }

    private fun fog(c: Canvas, s: Float) {
        val w = s * 0.07f
        c.drawLine(0.16f * s, 0.68f * s, 0.84f * s, 0.68f * s, stroke(w, 0xCCD8D8E0.toInt()))
        val p2 = stroke(w, 0x88D8D8E0.toInt())
        c.drawLine(0.26f * s, 0.80f * s, 0.90f * s, 0.80f * s, p2)
        c.drawLine(0.12f * s, 0.92f * s, 0.62f * s, 0.92f * s, p2)
    }
}

/**
 * Mini meteogram: crimson temperature line over a crimson-deep glass fill, blue rain bars
 * beneath, the "now" dot, max/min labels and hour ticks.
 */
object Plot {

    class Series(
        val temp: DoubleArray,
        val rain: DoubleArray,
        /** (index, label) pairs printed under the plot. */
        val ticks: List<Pair<Int, String>>,
    )

    fun bitmap(s: Series, w: Int, h: Int, scale: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(8), h.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), RectF(0f, 0f, w.toFloat(), h.toFloat()), s, scale)
        return bmp
    }

    /** [u] is the size unit in px (≈ 1 dp on a widget, ≈ 2.6 px per "dp" on the share card). */
    fun draw(c: Canvas, box: RectF, s: Series, u: Float) {
        val n = s.temp.size
        if (n < 2) return
        val labelPx = 11f * u
        val label = Faces.paint(Faces.regular, labelPx, Ink.SUB, Paint.Align.CENTER)
        val valuePaint = Faces.paint(Faces.medium, labelPx * 1.05f, Ink.TEXT, Paint.Align.CENTER)
        val left = box.left + 8f * u
        val right = box.right - 8f * u
        val top = box.top + labelPx + 6f * u
        val bottom = box.bottom - (if (s.ticks.isEmpty()) 2f * u else labelPx + 6f * u)
        val step = (right - left) / (n - 1)
        fun x(i: Int) = left + i * step

        // baseline
        c.drawLine(box.left, bottom, box.right, bottom, Paint().apply { color = Ink.GRID; strokeWidth = max(1f, u * 0.75f) })

        // rain bars (bottom half)
        var rMax = 2.0
        for (v in s.rain) if (!v.isNaN()) rMax = max(rMax, v)
        val barW = step * 0.56f
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        val rr = min(barW / 2f, 3f * u)
        for (i in 0 until n) {
            val v = s.rain.getOrNull(i) ?: continue
            if (v.isNaN() || v < 0.1) continue
            val bh = ((v / rMax).toFloat().coerceIn(0.06f, 1f)) * (bottom - top) * 0.5f
            bar.color = Ink.rain(v)
            val l = (x(i) - barW / 2).coerceAtLeast(box.left)
            val r = (x(i) + barW / 2).coerceAtMost(box.right)
            c.drawRoundRect(RectF(l, bottom - bh, r, bottom), rr, rr, bar)
        }

        // temperature
        val t = fillGaps(s.temp) ?: return drawTicks(c, s, ::x, box, label)
        var lo = t.min()
        var hi = t.max()
        if (hi - lo < 4.0) { val m = (hi + lo) / 2; lo = m - 2; hi = m + 2 }
        val plotTop = top + 4f * u
        val plotBot = bottom - (bottom - top) * 0.22f
        fun y(v: Double) = (plotBot - (v - lo) / (hi - lo) * (plotBot - plotTop)).toFloat()

        val line = Path()
        line.moveTo(x(0), y(t[0]))
        for (i in 1 until n) {
            val mx = (x(i - 1) + x(i)) / 2f
            line.cubicTo(mx, y(t[i - 1]), mx, y(t[i]), x(i), y(t[i]))
        }
        val area = Path(line).apply { lineTo(x(n - 1), bottom); lineTo(x(0), bottom); close() }
        c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, plotTop, 0f, bottom, Ink.withAlpha(Ink.CRIMSON_DEEP, 0x99), Ink.withAlpha(Ink.CRIMSON_DEEP, 0), Shader.TileMode.CLAMP)
        })
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(line, stroke.apply { color = Ink.withAlpha(Ink.CRIMSON, 0x40); strokeWidth = 6f * u })
        c.drawPath(line, stroke.apply {
            shader = LinearGradient(0f, plotTop, 0f, plotBot, Ink.CRIMSON_HI, Ink.CRIMSON, Shader.TileMode.CLAMP)
            strokeWidth = 2.2f * u
        })

        // max / min labels (first occurrence)
        val iMax = t.indices.maxBy { t[it] }
        val iMin = t.indices.minBy { t[it] }
        fun clampX(xx: Float, txt: String): Float {
            val half = valuePaint.measureText(txt) / 2f
            return xx.coerceIn(box.left + half, box.right - half)
        }
        val tMax = fmt(t[iMax])
        c.drawText(tMax, clampX(x(iMax), tMax), y(t[iMax]) - 5f * u, valuePaint)
        if (iMin != iMax && t[iMax] - t[iMin] >= 1.0) {
            val tMin = fmt(t[iMin])
            val yy = (y(t[iMin]) + labelPx + 4f * u).coerceAtMost(bottom - 2f * u)
            c.drawText(tMin, clampX(x(iMin), tMin), yy, valuePaint.apply { color = Ink.SUB })
        }

        // now dot
        val nx = x(0)
        val ny = y(t[0])
        c.drawCircle(nx, ny, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(nx, ny, 6f * u, Ink.withAlpha(Ink.CRIMSON, 0x80), Ink.withAlpha(Ink.CRIMSON, 0), Shader.TileMode.CLAMP)
        })
        c.drawCircle(nx, ny, 3.2f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(nx - u, ny - u, 3.6f * u, Ink.CRIMSON_HI, Ink.CRIMSON_DEEP, Shader.TileMode.CLAMP)
        })
        drawTicks(c, s, ::x, box, label)
    }

    private fun drawTicks(c: Canvas, s: Series, x: (Int) -> Float, box: RectF, p: Paint) {
        for ((i, txt) in s.ticks) {
            val half = p.measureText(txt) / 2f
            c.drawText(txt, x(i).coerceIn(box.left + half, box.right - half), box.bottom - p.descent(), p)
        }
    }

    fun fmt(v: Double): String = "%.0f°".format(v).replace("-", "−")

    /** NaNs replaced by the nearest valid neighbour; null if there is no valid value. */
    private fun fillGaps(a: DoubleArray): DoubleArray? {
        val first = a.indexOfFirst { !it.isNaN() }
        if (first < 0) return null
        val out = a.copyOf()
        for (i in 0 until first) out[i] = a[first]
        for (i in first + 1 until out.size) if (out[i].isNaN()) out[i] = out[i - 1]
        return out
    }
}
