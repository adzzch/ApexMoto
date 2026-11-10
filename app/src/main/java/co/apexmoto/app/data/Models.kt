package co.apexmoto.app.data

import com.google.firebase.firestore.DocumentId

object Role {
    const val RIDER = "m"; const val SHOP = "c"; const val OWNER = "o"; const val DRIVER = "d"; const val WORKSHOP = "t"
    fun label(r: String) = when (r) { RIDER -> "Rider"; SHOP -> "Comercio"; OWNER -> "Dueño de moto"; WORKSHOP -> "Taller mecánico"; else -> "Guía de rutas" }
    /** Roles que administran un negocio (comercio o taller) y por eso tienen un Venue propio. */
    fun hasVenue(r: String) = r == SHOP || r == WORKSHOP
}

// Todos los modelos tienen valores por defecto y propiedades var: Firestore los necesita para leer/escribir.
data class AppUser(
    @DocumentId var id: String = "", var name: String = "", var email: String = "", var role: String = Role.RIDER,
    /** Documento legal → versión aceptada. El detalle probatorio de cada aceptación está en legal_acceptances. */
    var legal: Map<String, String> = emptyMap(),
)

/** Cuenta autenticada (Google o teléfono) que todavía no eligió su rol. */
data class PendingProfile(val name: String = "", val contact: String = "")

data class MenuItem(var id: String = "", var name: String = "", var desc: String = "", var price: Long = 0, var cat: String = "Platos fuertes", var on: Boolean = true)

data class Venue(
    @DocumentId var id: String = "", var ownerId: String = "", var name: String = "", var open: Boolean = true,
    var types: List<String> = listOf("food"), var corridor: String = "calima", var km: Int = 30,
    var rating: Double = 5.0, var reviews: Int = 0, var badge: String = "Aliado Apex", var kind: String = "",
    var promo: String = "", var desc: String = "", var tags: List<String> = emptyList(), var cuisines: List<String> = emptyList(),
    var menu: List<MenuItem> = emptyList(), var lat: Double = 0.0, var lng: Double = 0.0, var visits: Int = 0,
    // Solo talleres: qué tipos de S.O.S. atiende, hasta qué distancia y si está recibiendo S.O.S. ahora.
    var covers: List<String> = listOf("flat", "battery", "fuel", "tow", "mech"), var radiusKm: Int = 40, var sosOn: Boolean = true,
)

data class Bike(
    @DocumentId var id: String = "", var ownerId: String = "", var name: String = "", var cc: Int = 250, var category: String = "Aventura",
    var pricePerDay: Long = 150000, var city: String = "Cali", var specs: List<String> = emptyList(), var rating: Double = 5.0,
    var trips: Int = 0, var instant: Boolean = false, var delivery: Boolean = false, var available: Boolean = true,
    var booked: List<String> = emptyList(),
    // Vencimientos que el dueño declara (fecha ISO). Sin SOAT o revisión vigentes la moto no se puede rentar.
    var soatUntil: String = "", var rtmUntil: String = "",
    /** Última solicitud que reservó o liberó fechas. Las reglas de Firestore la usan para validar el cambio de `booked`. */
    var lastReq: String = "",
)

data class Driver(
    @DocumentId var id: String = "", var ownerId: String = "", var name: String = "", var bike: String = "", var zone: String = "Cali",
    var rate: Long = 200000, var rating: Double = 5.0, var reviews: Int = 0, var services: List<String> = listOf("Ruta guiada"),
    var bio: String = "", var online: Boolean = true,
    /** Solicitud que originó la última calificación. Las reglas de Firestore la usan para validar que es real. */
    var lastRatingReq: String = "",
)

data class Rodada(
    @DocumentId var id: String = "", var creatorId: String = "", var name: String = "", var whenLabel: String = "Este fin de semana",
    var captain: String = "", var rating: Double = 5.0, var meet: String = "", var style: String = "Turismo", var seats: Int = 8,
    var joined: List<String> = emptyList(),
)

data class ServiceRequest(
    @DocumentId var id: String = "", var type: String = "rent", var fromId: String = "", var fromName: String = "", var toId: String = "",
    var itemId: String = "", var itemName: String = "", var days: Int = 1, var amount: Long = 0, var total: Long = 0,
    var dateFrom: String = "", var dateTo: String = "", var plan: String = "", var extras: List<String> = emptyList(),
    var delivery: Boolean = false, var status: String = "p", var meet: String = "", var service: String = "",
    var driverName: String = "", var rated: Int = 0, var createdAt: Long = 0,
    /** Renta: el rider declaró tener licencia vigente de esta categoría (A1 o A2). No se guarda foto ni número. */
    var licenseDeclared: Boolean = false, var licenseCat: String = "",
    // Cancelación por el rider. cancelKind: "retracto" (art. 47 Ley 1480) o "cancelacion".
    var cancelKind: String = "", var cancelledAt: Long = 0,
)

data class OrderItem(var name: String = "", var qty: Int = 1, var price: Long = 0)

data class Order(
    @DocumentId var id: String = "", var venueId: String = "", var venueName: String = "", var ownerId: String = "",
    var fromId: String = "", var fromName: String = "", var items: List<OrderItem> = emptyList(), var total: Long = 0,
    var eta: String = "", var note: String = "", var status: String = "new", var createdAt: Long = 0,
)

data class Coupon(
    @DocumentId var id: String = "", var code: String = "", var venueId: String = "", var ownerId: String = "",
    var userId: String = "", var userName: String = "", var used: Boolean = false, var createdAt: Long = 0, var usedAt: Long = 0,
)

data class Sos(
    @DocumentId var id: String = "", var fromId: String = "", var fromName: String = "", var kind: String = "", var kindName: String = "",
    var locationText: String = "", var lat: Double = 0.0, var lng: Double = 0.0, var note: String = "", var price: Long = 0,
    var status: String = "new", var providerId: String = "", var providerName: String = "", var eta: Int = 0, var createdAt: Long = 0,
    var acceptedAt: Long = 0, var doneAt: Long = 0,
)

data class ChatMessage(
    @DocumentId var id: String = "", var thread: String = "", var fromId: String = "", var fromName: String = "",
    var text: String = "", var createdAt: Long = 0,
    /** Las dos personas del servicio. Solo ellas pueden leer el mensaje. */
    var members: List<String> = emptyList(),
)

data class SavedStop(var venueId: String = "", var name: String = "", var eta: String = "", var lat: Double = 0.0, var lng: Double = 0.0)

data class SavedRoute(
    @DocumentId var id: String = "", var userId: String = "", var name: String = "", var corridor: String = "", var km: Int = 0,
    var mins: Int = 0, var stops: List<SavedStop> = emptyList(), var createdAt: Long = 0,
)
