package co.apexmoto.app.maps

import co.apexmoto.app.core.C
import co.apexmoto.app.core.fmtMin
import co.apexmoto.app.core.norm
import co.apexmoto.app.data.MenuItem
import co.apexmoto.app.data.Venue
import kotlin.math.roundToInt

data class RouteFilters(
    val text: String = "", val corridor: String = "any", val start: String = "07:00", val full: Boolean = false,
    val pace: String = "m", val bigBike: Boolean = true, val cuisines: Set<String> = emptySet(),
    val cats: Set<String> = emptySet(), val budget: Long = 60000,
)
data class PlanStop(val venue: Venue, val why: List<String>, val eta: String)
data class RoutePlanResult(
    val corridor: String, val stops: List<PlanStop>, val km: Int, val mins: Int, val end: String,
    val fuelCost: Long, val food: Long, val inferred: List<String>, val title: String = "",
)

/** Recomendador local de paradas (mismo criterio de puntaje que el prototipo). */
object RoutePlanner {
    private fun avg(v: Venue): Long {
        val xs = v.menu.filter { it.cat != "Servicios" && it.on }.map(MenuItem::price)
        return if (xs.isEmpty()) 0 else xs.average().roundToInt().toLong()
    }

    /** Lo que se entiende del texto y de los filtros, y el puntaje de cada lugar según eso. */
    private class Taste(private val f: RouteFilters) {
        private val toks = norm(f.text).split(Regex("[^a-z0-9]+")).filter { it.length > 3 && it !in C.STOP }
        private val cu = f.cuisines.toMutableSet()
        val inferred = mutableListOf<String>()
        private val budget: Long

        init {
            for ((tag, ws) in C.KW) {
                if (tag !in cu && toks.any { t -> ws.any { w -> t.contains(w) || w.contains(t) } }) { cu += tag; inferred += tag }
            }
            budget = if (toks.any { it in C.CHEAP }) minOf(f.budget, 35000L) else f.budget
        }

        fun score(v: Venue): Pair<Double, List<String>> {
            var s = v.rating; val why = mutableListOf<String>()
            v.cuisines.forEach { if (it in cu) { s += 4; why += it } }
            val hay = norm((v.tags + v.cuisines + v.name + v.desc).joinToString(" "))
            toks.forEach { t ->
                if (hay.contains(t)) {
                    s += 2
                    val covered = C.KW.any { (k, ws) -> k in why && ws.any { w -> t.contains(w) || w.contains(t) } }
                    if (!covered) why += "«$t»"
                }
            }
            if (f.cats.isNotEmpty()) { if (v.types.any { it in f.cats }) s += 3 else s -= 2 }
            val a = avg(v)
            if (a > budget) s -= 3 else if (a > 0 && "moto" !in v.types) why += "En tu presupuesto"
            if (!v.open) s -= 10
            return s to why
        }
    }

    /** Escenario 1 · La IA elige: busca la mejor zona y sus mejores paradas. */
    fun generate(venues: List<Venue>, f: RouteFilters): RoutePlanResult? {
        val taste = Taste(f)
        val n = if (f.full) 5 else 3
        val cors = if (f.corridor == "any") C.CORRIDORS.map { it.key } else listOf(f.corridor)
        var best: Triple<String, Double, List<Pair<Venue, List<String>>>>? = null
        for (k in cors) {
            val picks = venues.filter { it.corridor == k }.map { it to taste.score(it) }.sortedByDescending { it.second.first }.take(n)
            if (picks.isEmpty()) continue
            val total = picks.sumOf { it.second.first }
            if (best == null || total > best.second) best = Triple(k, total, picks.map { it.first to it.second.second })
        }
        val b = best ?: return null
        return build(b.first, b.third, f, taste.inferred, "Ruta ${C.corridor(b.first).name}")
    }

    /**
     * Escenario 2 · Yo elijo destino: los lugares elegidos van sí o sí; la IA los ordena por distancia y,
     * si extras > 0, agrega paradas para comer o tomar café en las mismas zonas, según el gusto de la persona.
     */
    fun generateTo(venues: List<Venue>, dests: List<Venue>, f: RouteFilters, extras: Int): RoutePlanResult? {
        if (dests.isEmpty()) return null
        val taste = Taste(f)
        val ids = dests.map { it.id }.toSet()
        val zones = dests.map { it.corridor }.toSet()
        val added = if (extras <= 0) emptyList() else venues
            .filter { it.id !in ids && it.corridor in zones && it.open && it.types.any { t -> t == "food" || t == "cafe" } }
            .map { it to taste.score(it) }.sortedByDescending { it.second.first }.take(extras)
            .map { (v, s) -> v to (listOf("Sugerida para comer") + s.second) }
        val chosen = dests.map { it to listOf("Tu destino") } + added
        val title = if (dests.size == 1) "Ruta a ${dests.first().name}" else "Ruta a ${dests.size} destinos"
        return build(dests.first().corridor, chosen, f, taste.inferred, title)
    }

    /** Ordena por distancia desde Cali y calcula horas de llegada, kilómetros, gasolina y comida. */
    private fun build(corridor: String, picks: List<Pair<Venue, List<String>>>, f: RouteFilters, inferred: List<String>, title: String): RoutePlanResult {
        val chosen = picks.sortedBy { it.first.km }
        val sp = when (f.pace) { "t" -> 45; "r" -> 65; else -> 55 }
        var t = f.start.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val t0 = t; var prev = 0
        val stops = chosen.map { (v, why) ->
            t += ((v.km - prev).toDouble() / sp * 60).roundToInt()
            val eta = fmtMin(t); prev = v.km
            t += when (v.types.firstOrNull()) { "food" -> 45; "cafe" -> 30; "tour" -> 40; else -> 25 }
            PlanStop(v, why, eta)
        }
        val maxKm = chosen.last().first.km
        val kmTot = maxOf(maxKm * 2, 2)
        t += (maxKm.toDouble() / sp * 60).roundToInt()
        val gal = kmTot / (if (f.bigBike) 34.0 else 48.0)
        val food = chosen.filter { it.first.types != listOf("moto") }.sumOf { avg(it.first) }
        return RoutePlanResult(corridor, stops, kmTot, t - t0, fmtMin(t), (gal * 16000).roundToInt().toLong(), food, inferred, title)
    }
}
