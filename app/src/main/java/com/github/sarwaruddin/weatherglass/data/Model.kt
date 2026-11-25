package com.github.sarwaruddin.weatherglass.data

/**
 * Everything the screen shows for one place. Arrays are parallel; missing values are NaN.
 * All times are epoch milliseconds (UTC). `offsetSec` is the place's UTC offset, for display.
 */

data class Place(
    val name: String,
    val region: String,
    val lat: Double,
    val lng: Double,
    val gps: Boolean = false,
) {
    /** Cache/identity key. */
    val key: String get() = if (gps) "gps" else String.format(java.util.Locale.ROOT, "%.2f,%.2f", lat, lng)
}

data class Current(
    val t: Long, val temp: Double, val rh: Double, val feels: Double, val dew: Double,
    val pressure: Double, val code: Int, val isDay: Boolean, val wind: Double, val windDir: Double,
    val gust: Double, val cloud: Double, val vis: Double, val precip: Double, val uv: Double,
)

class Hourly(
    val t: LongArray,
    val temp: DoubleArray, val rh: DoubleArray, val dew: DoubleArray, val feels: DoubleArray,
    val pressure: DoubleArray, val precip: DoubleArray, val pop: DoubleArray, val code: IntArray,
    val cloud: DoubleArray, val cloudLow: DoubleArray, val cloudMid: DoubleArray, val cloudHigh: DoubleArray,
    val wind: DoubleArray, val windDir: DoubleArray, val gust: DoubleArray, val uv: DoubleArray,
    val cape: DoubleArray, val vis: DoubleArray, val isDay: BooleanArray,
    /** Global horizontal irradiance, W/m² (mean of the preceding hour). */
    val solar: DoubleArray,
    /** Model wet-bulb temperature, °C. */
    val wetBulb: DoubleArray,
) {
    val size get() = t.size
    /** Index of the hour containing instant ms (clamped to the series). */
    fun indexAt(ms: Long): Int {
        var i = 0
        while (i + 1 < t.size && t[i + 1] <= ms) i++
        return i
    }
}

class Daily(
    val t: LongArray, val code: IntArray, val max: DoubleArray, val min: DoubleArray,
    val sunrise: LongArray, val sunset: LongArray, val daylight: DoubleArray, val uvMax: DoubleArray,
    val precip: DoubleArray, val popMax: DoubleArray, val windMax: DoubleArray, val windDir: DoubleArray,
) {
    val size get() = t.size
}

/** Next ~3 hours in 15-minute steps (mm per 15 min). */
class Nowcast(val t: LongArray, val precip: DoubleArray)

class Forecast(
    val fetched: Long,
    val offsetSec: Int,
    val elevation: Double,
    val current: Current,
    val nowcast: Nowcast,
    val hourly: Hourly,
    val daily: Daily,
)

data class AirNow(
    val aqi: Double, val pm25: Double, val pm10: Double, val no2: Double,
    val o3: Double, val co: Double, val so2: Double, val dust: Double,
)

/** Hourly PM2.5 and US AQI, yesterday through +4 days. */
class Air(val now: AirNow, val t: LongArray, val pm25: DoubleArray, val pm10: DoubleArray, val aqi: DoubleArray)

/** One numerical weather model's daily run. */
class ModelRun(val id: String, val label: String, val max: DoubleArray, val min: DoubleArray, val precip: DoubleArray)

/** Daily runs from several models over the same days `t` (local midnights, epoch ms). */
class Models(val t: LongArray, val runs: List<ModelRun>)

/** ERA5 daily highs/lows since 1940 ("YYYY-MM-DD" dates), for climatology. */
class Archive(val fetched: Long, val dates: List<String>, val hi: DoubleArray, val lo: DoubleArray)

/** Geocoding search hit. */
data class SearchHit(val name: String, val region: String, val country: String, val lat: Double, val lng: Double)

/** What the screen renders. Any part may be null while loading or after a failure. */
data class Bundle(
    val place: Place,
    val forecast: Forecast? = null,
    val air: Air? = null,
    val models: Models? = null,
    val archive: Archive? = null,
)
