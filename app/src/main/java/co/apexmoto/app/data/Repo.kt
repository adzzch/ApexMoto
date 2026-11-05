package co.apexmoto.app.data

import co.apexmoto.app.core.C
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Acceso a Firebase Auth + Firestore. Todas las consultas usan un solo filtro de igualdad (sin índices compuestos). */
class Repo(val db: FirebaseFirestore, val auth: FirebaseAuth) {
    private fun col(n: String) = db.collection(n)
    private fun now() = System.currentTimeMillis()

    private inline fun <reified T : Any> Query.live(): Flow<List<T>> = callbackFlow {
        val reg = addSnapshotListener { s, _ -> if (s != null) trySend(s.toObjects(T::class.java)) }
        awaitClose { reg.remove() }
    }

    // ───────── Autenticación y usuarios ─────────
    /** Crea la cuenta con correo y contraseña y su perfil. */
    suspend fun register(name: String, email: String, pw: String, role: String, bizName: String = "", corridor: String = "calima"): AppUser {
        auth.createUserWithEmailAndPassword(email.trim(), pw).await()
        return completeProfile(name, role, bizName, corridor)
    }

    suspend fun signInEmail(email: String, pw: String) { auth.signInWithEmailAndPassword(email.trim(), pw).await() }
    suspend fun signInWithCredential(c: AuthCredential) { auth.signInWithCredential(c).await() }

    /** Datos de la cuenta recién autenticada que aún no tiene perfil (Google o teléfono). */
    fun authInfo(): PendingProfile? =
        auth.currentUser?.let { PendingProfile(it.displayName.orEmpty(), (it.email ?: it.phoneNumber).orEmpty()) }

    /** Guarda el perfil (rol incluido) de la cuenta autenticada y crea su comercio o perfil de conductor si aplica. */
    suspend fun completeProfile(name: String, role: String, bizName: String = "", corridor: String = "calima"): AppUser {
        val fu = auth.currentUser ?: error("Tu sesión no es válida. Vuelve a ingresar.")
        val u = AppUser(fu.uid, name.trim().ifBlank { "Moto Rider" }, (fu.email ?: fu.phoneNumber).orEmpty().lowercase(), role)
        col("users").document(fu.uid).set(u).await()
        if (role == Role.SHOP) newVenue(fu.uid, bizName.ifBlank { "Mi negocio" }, corridor)
        if (role == Role.DRIVER) ensureDriver(u)
        return u
    }

    suspend fun currentUser(): AppUser? {
        val id = auth.currentUser?.uid ?: return null
        return col("users").document(id).get().await().toObject(AppUser::class.java)
    }

    fun logout() = auth.signOut()

    suspend fun setRole(u: AppUser, role: String) {
        col("users").document(u.id).update("role", role).await()
        if (role == Role.DRIVER) ensureDriver(u)
    }

    private suspend fun ensureDriver(u: AppUser) {
        val ref = col("drivers").document(u.id)
        if (!ref.get().await().exists()) ref.set(Driver(ownerId = u.id, name = u.name, bio = "Conductor guía en Apex Moto.")).await()
    }

    // ───────── Comercios ─────────
    fun venues() = col("venues").live<Venue>()

    suspend fun newVenue(owner: String, name: String, corridor: String) {
        val c = C.corridor(corridor)
        col("venues").add(Venue(ownerId = owner, name = name, corridor = corridor, lat = c.lat, lng = c.lng,
            kind = C.CATS["food"]!!.third, promo = "Define tu promoción", desc = "Cuéntales a los moteros qué ofreces.")).await()
    }

    suspend fun saveVenue(v: Venue) { col("venues").document(v.id).set(v).await() }
    suspend fun setMenu(venueId: String, menu: List<MenuItem>) { col("venues").document(venueId).update("menu", menu).await() }
    suspend fun addVisit(venueId: String) { col("venues").document(venueId).update("visits", FieldValue.increment(1)).await() }

    // ───────── Pedidos ─────────
    fun ordersFrom(uid: String) = col("orders").whereEqualTo("fromId", uid).live<Order>()
    fun ordersOfOwner(uid: String) = col("orders").whereEqualTo("ownerId", uid).live<Order>()

    suspend fun placeOrder(v: Venue, u: AppUser, items: List<OrderItem>, eta: String, note: String) {
        col("orders").add(Order(venueId = v.id, venueName = v.name, ownerId = v.ownerId, fromId = u.id, fromName = u.name,
            items = items, total = items.sumOf { it.qty * it.price }, eta = eta, note = note, status = "new", createdAt = now())).await()
    }

    suspend fun setOrderStatus(id: String, st: String) { col("orders").document(id).update("status", st).await() }

    // ───────── Cupones ─────────
    fun couponsOfOwner(uid: String) = col("coupons").whereEqualTo("ownerId", uid).live<Coupon>()

    suspend fun couponFor(v: Venue, u: AppUser): Coupon {
        val mine = col("coupons").whereEqualTo("userId", u.id).get().await().toObjects(Coupon::class.java)
        mine.firstOrNull { it.venueId == v.id && !it.used }?.let { return it }
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val c = Coupon(code = "APX-" + (1..4).map { alphabet.random() }.joinToString(""), venueId = v.id, ownerId = v.ownerId,
            userId = u.id, userName = u.name, createdAt = now())
        val ref = col("coupons").add(c).await()
        return c.copy(id = ref.id)
    }

    /** Devuelve el nombre del rider que presentó el cupón. */
    suspend fun redeem(code: String, ownerId: String): String {
        val d = col("coupons").whereEqualTo("code", code.trim().uppercase()).whereEqualTo("ownerId", ownerId)
            .get().await().documents.firstOrNull() ?: error("Ese código no es válido para tu negocio")
        if (d.getBoolean("used") == true) error("Ese cupón ya fue canjeado")
        d.reference.update(mapOf("used" to true, "usedAt" to now())).await()
        return d.getString("userName").orEmpty()
    }

    // ───────── Motos, conductores y rodadas ─────────
    fun bikes() = col("bikes").live<Bike>()
    suspend fun saveBike(b: Bike) { if (b.id.isBlank()) col("bikes").add(b).await() else col("bikes").document(b.id).set(b).await() }
    suspend fun deleteBike(id: String) { col("bikes").document(id).delete().await() }
    suspend fun addBooking(bikeId: String, from: String, to: String) {
        col("bikes").document(bikeId).update("booked", FieldValue.arrayUnion("$from|$to")).await()
    }

    fun drivers() = col("drivers").live<Driver>()
    suspend fun saveDriver(d: Driver) { col("drivers").document(d.id).set(d).await() }

    fun rodadas() = col("rodadas").live<Rodada>()
    suspend fun createRodada(r: Rodada) { col("rodadas").add(r).await() }
    suspend fun toggleJoin(r: Rodada, uid: String) {
        val ref = col("rodadas").document(r.id)
        if (uid in r.joined) ref.update("joined", FieldValue.arrayRemove(uid)).await()
        else {
            if (r.joined.size >= r.seats) error("Esta rodada ya no tiene cupos")
            ref.update("joined", FieldValue.arrayUnion(uid)).await()
        }
    }

    // ───────── Solicitudes (renta y conductor) ─────────
    fun requestsFrom(uid: String) = col("requests").whereEqualTo("fromId", uid).live<ServiceRequest>()
    fun requestsTo(uid: String) = col("requests").whereEqualTo("toId", uid).live<ServiceRequest>()
    suspend fun createRequest(r: ServiceRequest) { col("requests").add(r.copy(createdAt = now())).await() }
    suspend fun setRequestStatus(id: String, st: String) { col("requests").document(id).update("status", st).await() }

    suspend fun rateDriver(req: ServiceRequest, stars: Int) {
        val ref = col("drivers").document(req.itemId)
        val d = ref.get().await().toObject(Driver::class.java)
        if (d != null) {
            val n = d.reviews + 1
            ref.update(mapOf("rating" to ((d.rating * d.reviews + stars) / n), "reviews" to n)).await()
        }
        col("requests").document(req.id).update("rated", stars).await()
    }

    // ───────── S.O.S. taller móvil ─────────
    fun sosAll() = col("sos").live<Sos>()
    suspend fun createSos(s: Sos) { col("sos").add(s.copy(createdAt = now())).await() }
    suspend fun setSosStatus(id: String, st: String) { col("sos").document(id).update("status", st).await() }
    suspend fun acceptSos(id: String, providerId: String, providerName: String, eta: Int) {
        val ref = col("sos").document(id)
        db.runTransaction { tx ->
            val s = tx.get(ref)
            if (s.getString("status") != "new") throw IllegalStateException("Otro taller ya aceptó este S.O.S.")
            tx.update(ref, mapOf("status" to "ok", "providerId" to providerId, "providerName" to providerName, "eta" to eta))
        }.await()
    }

    // ───────── Chat ─────────
    fun messages(thread: String) = col("chats").whereEqualTo("thread", thread).live<ChatMessage>()
    suspend fun send(thread: String, u: AppUser, text: String) {
        col("chats").add(ChatMessage(thread = thread, fromId = u.id, fromName = u.name, text = text.trim(), createdAt = now())).await()
    }

    // ───────── Rutas guardadas ─────────
    fun routesOf(uid: String) = col("routes").whereEqualTo("userId", uid).live<SavedRoute>()
    suspend fun saveRoute(r: SavedRoute) { col("routes").add(r.copy(createdAt = now())).await() }
    suspend fun deleteRoute(id: String) { col("routes").document(id).delete().await() }

    // ───────── Datos demo (tomados del prototipo) ─────────
    /**
     * Borra solo los datos demo (los que no tienen dueño: ownerId / creatorId vacío) y vuelve a cargarlos sin repetidos.
     * Los comercios, motos y perfiles creados por usuarios reales no se tocan.
     */
    suspend fun resetDemo() {
        for ((c, f) in listOf("venues" to "ownerId", "bikes" to "ownerId", "drivers" to "ownerId", "rodadas" to "creatorId")) {
            col(c).whereEqualTo(f, "").get().await().documents.chunked(400).forEach { chunk ->
                val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await()
            }
        }
        col("meta").document("seed").delete().await()
        seedDemo()
    }

    suspend fun seedDemo() {
        val marker = col("meta").document("seed")
        if (marker.get().await().exists()) error("Los datos demo ya fueron cargados")
        fun m(i: Int, n: String, d: String, p: Long, c: String) = MenuItem("m$i", n, d, p, c)
        data class S(val n: String, val t: List<String>, val cor: String, val km: Int, val r: Double, val rv: Int, val promo: String,
                     val desc: String, val tags: List<String>, val cu: List<String>, val menu: List<MenuItem>)
        val venues = listOf(
            S("Parrilla El Mirador", listOf("food"), "calima", 38, 4.8, 124, "10% de descuento + parqueo gratis", "Asados con vista al lago y parqueadero vigilado.",
                listOf("asado", "parrilla", "lago"), listOf("Parrilla"), listOf(m(1, "Picada para dos", "Carnes y chorizo", 58000, "Platos fuertes"), m(2, "Churrasco", "300 g con papas", 42000, "Platos fuertes"), m(3, "Limonada", "Natural", 8000, "Bebidas"))),
            S("Café Calima Ruta", listOf("cafe"), "calima", 45, 4.9, 89, "Tinto de cortesía", "Café de origen y mirador.",
                listOf("cafe", "mirador", "desayuno"), listOf("Café", "Postres"), listOf(m(1, "Capuchino", "Grano local", 9000, "Bebidas"), m(2, "Torta de zanahoria", "Porción", 11000, "Postres"))),
            S("Trucha del Lago", listOf("food"), "calima", 52, 4.6, 61, "Bebida gratis con plato fuerte", "Trucha fresca y pescado frito.",
                listOf("pescado", "trucha", "lago"), listOf("Pescado"), listOf(m(1, "Trucha al ajillo", "Con patacón", 38000, "Platos fuertes"), m(2, "Ceviche", "Mixto", 32000, "Entradas"))),
            S("Taller Moto Express", listOf("moto"), "calima", 30, 4.7, 203, "Revisión básica gratis", "Mecánica ligera y asistencia en carretera.",
                listOf("taller", "mecanica"), emptyList(), listOf(m(1, "Revisión de frenos", "Diagnóstico", 25000, "Servicios"), m(2, "Cambio de aceite", "Mano de obra", 30000, "Servicios"))),
            S("Fonda La Línea", listOf("food"), "linea", 90, 4.5, 77, "Postre de cortesía", "Comida criolla de montaña.",
                listOf("criolla", "bandeja", "sancocho"), listOf("Criolla"), listOf(m(1, "Bandeja paisa", "Completa", 36000, "Platos fuertes"), m(2, "Sancocho", "De gallina", 30000, "Platos fuertes"))),
            S("Mirador Las Palmas", listOf("cafe", "food"), "palmas", 18, 4.7, 150, "10% en desayunos", "Desayunos con panorámica de la ciudad.",
                listOf("desayuno", "mirador", "cafe"), listOf("Café", "Saludable"), listOf(m(1, "Desayuno motero", "Huevos, arepa y café", 22000, "Platos fuertes"), m(2, "Ensalada fit", "Pollo y verduras", 24000, "Platos fuertes"))),
            S("Pizzería El Cañón", listOf("food"), "chica", 120, 4.4, 55, "Gaseosa gratis", "Pizza al horno de leña.",
                listOf("pizza", "italiana"), listOf("Pizza"), listOf(m(1, "Pizza mediana", "Hawaiana", 34000, "Platos fuertes"))),
            S("Estación Chicamocha", listOf("moto"), "chica", 118, 4.3, 40, "Lavado de cadena gratis", "Gasolina y asistencia básica.",
                listOf("gasolina", "taller"), emptyList(), listOf(m(1, "Lavado y lubricación", "De cadena", 15000, "Servicios"))),
        )
        venues.forEachIndexed { i, s ->
            val c = C.corridor(s.cor)
            val k = C.CATS[s.t[0]]!!
            // ID fijo: cargar los datos demo dos veces ya no genera comercios repetidos
            col("venues").document("demo_v${i + 1}").set(Venue(name = s.n, types = s.t, corridor = s.cor, km = s.km, rating = s.r, reviews = s.rv, badge = k.second, kind = k.third,
                promo = s.promo, desc = s.desc, tags = s.tags, cuisines = s.cu, menu = s.menu,
                lat = c.lat + ((i % 3) - 1) * 0.012, lng = c.lng + (i % 2) * 0.015)).await()
        }
        listOf(
            Bike(name = "Yamaha MT-07", cc = 689, category = "Deportiva", pricePerDay = 280000, city = "Cali", specs = listOf("73 hp", "ABS"), rating = 4.9, trips = 31, instant = true),
            Bike(name = "Honda XRE 300", cc = 300, category = "Aventura", pricePerDay = 160000, city = "Cali", specs = listOf("Doble propósito"), rating = 4.8, trips = 54, delivery = true),
            Bike(name = "Suzuki V-Strom 650", cc = 645, category = "Aventura", pricePerDay = 320000, city = "Medellín", specs = listOf("Maletas incluidas"), rating = 4.9, trips = 22),
            Bike(name = "Royal Enfield Himalayan", cc = 411, category = "Aventura", pricePerDay = 200000, city = "Bogotá", specs = listOf("Ideal para trocha"), rating = 4.7, trips = 40, instant = true),
        ).forEachIndexed { i, b -> col("bikes").document("demo_b${i + 1}").set(b).await() }
        listOf(
            Driver(name = "Camilo R.", bike = "Honda XRE 300", zone = "Cali", rate = 220000, rating = 4.9, reviews = 38, services = listOf("Ruta guiada", "Escolta"), bio = "10 años guiando por el Valle y el Eje Cafetero."),
            Driver(name = "Laura M.", bike = "BMW G 310 GS", zone = "Bogotá", rate = 250000, rating = 4.8, reviews = 21, services = listOf("Ruta guiada", "Fotografía"), bio = "Guía certificada, rutas de montaña."),
        ).forEachIndexed { i, d -> col("drivers").document("demo_d${i + 1}").set(d).await() }
        listOf(
            Rodada(name = "Rodada Lago Calima", whenLabel = "Sábado 7:00", captain = "@camilo", meet = "Peaje Cencar, Cali", style = "Turismo", seats = 10),
            Rodada(name = "Madrugón La Línea", whenLabel = "Domingo 5:30", captain = "@laura", meet = "Estación Terpel Armenia", style = "Aventura", seats = 8),
        ).forEachIndexed { i, r -> col("rodadas").document("demo_r${i + 1}").set(r).await() }
        marker.set(mapOf("at" to now())).await()
    }
}
