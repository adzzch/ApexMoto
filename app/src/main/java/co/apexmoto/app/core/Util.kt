package co.apexmoto.app.core

import java.text.NumberFormat
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.*

fun cop(n: Number): String = "$" + NumberFormat.getIntegerInstance(Locale("es", "CO")).format(Math.round(n.toDouble()))
fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
fun today(): String = LocalDate.now().toString()
fun addDays(d: String, n: Long): String = LocalDate.parse(d).plusDays(n).toString()
fun daysBetween(a: String, b: String): Int = ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)).toInt()
fun fmtMin(m: Int): String { val x = ((m % 1440) + 1440) % 1440; return "%02d:%02d".format(x / 60, x % 60) }

/** Distancia en metros entre dos coordenadas. */
fun haversine(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(aLat); val p2 = Math.toRadians(bLat)
    val dp = p2 - p1; val dl = Math.toRadians(bLng - aLng)
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** booked: lista de "desde|hasta" (ambos inclusive). */
fun overlaps(booked: List<String>, from: String, to: String): Boolean =
    booked.any { val p = it.split("|"); p.size == 2 && !(to < p[0] || from > p[1]) }

fun fmtDist(m: Double): String = if (m >= 1000) "%.1f km".format(m / 1000) else "${m.roundToInt()} m"
fun fmtDur(s: Int): String { val m = (s / 60.0).roundToInt(); return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }
