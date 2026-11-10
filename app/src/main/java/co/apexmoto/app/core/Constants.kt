package co.apexmoto.app.core

data class Corridor(val key: String, val name: String, val lat: Double, val lng: Double)
data class Plan(val key: String, val name: String, val perDay: Long, val desc: String)
data class Extra(val key: String, val name: String, val price: Long)
data class SosKind(val key: String, val name: String, val price: Long)

object C {
    const val CALI_LAT = 3.4516
    const val CALI_LNG = -76.5320

    // Coordenadas aproximadas del centro de cada corredor (ajústalas a tus puntos reales).
    val CORRIDORS = listOf(
        Corridor("cali", "Cali", 3.4516, -76.5320),
        Corridor("calima", "Lago Calima", 3.9330, -76.4850),
        Corridor("linea", "Alto de La Línea", 4.5050, -75.5300),
        Corridor("palmas", "Las Palmas", 3.4750, -76.6000),
        Corridor("chica", "Chicamocha", 6.7960, -73.0200),
    )
    fun corridor(key: String) = CORRIDORS.firstOrNull { it.key == key } ?: CORRIDORS[0]

    val CATS = mapOf(
        "food" to Triple("Comida + Parqueo", "Aliado Apex", "Gastronomía & Descanso"),
        "cafe" to Triple("Café & Mirador", "Mirador Especial", "Café de origen & panorámica"),
        "moto" to Triple("Gasolina & Talleres", "S.O.S Técnico", "Taller & asistencia"),
        "tour" to Triple("Sitios turísticos", "Sitio turístico", "Turismo & historia"),
    )
    val CUISINES = listOf("Parrilla", "Criolla", "Café", "Pizza", "Pescado", "Postres", "Saludable")
    val KW = mapOf(
        "Parrilla" to listOf("asado", "parrilla", "carne", "bbq", "churrasco", "chuzo"),
        "Criolla" to listOf("criolla", "tipica", "bandeja", "sancocho", "tradicional", "fritanga"),
        "Café" to listOf("cafe", "capuchino", "tinto", "espresso", "desayuno"),
        "Pizza" to listOf("pizza", "italiana", "pasta"),
        "Pescado" to listOf("pescado", "mariscos", "trucha", "ceviche", "lago"),
        "Postres" to listOf("postre", "dulce", "helado", "torta", "brownie"),
        "Saludable" to listOf("saludable", "vegetariano", "vegano", "ensalada", "fit"),
    )
    val STOP = setOf("quiero", "para", "unas", "unos", "algo", "bueno", "buena", "barato", "ruta", "tengo", "puedo", "comer", "como", "donde", "desde", "hasta", "pero", "mejor")
    val CHEAP = setOf("barato", "economico", "ahorrar", "economica")

    val MENU_CATS = listOf("Entradas", "Platos fuertes", "Bebidas", "Postres", "Servicios")
    val PLANS = listOf(
        Plan("b", "Básica", 0, "Cobertura obligatoria"),
        Plan("s", "Estándar", 30000, "Reduce responsabilidad por daños"),
        Plan("p", "Premium", 55000, "Cero responsabilidad"),
    )
    val EXTRAS = listOf(Extra("casco", "Casco extra", 12000), Extra("maletas", "Maletas laterales", 25000), Extra("gps", "Soporte GPS", 8000), Extra("intercom", "Intercom", 20000))
    val SOS_KINDS = listOf(SosKind("flat", "Pinchazo", 35000), SosKind("battery", "Batería", 45000), SosKind("fuel", "Sin gasolina", 25000), SosKind("tow", "Remolque", 120000), SosKind("mech", "Falla mecánica", 60000))
    // Todos los precios de la app están en pesos colombianos (COP).
    const val DELIVERY_FEE = 40000L

    val RIDE_STYLES = listOf("Turismo", "Aventura", "Deportivo", "Relajado")

    /** Un S.O.S. que nadie aceptó caduca: deja de mostrarse a los talleres y se cancela (borrando la ubicación) al abrir la app. */
    const val SOS_TTL_MS = 6 * 60 * 60 * 1000L
}
