package com.github.sarwaruddin.weatherglass.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Open-Meteo client: URL builders, blocking-free fetchers and pure JSON parsers.
 * Parsers map JSON null to NaN (doubles), -1 (codes) or 0 (times).
 */
object Api {

    private const val USER_AGENT = "Weatherglass/0.2 (Android)"
    private const val TIMEOUT_MS = 15_000

    private val MODELS = listOf(
        "ecmwf_ifs025" to "ECMWF",
        "gfs_seamless" to "GFS",
        "icon_seamless" to "ICON",
        "gem_seamless" to "GEM",
        "jma_seamless" to "JMA",
    )

    // ---- URLs -------------------------------------------------------------------

    /** Two decimals, dot separator: ~1 km, never more precise than needed. */
    private fun coord(v: Double) = String.format(Locale.ROOT, "%.2f", v)

    private fun latLng(lat: Double, lng: Double) = "latitude=${coord(lat)}&longitude=${coord(lng)}"

    internal fun forecastUrl(lat: Double, lng: Double) =
        "https://api.open-meteo.com/v1/forecast?${latLng(lat, lng)}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,pressure_msl," +
            "weather_code,is_day,wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility," +
            "precipitation,uv_index" +
            "&minutely_15=precipitation" +
            "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,pressure_msl," +
            "precipitation,precipitation_probability,weather_code,cloud_cover,cloud_cover_low,cloud_cover_mid," +
            "cloud_cover_high,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,cape,visibility,is_day,shortwave_radiation,wet_bulb_temperature_2m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,daylight_duration," +
            "uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max," +
            "wind_direction_10m_dominant" +
            "&past_days=1&forecast_days=16&forecast_minutely_15=12&past_minutely_15=0" +
            "&timezone=auto&timeformat=unixtime"

    internal fun modelsUrl(lat: Double, lng: Double) =
        "https://api.open-meteo.com/v1/forecast?${latLng(lat, lng)}" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum" +
            "&models=${MODELS.joinToString(",") { it.first }}" +
            "&forecast_days=10&timezone=auto&timeformat=unixtime"

    internal fun airUrl(lat: Double, lng: Double) =
        "https://air-quality-api.open-meteo.com/v1/air-quality?${latLng(lat, lng)}" +
            "&current=us_aqi,pm2_5,pm10,nitrogen_dioxide,ozone,carbon_monoxide,sulphur_dioxide,dust" +
            "&hourly=pm2_5,pm10,us_aqi&past_days=1&forecast_days=4&timezone=auto&timeformat=unixtime"

    /** [endDate] is ISO "YYYY-MM-DD"; ERA5 lags real time by about five days. */
    internal fun archiveUrl(lat: Double, lng: Double, endDate: String) =
        "https://archive-api.open-meteo.com/v1/archive?${latLng(lat, lng)}" +
            "&start_date=1940-01-01&end_date=$endDate" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"

    internal fun searchUrl(q: String) =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q.trim(), "UTF-8")}" +
            "&count=8&language=en&format=json"

    // ---- fetchers ---------------------------------------------------------------

    suspend fun forecastJson(lat: Double, lng: Double): String = get(forecastUrl(lat, lng))
    suspend fun airJson(lat: Double, lng: Double): String = get(airUrl(lat, lng))
    suspend fun modelsJson(lat: Double, lng: Double): String = get(modelsUrl(lat, lng))
    suspend fun archiveJson(lat: Double, lng: Double, endDate: String): String = get(archiveUrl(lat, lng, endDate))
    suspend fun searchJson(q: String): String = get(searchUrl(q))

    /** GET [url] on the IO dispatcher; throws [IOException] on any non-200 reply. */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // Open-Meteo explains errors as {"error":true,"reason":"..."}.
                val reason = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
                }.getOrNull().orEmpty()
                throw IOException("HTTP $code from ${URL(url).host} $reason".trim())
            }
            val raw = conn.inputStream
            val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) GZIPInputStream(raw) else raw
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---- parsers ----------------------------------------------------------------

    fun parseForecast(json: String, fetched: Long): Forecast {
        val root = root(json)
        val c = root.obj("current")
        val current = Current(
            t = c["time"].sec(),
            temp = c["temperature_2m"].dbl(), rh = c["relative_humidity_2m"].dbl(),
            feels = c["apparent_temperature"].dbl(), dew = c["dew_point_2m"].dbl(),
            pressure = c["pressure_msl"].dbl(), code = c["weather_code"].code(),
            isDay = c["is_day"].dbl() == 1.0,
            wind = c["wind_speed_10m"].dbl(), windDir = c["wind_direction_10m"].dbl(),
            gust = c["wind_gusts_10m"].dbl(), cloud = c["cloud_cover"].dbl(),
            vis = c["visibility"].dbl(), precip = c["precipitation"].dbl(), uv = c["uv_index"].dbl(),
        )

        val m = root.obj("minutely_15")
        val mt = m.times("time")
        val nowcast = Nowcast(mt, m.doubles("precipitation", mt.size))

        val h = root.obj("hourly")
        val ht = h.times("time")
        val n = ht.size
        val hourly = Hourly(
            t = ht,
            temp = h.doubles("temperature_2m", n), rh = h.doubles("relative_humidity_2m", n),
            dew = h.doubles("dew_point_2m", n), feels = h.doubles("apparent_temperature", n),
            pressure = h.doubles("pressure_msl", n), precip = h.doubles("precipitation", n),
            pop = h.doubles("precipitation_probability", n), code = h.codes("weather_code", n),
            cloud = h.doubles("cloud_cover", n), cloudLow = h.doubles("cloud_cover_low", n),
            cloudMid = h.doubles("cloud_cover_mid", n), cloudHigh = h.doubles("cloud_cover_high", n),
            wind = h.doubles("wind_speed_10m", n), windDir = h.doubles("wind_direction_10m", n),
            gust = h.doubles("wind_gusts_10m", n), uv = h.doubles("uv_index", n),
            cape = h.doubles("cape", n), vis = h.doubles("visibility", n),
            solar = h.doubles("shortwave_radiation", n), wetBulb = h.doubles("wet_bulb_temperature_2m", n),
            isDay = h.doubles("is_day", n).let { a -> BooleanArray(n) { a[it] == 1.0 } },
        )

        val d = root.obj("daily")
        val dt = d.times("time")
        val k = dt.size
        val daily = Daily(
            t = dt, code = d.codes("weather_code", k),
            max = d.doubles("temperature_2m_max", k), min = d.doubles("temperature_2m_min", k),
            sunrise = d.times("sunrise", k), sunset = d.times("sunset", k),
            daylight = d.doubles("daylight_duration", k), uvMax = d.doubles("uv_index_max", k),
            precip = d.doubles("precipitation_sum", k), popMax = d.doubles("precipitation_probability_max", k),
            windMax = d.doubles("wind_speed_10m_max", k), windDir = d.doubles("wind_direction_10m_dominant", k),
        )

        return Forecast(
            fetched = fetched,
            offsetSec = root["utc_offset_seconds"].dbl().let { if (it.isNaN()) 0 else it.toInt() },
            elevation = root["elevation"].dbl(),
            current = current, nowcast = nowcast, hourly = hourly, daily = daily,
        )
    }

    fun parseAir(json: String): Air {
        val root = root(json)
        val c = root.obj("current")
        val now = AirNow(
            aqi = c["us_aqi"].dbl(), pm25 = c["pm2_5"].dbl(), pm10 = c["pm10"].dbl(),
            no2 = c["nitrogen_dioxide"].dbl(), o3 = c["ozone"].dbl(), co = c["carbon_monoxide"].dbl(),
            so2 = c["sulphur_dioxide"].dbl(), dust = c["dust"].dbl(),
        )
        val h = root.obj("hourly")
        val t = h.times("time")
        return Air(now, t, h.doubles("pm2_5", t.size), h.doubles("pm10", t.size), h.doubles("us_aqi", t.size))
    }

    /** Models whose every value is null (outside their domain) are dropped. */
    fun parseModels(json: String): Models {
        val d = root(json).obj("daily")
        val t = d.times("time")
        val runs = MODELS.mapNotNull { (id, label) ->
            val max = d.doubles("temperature_2m_max_$id", t.size)
            val min = d.doubles("temperature_2m_min_$id", t.size)
            val precip = d.doubles("precipitation_sum_$id", t.size)
            if (max.all { it.isNaN() } && min.all { it.isNaN() } && precip.all { it.isNaN() }) null
            else ModelRun(id, label, max, min, precip)
        }
        return Models(t, runs)
    }

    fun parseArchive(json: String, fetched: Long): Archive {
        val d = root(json).obj("daily")
        val dates = (d["time"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
        return Archive(
            fetched, dates,
            d.doubles("temperature_2m_max", dates.size), d.doubles("temperature_2m_min", dates.size),
        )
    }

    /** No "results" key means no matches. Region is the first-level admin area. */
    fun parseSearch(json: String): List<SearchHit> {
        val results = root(json)["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val lat = o["latitude"].dbl()
            val lng = o["longitude"].dbl()
            if (lat.isNaN() || lng.isNaN()) return@mapNotNull null
            SearchHit(o["name"].str(), o["admin1"].str(), o["country"].str(), lat, lng)
        }
    }

    // ---- JSON helpers -----------------------------------------------------------

    private fun root(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw IllegalArgumentException("missing \"$key\"")

    private fun JsonElement?.dbl(): Double = (this as? JsonPrimitive)?.doubleOrNull ?: Double.NaN

    private fun JsonElement?.str(): String =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun JsonElement?.code(): Int = dbl().let { if (it.isNaN()) -1 else it.toInt() }

    /** Epoch seconds to ms; null becomes 0. */
    private fun JsonElement?.sec(): Long = dbl().let { if (it.isNaN()) 0L else it.toLong() * 1000L }

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    /** Array of [n] doubles; a missing key yields all-NaN. */
    private fun JsonObject.doubles(key: String, n: Int): DoubleArray {
        val a = array(key) ?: return DoubleArray(n) { Double.NaN }
        return DoubleArray(n) { if (it < a.size) a[it].dbl() else Double.NaN }
    }

    private fun JsonObject.codes(key: String, n: Int): IntArray {
        val a = array(key) ?: return IntArray(n) { -1 }
        return IntArray(n) { if (it < a.size) a[it].code() else -1 }
    }

    /** Unix-second times as ms; [n] defaults to the array's own length. */
    private fun JsonObject.times(key: String, n: Int = -1): LongArray {
        val a = array(key) ?: return LongArray(maxOf(n, 0))
        val size = if (n < 0) a.size else n
        return LongArray(size) { if (it < a.size) a[it].sec() else 0L }
    }
}
