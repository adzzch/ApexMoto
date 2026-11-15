package co.apexmoto.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Días hábiles en Colombia, para los plazos legales (retracto: 5 días hábiles, art. 47 Ley 1480;
 * consultas y reclamos de datos: 10 y 15 días hábiles, arts. 14 y 15 Ley 1581).
 * No cuentan sábados, domingos ni festivos (Ley 51 de 1983). Contar el sábado como no hábil favorece a la persona.
 */
object BusinessDays {
    private val ZONE: ZoneId = ZoneId.of("America/Bogota")

    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate()
    fun today(): LocalDate = LocalDate.now(ZONE)

    /** Domingo de Pascua (algoritmo gregoriano anónimo). */
    private fun easter(y: Int): LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(y, month, day)
    }

    /** Festivo que se traslada al lunes siguiente si no cae en lunes. */
    private fun toMonday(d: LocalDate): LocalDate = if (d.dayOfWeek == DayOfWeek.MONDAY) d else d.plusDays(((8 - d.dayOfWeek.value) % 7).toLong())

    private val cache = HashMap<Int, Set<LocalDate>>()

    fun holidays(y: Int): Set<LocalDate> = cache.getOrPut(y) {
        val e = easter(y)
        setOf(
            LocalDate.of(y, 1, 1), LocalDate.of(y, 5, 1), LocalDate.of(y, 7, 20), LocalDate.of(y, 8, 7), LocalDate.of(y, 12, 8), LocalDate.of(y, 12, 25),
            toMonday(LocalDate.of(y, 1, 6)), toMonday(LocalDate.of(y, 3, 19)), toMonday(LocalDate.of(y, 6, 29)), toMonday(LocalDate.of(y, 8, 15)),
            toMonday(LocalDate.of(y, 10, 12)), toMonday(LocalDate.of(y, 11, 1)), toMonday(LocalDate.of(y, 11, 11)),
            e.minusDays(3), e.minusDays(2),                                   // Jueves y Viernes Santo
            toMonday(e.plusDays(39)), toMonday(e.plusDays(60)), toMonday(e.plusDays(68)),   // Ascensión, Corpus Christi, Sagrado Corazón
        )
    }

    fun isBusinessDay(d: LocalDate): Boolean =
        d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && d !in holidays(d.year)

    /** Último día del plazo: n días hábiles contados a partir del día siguiente a [from]. */
    fun addBusinessDays(from: LocalDate, n: Int): LocalDate {
        var d = from; var left = n
        while (left > 0) { d = d.plusDays(1); if (isBusinessDay(d)) left-- }
        return d
    }
}

/** Plazo del derecho de retracto: 5 días hábiles desde que se celebró el contrato (art. 47 Ley 1480 de 2011). */
object Retract {
    const val DAYS = 5

    fun deadline(createdAtMillis: Long): LocalDate = BusinessDays.addBusinessDays(BusinessDays.dateOf(createdAtMillis), DAYS)

    /**
     * Aplica si el plazo no ha vencido y el servicio no ha empezado (excepción 1 del art. 47).
     * [serviceDate] es la fecha ISO de inicio (día de recogida de la moto).
     */
    fun applies(createdAtMillis: Long, serviceDate: String, started: Boolean): Boolean {
        if (started || createdAtMillis <= 0) return false
        val today = BusinessDays.today()
        val begins = runCatching { LocalDate.parse(serviceDate) }.getOrNull()
        return !today.isAfter(deadline(createdAtMillis)) && (begins == null || today.isBefore(begins))
    }
}
