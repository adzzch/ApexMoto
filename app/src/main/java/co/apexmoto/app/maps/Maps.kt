package co.apexmoto.app.maps

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Coordenada propia (sin depender de Google Maps). */
data class LatLng(val latitude: Double, val longitude: Double)

data class NavStop(val name: String, val lat: Double, val lng: Double)
data class NavSession(val title: String, val stops: List<NavStop>)
data class RouteStep(val text: String, val distanceM: Int, val start: LatLng)
data class RoutePlan(val distanceM: Int, val durationS: Int, val points: List<LatLng>, val steps: List<RouteStep>)

/**
 * Cliente de OpenRouteService (https://openrouteservice.org). Gratuito con clave de correo, sin tarjeta.
 * Calcula rutas en auto con paradas intermedias e instrucciones en español.
 */
class RoutesClient(private val apiKey: String) {
    private val http = OkHttpClient()
    val configured: Boolean get() = apiKey.isNotBlank()

    suspend fun route(origin: LatLng, dest: LatLng, via: List<LatLng> = emptyList()): RoutePlan = withContext(Dispatchers.IO) {
        if (!configured) error("Falta ORS_API_KEY en local.properties")
        val pts = listOf(origin) + via + dest
        val coords = JSONArray(pts.map { JSONArray(listOf(it.longitude, it.latitude)) })
        val body = JSONObject().put("coordinates", coords).put("language", "es").put("instructions", true)
            // -1 = sin límite de búsqueda: acepta puntos alejados de una vía (comercios en la montaña)
            .put("radiuses", JSONArray(pts.map { -1 }))
        val req = Request.Builder().url("https://api.openrouteservice.org/v2/directions/driving-car")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", apiKey)
            .header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(txt).getJSONObject("error").getString("message") }.getOrNull() ?: txt.take(160)
                error(when (r.code) {
                    401, 403 -> "OpenRouteService rechazó la clave (${r.code}). Revisa ORS_API_KEY en local.properties."
                    429 -> "Se alcanzó el límite diario de OpenRouteService. Inténtalo más tarde."
                    else -> "OpenRouteService (${r.code}): $msg"
                })
            }
            parse(txt)
        }
    }

    private fun parse(txt: String): RoutePlan {
        val r = JSONObject(txt).optJSONArray("routes")?.optJSONObject(0) ?: error("No hay una ruta disponible para ese trayecto")
        val points = decode(r.getString("geometry"))
        val sum = r.getJSONObject("summary")
        val steps = mutableListOf<RouteStep>()
        val segs = r.optJSONArray("segments") ?: JSONArray()
        for (i in 0 until segs.length()) {
            val st = segs.getJSONObject(i).optJSONArray("steps") ?: continue
            for (j in 0 until st.length()) {
                val s = st.getJSONObject(j)
                val wp = s.optJSONArray("way_points")
                val start = points.getOrNull(wp?.optInt(0) ?: 0) ?: continue
                steps += RouteStep(forRider(s.optString("instruction")).ifBlank { "Continúa por la vía" }, s.optDouble("distance").toInt(), start)
            }
        }
        return RoutePlan(sum.optDouble("distance").toInt(), sum.optDouble("duration").toInt(), points, steps)
    }

    /** Decodifica un "encoded polyline" (precisión 5). */
    private fun decode(enc: String): List<LatLng> {
        val out = ArrayList<LatLng>(); var i = 0; var lat = 0; var lng = 0
        while (i < enc.length) {
            var b: Int; var shift = 0; var res = 0
            do { b = enc[i++].code - 63; res = res or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lat += if (res and 1 != 0) (res shr 1).inv() else res shr 1
            shift = 0; res = 0
            do { b = enc[i++].code - 63; res = res or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lng += if (res and 1 != 0) (res shr 1).inv() else res shr 1
            out += LatLng(lat / 1e5, lng / 1e5)
        }
        return out
    }
}

class LocationHelper(private val ctx: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(ctx)

    fun hasPermission() = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun last(): LatLng? {
        if (!hasPermission()) return null
        // Pide una ubicación fresca (si no llega en 4 s usa la última conocida, que puede ser vieja)
        val fresh = try {
            withTimeoutOrNull(4000) { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await() }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        val l = fresh ?: client.lastLocation.await() ?: return null
        return LatLng(l.latitude, l.longitude)
    }

    @SuppressLint("MissingPermission")
    fun updates(): Flow<Location> = callbackFlow {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).setMinUpdateDistanceMeters(5f).build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { trySend(it) } }
        }
        client.requestLocationUpdates(req, cb, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(cb) }
    }
}

/** Abre la navegación en la app de Google Maps (alternativa a la guía dentro de ApexMoto). */
fun openInGoogleMaps(ctx: Context, lat: Double, lng: Double) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=d"))
        .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(nav) } catch (e: ActivityNotFoundException) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** OpenRouteService traduce algunas instrucciones como si fueras a pie ("Camina hacia…"); se ajustan para moto. */
private fun forRider(t: String): String = when {
    t.startsWith("Camina ") -> "Avanza " + t.removePrefix("Camina ")
    t.startsWith("Camine ") -> "Avance " + t.removePrefix("Camine ")
    else -> t
}.replace(" caminando", "")
