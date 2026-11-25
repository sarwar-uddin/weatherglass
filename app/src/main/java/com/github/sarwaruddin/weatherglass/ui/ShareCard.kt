package com.github.sarwaruddin.weatherglass.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.github.sarwaruddin.weatherglass.R
import com.github.sarwaruddin.weatherglass.data.Bundle
import com.github.sarwaruddin.weatherglass.data.Forecast
import com.github.sarwaruddin.weatherglass.data.Meteo
import com.github.sarwaruddin.weatherglass.widget.Faces
import com.github.sarwaruddin.weatherglass.widget.Glyph
import com.github.sarwaruddin.weatherglass.widget.Ink
import com.github.sarwaruddin.weatherglass.widget.Plot
import com.github.sarwaruddin.weatherglass.widget.WidgetData
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A designed 1080×1350 PNG of the current reading (Instagram/WhatsApp portrait), drawn with
 * android.graphics and shared through FileProvider. Attribution is baked into the image.
 */
object ShareCard {

    private const val W = 1080
    private const val H = 1350
    private const val M = 64f // side margin

    fun share(context: Context, b: Bundle) {
        val f = b.forecast
        if (f == null) {
            Toast.makeText(context, context.getString(R.string.share_nothing), Toast.LENGTH_SHORT).show()
            return
        }
        val main = Handler(Looper.getMainLooper())
        Thread({
            val result = runCatching {
                val bmp = render(b, f, System.currentTimeMillis())
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // only ever keep the latest card
                val safe = b.place.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "place" }
                val file = File(dir, "weatherglass-$safe.png")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            }
            main.post {
                val uri = result.getOrNull()
                if (uri == null) {
                    Toast.makeText(context, "Couldn't make the card", Toast.LENGTH_SHORT).show()
                    return@post
                }
                val c = f.current
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "${b.place.name}: ${Fmt.temp(c.temp)}, ${Meteo.wmo(c.code).text}. Read with Weatherglass.")
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser))
                if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(chooser) }
            }
        }, "weatherglass-share").start()
    }

    // ---- rendering ------------------------------------------------------------------

    internal fun render(b: Bundle, f: Forecast, now: Long): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val off = f.offsetSec
        val cur = f.current
        val alt = Meteo.sunAltitude(now, b.place.lat, b.place.lng).toFloat()

        sky(c, alt, cur.cloud, cur.isDay, now)

        // header
        val name = shade(Faces.paint(Faces.regular, 60f, Ink.TEXT))
        c.drawText(ellipsize(b.place.name, name, 600f), M, 132f, name)
        if (b.place.region.isNotBlank()) {
            val region = shade(Faces.paint(Faces.regular, 34f, 0xFFB4B4BC.toInt()))
            c.drawText(ellipsize(b.place.region, region, 600f), M, 184f, region)
        }
        c.drawText(Fmt.time(now, off), W - M, 128f, shade(Faces.paint(Faces.light, 56f, Ink.TEXT, Paint.Align.RIGHT)))
        c.drawText(Fmt.fullDate(now, off), W - M, 184f, shade(Faces.paint(Faces.regular, 32f, 0xFFB4B4BC.toInt(), Paint.Align.RIGHT)))

        // hero
        Glyph.draw(c, cur.code, cur.isDay, W - M - 300f, 230f, 300f)
        val temp = shade(Faces.paint(Faces.thin, 290f, Ink.TEXT, spacing = -0.04f))
        c.drawText(Fmt.temp(cur.temp), M - 14f, 520f, temp)
        c.drawText(Meteo.wmo(cur.code).text, M, 604f, shade(Faces.paint(Faces.light, 58f, Ink.TEXT)))
        val d = f.daily
        val di = WidgetData.todayIndex(d, now, off)
        val feels = buildString {
            append("Feels ").append(Fmt.temp(cur.feels))
            if (di >= 0) append("   ·   H ").append(Fmt.temp(d.max[di])).append("   L ").append(Fmt.temp(d.min[di]))
        }
        c.drawText(feels, M, 664f, shade(Faces.paint(Faces.regular, 38f, 0xFFC4C4CC.toInt())))

        // pills: AQI, then the summary line
        var y = 700f
        val aqi = b.air?.now?.aqi?.takeUnless { it.isNaN() }
        if (aqi != null) {
            val cat = Meteo.aqiCat(aqi)
            pill(c, M, y, "AQI ${aqi.roundToInt()}  ·  ${cat.name}", cat.color.toInt(), 30f, 58f, dot = false)
            y += 76f
        }
        val line = runCatching { summary(f, b.air, now) }.getOrDefault("")
        if (line.isNotBlank()) pill(c, M, y, line, Ink.CRIMSON, 34f, 72f, dot = true)

        // 24-hour card
        val card1 = RectF(M - 16f, 864f, W - M + 16f, 1114f)
        glass(c, card1)
        c.drawText("NEXT 24 HOURS", card1.left + 32f, card1.top + 44f, Faces.paint(Faces.medium, 24f, Ink.SUB, spacing = 0.16f))
        val h = f.hourly
        if (h.size > 2) {
            val i0 = h.indexAt(now)
            val end = minOf(h.size, i0 + 25)
            if (end - i0 >= 3) {
                val ticks = ArrayList<Pair<Int, String>>()
                for (k in 0 until end - i0 step 6) ticks += k to if (k == 0) "Now" else Fmt.time(h.t[i0 + k], off)
                var rain = 0.0
                for (i in i0 until end) if (!h.precip[i].isNaN()) rain += h.precip[i]
                if (rain >= 0.1) {
                    c.drawText(
                        "Rain ${Fmt.num(rain, 1)} mm", card1.right - 32f, card1.top + 44f,
                        Faces.paint(Faces.regular, 26f, Ink.RAIN, Paint.Align.RIGHT),
                    )
                }
                val series = Plot.Series(h.temp.copyOfRange(i0, end), h.precip.copyOfRange(i0, end), ticks)
                Plot.draw(c, RectF(card1.left + 20f, card1.top + 50f, card1.right - 20f, card1.bottom - 14f), series, 2.1f)
            }
        }

        // 3-day outlook
        val card2 = RectF(M - 16f, 1132f, W - M + 16f, 1252f)
        glass(c, card2)
        val start = if (di >= 0) di + 1 else 0
        val n = minOf(3, d.size - start)
        if (n > 0) {
            val colW = card2.width() / 3f
            val dayP = Faces.paint(Faces.medium, 32f, Ink.TEXT)
            val hiP = Faces.paint(Faces.regular, 32f, Ink.TEXT)
            val loP = Faces.paint(Faces.regular, 32f, Ink.DIM)
            val popP = Faces.paint(Faces.regular, 24f, Ink.RAIN)
            for (k in 0 until n) {
                val i = start + k
                val x0 = card2.left + colW * k + 28f
                val cy = card2.centerY()
                c.drawText(Fmt.weekday(d.t[i] + 43_200_000L, off), x0, cy - 8f, dayP)
                val pop = d.popMax[i]
                if (!pop.isNaN() && pop >= 20) c.drawText("${pop.roundToInt()}%", x0, cy + 30f, popP)
                Glyph.draw(c, d.code[i], true, x0 + 76f, cy - 40f, 72f)
                val hi = Fmt.temp(d.max[i])
                c.drawText(hi, x0 + 160f, cy - 6f, hiP)
                c.drawText(Fmt.temp(d.min[i]), x0 + 160f, cy + 32f, loP)
                if (k > 0) {
                    c.drawLine(card2.left + colW * k, card2.top + 24f, card2.left + colW * k, card2.bottom - 24f,
                        Paint().apply { color = Ink.EDGE; strokeWidth = 2f })
                }
            }
        }

        footer(c)
        return bmp
    }

    // ---- pieces -----------------------------------------------------------------------

    private val ALT = floatArrayOf(-18f, -12f, -6f, -2f, 2f, 8f, 20f)
    private val TOP = intArrayOf(0xFF020308.toInt(), 0xFF04061A.toInt(), 0xFF0A0D2A.toInt(), 0xFF141A3A.toInt(), 0xFF1C2A4A.toInt(), 0xFF22385A.toInt(), 0xFF1E3A5E.toInt())
    private val MID = intArrayOf(0xFF04060E.toInt(), 0xFF080A22.toInt(), 0xFF1A1236.toInt(), 0xFF3A1838.toInt(), 0xFF3E3A58.toInt(), 0xFF3E5A7A.toInt(), 0xFF3E6688.toInt())
    private val HOR = intArrayOf(0xFF060812.toInt(), 0xFF120C26.toInt(), 0xFF4A1026.toInt(), 0xFFA0202E.toInt(), 0xFFC0503A.toInt(), 0xFF8A7A78.toInt(), 0xFF6A8AA6.toInt())

    private fun stop(arr: IntArray, alt: Float): Int {
        if (alt <= ALT[0]) return arr[0]
        for (i in 1 until ALT.size) if (alt <= ALT[i]) return Ink.lerp(arr[i - 1], arr[i], (alt - ALT[i - 1]) / (ALT[i] - ALT[i - 1]))
        return arr.last()
    }

    /** Sky for the sun's altitude, greyed by cloud, sinking into ink; stars at night. */
    private fun sky(c: Canvas, alt: Float, cloud: Double, isDay: Boolean, now: Long) {
        c.drawColor(Ink.BG)
        val grey = if (cloud.isNaN()) 0f else (cloud / 100.0).toFloat().coerceIn(0f, 1f) * 0.45f
        fun g(col: Int) = Ink.lerp(col, Ink.lerp(0xFF2A2C34.toInt(), col, 0.5f), grey)
        val skyH = 900f
        c.drawRect(0f, 0f, W.toFloat(), skyH, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, skyH,
                intArrayOf(g(stop(TOP, alt)), g(stop(MID, alt)), g(stop(HOR, alt)), Ink.BG),
                floatArrayOf(0f, 0.38f, 0.66f, 1f), Shader.TileMode.CLAMP,
            )
        })
        // stars when the sun is well down and the sky is not overcast
        val starK = ((-alt - 4f) / 10f).coerceIn(0f, 1f) * (1f - (if (cloud.isNaN()) 0f else (cloud / 100.0).toFloat())).coerceIn(0f, 1f)
        if (starK > 0.05f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            var seed = (now / 86_400_000L).toInt() * 7919 + 17
            fun rnd(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65535f }
            repeat(110) {
                val x = rnd() * W
                val y = rnd() * 640f
                val r = 1.2f + rnd() * 2.2f
                p.color = Ink.withAlpha(0xFFFFFFFF.toInt(), ((0.25f + rnd() * 0.75f) * starK * (1f - y / 760f) * 255).toInt())
                c.drawCircle(x, y, r, p)
            }
        }
        // a soft glow behind the hero glyph: warm by day, cool by night
        val glow = if (isDay) 0x33FF7A3A else 0x22C8D4FF
        c.drawCircle(W - M - 150f, 380f, 420f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(W - M - 150f, 380f, 420f, glow, 0x00000000, Shader.TileMode.CLAMP)
        })
        // crimson horizon vignette, very faint: the instrument's needle colour
        c.drawRect(0f, 760f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(W / 2f, H + 200f, 900f, 0x228A1028, 0x00000000, Shader.TileMode.CLAMP)
        })
    }

    /** House glass card: gradient body, glossy top, hairline edge. */
    private fun glass(c: Canvas, r: RectF) {
        val rad = 40f
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, r.top, 0f, r.bottom,
                intArrayOf(Ink.withAlpha(Ink.CARD_TOP, 0xF0), Ink.withAlpha(Ink.CARD, 0xF0), Ink.withAlpha(Ink.CARD, 0xF0)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        })
        c.save()
        c.clipRect(r.left, r.top, r.right, r.top + r.height() * 0.45f)
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.45f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        c.restore()
        val inset = RectF(r).apply { inset(1f, 1f) }
        c.drawRoundRect(inset, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0x40FFFFFF, Ink.EDGE, 0x14FFFFFF), null, Shader.TileMode.CLAMP)
        })
    }

    /** Rounded pill, glassy, outlined in [color]; [dot] puts a crimson bead before the text. */
    private fun pill(c: Canvas, x: Float, top: Float, text: String, color: Int, textPx: Float, h: Float, dot: Boolean) {
        val p = Faces.paint(Faces.regular, textPx, Ink.TEXT)
        val padL = if (dot) h * 0.82f else h * 0.42f
        val padR = h * 0.42f
        val label = ellipsize(text, p, W - 2 * M - padL - padR)
        val w = padL + p.measureText(label) + padR
        val r = RectF(x, top, x + w, top + h)
        if (dot) {
            c.drawRoundRect(r, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xE01C1C21.toInt(), 0xE00A0A0C.toInt(), Shader.TileMode.CLAMP)
            })
            c.drawRoundRect(RectF(r).apply { inset(1f, 1f) }, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f
                shader = LinearGradient(0f, r.top, 0f, r.bottom, 0x55FFFFFF, 0x22FFFFFF, Shader.TileMode.CLAMP)
            })
            val cx = x + h * 0.42f
            val cy = r.centerY()
            val br = h * 0.13f
            c.drawCircle(cx, cy, br * 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, br * 2.2f, Ink.withAlpha(color, 0x70), Ink.withAlpha(color, 0), Shader.TileMode.CLAMP)
            })
            c.drawCircle(cx, cy, br, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx - br * 0.35f, cy - br * 0.35f, br * 1.3f, Ink.CRIMSON_HI, Ink.CRIMSON_DEEP, Shader.TileMode.CLAMP)
            })
        } else {
            c.drawRoundRect(r, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Ink.withAlpha(color, 0x38) })
            c.drawRoundRect(RectF(r).apply { inset(1.5f, 1.5f) }, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3f; this.color = Ink.withAlpha(color, 0xB0)
            })
        }
        c.drawText(label, x + padL, r.centerY() - (p.ascent() + p.descent()) / 2f, p)
    }

    /** "WEATHERGLASS" in chrome spaced caps, the tagline, and the data licence. */
    private fun footer(c: Canvas) {
        val base = 1306f
        c.drawLine(M, 1270f, W - M, 1270f, Paint().apply {
            shader = LinearGradient(M, 0f, W - M, 0f, intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            strokeWidth = 2f
        })
        val word = Faces.paint(Faces.bold, 34f, Ink.TEXT, spacing = 0.32f)
        val asc = word.ascent()
        // chrome: top light, hard horizon at 50 %, bounce light
        word.shader = LinearGradient(
            0f, base + asc, 0f, base,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFC9C9D1.toInt(), 0xFF7A7A84.toInt(), 0xFF4A4A52.toInt(), 0xFF9A9AA4.toInt(), 0xFFE6E6EE.toInt()),
            floatArrayOf(0f, 0.3f, 0.5f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawText("WEATHERGLASS", M, base, word)
        var x = M + word.measureText("WEATHERGLASS") + 10f
        val dot = Faces.paint(Faces.bold, 34f, Ink.CRIMSON)
        c.drawText("·", x, base, dot)
        x += dot.measureText("·") + 14f
        c.drawText("Read the air.", x, base, Faces.paint(Faces.light, 30f, Ink.SUB, spacing = 0.06f))
        c.drawText("Data: Open-Meteo CC BY 4.0", W - M, base, Faces.paint(Faces.regular, 24f, Ink.DIM, Paint.Align.RIGHT))
    }

    /** Soft drop shadow so text holds up on a bright daytime sky. */
    private fun shade(p: Paint) = p.apply { setShadowLayer(10f, 0f, 2f, 0x66000000) }

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var end = s.length
        while (end > 1 && p.measureText(s, 0, end) + p.measureText("…") > max) end--
        return s.substring(0, end).trimEnd() + "…"
    }
}
