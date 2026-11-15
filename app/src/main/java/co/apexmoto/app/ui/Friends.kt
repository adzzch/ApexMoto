@file:OptIn(ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*
import java.text.Normalizer

/** Avatares sin fotos: silueta de moto y color de la paleta. Las claves son las mismas que validan las reglas de Firestore. */
object Avatars {
    val MOTOS: List<Triple<String, String, ImageVector>> = listOf(
        Triple("deportiva", "Deportiva", Icons.Filled.Motorcycle),
        Triple("naked", "Naked", Icons.Filled.TwoWheeler),
        Triple("scooter", "Scooter", Icons.Filled.Moped),
        Triple("electrica", "Eléctrica", Icons.Filled.ElectricMoped),
        Triple("casco", "Casco", Icons.Filled.SportsMotorsports),
    )
    val COLORS: List<Triple<String, String, Color>> = listOf(
        Triple("orange", "Naranja", Orange), Triple("cyan", "Cian", Cyan), Triple("green", "Verde", Green),
        Triple("red", "Rojo", Red), Triple("ink", "Tinta", Ink), Triple("amber", "Ámbar", Amber),
    )
    fun icon(moto: String) = MOTOS.firstOrNull { it.first == moto }?.third ?: Icons.Filled.TwoWheeler
    fun color(c: String) = COLORS.firstOrNull { it.first == c }?.third ?: Orange
    /** El color en #RRGGBB, para los marcadores del mapa (osmdroid no usa colores de Compose). */
    fun hex(c: String) = String.format("#%06X", 0xFFFFFF and color(c).toArgb())
}

/** Avatar de un rider: círculo de su color con la silueta de su moto. ring = anillo de un color (líder, S.O.S.). */
@Composable
fun MotoAvatar(moto: String, color: String, size: Dp = 44.dp, ring: Color? = null) {
    val outer = if (ring != null) Modifier.border(2.5.dp, ring, CircleShape).padding(3.dp) else Modifier
    Box(Modifier.size(size).then(outer).clip(CircleShape).background(Avatars.color(color)), contentAlignment = Alignment.Center) {
        Icon(Avatars.icon(moto), null, Modifier.size(size * 0.54f), tint = Color.White)
    }
}

/** Texto del estado de un amigo y si está activo ahora (latido de los últimos 5 minutos). null si no hay estado. */
fun presenceText(p: Presence?): Pair<String, Boolean>? {
    val t = p?.updatedAt?.time ?: return null
    val mins = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
    return when {
        mins <= 5 && p.state == "riding" && p.rodada.isNotBlank() -> "En rodada: ${p.rodada}" to true
        mins <= 5 -> "En línea" to true
        mins < 60 -> "Visto hace $mins min" to false
        mins < 60 * 24 -> "Visto hace ${mins / 60} h" to false
        else -> "Visto hace ${mins / (60 * 24)} d" to false
    }
}

private val UsernameRe = Regex("^[a-z0-9_.]{3,20}$")

/** Sugiere un @usuario a partir del nombre: minúsculas, sin tildes, con guion bajo. */
fun suggestUsername(name: String): String {
    val plain = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    val base = plain.trim().replace(Regex("\\s+"), "_").filter { (it in 'a'..'z') || (it in '0'..'9') || it == '_' || it == '.' }.take(20)
    return if (base.length >= 3) base else "rider_${(100..999).random()}"
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir"))
}

/** Crear o editar el perfil público: @usuario, nombre y avatar. */
@Composable
fun ProfileEditor(vm: AppViewModel, u: AppUser, current: Profile?, onDone: () -> Unit = {}) {
    var username by remember(current?.username) { mutableStateOf(current?.username ?: suggestUsername(u.name)) }
    var name by remember(current?.name) { mutableStateOf(current?.name ?: u.name) }
    var moto by remember(current?.moto) { mutableStateOf(current?.moto ?: "naked") }
    var color by remember(current?.color) { mutableStateOf(current?.color ?: "orange") }
    var busy by remember { mutableStateOf(false) }
    val valid = UsernameRe.matches(username)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MotoAvatar(moto, color, 64.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Tu nombre" }, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Lbl("@" + username.ifBlank { "usuario" }, CyanDeep)
            }
        }
        Field("Tu @usuario", username, { v -> username = v.lowercase().filter { (it in 'a'..'z') || (it in '0'..'9') || it == '_' || it == '.' }.take(20) },
            prefix = "@", hint = "tu_usuario")
        if (username.isNotEmpty() && !valid) Lbl("De 3 a 20 caracteres: letras sin tilde, números, punto o guion bajo.", Red)
        Field("Nombre que ven los demás", name, { name = it.take(60) })
        Lbl("Tu moto")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatars.MOTOS.forEach { (k, label, ic) -> Chip(label, moto == k, ic, accent = Cyan) { moto = k } }
        }
        Lbl("Color")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatars.COLORS.forEach { (k, label, c) ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(c).border(3.dp, if (color == k) Ink else Color.Transparent, CircleShape)
                    .clickable(onClickLabel = label) { color = k }, contentAlignment = Alignment.Center) {
                    if (color == k) Icon(Icons.Filled.Check, label, Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
        Btn(if (current == null) "Crear mi perfil" else "Guardar perfil", Modifier.fillMaxWidth(), Icons.Filled.Check,
            enabled = valid && name.isNotBlank() && !busy, loading = busy) {
            busy = true
            vm.act(if (current == null) "¡Perfil creado!" else "Perfil guardado") {
                try {
                    vm.repo.saveProfile(u.id, current, Profile(username = username, name = name, moto = moto, color = color, showStatus = current?.showStatus ?: true))
                    onDone()
                } finally { busy = false }
            }
        }
    }
}

/** Pestaña Amigos de Comunidad: mi perfil, buscar por @usuario, solicitudes, amigos con su estado y bloqueados. */
@Composable
fun ColumnScope.FriendsPane(vm: AppViewModel, u: AppUser) {
    val me by vm.myProfile.collectAsState()
    val p = me
    if (p == null) {
        Card2 {
            H("Crea tu perfil de rider", Icons.Filled.Badge)
            Sub("Elige tu @usuario y tu moto. Es lo único que ven los demás: nunca tu correo ni tu celular.")
            ProfileEditor(vm, u, null)
        }
        return
    }
    val friendships by vm.friendships.collectAsState()
    val friends by vm.friendProfiles.collectAsState()
    val reqIn by vm.friendReqIn.collectAsState()
    val reqOut by vm.friendReqOut.collectAsState()
    val blocks by vm.blocks.collectAsState()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Profile?>(null) }
    var searched by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, Profile>?>(null) }   // ("quitar" | "bloquear", perfil)
    val friendIds = friendships.mapNotNull { f -> f.members.firstOrNull { it != u.id } }.toSet()

    Card2 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MotoAvatar(p.moto, p.color, 56.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Lbl("@${p.username}", CyanDeep)
            }
            IconBtn(Icons.Filled.Share, "Compartir mi @usuario") { shareText(ctx, "¡Agrégame en ApexMoto! Búscame como @${p.username} en Comunidad → Amigos.") }
            Spacer(Modifier.width(8.dp))
            IconBtn(if (editing) Icons.Filled.Close else Icons.Filled.Edit, if (editing) "Cerrar edición" else "Editar perfil") { editing = !editing }
        }
        if (editing) ProfileEditor(vm, u, p) { editing = false }
    }

    Card2 {
        H("Agregar amigo", Icons.Filled.PersonAdd)
        Sub("Escribe el @usuario exacto. No hay listados de personas: solo encuentras a quien ya conoces.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("", query, { v -> query = v.trim().removePrefix("@").lowercase().take(20); searched = false; found = null }, Modifier.weight(1f), prefix = "@", hint = "usuario")
            IconBtn(Icons.Filled.Search, "Buscar", enabled = query.length >= 3) { vm.act { found = vm.repo.findProfile(query); searched = true } }
        }
        if (searched && found == null) Sub("No hay ningún rider con ese @usuario.")
        found?.let { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MotoAvatar(f.moto, f.color, 44.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(f.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Lbl("@${f.username}") }
                val out = reqOut.firstOrNull { it.to == f.id }
                val inc = reqIn.firstOrNull { it.from == f.id }
                val blk = blocks.firstOrNull { it.blocked == f.id }
                when {
                    f.id == u.id -> Pill("Eres tú")
                    f.id in friendIds -> Pill("Ya son amigos", GreenSoft, Green)
                    blk != null -> Btn("Desbloquear", small = true, kind = BK.Ghost) { vm.act("Desbloqueado") { vm.repo.unblock(blk) } }
                    inc != null -> Btn("Aceptar", small = true, icon = Icons.Filled.Check) { vm.act("¡Ahora son amigos!") { vm.repo.acceptFriend(inc) } }
                    out != null -> Pill("Solicitud enviada", AmberSoft, Amber)
                    else -> Btn("Agregar", small = true, icon = Icons.Filled.PersonAdd) { vm.act("Solicitud enviada") { vm.repo.sendFriendRequest(p, f) } }
                }
            }
        }
    }

    if (reqIn.isNotEmpty()) {
        H("Solicitudes recibidas", Icons.Filled.MarkEmailUnread)
        reqIn.sortedByDescending { it.createdAt }.forEachIndexed { i, r ->
            Card2(index = i) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MotoAvatar(r.fromMoto, r.fromColor, 44.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(r.fromName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Lbl("@${r.fromUsername}") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Aceptar", Modifier.weight(1f), Icons.Filled.Check, small = true) { vm.act("¡Ahora son amigos!") { vm.repo.acceptFriend(r) } }
                    Btn("Rechazar", Modifier.weight(1f), kind = BK.Ghost, small = true) { vm.act { vm.repo.deleteFriendRequest(r) } }
                }
            }
        }
    }
    if (reqOut.isNotEmpty()) {
        H("Solicitudes enviadas", Icons.Filled.Outbox)
        reqOut.sortedByDescending { it.createdAt }.forEach { r ->
            Card2(tone = Surf1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(r.toName.ifBlank { "@${r.toUsername}" }, fontWeight = FontWeight.Bold, maxLines = 1); Lbl("@${r.toUsername} · esperando respuesta") }
                    Btn("Cancelar", small = true, kind = BK.Ghost) { vm.act("Solicitud cancelada") { vm.repo.deleteFriendRequest(r) } }
                }
            }
        }
    }

    H(if (friends.isEmpty()) "Amigos" else "Amigos (${friends.size})", Icons.Filled.People)
    if (friends.isEmpty()) Empty("Todavía no tienes amigos en ApexMoto. Comparte tu @usuario o busca el de alguien que conozcas.", Icons.Filled.People)
    friends.sortedBy { it.name.lowercase() }.forEachIndexed { i, f ->
        val pres by remember(f.id) { vm.repo.presence(f.id) }.collectAsState(initial = null)
        val st = presenceText(pres)
        var menu by remember { mutableStateOf(false) }
        Card2(index = i) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box { MotoAvatar(f.moto, f.color, 46.dp); if (st?.second == true) Box(Modifier.align(Alignment.BottomEnd).clip(CircleShape).background(Color.White).padding(2.dp)) { Ping() } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Lbl("@${f.username}")
                    if (st != null) Lbl(st.first, if (st.second) Green else Muted)
                }
                Box {
                    IconBtn(Icons.Filled.MoreVert, "Opciones de ${f.name}") { menu = true }
                    DropdownMenu(menu, { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem({ Text("Eliminar de mis amigos") }, { menu = false; confirm = "quitar" to f }, leadingIcon = { Icon(Icons.Filled.PersonRemove, null) })
                        DropdownMenuItem({ Text("Bloquear", color = Red) }, { menu = false; confirm = "bloquear" to f }, leadingIcon = { Icon(Icons.Filled.Block, null, tint = Red) })
                    }
                }
            }
        }
    }

    if (blocks.isNotEmpty()) {
        H("Bloqueados", Icons.Filled.Block)
        blocks.forEach { b ->
            Card2(tone = Surf1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(b.blockedName.ifBlank { "@${b.blockedUsername}" }, fontWeight = FontWeight.Bold, maxLines = 1); Lbl("@${b.blockedUsername}") }
                    Btn("Desbloquear", small = true, kind = BK.Ghost) { vm.act("Desbloqueado") { vm.repo.unblock(b) } }
                }
            }
        }
    }

    confirm?.let { (what, f) ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = Color.White,
            title = { Text(if (what == "quitar") "¿Eliminar a ${f.name}?" else "¿Bloquear a ${f.name}?", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold) },
            text = { Text(if (what == "quitar") "Dejarán de ver su estado. Pueden volver a ser amigos con una nueva solicitud."
                else "Dejarán de ser amigos y no podrá enviarte solicitudes ni ver tu estado. Puedes desbloquearlo después.", color = Muted) },
            confirmButton = {
                Btn(if (what == "quitar") "Eliminar" else "Bloquear", kind = BK.Danger) {
                    confirm = null
                    if (what == "quitar") vm.act("Ya no son amigos") { vm.repo.unfriend(u.id, f.id) }
                    else vm.act("Bloqueado") { vm.repo.block(u.id, f, f.id in friendIds, reqIn + reqOut) }
                }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancelar", color = Muted) } },
        )
    }
}
