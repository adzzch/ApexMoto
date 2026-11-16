package co.apexmoto.app.data

import co.apexmoto.app.core.C
import co.apexmoto.app.core.addDays
import co.apexmoto.app.core.today
import co.apexmoto.app.legal.Acceptance
import co.apexmoto.app.legal.ConsentRecorder
import co.apexmoto.app.legal.Legal
import co.apexmoto.app.legal.LegalDoc
import co.apexmoto.app.legal.PrivacyRequest
import co.apexmoto.app.legal.Provider
import co.apexmoto.app.legal.RequestType
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Date

/**
 * Acceso a Firebase Auth + Firestore. Las consultas usan solo filtros de igualdad (sin índices compuestos)
 * y cada una pide únicamente lo que las reglas de Firestore le permiten ver a ese usuario.
 */
class Repo(val db: FirebaseFirestore, val auth: FirebaseAuth, private val consent: ConsentRecorder) {
    private fun col(n: String) = db.collection(n)
    private fun now() = System.currentTimeMillis()

    /**
     * Sello de límite de uso: va en el mismo lote que la acción. Las reglas exigen que el sello sea la hora del
     * servidor y que haya pasado un tiempo mínimo desde el anterior; así un cliente modificado no puede inundar la base.
     */
    private fun WriteBatch.stamp(uid: String, kind: String) =
        set(col("rate_limits").document(uid), mapOf(kind to FieldValue.serverTimestamp()), SetOptions.merge())

    private inline fun <reified T : Any> Query.live(): Flow<List<T>> = callbackFlow {
        val reg = addSnapshotListener { s, _ -> if (s != null) trySend(s.toObjects(T::class.java)) }
        awaitClose { reg.remove() }
    }

    /** Un documento en vivo. Si no existe o las reglas no dejan leerlo, emite null. */
    private inline fun <reified T : Any> DocumentReference.liveDoc(): Flow<T?> = callbackFlow {
        val reg = addSnapshotListener { s, e -> trySend(if (e == null && s != null && s.exists()) s.toObject(T::class.java) else null) }
        awaitClose { reg.remove() }
    }

    // ───────── Autenticación y usuarios ─────────
    /** Crea la cuenta con correo y contraseña y su perfil. */
    suspend fun register(name: String, email: String, pw: String, role: String, bizName: String, corridor: String, method: String): AppUser {
        auth.createUserWithEmailAndPassword(email.trim(), pw).await()
        return completeProfile(name, role, bizName, corridor, method)
    }

    suspend fun signInEmail(email: String, pw: String) { auth.signInWithEmailAndPassword(email.trim(), pw).await() }
    suspend fun resetPassword(email: String) { auth.setLanguageCode("es"); auth.sendPasswordResetEmail(email.trim()).await() }
    suspend fun signInWithCredential(c: AuthCredential) { auth.signInWithCredential(c).await() }

    /** Datos de la cuenta recién autenticada que aún no tiene perfil (Google o teléfono). */
    fun authInfo(): PendingProfile? =
        auth.currentUser?.let { PendingProfile(it.displayName.orEmpty(), (it.email ?: it.phoneNumber).orEmpty()) }

    /**
     * Guarda el perfil (rol incluido) junto con el registro de cada aceptación legal, en un solo lote:
     * o quedan las dos cosas o ninguna. Las reglas de Firestore rechazan un perfil sin sus aceptaciones.
     * Luego crea el comercio o el taller si aplica.
     */
    suspend fun completeProfile(name: String, role: String, bizName: String, corridor: String, method: String): AppUser {
        val fu = auth.currentUser ?: error("Tu sesión no es válida. Vuelve a ingresar.")
        val docs = Legal.requiredFor(role)
        val u = AppUser(fu.uid, name.trim().take(60).ifBlank { "Moto Rider" }, (fu.email ?: fu.phoneNumber).orEmpty().lowercase(), role,
            legal = docs.associate { it.id to it.version })
        val b = db.batch()
        b.set(col("users").document(fu.uid), u)
        addAcceptances(b, fu.uid, role, docs, method)
        b.commit().await()
        if (role == Role.SHOP) newVenue(fu.uid, bizName.ifBlank { "Mi negocio" }, corridor)
        if (role == Role.WORKSHOP) newWorkshop(fu.uid, bizName.ifBlank { "Mi taller" }, corridor)
        return u
    }

    suspend fun currentUser(): AppUser? {
        val id = auth.currentUser?.uid ?: return null
        val u = col("users").document(id).get().await().toObject(AppUser::class.java) ?: return null
        if (u.role != LEGACY_GUIDE) return u
        // El rol de guía se retiró: la cuenta pasa a rider (las reglas solo permiten ese cambio). Si no se puede guardar
        // ahora, igual entra como rider y se intenta de nuevo la próxima vez.
        runCatching { col("users").document(id).update("role", Role.RIDER).await() }
        return u.copy(role = Role.RIDER)
    }

    fun logout() = auth.signOut()

    // ───────── Aceptación legal trazable ─────────
    /**
     * Agrega al lote un registro por documento: quién, qué documento y versión, hash del texto, cuándo (hora del servidor),
     * cómo, desde qué plataforma y con qué instalación. Las aceptaciones que ya existen no se reescriben (son inmutables).
     */
    private suspend fun addAcceptances(b: WriteBatch, uid: String, role: String, docs: List<LegalDoc>, method: String) {
        if (docs.isEmpty()) return
        val meta = consent.meta()
        val existing = col("legal_acceptances").whereEqualTo("userId", uid).get().await().documents.map { it.id }.toSet()
        docs.forEach { d ->
            val id = Legal.acceptanceId(uid, d)
            if (id !in existing) b.set(col("legal_acceptances").document(id), Acceptance(userId = uid, docId = d.id, version = d.version,
                docHash = consent.hash(d), method = method, appVersion = meta.appVersion, os = meta.os, deviceId = meta.deviceId, locale = meta.locale, role = role))
        }
    }

    /** Registra la aceptación de uno o varios documentos para una cuenta que ya existe (versión nueva, ubicación…). */
    suspend fun acceptDocs(u: AppUser, docs: List<LegalDoc>, method: String): AppUser {
        val todo = docs.filter { u.legal[it.id] != it.version }
        if (todo.isEmpty()) return u
        val legal = u.legal + todo.associate { it.id to it.version }
        val b = db.batch()
        b.update(col("users").document(u.id), mapOf("legal" to legal))
        addAcceptances(b, u.id, u.role, todo, method)
        b.commit().await()
        return u.copy(legal = legal)
    }

    fun acceptances(uid: String) = col("legal_acceptances").whereEqualTo("userId", uid).live<Acceptance>()

    // ───────── Derechos de los titulares (Ley 1581 de 2012) ─────────
    fun privacyRequests(uid: String) = col("privacy_requests").whereEqualTo("userId", uid).live<PrivacyRequest>()

    /** Radica una solicitud. Devuelve el número de radicado. La hora la pone el servidor: desde ahí corre el plazo legal. */
    suspend fun createPrivacyRequest(u: AppUser, type: String, detail: String): String {
        val ref = col("privacy_requests").document()
        ref.set(PrivacyRequest(userId = u.id, type = type, detail = detail.trim().take(2000), contact = u.email)).await()
        return ref.id
    }

    suspend fun updateName(u: AppUser, name: String): AppUser {
        val n = name.trim().take(60)
        if (n.isBlank()) error("Escribe tu nombre")
        col("users").document(u.id).update("name", n).await()
        // El perfil público lleva el mismo nombre (si la persona no tiene perfil, no existe y no pasa nada).
        runCatching { col("profiles").document(u.id).update("name", n).await() }
        return u.copy(name = n)
    }

    suspend fun provider(uid: String): Provider? = col("providers").document(uid).get().await().toObject(Provider::class.java)
    suspend fun saveProvider(uid: String, p: Provider) {
        col("providers").document(uid).set(p.copy(legalName = p.legalName.trim(), idNumber = p.idNumber.trim(), address = p.address.trim(), phone = p.phone.trim(), updatedAt = now())).await()
    }

    /** Derecho de acceso: todo lo que la plataforma guarda de esta persona, en un texto que puede leer y compartir. */
    suspend fun exportMyData(uid: String): String {
        fun clean(v: Any?): Any? = when (v) {
            null -> JSONObject.NULL
            is Timestamp -> v.toDate().toInstant().toString()
            is Date -> v.toInstant().toString()
            is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), clean(x)) } }
            is List<*> -> JSONArray().also { a -> v.forEach { a.put(clean(it)) } }
            else -> v
        }
        suspend fun one(c: String): Any = runCatching { col(c).document(uid).get().await().data }.getOrNull()?.let { clean(it) } ?: JSONObject.NULL
        suspend fun many(c: String, field: String): JSONArray {
            val docs = runCatching { col(c).whereEqualTo(field, uid).get().await().documents }.getOrDefault(emptyList())
            return JSONArray().also { a -> docs.forEach { d -> a.put((clean(d.data) as? JSONObject)?.put("_id", d.id) ?: JSONObject.NULL) } }
        }
        val out = JSONObject()
        out.put("generado", Instant.now().toString())
        out.put("cuenta", JSONObject().put("uid", uid).put("contacto", (auth.currentUser?.email ?: auth.currentUser?.phoneNumber).orEmpty()))
        out.put("perfil", one("users"))
        out.put("aceptaciones", many("legal_acceptances", "userId"))
        out.put("solicitudes_de_privacidad", many("privacy_requests", "userId"))
        out.put("identificacion_oferente", one("providers"))
        out.put("negocios", many("venues", "ownerId"))
        out.put("motos", many("bikes", "ownerId"))
        out.put("pedidos", many("orders", "fromId"))
        out.put("rentas", many("requests", "fromId"))
        out.put("perfil_de_conductor", one("rider_profiles"))
        out.put("actas_como_rider", many("actas", "riderId"))
        out.put("actas_como_dueno", many("actas", "ownerId"))
        out.put("cupones", many("coupons", "userId"))
        out.put("sos", many("sos", "fromId"))
        out.put("rutas_guardadas", many("routes", "userId"))
        out.put("perfil_publico", one("profiles"))
        out.put("estado_para_amigos", one("status"))
        out.put("solicitudes_de_amistad_enviadas", many("friend_requests", "from"))
        out.put("solicitudes_de_amistad_recibidas", many("friend_requests", "to"))
        out.put("bloqueos", many("blocks", "blocker"))
        val friends = runCatching { col("friendships").whereArrayContains("members", uid).get().await().documents }.getOrDefault(emptyList())
        out.put("amistades", JSONArray().also { a -> friends.forEach { a.put(clean(it.data)) } })
        out.put("resenas", many("reviews", "authorId"))
        val rides = runCatching { col("rodadas").whereArrayContains("joined", uid).get().await().documents }.getOrDefault(emptyList())
        out.put("rodadas", JSONArray().also { a -> rides.forEach { d -> a.put((clean(d.data) as? JSONObject)?.put("_id", d.id) ?: JSONObject.NULL) } })
        val chats = runCatching { col("chats").whereArrayContains("members", uid).get().await().documents }.getOrDefault(emptyList())
        out.put("mensajes_enviados", JSONArray().also { a -> chats.filter { it.getString("fromId") == uid }.forEach { a.put(clean(it.data)) } })
        return out.toString(2)
    }

    /**
     * Derecho de supresión: borra el perfil y lo que la persona publicó, y luego la cuenta de acceso.
     * Se conservan (ver Política de Privacidad, punto 8) el registro de aceptaciones, las solicitudes de privacidad y el
     * historial de servicios con otras personas, que son prueba y soporte de reclamos.
     */
    suspend fun deleteAccount(u: AppUser) {
        val fu = auth.currentUser ?: error("Tu sesión no es válida. Vuelve a ingresar.")
        // Firebase solo deja borrar la cuenta con un ingreso reciente. Se revisa ANTES de borrar datos para no dejarla a medias.
        val last = fu.metadata?.lastSignInTimestamp ?: 0
        if (now() - last > 4 * 60_000) error("Por seguridad, cierra sesión, vuelve a ingresar y elimina la cuenta en los siguientes minutos.")
        col("privacy_requests").document().set(PrivacyRequest(userId = u.id, type = RequestType.ACCOUNT_DELETED,
            detail = "Eliminación de la cuenta solicitada por el titular desde la aplicación.", contact = u.email)).await()
        for ((c, f) in listOf("routes" to "userId", "venues" to "ownerId", "bikes" to "ownerId")) {
            col(c).whereEqualTo(f, u.id).get().await().documents.chunked(400).forEach { chunk ->
                val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await()
            }
        }
        runCatching { col("providers").document(u.id).delete().await() }
        runCatching { col("rider_profiles").document(u.id).delete().await() }
        runCatching { col("rider_stats").document(u.id).delete().await() }
        runCatching { deleteSocial(u.id) }
        col("users").document(u.id).delete().await()
        fu.delete().await()
    }

    /** Borra el perfil público, el @usuario, las amistades, las solicitudes, los bloqueos y el estado de la persona. */
    private suspend fun deleteSocial(uid: String) {
        val p = col("profiles").document(uid).get().await()
        if (p.exists()) {
            val b = db.batch()
            b.delete(p.reference)
            p.getString("username")?.takeIf { it.isNotBlank() }?.let { b.delete(col("usernames").document(it)) }
            b.commit().await()
        }
        for (q in listOf(col("friendships").whereArrayContains("members", uid), col("friend_requests").whereEqualTo("from", uid),
            col("friend_requests").whereEqualTo("to", uid), col("blocks").whereEqualTo("blocker", uid))) {
            q.get().await().documents.chunked(400).forEach { ch -> val b = db.batch(); ch.forEach { b.delete(it.reference) }; b.commit().await() }
        }
        runCatching { col("status").document(uid).delete().await() }
        // Rodadas: se borran las que creó (con sus posiciones en vivo); de las demás sale y retira solicitudes e invitaciones.
        col("rodadas").whereEqualTo("creatorId", uid).get().await().documents.forEach { d ->
            runCatching {
                d.reference.collection("live").get().await().documents.forEach { it.reference.delete().await() }
                d.getString("inviteCode")?.takeIf { it.isNotBlank() }?.let { col("rodada_codes").document(it).delete().await() }
                d.reference.delete().await()
            }
        }
        col("rodadas").whereArrayContains("joined", uid).get().await().toObjects(Rodada::class.java).filter { it.creatorId != uid }
            .forEach { runCatching { leaveRodada(it, uid) } }
        col("rodadas").whereArrayContains("pending", uid).get().await().toObjects(Rodada::class.java).forEach { runCatching { cancelJoinRequest(it, uid) } }
        col("rodadas").whereArrayContains("invited", uid).get().await().toObjects(Rodada::class.java).forEach { runCatching { declineInvite(it, uid) } }
        col("link_joins").whereEqualTo("uid", uid).get().await().documents.forEach { runCatching { it.reference.delete().await() } }
        // Reseñas: llevan el nombre de la persona, así que se borran (el promedio del comercio no cambia).
        col("reviews").whereEqualTo("authorId", uid).get().await().documents.forEach { runCatching { it.reference.delete().await() } }
    }

    // ───────── Perfil público y amigos ─────────
    fun profile(uid: String) = col("profiles").document(uid).liveDoc<Profile>()

    /** Perfiles públicos por uid, en grupos de 30 (el límite de whereIn). */
    fun profiles(ids: List<String>): Flow<List<Profile>> {
        val unique = ids.distinct()
        if (unique.isEmpty()) return flowOf(emptyList())
        val parts = unique.chunked(30).map { c -> col("profiles").whereIn(FieldPath.documentId(), c).live<Profile>() }
        return combine(parts) { arr -> arr.flatMap { it } }
    }

    /** Busca un rider por su @usuario exacto. No hay listados: solo se encuentra a quien uno ya conoce. */
    suspend fun findProfile(username: String): Profile? {
        val u = username.trim().removePrefix("@").lowercase()
        if (!USERNAME.matches(u)) return null
        val uid = col("usernames").document(u).get().await().getString("uid") ?: return null
        return col("profiles").document(uid).get().await().toObject(Profile::class.java)
    }

    /** Crea o actualiza el perfil. Si cambia el @usuario, en el mismo lote se reserva el nuevo y se libera el anterior. */
    suspend fun saveProfile(uid: String, old: Profile?, p: Profile): Profile {
        val username = p.username.trim().removePrefix("@").lowercase()
        if (!USERNAME.matches(username)) error("El @usuario debe tener de 3 a 20 caracteres: letras minúsculas sin tilde, números, punto o guion bajo.")
        val name = p.name.trim().take(60)
        if (name.isBlank()) error("Escribe tu nombre")
        val changed = old?.username != username
        if (changed) {
            val taken = col("usernames").document(username).get().await()
            if (taken.exists() && taken.getString("uid") != uid) error("Ese @usuario ya lo tiene otra persona. Prueba otro.")
        }
        val b = db.batch()
        b.set(col("profiles").document(uid), mapOf("username" to username, "name" to name, "moto" to p.moto, "color" to p.color, "showStatus" to p.showStatus))
        if (changed) {
            b.set(col("usernames").document(username), mapOf("uid" to uid))
            if (old != null && old.username.isNotBlank()) b.delete(col("usernames").document(old.username))
        }
        b.commit().await()
        return p.copy(id = uid, username = username, name = name)
    }

    fun friendships(uid: String) = col("friendships").whereArrayContains("members", uid).live<Friendship>()
    fun friendRequestsTo(uid: String) = col("friend_requests").whereEqualTo("to", uid).live<FriendRequest>()
    fun friendRequestsFrom(uid: String) = col("friend_requests").whereEqualTo("from", uid).live<FriendRequest>()
    fun blocks(uid: String) = col("blocks").whereEqualTo("blocker", uid).live<Block>()
    /** Estado de un amigo. Si no son amigos (o lo tiene apagado), las reglas no lo dejan leer y llega null. */
    fun presence(uid: String) = col("status").document(uid).liveDoc<Presence>()

    suspend fun sendFriendRequest(me: Profile, to: Profile) {
        if (me.id == to.id) error("Ese perfil es el tuyo.")
        val b = db.batch()
        b.set(col("friend_requests").document("${me.id}_${to.id}"), mapOf(
            "from" to me.id, "to" to to.id, "fromName" to me.name, "fromUsername" to me.username, "fromMoto" to me.moto, "fromColor" to me.color,
            "toName" to to.name, "toUsername" to to.username, "createdAt" to now()))
        b.stamp(me.id, "friend")
        b.commit().await()
    }

    /** Aceptar crea la amistad y borra la solicitud en el mismo lote: las reglas exigen las dos cosas juntas. */
    suspend fun acceptFriend(r: FriendRequest) {
        val (a, z) = listOf(r.from, r.to).sorted()
        val b = db.batch()
        b.set(col("friendships").document("${a}_$z"), mapOf("members" to listOf(a, z), "since" to now()))
        b.delete(col("friend_requests").document(r.id))
        b.commit().await()
    }

    /** Rechazar (quien la recibió) o cancelar (quien la envió). */
    suspend fun deleteFriendRequest(r: FriendRequest) { col("friend_requests").document(r.id).delete().await() }
    suspend fun unfriend(me: String, other: String) { col("friendships").document(pairId(me, other)).delete().await() }

    /** Bloquear: en el mismo lote se borran la amistad y las solicitudes que haya entre los dos. */
    suspend fun block(me: String, other: Profile, areFriends: Boolean, pending: List<FriendRequest>) {
        val b = db.batch()
        b.set(col("blocks").document("${me}_${other.id}"), mapOf("blocker" to me, "blocked" to other.id, "blockedName" to other.name,
            "blockedUsername" to other.username, "createdAt" to now()))
        if (areFriends) b.delete(col("friendships").document(pairId(me, other.id)))
        pending.filter { (it.from == me && it.to == other.id) || (it.from == other.id && it.to == me) }.forEach { b.delete(col("friend_requests").document(it.id)) }
        b.commit().await()
    }
    suspend fun unblock(b: Block) { col("blocks").document(b.id).delete().await() }

    /** Latido del estado para los amigos: "online", o "riding" con el nombre de la rodada activa. */
    suspend fun heartbeat(uid: String, rodada: String) {
        col("status").document(uid).set(mapOf("state" to if (rodada.isBlank()) "online" else "riding", "rodada" to rodada.take(80),
            "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun setShowStatus(p: Profile, on: Boolean) {
        col("profiles").document(p.id).update("showStatus", on).await()
        if (!on) runCatching { col("status").document(p.id).delete().await() }
    }

    // ───────── Comercios ─────────
    fun venues() = col("venues").live<Venue>()

    suspend fun newVenue(owner: String, name: String, corridor: String) {
        val c = C.corridor(corridor)
        col("venues").add(Venue(ownerId = owner, name = name, corridor = corridor, lat = c.lat, lng = c.lng,
            kind = C.CATS["food"]!!.third, promo = "Define tu promoción", desc = "Cuéntales a los moteros qué ofreces.")).await()
    }

    /** Taller: aparece para los riders en "Gasolina & Talleres" y arranca con un catálogo de servicios básico. */
    suspend fun newWorkshop(owner: String, name: String, corridor: String) {
        val c = C.corridor(corridor)
        val k = C.CATS["moto"]!!
        val services = C.SOS_KINDS.mapIndexed { i, s -> MenuItem("s$i", s.name, "Atención en carretera", s.price, "Servicios") }
        col("venues").add(Venue(ownerId = owner, name = name, corridor = corridor, lat = c.lat, lng = c.lng, types = listOf("moto"),
            badge = k.second, kind = k.third, promo = "Revisión básica gratis", desc = "Mecánica y asistencia para motos en carretera.", menu = services)).await()
    }

    suspend fun saveVenue(v: Venue) { col("venues").document(v.id).set(v).await() }
    suspend fun setMenu(venueId: String, menu: List<MenuItem>) { col("venues").document(venueId).update("menu", menu).await() }
    suspend fun addVisit(venueId: String) { col("venues").document(venueId).update("visits", FieldValue.increment(1)).await() }

    // ───────── Pedidos ─────────
    fun ordersFrom(uid: String) = col("orders").whereEqualTo("fromId", uid).live<Order>()
    fun ordersOfOwner(uid: String) = col("orders").whereEqualTo("ownerId", uid).live<Order>()

    /** readyAt: hora (epoch ms) a la que el rider quiere el pedido listo; eta es la misma hora en texto para mostrar. */
    suspend fun placeOrder(v: Venue, u: AppUser, items: List<OrderItem>, eta: String, readyAt: Long, note: String) {
        val b = db.batch()
        b.set(col("orders").document(), Order(venueId = v.id, venueName = v.name, ownerId = v.ownerId, fromId = u.id, fromName = u.name,
            items = items, total = items.sumOf { it.qty * it.price }, eta = eta.take(20), note = note.trim().take(300), status = "new", createdAt = now(),
            readyAt = readyAt))
        b.stamp(u.id, "order")
        b.commit().await()
    }

    suspend fun setOrderStatus(id: String, st: String) { col("orders").document(id).update("status", st).await() }
    /** El rider cancela sin costo mientras el comercio no haya aceptado el pedido (Política de cancelaciones, punto 3). */
    suspend fun cancelOrder(id: String) { col("orders").document(id).update("status", "can").await() }

    // ───────── Reseñas (fase 3) ─────────
    /** Reseñas de un comercio, taller o moto. Sin índices compuestos: se ordenan en el teléfono. */
    fun reviewsOf(targetType: String, targetId: String) = col("reviews").whereEqualTo("targetType", targetType).whereEqualTo("targetId", targetId).live<Review>()
    fun myReviews(uid: String) = col("reviews").whereEqualTo("authorId", uid).live<Review>()

    /**
     * Publica una reseña. Si es de un comercio, taller o moto, en el mismo lote se actualiza su promedio: las reglas
     * comprueban la cuenta, que la reseña sea nueva y que la escriba quien usó el servicio.
     */
    suspend fun submitReview(r: Review) {
        val id = "${r.kind}_${r.serviceId}"
        val b = db.batch()
        b.set(col("reviews").document(id), mapOf("kind" to r.kind, "serviceId" to r.serviceId, "targetType" to r.targetType, "targetId" to r.targetId,
            "authorId" to r.authorId, "authorName" to r.authorName.take(60).ifBlank { "Rider" }, "stars" to r.stars.coerceIn(1, 5),
            "comment" to r.comment.trim().take(200), "createdAt" to now()))
        val target = when (r.targetType) { "venue" -> col("venues").document(r.targetId); "bike" -> col("bikes").document(r.targetId); else -> null }
        if (target != null) {
            val snap = target.get().await()
            val n0 = snap.getLong("reviews") ?: 0L
            val avg0 = snap.getDouble("rating") ?: 5.0
            val n = n0 + 1
            b.update(target, mapOf("rating" to (avg0 * n0 + r.stars.coerceIn(1, 5)) / n, "reviews" to n, "lastReview" to id))
        }
        b.commit().await()
    }

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

    // ───────── Motos y rodadas ─────────
    fun bikes() = col("bikes").live<Bike>()
    suspend fun saveBike(b: Bike) { if (b.id.isBlank()) col("bikes").add(b).await() else col("bikes").document(b.id).set(b).await() }
    suspend fun deleteBike(id: String) { col("bikes").document(id).delete().await() }

    // ───────── Renta segura (fase 5) ─────────
    /** Perfil de conductor. Privado: las reglas solo se lo dejan leer a su titular. */
    fun riderProfile(uid: String) = col("rider_profiles").document(uid).liveDoc<RiderProfile>()
    suspend fun saveRiderProfile(uid: String, p: RiderProfile) {
        col("rider_profiles").document(uid).set(mapOf(
            "licenseCat" to p.licenseCat, "licenseUntil" to p.licenseUntil, "since" to p.since, "maxCc" to p.maxCc,
            "emergencyName" to p.emergencyName.trim().take(60),
            "emergencyPhone" to p.emergencyPhone.filter { it.isDigit() || it == '+' || it == ' ' }.trim().take(20),
            "updatedAt" to now())).await()
    }

    /** Actas en las que participa la persona: como dueño (las que llenó) o como rider (las que confirma). */
    fun actasOf(uid: String, owner: Boolean) = col("actas").whereEqualTo(if (owner) "ownerId" else "riderId", uid).live<Acta>()

    /** El dueño llena el acta. ID fijo: una de entrega y una de devolución por renta. */
    suspend fun createActa(a: Acta) {
        col("actas").document("${a.reqId}_${a.kind}").set(a.copy(id = "", bikeName = a.bikeName.take(80), condition = a.condition.trim().take(500),
            damages = a.damages.trim().take(500), items = a.items.take(12), confirmed = false, createdAt = null, confirmedAt = null)).await()
    }
    /** Solo mientras el rider no la haya confirmado (para corregirla). */
    suspend fun deleteActa(id: String) { col("actas").document(id).delete().await() }

    /**
     * El rider confirma el acta y, en el mismo lote, la renta avanza: con la de entrega empieza; con la de devolución
     * termina y se borra el contacto de emergencia de la solicitud. Las reglas no dejan avanzar sin el acta confirmada.
     */
    suspend fun confirmActa(a: Acta) {
        val b = db.batch()
        b.update(col("actas").document(a.id), mapOf("confirmed" to true, "confirmedAt" to FieldValue.serverTimestamp()))
        val req = col("requests").document(a.reqId)
        if (a.kind == "entrega") b.update(req, "status", "ride")
        else b.update(req, mapOf("status" to "fin", "emergencyName" to "", "emergencyPhone" to ""))
        b.commit().await()
    }

    /** S.O.S. de riders que van en una moto de este dueño. */
    fun sosForOwner(uid: String) = col("sos").whereEqualTo("rentalOwnerId", uid).live<Sos>()

    // ───────── Rodadas (fase 2) ─────────
    /** Lo que cada quien puede ver: las públicas, aquellas a las que lo invitaron y en las que ya está. */
    fun rodadas(uid: String): Flow<List<Rodada>> = combine(
        col("rodadas").whereEqualTo("visibility", "public").live<Rodada>(),
        col("rodadas").whereArrayContains("invited", uid).live<Rodada>(),
        col("rodadas").whereArrayContains("joined", uid).live<Rodada>(),
    ) { a, b, c -> (c + b + a).distinctBy { it.id } }

    fun rodada(id: String) = col("rodadas").document(id).liveDoc<Rodada>()

    /** Crea la rodada y, en el mismo lote, su código de invitación (para compartirla por enlace). */
    suspend fun createRodada(r: Rodada): String {
        val ref = col("rodadas").document()
        val code = newInviteCode()
        val b = db.batch()
        b.set(ref, r.copy(joined = listOf(r.creatorId), pending = emptyList(), invited = emptyList(), lastInvited = "", leaderId = r.creatorId,
            sweeperId = "", status = "planned", startedAt = null, endedAt = null, inviteCode = code))
        b.set(col("rodada_codes").document(code), mapOf("rodadaId" to ref.id, "creatorId" to r.creatorId, "name" to r.name.take(80),
            "whenLabel" to r.whenLabel.take(40), "meet" to r.meet.take(160)))
        b.commit().await()
        return ref.id
    }

    /** Rodadas viejas sin código: el creador le crea uno la primera vez que la comparte. */
    suspend fun ensureInviteCode(r: Rodada): String {
        if (r.inviteCode.isNotBlank()) return r.inviteCode
        val code = newInviteCode()
        val b = db.batch()
        b.update(col("rodadas").document(r.id), mapOf("inviteCode" to code))
        b.set(col("rodada_codes").document(code), mapOf("rodadaId" to r.id, "creatorId" to r.creatorId, "name" to r.name.take(80),
            "whenLabel" to r.whenLabel.take(40), "meet" to r.meet.take(160)))
        b.commit().await()
        return code
    }

    /** Invitación por código: lo básico de la rodada, o null si el código no existe. */
    suspend fun inviteByCode(code: String): RodadaCode? {
        val c = code.trim().uppercase().filter { it.isLetterOrDigit() }
        if (c.length != 8) return null
        return col("rodada_codes").document(c).get().await().toObject(RodadaCode::class.java)
    }

    /** Unirse con el código: la prueba (link_joins) y la entrada a la rodada van en el mismo lote. */
    suspend fun joinByCode(c: RodadaCode, uid: String) {
        val b = db.batch()
        b.set(col("link_joins").document("${c.rodadaId}_$uid"), mapOf("uid" to uid, "rodadaId" to c.rodadaId, "code" to c.id))
        b.update(col("rodadas").document(c.rodadaId), mapOf("joined" to FieldValue.arrayUnion(uid), "pending" to FieldValue.arrayRemove(uid),
            "invited" to FieldValue.arrayRemove(uid)))
        b.commit().await()
    }

    private fun newInviteCode(): String {
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // sin 0/O ni 1/I, para dictarlo sin confusiones
        val rnd = java.security.SecureRandom()
        return (1..8).map { abc[rnd.nextInt(abc.length)] }.joinToString("")
    }

    private suspend fun rod(id: String, m: Map<String, Any>) { col("rodadas").document(id).update(m).await() }

    /** Unirse directo: rodada sin aprobación, o con invitación. */
    suspend fun joinRodada(r: Rodada, uid: String) {
        if (r.joined.size >= r.seats) error("Esta rodada ya está completa")
        rod(r.id, mapOf("joined" to FieldValue.arrayUnion(uid), "pending" to FieldValue.arrayRemove(uid)))
    }
    suspend fun requestJoin(r: Rodada, uid: String) = rod(r.id, mapOf("pending" to FieldValue.arrayUnion(uid)))
    suspend fun cancelJoinRequest(r: Rodada, uid: String) = rod(r.id, mapOf("pending" to FieldValue.arrayRemove(uid)))
    suspend fun declineInvite(r: Rodada, uid: String) = rod(r.id, mapOf("invited" to FieldValue.arrayRemove(uid)))

    /** Salir. Si era el líder, el liderazgo vuelve al creador; si cerraba el grupo, ese puesto queda libre. */
    suspend fun leaveRodada(r: Rodada, uid: String) {
        val m = mutableMapOf<String, Any>("joined" to FieldValue.arrayRemove(uid))
        if (r.leaderId == uid) m["leaderId"] = r.creatorId
        if (r.sweeperId == uid) m["sweeperId"] = ""
        val b = db.batch()
        b.update(col("rodadas").document(r.id), m)
        b.delete(col("rodadas").document(r.id).collection("live").document(uid))
        b.commit().await()
    }

    // Solo el creador
    suspend fun approveJoin(r: Rodada, uid: String) {
        if (r.joined.size >= r.seats) error("La rodada ya está completa. Aumenta el número de motos o quita a alguien.")
        rod(r.id, mapOf("pending" to FieldValue.arrayRemove(uid), "joined" to FieldValue.arrayUnion(uid)))
    }
    suspend fun rejectJoin(r: Rodada, uid: String) = rod(r.id, mapOf("pending" to FieldValue.arrayRemove(uid)))
    suspend fun kick(r: Rodada, uid: String) {
        val m = mutableMapOf<String, Any>("joined" to FieldValue.arrayRemove(uid))
        if (r.leaderId == uid) m["leaderId"] = r.creatorId
        if (r.sweeperId == uid) m["sweeperId"] = ""
        val b = db.batch()
        b.update(col("rodadas").document(r.id), m)
        b.delete(col("rodadas").document(r.id).collection("live").document(uid))
        b.commit().await()
    }
    /** Invita a un amigo (de a uno: las reglas comprueban la amistad con lastInvited). */
    suspend fun invite(r: Rodada, friendUid: String) = rod(r.id, mapOf("invited" to FieldValue.arrayUnion(friendUid), "lastInvited" to friendUid))
    suspend fun setRoles(r: Rodada, leader: String, sweeper: String) = rod(r.id, mapOf("leaderId" to leader, "sweeperId" to sweeper))
    suspend fun startRodada(r: Rodada) = rod(r.id, mapOf("status" to "active", "startedAt" to FieldValue.serverTimestamp()))

    /** Terminar: la rodada queda cerrada y se borran todas las posiciones en vivo en el mismo lote. */
    suspend fun endRodada(r: Rodada) {
        val live = col("rodadas").document(r.id).collection("live").get().await().documents
        val b = db.batch()
        b.update(col("rodadas").document(r.id), mapOf("status" to "done", "endedAt" to FieldValue.serverTimestamp()))
        live.take(450).forEach { b.delete(it.reference) }
        b.commit().await()
    }
    suspend fun deleteRodada(r: Rodada) {
        val b = db.batch()
        b.delete(col("rodadas").document(r.id))
        if (r.inviteCode.isNotBlank()) b.delete(col("rodada_codes").document(r.inviteCode))
        b.commit().await()
    }

    // ───────── Estadísticas del rider (perfil y medallas) ─────────
    fun riderStats(uid: String) = col("rider_stats").document(uid).liveDoc<RiderStats>()

    /**
     * Suma una ruta completada con el guiado. La primera vez crea las estadísticas con la fecha en que se creó la cuenta.
     * Las reglas solo dejan sumar de a una y una cada 20 minutos; si no se puede, no pasa nada.
     */
    suspend fun addCompletedRoute(uid: String) {
        val ref = col("rider_stats").document(uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val since = auth.currentUser?.metadata?.creationTimestamp?.takeIf { it > 1_700_000_000_000L } ?: System.currentTimeMillis()
            ref.set(mapOf("since" to since, "routes" to 1, "lastAt" to FieldValue.serverTimestamp())).await()
        } else {
            ref.update(mapOf("routes" to FieldValue.increment(1), "lastAt" to FieldValue.serverTimestamp())).await()
        }
    }

    // Mapa del grupo
    fun livePositions(rodadaId: String) = col("rodadas").document(rodadaId).collection("live").live<LivePos>()

    suspend fun shareLive(rodadaId: String, uid: String, lat: Double, lng: Double, heading: Double, p: Profile?, fallbackName: String, sos: Boolean) {
        col("rodadas").document(rodadaId).collection("live").document(uid).set(mapOf(
            "lat" to lat, "lng" to lng, "heading" to heading, "name" to (p?.name ?: fallbackName).take(60).ifBlank { "Rider" },
            "moto" to (p?.moto ?: "naked"), "color" to (p?.color ?: "orange"), "sos" to sos, "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun stopLive(rodadaId: String, uid: String) { runCatching { col("rodadas").document(rodadaId).collection("live").document(uid).delete().await() } }

    // ───────── Solicitudes de renta ─────────
    fun requestsFrom(uid: String) = col("requests").whereEqualTo("fromId", uid).live<ServiceRequest>()
    fun requestsTo(uid: String) = col("requests").whereEqualTo("toId", uid).live<ServiceRequest>()
    /**
     * Crea la solicitud. Si la moto es de reserva inmediata, aparta las fechas en el mismo lote: las reglas solo dejan
     * tocar `booked` a quien acaba de crear una solicitud aceptada para esa moto y esas fechas.
     */
    suspend fun createRequest(r: ServiceRequest, instantBike: Bike? = null) {
        val ref = col("requests").document()
        val b = db.batch()
        b.set(ref, r.copy(createdAt = now()))
        if (instantBike != null) b.update(col("bikes").document(instantBike.id), mapOf("booked" to FieldValue.arrayUnion("${r.dateFrom}|${r.dateTo}"), "lastReq" to ref.id))
        b.stamp(r.fromId, "request")
        b.commit().await()
    }

    /** El dueño acepta: la solicitud y el apartado de las fechas en la moto van en un solo lote. */
    suspend fun acceptRequest(r: ServiceRequest) {
        val b = db.batch()
        b.update(col("requests").document(r.id), "status", "ok")
        b.update(col("bikes").document(r.itemId), "booked", FieldValue.arrayUnion("${r.dateFrom}|${r.dateTo}"))
        b.commit().await()
    }

    /**
     * El dueño rechaza una solicitud pendiente o cancela en la entrega (reason: clave de C.OWNER_CANCEL). Se borra el
     * contacto de emergencia y, si ya estaba aceptada, se liberan las fechas de la moto. El rider recibe el reembolso total.
     */
    suspend fun rejectRequest(r: ServiceRequest, reason: String = "") {
        val b = db.batch()
        b.update(col("requests").document(r.id), mapOf("status" to "no", "ownerCancel" to reason, "emergencyName" to "", "emergencyPhone" to ""))
        if (r.status == "ok") b.update(col("bikes").document(r.itemId), "booked", FieldValue.arrayRemove("${r.dateFrom}|${r.dateTo}"))
        b.commit().await()
    }

    /**
     * Cancelación por el rider antes de que empiece el servicio. kind = "retracto" (dentro de los 5 días hábiles,
     * art. 47 Ley 1480) o "cancelacion". Queda la fecha, y si la renta ya estaba aceptada se liberan las fechas de la moto.
     */
    suspend fun cancelRequest(r: ServiceRequest, kind: String) {
        val b = db.batch()
        b.update(col("requests").document(r.id), mapOf("status" to "can", "cancelKind" to kind, "cancelledAt" to now(), "emergencyName" to "", "emergencyPhone" to ""))
        if (r.type == "rent" && r.status == "ok")
            b.update(col("bikes").document(r.itemId), mapOf("booked" to FieldValue.arrayRemove("${r.dateFrom}|${r.dateTo}"), "lastReq" to r.id))
        b.commit().await()
    }

    // ───────── S.O.S. taller móvil ─────────
    /** Los S.O.S. que pidió este rider. */
    fun sosMine(uid: String) = col("sos").whereEqualTo("fromId", uid).live<Sos>()
    /** Lo único que puede ver un taller: los S.O.S. que aún buscan taller y los que él mismo aceptó. */
    fun sosForWorkshop(uid: String): Flow<List<Sos>> =
        combine(col("sos").whereEqualTo("status", "new").live<Sos>(), col("sos").whereEqualTo("providerId", uid).live<Sos>()) { a, b -> (a + b).distinctBy { it.id } }

    suspend fun createSos(s: Sos) {
        val b = db.batch()
        b.set(col("sos").document(), s.copy(locationText = s.locationText.trim().take(200), note = s.note.trim().take(300), createdAt = now()))
        b.stamp(s.fromId, "sos")
        b.commit().await()
    }
    suspend fun setSosStatus(id: String, st: String) { col("sos").document(id).update("status", st).await() }
    /** El taller llegó: empieza la atención y se borra su ubicación. */
    suspend fun arriveSos(id: String) { col("sos").document(id).update(mapOf("status" to "site", "providerLat" to 0.0, "providerLng" to 0.0)).await() }
    /** Dónde va el taller mientras va en camino. Solo lo ve el rider de ese S.O.S. */
    suspend fun shareProviderLocation(id: String, lat: Double, lng: Double) {
        col("sos").document(id).update(mapOf("providerLat" to lat, "providerLng" to lng, "providerAt" to FieldValue.serverTimestamp())).await()
    }
    /** Al cancelar se borran las coordenadas: la ubicación solo se conserva mientras el servicio está activo. */
    suspend fun cancelSos(id: String) {
        col("sos").document(id).update(mapOf("status" to "no", "lat" to 0.0, "lng" to 0.0, "doneAt" to now(), "providerLat" to 0.0, "providerLng" to 0.0)).await()
    }
    suspend fun acceptSos(id: String, providerId: String, providerName: String, eta: Int, venueId: String) {
        val ref = col("sos").document(id)
        db.runTransaction { tx ->
            val s = tx.get(ref)
            if (s.getString("status") != "new") throw IllegalStateException("Otro taller ya aceptó este S.O.S.")
            tx.update(ref, mapOf("status" to "ok", "providerId" to providerId, "providerName" to providerName, "eta" to eta, "acceptedAt" to now(), "providerVenueId" to venueId))
        }.await()
    }
    /** Al terminar se borran las coordenadas del rider; queda solo la descripción del lugar. */
    suspend fun finishSos(id: String) {
        col("sos").document(id).update(mapOf("status" to "fin", "doneAt" to now(), "lat" to 0.0, "lng" to 0.0, "providerLat" to 0.0, "providerLng" to 0.0)).await()
    }
    /** El taller suelta el S.O.S. (no puede ir): vuelve a quedar disponible para otros talleres. */
    suspend fun releaseSos(id: String) {
        col("sos").document(id).update(mapOf("status" to "new", "providerId" to "", "providerName" to "", "eta" to 0, "providerVenueId" to "",
            "providerLat" to 0.0, "providerLng" to 0.0)).await()
    }

    // ───────── Chat ─────────
    /** Solo los mensajes de este hilo en los que participa esta persona (las reglas no dejan leer los demás). */
    fun messages(thread: String, uid: String) = col("chats").whereEqualTo("thread", thread).whereArrayContains("members", uid).live<ChatMessage>()
    suspend fun send(thread: String, u: AppUser, to: String, text: String) {
        if (to.isBlank()) error("Este chat no tiene destinatario.")
        col("chats").add(ChatMessage(thread = thread, fromId = u.id, fromName = u.name, text = text.trim().take(1000), createdAt = now(),
            members = listOf(u.id, to).sorted())).await()
    }

    // ───────── Rutas guardadas ─────────
    fun routesOf(uid: String) = col("routes").whereEqualTo("userId", uid).live<SavedRoute>()
    suspend fun saveRoute(r: SavedRoute) { col("routes").add(r.copy(createdAt = now())).await() }
    suspend fun deleteRoute(id: String) { col("routes").document(id).delete().await() }

    // ───────── Datos demo (tomados del prototipo; solo en la versión de pruebas) ─────────
    /**
     * Borra solo los datos demo: los que no tienen dueño (ownerId / creatorId vacío). Los comercios, motos y rodadas
     * creados por usuarios reales no se tocan. Después se pueden volver a cargar.
     */
    suspend fun clearDemo() {
        for ((c, f) in listOf("venues" to "ownerId", "bikes" to "ownerId", "rodadas" to "creatorId")) {
            col(c).whereEqualTo(f, "").get().await().documents.chunked(400).forEach { chunk ->
                val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await()
            }
        }
        col("reviews").whereEqualTo("kind", "demo").get().await().documents.chunked(400).forEach { chunk ->
            val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await()
        }
        col("meta").document("seed").delete().await()
    }

    /** Borra los datos demo y los vuelve a cargar, sin repetidos. */
    suspend fun resetDemo() { clearDemo(); seedDemo() }

    suspend fun seedDemo() {
        val marker = col("meta").document("seed")
        if (marker.get().await().exists()) error("Los datos demo ya fueron cargados")
        fun m(i: Int, n: String, d: String, p: Long, c: String) = MenuItem("m$i", n, d, p, c)
        // lat/lng reales (aproximadas) para los lugares de Cali; los demás se ubican alrededor del centro de su corredor.
        data class S(val n: String, val t: List<String>, val cor: String, val km: Int, val r: Double, val rv: Int, val promo: String,
                     val desc: String, val tags: List<String>, val cu: List<String>, val menu: List<MenuItem>, val lat: Double = 0.0, val lng: Double = 0.0)
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

            // ── Cali ciudad · sitios turísticos (coordenadas aproximadas; ajústalas si hace falta)
            S("Monumento a Sebastián de Belalcázar", listOf("tour"), "cali", 2, 4.7, 0, "", "Estatua del fundador de Cali en el cerro del oeste, con vista panorámica sobre la ciudad.",
                listOf("mirador", "historia", "monumento", "belalcazar"), emptyList(), emptyList(), 3.4517, -76.5468),
            S("Parque del Gato del Río", listOf("tour"), "cali", 2, 4.8, 0, "", "El Gato de Hernando Tejada a orillas del río Cali, rodeado de las gatas intervenidas por artistas.",
                listOf("parque", "rio", "gato", "arte"), emptyList(), emptyList(), 3.4558, -76.5393),
            S("Loma de la Cruz", listOf("tour"), "cali", 3, 4.6, 0, "", "Parque artesanal con artesanías, música en vivo y comida callejera, sobre todo al caer la tarde.",
                listOf("artesanias", "parque", "comida", "loma"), emptyList(), emptyList(), 3.4431, -76.5392),
            S("Cristo Rey", listOf("tour"), "cali", 6, 4.8, 0, "", "La estatua en el cerro de Los Cristales: el mirador más alto sobre todo el valle. Vía de montaña.",
                listOf("mirador", "cristo", "cerro", "panoramica"), emptyList(), emptyList(), 3.4364, -76.5649),
            S("Colina de San Antonio", listOf("tour"), "cali", 3, 4.8, 0, "", "Capilla colonial y mirador en el barrio más tradicional de Cali, con cafés y casas antiguas.",
                listOf("mirador", "historia", "san antonio", "cafe"), emptyList(), emptyList(), 3.4466, -76.5384),
            S("Iglesia La Ermita", listOf("tour"), "cali", 1, 4.8, 0, "", "Templo neogótico junto al río Cali y al Bulevar del Río, uno de los íconos de la ciudad.",
                listOf("iglesia", "historia", "centro", "rio"), emptyList(), emptyList(), 3.4530, -76.5326),
            S("Bulevar del Río", listOf("tour"), "cali", 1, 4.7, 0, "", "Paseo peatonal a lo largo del río Cali, entre La Ermita y el Puente Ortiz.",
                listOf("paseo", "rio", "centro"), emptyList(), emptyList(), 3.4521, -76.5340),
            S("Zoológico de Cali", listOf("tour"), "cali", 3, 4.8, 0, "", "Uno de los zoológicos más reconocidos del país, a orillas del río Cali en Santa Teresita.",
                listOf("zoologico", "familia", "naturaleza"), emptyList(), emptyList(), 3.4486, -76.5546),

            // ── Cali ciudad · restaurantes reales usados SOLO como datos de prueba (no son aliados reales; menús y promociones inventados)
            S("El Zaguán de San Antonio", listOf("food"), "cali", 3, 4.6, 0, "Cupón de prueba", "Cocina vallecaucana tradicional con terraza sobre los tejados de San Antonio.",
                listOf("tipica", "sancocho", "san antonio", "terraza"), listOf("Criolla"),
                listOf(m(1, "Sancocho de gallina", "Con arroz y aguacate", 32000, "Platos fuertes"), m(2, "Arroz atollado", "Receta valluna", 30000, "Platos fuertes"), m(3, "Lulada", "Bebida típica", 9000, "Bebidas")), 3.4471, -76.5379),
            S("Platillos Voladores", listOf("food"), "cali", 2, 4.7, 0, "Cupón de prueba", "Cocina de autor con producto local en el barrio Granada (Calle 3 N # 7-19).",
                listOf("autor", "granada", "gourmet"), listOf("Saludable"),
                listOf(m(1, "Pesca del día", "Con leche de coco", 54000, "Platos fuertes"), m(2, "Ceviche de la casa", "Para compartir", 36000, "Entradas"), m(3, "Jugo natural", "Fruta de temporada", 10000, "Bebidas")), 3.4573, -76.5333),
            S("Domingo", listOf("food"), "cali", 3, 4.8, 0, "Cupón de prueba", "Cocina del Pacífico colombiano moderna en San Antonio, con menú degustación.",
                listOf("pacifico", "autor", "san antonio", "pescado"), listOf("Pescado"),
                listOf(m(1, "Encocado de camarón", "Arroz con coco", 52000, "Platos fuertes"), m(2, "Pandebono de la casa", "Para la mesa", 12000, "Entradas"), m(3, "Viche cóctel", "Destilado del Pacífico", 26000, "Bebidas")), 3.4462, -76.5371),
            S("Casa Ibérica", listOf("food"), "cali", 2, 4.6, 0, "Cupón de prueba", "Tapas y paella españolas en El Peñón.",
                listOf("tapas", "paella", "penon", "espanola"), listOf("Pescado"),
                listOf(m(1, "Paella para dos", "Mixta", 98000, "Platos fuertes"), m(2, "Tabla de tapas", "Selección de la casa", 45000, "Entradas"), m(3, "Sangría", "Copa", 18000, "Bebidas")), 3.4497, -76.5418),
            S("Pizzería Carpaneto", listOf("food"), "cali", 2, 4.7, 0, "Cupón de prueba", "Pizza napolitana en el barrio Granada.",
                listOf("pizza", "italiana", "granada"), listOf("Pizza"),
                listOf(m(1, "Pizza margarita", "Napolitana", 38000, "Platos fuertes"), m(2, "Pizza diávola", "Salami picante", 44000, "Platos fuertes"), m(3, "Tiramisú", "Porción", 16000, "Postres")), 3.4591, -76.5341),
            S("Cantina La 15", listOf("food"), "cali", 3, 4.4, 0, "Cupón de prueba", "Comida mexicana y cócteles a buen precio en Granada Norte.",
                listOf("mexicana", "tacos", "granada", "barato", "economico"), listOf("Parrilla"),
                listOf(m(1, "Tacos al pastor", "Orden de 3", 26000, "Platos fuertes"), m(2, "Nachos para compartir", "Con guacamole", 28000, "Entradas"), m(3, "Michelada", "Vaso", 14000, "Bebidas")), 3.4606, -76.5327),
        )
        venues.forEachIndexed { i, s ->
            val c = C.corridor(s.cor)
            val k = C.CATS[s.t[0]]!!
            // ID fijo: cargar los datos demo dos veces ya no genera comercios repetidos
            col("venues").document("demo_v${i + 1}").set(Venue(name = s.n, types = s.t, corridor = s.cor, km = s.km, rating = s.r, reviews = s.rv, badge = k.second, kind = k.third,
                promo = s.promo, desc = s.desc, tags = s.tags, cuisines = s.cu, menu = s.menu,
                lat = if (s.lat != 0.0) s.lat else c.lat + ((i % 3) - 1) * 0.012, lng = if (s.lng != 0.0) s.lng else c.lng + (i % 2) * 0.015)).await()
        }
        // Con requisitos y acuerdo de daños distintos, para mostrar cada caso en una presentación.
        listOf(
            Bike(name = "Yamaha MT-07", cc = 689, category = "Deportiva", pricePerDay = 280000, city = "Cali", specs = listOf("73 hp", "ABS"), rating = 4.9, trips = 31, instant = true,
                minYears = 3, deposit = 800000, damageCap = 3000000, reviews = 31),
            Bike(name = "Honda XRE 300", cc = 300, category = "Aventura", pricePerDay = 160000, city = "Cali", specs = listOf("Doble propósito"), rating = 4.8, trips = 54, delivery = true,
                minYears = 1, deposit = 400000, damageCap = 1500000, reviews = 54),
            Bike(name = "Suzuki V-Strom 650", cc = 645, category = "Aventura", pricePerDay = 320000, city = "Medellín", specs = listOf("Maletas incluidas"), rating = 4.9, trips = 22,
                minYears = 2, deposit = 1000000, damageCap = 0, reviews = 22),
            Bike(name = "Royal Enfield Himalayan", cc = 411, category = "Aventura", pricePerDay = 200000, city = "Bogotá", specs = listOf("Ideal para trocha"), rating = 4.7, trips = 40, instant = true,
                reviews = 40),
        ).forEachIndexed { i, b -> col("bikes").document("demo_b${i + 1}").set(b.copy(soatUntil = addDays(today(), 300), rtmUntil = addDays(today(), 300))).await() }
        listOf(
            // Públicas y sin aprobación para que cualquiera pueda unirse en una presentación. No tienen creador: no se pueden iniciar.
            Rodada(name = "Rodada Lago Calima", whenLabel = "Sábado 7:00", captain = "@camilo", meet = "Peaje Cencar, Cali", style = "Turismo", seats = 10,
                visibility = "public", approval = false, routeName = "Cali → Lago Calima",
                stops = listOf(RodadaStop("Peaje Cencar", 3.4954, -76.5251), RodadaStop("Lago Calima", C.corridor("calima").lat, C.corridor("calima").lng))),
            Rodada(name = "Madrugón La Línea", whenLabel = "Domingo 5:30", captain = "@laura", meet = "Estación Terpel Armenia", style = "Aventura", seats = 8,
                visibility = "public", approval = false),
        ).forEachIndexed { i, r -> col("rodadas").document("demo_r${i + 1}").set(r).await() }
        // Reseñas de ejemplo (kind "demo") en los primeros comercios, para presentar la función.
        val demoReviews = listOf(
            Triple("Andrés P.", 5, "Parqueadero vigilado y la picada para dos rinde. Volvemos en la próxima rodada."),
            Triple("Carolina M.", 4, "Buena vista al lago. A mediodía se llena: pide con hora desde la ruta."),
            Triple("Julián R.", 5, "Atienden rápido a los grupos de motos."),
        )
        (1..3).forEach { v ->
            demoReviews.forEachIndexed { k, (who, stars, text) ->
                col("reviews").document("demo_dv${v}_$k").set(Review(kind = "demo", serviceId = "dv${v}_$k", targetType = "venue", targetId = "demo_v$v",
                    authorId = "", authorName = who, stars = stars, comment = text, createdAt = now() - (k + 1) * 86_400_000L)).await()
            }
        }
        // Y en las dos primeras motos.
        val bikeReviews = listOf(
            Triple("Mateo G.", 5, "La moto impecable. En la entrega revisamos juntos el acta y la licencia; fueron cinco minutos."),
            Triple("Daniela S.", 5, "La devolví con el tanque lleno y me reintegraron el depósito ahí mismo."),
        )
        (1..2).forEach { v ->
            bikeReviews.forEachIndexed { k, (who, stars, text) ->
                col("reviews").document("demo_db${v}_$k").set(Review(kind = "demo", serviceId = "db${v}_$k", targetType = "bike", targetId = "demo_b$v",
                    authorId = "", authorName = who, stars = stars, comment = text, createdAt = now() - (k + 2) * 86_400_000L)).await()
            }
        }
        marker.set(mapOf("at" to now())).await()
    }

    private companion object {
        /** Rol "Guía de rutas", retirado. Solo puede aparecer en cuentas creadas antes del 2026-11-15. */
        const val LEGACY_GUIDE = "d"
        /** @usuario: 3 a 20 caracteres en minúscula. Las reglas de Firestore exigen lo mismo. */
        val USERNAME = Regex("^[a-z0-9_.]{3,20}$")
    }
}
