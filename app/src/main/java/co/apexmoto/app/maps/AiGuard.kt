package co.apexmoto.app.maps

import co.apexmoto.app.core.norm

/**
 * Protecciones de la Ruta IA. Gemini no tiene acceso a nada de la app (ni base de datos, ni reglas, ni claves, ni datos
 * de usuarios, ni herramientas): solo recibe el pedido y una lista de lugares, y la app solo acepta de vuelta IDs de esa
 * lista y textos cortos que se muestran como texto plano. Esto agrega varias capas para que se limite a rutas:
 *  1. Antes de enviar: limpia el texto y no envía pedidos fuera de tema o con intentos de cambiarle las instrucciones.
 *  2. Al responder: quita enlaces, correos, teléfonos y código, y descarta frases sobre la app, claves o instrucciones.
 *  3. Límite de uso por teléfono (ver [allow]) para que un script no gaste el cupo.
 */
object AiGuard {

    /** Mensaje para todo lo que no sea armar rutas. */
    const val ONLY_ROUTES = "Solo puedo ayudarte a armar rutas en moto y recomendarte lugares para rodar. Cuéntame a dónde quieres ir o qué se te antoja."

    /** Gemini (o el filtro) decidió que el pedido no es sobre rutas. */
    class OffTopic : Exception(ONLY_ROUTES)

    /** Demasiadas consultas seguidas o en el día. */
    class TooMany(msg: String) : Exception(msg)

    const val MAX_INPUT = 300

    /** Quita caracteres de control y símbolos de código, y recorta el largo. */
    fun cleanInput(text: String): String = text
        .replace(Regex("[\\p{Cntrl}&&[^\\n]]"), " ")
        .replace(Regex("[<>{}\\[\\]`\\\\|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim().take(MAX_INPUT)

    // Intentos de cambiarle el papel o sacarle información, y temas de la app que no son rutas.
    private val BLOCKED = listOf(
        "ignora", "olvida tus", "olvida las", "olvida todo", "instruccion", "system prompt", "prompt", "jailbreak", "modo desarrollador",
        "actua como", "actua de", "eres ahora", "a partir de ahora eres", "finge", "pretende ser", "roleplay", "ignore", "act as", "you are now",
        "api key", "apikey", "clave de", "tu clave", "contrasena", "password", "token", "credencial",
        "firebase", "firestore", "base de datos", "servidor", "backend", "reglas de", "codigo fuente", "configuracion", "configurar la app",
        "administrador", "admin", "root", "sql", "select ", "drop table", "script", "javascript", "http://", "https://", "www.",
        "correo de", "telefono de", "cedula", "direccion de", "datos de otros", "datos personales",
    )

    /** null si el pedido se puede enviar; si no, el motivo para mostrar. */
    fun checkInput(text: String): String? {
        val n = norm(text)
        if (BLOCKED.any { n.contains(it) }) return ONLY_ROUTES
        return null
    }

    private val URL = Regex("(https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("\\S+@\\S+\\.\\S+")
    private val PHONE = Regex("(\\+?\\d[\\d\\s-]{6,}\\d)")
    private val CODE = Regex("[<>{}\\[\\]`\\\\|*#_]")
    // Palabras completas (con límites de palabra) para no tumbar textos normales como "rápido" o "capital".
    private val OUT_BLOCKED = Regex("\\b(instrucciones?|prompt|api|claves?|contrasenas?|tokens?|firebase|firestore|configuracion|codigo|servidor|" +
        "base de datos|administrador|gemini|modelo de lenguaje|inteligencia artificial)\\b")

    /** Texto de Gemini listo para mostrar: sin enlaces, correos, teléfonos ni código; vacío si habla de otra cosa. */
    fun cleanOutput(s: String, max: Int): String {
        val t = s.replace(URL, "").replace(EMAIL, "").replace(PHONE, "").replace(CODE, "").replace(Regex("\\s+"), " ").trim().take(max)
        val n = norm(t)
        return if (OUT_BLOCKED.containsMatchIn(n)) "" else t
    }

    // Límite de uso en el teléfono: una consulta cada 15 s y como mucho 30 al día.
    private const val MIN_GAP_MS = 15_000L
    private const val MAX_PER_DAY = 30

    /** Revisa y registra una consulta. [day] y [count] vienen de las preferencias del teléfono. */
    fun allow(lastAt: Long, day: String, count: Int, today: String, now: Long = System.currentTimeMillis()): Pair<Boolean, String?> {
        if (now - lastAt < MIN_GAP_MS) return false to "Espera unos segundos antes de pedir otra ruta."
        val used = if (day == today) count else 0
        if (used >= MAX_PER_DAY) return false to "Llegaste al máximo de rutas con IA por hoy. Mañana puedes pedir más; mientras tanto usa «Yo elijo destino»."
        return true to null
    }
}
