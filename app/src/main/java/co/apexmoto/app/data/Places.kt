package co.apexmoto.app.data

import android.content.Context
import co.apexmoto.app.core.haversine
import co.apexmoto.app.core.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Catálogo de lugares para rodar y rodadas famosas de Colombia. Viene dentro de la app (assets/places/colombia.json),
 * así que funciona sin internet y no cuesta lecturas de Firestore. Se genera con tools/places/build_places.py a partir
 * de la lista curada de ApexMoto y de OpenStreetMap (© colaboradores de OpenStreetMap, ODbL).
 */
/** level: 2 = ciudad, 1 = municipio o pueblo, 0 = vereda o caserío. */
data class Town(val name: String, val dept: String, val lat: Double, val lng: Double, val pop: Int, val level: Int) {
    val city get() = level == 2
    val label get() = if (dept.isBlank() || dept == name) name else "$name, $dept"
}

data class Place(
    val id: String, val name: String, val kind: String, val dept: String, val town: String,
    val lat: Double, val lng: Double, val desc: String, val tip: String, val famous: Boolean,
)

data class RidePoint(val name: String, val lat: Double, val lng: Double)

data class FamousRide(
    val id: String, val name: String, val dept: String, val level: String, val road: String,
    val km: Int, val hours: Double, val desc: String, val tip: String, val pts: List<RidePoint>,
) {
    val start get() = pts.first()
}

/** Punto de encuentro seguro: estación de servicio, supermercado, centro comercial, peaje o un lugar conocido. */
data class MeetPoint(val id: String, val name: String, val kind: String, val dept: String, val town: String, val area: String,
                     val lat: Double, val lng: Double) {
    /** "D1 · Los Chorros, Cali": lo que se guarda en la rodada y ven los demás. */
    val label get() = name + listOf(area, town).filter { it.isNotBlank() && !name.contains(it, ignoreCase = true) }.distinct()
        .joinToString(", ").let { if (it.isBlank()) "" else " · $it" }
}

/** Un lugar o una rodada con la distancia (en línea recta) desde donde está la persona. */
data class Near<T>(val item: T, val km: Double)

class PlacesCatalog(val towns: List<Town>, val places: List<Place>, val rides: List<FamousRide>, val attribution: String,
                    val meet: List<MeetPoint> = emptyList()) {

    /** Población más cercana: así se sabe "en qué ciudad está" sin mandar la ubicación a ningún servicio. */
    fun nearestTown(lat: Double, lng: Double): Town? {
        // Primero una ciudad o municipio a menos de 15 km; si no hay, lo más cercano (aunque sea una vereda).
        val near = towns.map { it to distanceKm(lat, lng, it.lat, it.lng) }
        return near.filter { it.first.level >= 1 && it.second <= 15 }.minByOrNull { it.second }?.first ?: near.minByOrNull { it.second }?.first
    }

    /** Busca una población por nombre (sin tildes ni mayúsculas); primero las ciudades y las más grandes. */
    fun searchTowns(q: String, limit: Int = 30): List<Town> {
        val k = norm(q).trim()
        val base = if (k.isEmpty()) towns.filter { it.city } else towns.filter { norm(it.name).contains(k) || (it.level >= 1 && norm(it.dept).contains(k)) }
        return base.sortedWith(compareByDescending<Town> { norm(it.name).startsWith(k) }.thenByDescending { it.level }.thenByDescending { it.pop }).take(limit)
    }

    fun town(name: String): Town? = towns.firstOrNull { it.label == name } ?: towns.filter { it.name == name }.maxByOrNull { it.level }

    /**
     * Lugares recomendados alrededor de un punto. Los famosos (lista curada) cuentan como si estuvieran a un tercio de
     * su distancia real, para que salgan primero sin esconder los que están muy cerca.
     */
    fun placesNear(lat: Double, lng: Double, radiusKm: Int, kinds: Set<String> = emptySet()): List<Near<Place>> =
        places.asSequence()
            .filter { kinds.isEmpty() || it.kind in kinds || (it.kind == "atractivo" && "atractivo" in kinds) }
            .map { Near(it, distanceKm(lat, lng, it.lat, it.lng)) }
            .filter { it.km <= radiusKm }
            .sortedBy { if (it.item.famous) it.km / 3 else it.km }
            .toList()

    /** Rodadas ordenadas por qué tan cerca empiezan. */
    fun ridesNear(lat: Double, lng: Double): List<Near<FamousRide>> =
        rides.map { Near(it, distanceKm(lat, lng, it.start.lat, it.start.lng)) }.sortedBy { it.km }

    fun search(q: String, lat: Double, lng: Double, limit: Int = 40): List<Near<Place>> {
        val k = norm(q).trim()
        if (k.length < 2) return emptyList()
        return places.asSequence()
            .filter { norm(it.name).contains(k) || norm(it.town).contains(k) || norm(it.dept).contains(k) }
            .map { Near(it, distanceKm(lat, lng, it.lat, it.lng)) }
            .sortedWith(compareByDescending<Near<Place>> { it.item.famous }.thenBy { it.km })
            .take(limit).toList()
    }

    /**
     * Puntos de encuentro mientras se escribe: coinciden con el nombre, el barrio o el municipio (sin tildes) y se ordenan
     * por cercanía. Sin texto, los más cercanos. Los lugares famosos también sirven de punto de encuentro.
     */
    fun meetSuggestions(q: String, lat: Double, lng: Double, limit: Int = 40): List<Near<MeetPoint>> {
        val words = norm(q).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        val all = meet.asSequence() + places.asSequence().filter { it.famous }.map { MeetPoint(it.id, it.name, "lugar", it.dept, it.town, "", it.lat, it.lng) }
        return all
            .filter { m -> words.isEmpty() || norm("${m.name} ${m.area} ${m.town}").let { h -> words.all { h.contains(it) } } }
            .map { Near(it, distanceKm(lat, lng, it.lat, it.lng)) }
            .filter { words.isNotEmpty() || it.km <= 30 }
            .sortedBy { it.km }
            .take(limit).toList()
    }

    companion object {
        fun parse(text: String): PlacesCatalog {
            val o = JSONObject(text)
            val towns = o.getJSONArray("towns").rows { a ->
                Town(a.getString(0), a.getString(1), a.getDouble(2), a.getDouble(3), a.optInt(4), a.optInt(5))
            }
            val places = o.getJSONArray("places").rows { a ->
                Place(a.getString(0), a.getString(1), a.getString(2), a.getString(3), a.getString(4), a.getDouble(5), a.getDouble(6),
                    a.optString(7), a.optString(8), a.optInt(9) == 1)
            }
            val rides = o.getJSONArray("rides").let { arr ->
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    FamousRide(r.getString("id"), r.getString("name"), r.getString("dept"), r.getString("level"), r.getString("road"),
                        r.getInt("km"), r.getDouble("hours"), r.getString("desc"), r.optString("tip"),
                        r.getJSONArray("pts").rows { p -> RidePoint(p.getString(0), p.getDouble(1), p.getDouble(2)) })
                }
            }
            // Puntos de encuentro: [id, nombre, tipo, departamento, municipio, barrio, lat, lng]
            val meet = o.optJSONArray("meet")?.rows { a ->
                MeetPoint(a.getString(0), a.getString(1), a.getString(2), a.getString(3), a.getString(4), a.getString(5), a.getDouble(6), a.getDouble(7))
            }.orEmpty()
            return PlacesCatalog(towns, places, rides, o.optString("attribution"), meet)
        }

        private inline fun <T> JSONArray.rows(f: (JSONArray) -> T): List<T> = (0 until length()).map { f(getJSONArray(it)) }
    }
}

/** Carga el catálogo una sola vez (en segundo plano) y lo deja en memoria. */
class PlacesLoader(private val ctx: Context) {
    private val lock = Mutex()
    @Volatile private var cached: PlacesCatalog? = null

    suspend fun get(): PlacesCatalog = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.IO) {
            PlacesCatalog.parse(ctx.assets.open("places/colombia.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        }.also { cached = it }
    }
}

/** Distancia en línea recta, en km. */
fun distanceKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double = haversine(aLat, aLng, bLat, bLng) / 1000.0
