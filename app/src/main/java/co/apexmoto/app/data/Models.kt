package co.apexmoto.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Roles. El de "Guía de rutas" ("d") se retiró el 2026-11-15: ApexMoto no ofrece guías pagados ni transporte de
 * pasajeros. Las cuentas que lo tenían pasan solas a rider al abrir la app (ver Repo.currentUser).
 */
object Role {
    const val RIDER = "m"; const val SHOP = "c"; const val OWNER = "o"; const val WORKSHOP = "t"
    fun label(r: String) = when (r) { SHOP -> "Comercio"; OWNER -> "Dueño de moto"; WORKSHOP -> "Taller mecánico"; else -> "Rider" }
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
    /** Última reseña que movió el promedio: las reglas la usan para validar la cuenta. */
    var lastReview: String = "",
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
    // Requisitos y acuerdo de daños del dueño (fase 5). Reemplazan los antiguos planes de "protección", que no eran un seguro.
    /** Años mínimos manejando moto (0 = sin mínimo). */
    var minYears: Int = 0,
    /** Depósito que el rider deja al recibir la moto y que se le devuelve al entregarla sin daños nuevos (0 = sin depósito). */
    var deposit: Long = 0,
    /** Lo máximo que el rider paga por daños (0 = sin tope pactado: responde según la ley). */
    var damageCap: Long = 0,
    /** Reseñas reales de quienes la rentaron, y la última que movió el promedio (las reglas la usan para validar la cuenta). */
    var reviews: Int = 0, var lastReview: String = "",
)

/**
 * Perfil de conductor del rider (rider_profiles/{uid}). Privado: solo lo lee su titular. Al pedir una moto se copia en la
 * solicitud, y así lo ve únicamente el dueño de esa moto. No se guarda foto ni número de la licencia.
 */
data class RiderProfile(
    @DocumentId var id: String = "", var licenseCat: String = "A2", var licenseUntil: String = "",
    /** Año desde el que maneja moto. */
    var since: Int = 0,
    /** Cilindraje más alto que ha manejado. */
    var maxCc: Int = 0,
    /** Contacto de emergencia (opcional). En la solicitud se borra cuando la renta termina o se cancela. */
    var emergencyName: String = "", var emergencyPhone: String = "",
    var updatedAt: Long = 0,
)

/**
 * Acta de entrega o de devolución de una moto rentada (actas/{solicitud}_{entrega|devolucion}). La llena el dueño y la
 * confirma el rider; confirmada no se puede cambiar ni borrar. Sin el acta de entrega confirmada la renta no empieza, y
 * sin la de devolución no termina. fuel: cuartos de tanque (0 = reserva … 4 = lleno).
 */
data class Acta(
    @DocumentId var id: String = "", var reqId: String = "", var kind: String = "entrega", var ownerId: String = "", var riderId: String = "",
    var bikeId: String = "", var bikeName: String = "", var km: Int = 0, var fuel: Int = 4, var condition: String = "",
    var items: List<String> = emptyList(),
    /** Entrega: el dueño vio la licencia física del rider, de la categoría y vigencia que declaró. Obligatorio. */
    var licenseChecked: Boolean = false,
    /** Entrega: hicieron una prueba de manejo corta. */
    var testRide: Boolean = false,
    /** Devolución: daños nuevos y lo que se descuenta por ellos (no más que el tope pactado). */
    var damages: String = "", var damageAmount: Long = 0,
    var confirmed: Boolean = false,
    @ServerTimestamp var createdAt: Date? = null, var confirmedAt: Date? = null,
)

data class RodadaStop(var name: String = "", var lat: Double = 0.0, var lng: Double = 0.0)

/**
 * Salida en grupo. Cada quien va en su propia moto (seats = número máximo de motos).
 * visibility: "public" (la ve cualquiera) o "friends" (solo invitados y miembros). Con approval, quien quiere unirse queda
 * en pending hasta que el creador lo apruebe; un invitado entra directo. El creador elige al líder (va adelante) y a
 * quien cierra el grupo. status: "planned" → "active" (mapa del grupo en vivo) → "done".
 */
data class Rodada(
    @DocumentId var id: String = "", var creatorId: String = "", var name: String = "", var whenLabel: String = "Este fin de semana",
    var captain: String = "", var rating: Double = 5.0, var meet: String = "", var style: String = "Turismo", var seats: Int = 8,
    var joined: List<String> = emptyList(),
    var visibility: String = "public", var approval: Boolean = true,
    var pending: List<String> = emptyList(), var invited: List<String> = emptyList(),
    /** Último invitado: las reglas lo usan para comprobar que es amigo del creador. */
    var lastInvited: String = "",
    var leaderId: String = "", var sweeperId: String = "",
    var status: String = "planned", var startedAt: Date? = null, var endedAt: Date? = null,
    var routeName: String = "", var stops: List<RodadaStop> = emptyList(),
    /** Fecha y hora de salida (milisegundos). 0 en las rodadas creadas antes de tener calendario: se usa whenLabel. */
    var startsAt: Long = 0,
    /** Punto de encuentro elegido de la lista (bomba, supermercado, centro comercial, peaje…). 0,0 = sin coordenada. */
    var meetLat: Double = 0.0, var meetLng: Double = 0.0,
    /** Código para invitar por enlace (rodada_codes/{código}). Solo lo ven los miembros. */
    var inviteCode: String = "",
)

/** Código de invitación de una rodada: quien lo tiene puede ver lo básico y unirse, aunque la rodada sea privada. */
data class RodadaCode(
    @DocumentId var id: String = "", var rodadaId: String = "", var creatorId: String = "", var name: String = "",
    var whenLabel: String = "", var meet: String = "",
)

/**
 * Estadísticas públicas de un rider (rider_stats/{uid}): desde cuándo usa la app y cuántas rutas ha completado con el
 * guiado. De ahí salen las medallas.
 */
data class RiderStats(@DocumentId var id: String = "", var since: Long = 0, var routes: Int = 0, @ServerTimestamp var lastAt: Date? = null)

/** Posición en vivo de un miembro en una rodada activa (rodadas/{id}/live/{uid}). Se borra al dejar de compartir. */
data class LivePos(
    @DocumentId var id: String = "", var lat: Double = 0.0, var lng: Double = 0.0, var heading: Double = 0.0,
    var name: String = "", var moto: String = "naked", var color: String = "orange", var sos: Boolean = false,
    /** Última señal rápida que mandó al grupo (ver RIDE_SIGNALS; "" si ninguna) y cuándo, con la hora del servidor. */
    var signal: String = "", var signalAt: Date? = null,
    @ServerTimestamp var updatedAt: Date? = null,
)

/**
 * Solicitud de renta de una moto (type = "rent", el único tipo que existe).
 * status: "p" (pendiente) → "ok" (aceptada) → "ride" (con el acta de entrega confirmada) → "fin" (con el acta de devolución
 * confirmada). También "no" (el dueño la rechazó o canceló la entrega) y "can" (el rider canceló o se retractó).
 */
data class ServiceRequest(
    @DocumentId var id: String = "", var type: String = "rent", var fromId: String = "", var fromName: String = "", var toId: String = "",
    var itemId: String = "", var itemName: String = "", var days: Int = 1, var amount: Long = 0, var total: Long = 0,
    /** plan: solo en solicitudes de antes de la fase 5 (los planes de "protección" se retiraron). */
    var dateFrom: String = "", var dateTo: String = "", var plan: String = "", var extras: List<String> = emptyList(),
    var delivery: Boolean = false, var status: String = "p", var rated: Int = 0, var createdAt: Long = 0,
    /** Renta: el rider declaró tener licencia vigente de esta categoría (A1 o A2). No se guarda foto ni número. */
    var licenseDeclared: Boolean = false, var licenseCat: String = "",
    // Cancelación por el rider. cancelKind: "retracto" (art. 47 Ley 1480) o "cancelacion".
    var cancelKind: String = "", var cancelledAt: Long = 0,
    // Copia del perfil de conductor al pedir la moto (las reglas comprueban que coincida con el perfil).
    var licenseUntil: String = "", var riderSince: Int = 0, var riderMaxCc: Int = 0,
    /** Contacto de emergencia: solo mientras la renta está activa; se borra al terminar, rechazar o cancelar. */
    var emergencyName: String = "", var emergencyPhone: String = "",
    // Acuerdo de daños que el rider aceptó: copia exacta del que publicó el dueño en la moto.
    var deposit: Long = 0, var damageCap: Long = 0, var damageAccepted: Boolean = false,
    /** Reserva inmediata: una renta terminada del mismo rider que demuestra su historial. */
    var prevReq: String = "",
    /** Por qué el dueño canceló en la entrega: "licencia", "prueba", "otro". */
    var ownerCancel: String = "",
)

data class OrderItem(var name: String = "", var qty: Int = 1, var price: Long = 0)

data class Order(
    @DocumentId var id: String = "", var venueId: String = "", var venueName: String = "", var ownerId: String = "",
    var fromId: String = "", var fromName: String = "", var items: List<OrderItem> = emptyList(), var total: Long = 0,
    var eta: String = "", var note: String = "", var status: String = "new", var createdAt: Long = 0,
    /** Hora (epoch ms) a la que el rider quiere el pedido listo. 0 = pedidos de antes, sin hora. */
    var readyAt: Long = 0,
)

/**
 * Reseña de un servicio terminado. ID = "{kind}_{serviceId}": una por servicio. kind: order (pedido), sos (taller),
 * rent (el rider reseña la moto), renter (el dueño reseña al rider) o demo. targetType: venue, bike o rider.
 */
data class Review(
    @DocumentId var id: String = "", var kind: String = "", var serviceId: String = "", var targetType: String = "", var targetId: String = "",
    var authorId: String = "", var authorName: String = "", var stars: Int = 5, var comment: String = "", var createdAt: Long = 0,
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
    /** Taller (venue) que lo atendió: para reseñarlo. */
    var providerVenueId: String = "",
    /** Dónde va el taller mientras va en camino (solo lo ve el rider). Vuelve a cero al llegar, terminar o liberar. */
    var providerLat: Double = 0.0, var providerLng: Double = 0.0, var providerAt: Date? = null,
    /** S.O.S. en una moto rentada: la renta en curso y su dueño, que también lo ve. */
    var rentalReq: String = "", var rentalOwnerId: String = "",
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

// ───────── Amigos (fase 1) ─────────
/** Perfil público de un rider (colección profiles): lo único que ven los demás. El @usuario es único (colección usernames). */
data class Profile(
    @DocumentId var id: String = "", var username: String = "", var name: String = "",
    /** Avatar: silueta de moto (ver Avatars.MOTOS) y color (Avatars.COLORS). Sin fotos. */
    var moto: String = "naked", var color: String = "orange",
    /** Si es false no se publica el estado (en línea / en rodada) a los amigos. */
    var showStatus: Boolean = true,
)

/** Solicitud de amistad pendiente. ID = "{from}_{to}". Se borra al aceptarla, rechazarla o cancelarla. */
data class FriendRequest(
    @DocumentId var id: String = "", var from: String = "", var to: String = "",
    var fromName: String = "", var fromUsername: String = "", var fromMoto: String = "", var fromColor: String = "",
    var toName: String = "", var toUsername: String = "", var createdAt: Long = 0,
)

/** Amistad entre dos riders. ID = "{a}_{b}" con a < b; members = [a, b]. */
data class Friendship(@DocumentId var id: String = "", var members: List<String> = emptyList(), var since: Long = 0)

/** Bloqueo. ID = "{blocker}_{blocked}". Quien está bloqueado no puede enviar solicitudes ni ver el estado. */
data class Block(
    @DocumentId var id: String = "", var blocker: String = "", var blocked: String = "",
    var blockedName: String = "", var blockedUsername: String = "", var createdAt: Long = 0,
)

/** Estado que ven los amigos (colección status). updatedAt es la hora del servidor del último latido. */
data class Presence(
    @DocumentId var id: String = "", var state: String = "online", var rodada: String = "",
    @ServerTimestamp var updatedAt: Date? = null,
)

/** ID de una amistad: los dos uid ordenados. */
fun pairId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"
