package com.github.sarwaruddin.weatherglass.widget

import android.content.Context
import com.github.sarwaruddin.weatherglass.data.Bundle
import com.github.sarwaruddin.weatherglass.data.Daily
import com.github.sarwaruddin.weatherglass.data.Meteo
import com.github.sarwaruddin.weatherglass.data.Place
import com.github.sarwaruddin.weatherglass.data.Repo
import com.github.sarwaruddin.weatherglass.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Everything a widget shows, precomputed off the main thread from the disk cache. */
internal class WidgetModel(
    val place: String,
    val temp: String,
    val code: Int,
    val isDay: Boolean,
    val condition: String,
    val hiLo: String,
    val aqi: Int?,
    val aqiColor: Int,
    val updated: String,
    val next12: Plot.Series?,
    val days: List<Day>,
) {
    class Day(val name: String, val code: Int, val hi: String, val lo: String)
}

internal object WidgetData {

    fun selected(repo: Repo): Place {
        val all = repo.places()
        val key = repo.selectedKey()
        return all.firstOrNull { it.key == key } ?: all.first()
    }

    suspend fun load(context: Context): WidgetModel? = withContext(Dispatchers.IO) {
        runCatching {
            val repo = Repo(context)
            model(repo.cached(selected(repo)), System.currentTimeMillis())
        }.getOrNull()
    }

    fun model(b: Bundle, now: Long): WidgetModel? {
        val f = b.forecast ?: return null
        val off = f.offsetSec
        val h = f.hourly
        val c = f.current
        // An old cache shouldn't show a stale "now": read the hourly series at this instant instead.
        val fresh = now - f.fetched < 90 * 60_000L
        val i0 = if (h.size > 0) h.indexAt(now) else -1
        val temp = if (fresh || i0 < 0) c.temp else Meteo.at(h.t, h.temp, now) ?: c.temp
        val code = if (fresh || i0 < 0) c.code else h.code[i0]
        val isDay = if (fresh || i0 < 0) c.isDay else h.isDay[i0]

        val d = f.daily
        val di = todayIndex(d, now, off)
        val hiLo = if (di >= 0) "H ${Fmt.temp(d.max[di])}  L ${Fmt.temp(d.min[di])}" else ""

        val aqi = b.air?.now?.aqi?.takeUnless { it.isNaN() }
        val next = if (i0 >= 0 && h.size - i0 >= 6) {
            val end = minOf(h.size, i0 + 13)
            val ticks = ArrayList<Pair<Int, String>>()
            for (k in 0 until end - i0 step 3) ticks += k to if (k == 0) "Now" else Fmt.hour(h.t[i0 + k], off)
            Plot.Series(h.temp.copyOfRange(i0, end), h.precip.copyOfRange(i0, end), ticks)
        } else null

        val days = ArrayList<WidgetModel.Day>(3)
        val start = if (di >= 0) di + 1 else 0
        for (k in start until minOf(d.size, start + 3)) {
            days += WidgetModel.Day(Fmt.weekday(d.t[k] + 12 * 3_600_000L, off), d.code[k], Fmt.temp(d.max[k]), Fmt.temp(d.min[k]))
        }

        return WidgetModel(
            place = b.place.name.ifBlank { "—" },
            temp = Fmt.temp(temp),
            code = code,
            isDay = isDay,
            condition = Meteo.wmo(code).text,
            hiLo = hiLo,
            aqi = aqi?.toInt(),
            aqiColor = Meteo.aqiCat(aqi).color.toInt(),
            updated = Fmt.time(f.fetched, off),
            next12 = next,
            days = days,
        )
    }

    /** Index of the place's local today in the daily series, or -1. */
    fun todayIndex(d: Daily, now: Long, off: Int): Int {
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
}
