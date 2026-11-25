package com.github.sarwaruddin.weatherglass.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToLong

/**
 * Saved places (SharedPreferences) plus a raw-JSON disk cache of every Open-Meteo
 * response, refreshed part by part.
 */
class Repo(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("places", Context.MODE_PRIVATE)
    private val cacheDir = File(app.filesDir, "cache-v1")

    private enum class Kind(val file: String) { FORECAST("forecast"), AIR("air"), MODELS("models"), ARCHIVE("archive") }

    // ---- places -----------------------------------------------------------------

    fun places(): List<Place> {
        val list = runCatching {
            val raw = prefs.getString(KEY_PLACES, null) ?: return@runCatching emptyList()
            (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val lng = (o["lng"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                Place(
                    name = (o["name"] as? JsonPrimitive)?.content.orEmpty(),
                    region = (o["region"] as? JsonPrimitive)?.content.orEmpty(),
                    lat = lat, lng = lng,
                    gps = (o["gps"] as? JsonPrimitive)?.booleanOrNull ?: false,
                )
            }
        }.getOrDefault(emptyList())
        return list.ifEmpty { DEFAULT_PLACES }
    }

    fun savePlaces(list: List<Place>) {
        val arr = buildJsonArray {
            for (p in list) add(buildJsonObject {
                put("name", p.name); put("region", p.region)
                put("lat", p.lat); put("lng", p.lng); put("gps", p.gps)
            })
        }
        prefs.edit().putString(KEY_PLACES, arr.toString()).apply()
    }

    fun selectedKey(): String? = prefs.getString(KEY_SELECTED, null)

    fun setSelectedKey(k: String) {
        prefs.edit().putString(KEY_SELECTED, k).apply()
    }

    // ---- cache ------------------------------------------------------------------

    private fun file(place: Place, kind: Kind): File {
        val safe = place.key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(cacheDir, "$safe-${kind.file}.json")
    }

    /** Age of a cached part in ms, or null if it is not on disk. */
    private fun age(place: Place, kind: Kind, now: Long): Long? =
        file(place, kind).takeIf { it.isFile }?.let { now - it.lastModified() }

    private fun write(place: Place, kind: Kind, json: String) {
        cacheDir.mkdirs()
        val target = file(place, kind)
        val tmp = File(cacheDir, target.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun <T> read(place: Place, kind: Kind, parse: (String, Long) -> T): T? = runCatching {
        val f = file(place, kind)
        if (!f.isFile) null else parse(f.readText(), f.lastModified())
    }.getOrNull()

    /**
     * Whatever is on disk for [place], parsed; unreadable parts are null.
     * Blocking file IO (the archive is ~730 KB): call it off the main thread.
     */
    fun cached(place: Place): Bundle = Bundle(
        place = place,
        forecast = read(place, Kind.FORECAST) { s, t -> Api.parseForecast(s, t) },
        air = read(place, Kind.AIR) { s, _ -> Api.parseAir(s) },
        models = read(place, Kind.MODELS) { s, _ -> Api.parseModels(s) },
        archive = read(place, Kind.ARCHIVE) { s, t -> Api.parseArchive(s, t) },
    )

    /**
     * Fetch the stale parts of [place] in parallel. Each part that arrives is cached and
     * reported through [onUpdate] as a freshly merged [Bundle]. One failure doesn't stop
     * the others; only if every requested part fails is the first error rethrown.
     */
    suspend fun refresh(place: Place, force: Boolean, onUpdate: (Bundle) -> Unit) {
        val now = System.currentTimeMillis()
        val start = withContext(Dispatchers.IO) { cached(place) }
        val lock = Mutex()
        var latest = start

        fun stale(kind: Kind, maxAge: Long): Boolean {
            val a = age(place, kind, now)
            return force || a == null || a !in 0..maxAge
        }

        /** Fetch, parse, cache and publish one part. */
        suspend fun <T> part(
            kind: Kind, fetch: suspend () -> String, parse: (String, Long) -> T, merge: (Bundle, T) -> Bundle,
        ) {
            val json = fetch()
            val value = withContext(Dispatchers.Default) { parse(json, System.currentTimeMillis()) }
            withContext(Dispatchers.IO) { write(place, kind, json) }
            lock.withLock {
                latest = merge(latest, value)
                onUpdate(latest)
            }
        }

        val lat = place.lat
        val lng = place.lng
        val jobs = ArrayList<suspend () -> Unit>()
        if (stale(Kind.FORECAST, FRESH_MS)) jobs += suspend {
            part(Kind.FORECAST, { Api.forecastJson(lat, lng) }, Api::parseForecast) { b, v -> b.copy(forecast = v) }
        }
        if (stale(Kind.AIR, FRESH_MS)) jobs += suspend {
            part(Kind.AIR, { Api.airJson(lat, lng) }, { s, _ -> Api.parseAir(s) }) { b, v -> b.copy(air = v) }
        }
        if (stale(Kind.MODELS, FRESH_MS)) jobs += suspend {
            part(Kind.MODELS, { Api.modelsJson(lat, lng) }, { s, _ -> Api.parseModels(s) }) { b, v -> b.copy(models = v) }
        }
        // History changes slowly: monthly, and `force` doesn't re-pull ~730 KB.
        val archiveAge = age(place, Kind.ARCHIVE, now)
        if (archiveAge == null || archiveAge !in 0..ARCHIVE_MS || start.archive == null) {
            val end = LocalDate.now().minusDays(6).toString()
            jobs += suspend {
                part(Kind.ARCHIVE, { Api.archiveJson(lat, lng, end) }, Api::parseArchive) { b, v -> b.copy(archive = v) }
            }
        }
        if (jobs.isEmpty()) return

        val results = supervisorScope {
            jobs.map { job -> async { runCatching { job() } } }.awaitAll()
        }
        currentCoroutineContext().ensureActive() // don't report cancellation as a fetch failure
        val failures = results.mapNotNull { it.exceptionOrNull() }
        if (failures.size == results.size) throw failures.first()
    }

    // ---- search & location ------------------------------------------------------

    suspend fun search(q: String): List<SearchHit> {
        if (q.isBlank()) return emptyList()
        val json = Api.searchJson(q)
        return withContext(Dispatchers.Default) { Api.parseSearch(json) }
    }

    /**
     * Coarse current position, named by the platform geocoder when available.
     * The caller must hold a location permission; returns null on denial or timeout.
     */
    @SuppressLint("MissingPermission")
    suspend fun locate(): Place? {
        val lm = ContextCompat.getSystemService(app, LocationManager::class.java) ?: return null
        val loc = try {
            currentLocation(lm) ?: lastKnown(lm)
        } catch (e: SecurityException) {
            null
        } ?: return null
        val lat = round2(loc.latitude)
        val lng = round2(loc.longitude)
        val (name, region) = placeName(lat, lng) // rounded first: the geocoder may be a network service
        return Place(name, region, lat, lng, gps = true)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(lm: LocationManager): Location? {
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    lm, provider, signal, ContextCompat.getMainExecutor(app),
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    /** (name, region) from the platform geocoder, or "Current location". */
    @Suppress("DEPRECATION")
    private suspend fun placeName(lat: Double, lng: Double): Pair<String, String> {
        val fallback = "Current location" to ""
        if (!Geocoder.isPresent()) return fallback
        return withContext(Dispatchers.IO) {
            runCatching {
                val a = Geocoder(app, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
                    ?: return@runCatching fallback
                val name = a.locality ?: a.subAdminArea ?: return@runCatching fallback.copy(second = a.adminArea.orEmpty())
                name to a.adminArea.orEmpty()
            }.getOrDefault(fallback)
        }
    }

    private fun round2(v: Double) = (v * 100).roundToLong() / 100.0

    private companion object {
        const val KEY_PLACES = "places"
        const val KEY_SELECTED = "selected"
        const val FRESH_MS = 15 * 60 * 1000L
        const val ARCHIVE_MS = 30L * 24 * 60 * 60 * 1000
        const val LOCATE_TIMEOUT_MS = 10_000L
        val DEFAULT_PLACES = listOf(Place("Dhaka", "Bangladesh", 23.81, 90.41))
    }
}
