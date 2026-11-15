@file:OptIn(ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import co.apexmoto.app.core.C
import co.apexmoto.app.core.haversine
import co.apexmoto.app.data.*
import co.apexmoto.app.maps.*
import co.apexmoto.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.util.Date

/** "hace 12 s", "hace 3 min"… a partir de la hora del servidor. */
fun agoText(d: Date?): String {
    val s = ((System.currentTimeMillis() - (d?.time ?: return "sin datos")) / 1000).coerceAtLeast(0)
    return when { s < 60 -> "hace $s s"; s < 3600 -> "hace ${s / 60} min"; else -> "hace ${s / 3600} h" }
}

private fun km(m: Double) = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000).replace('.', ',')

/** Pestaña Rodadas de Comunidad: las públicas, las de amigos a las que me invitaron y las mías. */
@Composable
fun ColumnScope.RodadasPane(vm: AppViewModel, nav: NavController, u: AppUser) {
    val rodadas by vm.rodadas.collectAsState()
    var create by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        H("Rodadas", Icons.Filled.Groups)
        Sub("Salidas en grupo, cada quien en su moto. Quien la crea elige al líder, que va adelante.")
    }
    val visible = rodadas.filter { it.status != "done" || u.id in it.joined }
        .sortedWith(compareBy<Rodada>({ it.status != "active" }, { it.status == "done" }, { u.id !in it.joined && u.id !in it.invited }, { it.name.lowercase() }))
    if (visible.isEmpty()) Empty("Aún no hay rodadas. Crea la primera y reúne a tu grupo.", Icons.Filled.Groups)
    visible.forEachIndexed { i, r -> RodadaCard(vm, nav, u, r, i) }
    Btn("Crear rodada", Modifier.fillMaxWidth(), Icons.Filled.GroupAdd) { create = true }
    if (create) CreateRodadaSheet(vm, nav, u) { create = false }
}

@Composable
private fun RodadaPills(r: Rodada, u: AppUser) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (r.visibility == "friends") Pill("Solo amigos", CyanSoft, CyanDeep, Icons.Filled.Lock) else Pill("Pública", Surf3, Muted, Icons.Filled.Public)
        if (r.approval) Pill("Con aprobación", Surf3, Muted, Icons.Filled.HowToReg)
        if (u.id in r.invited && u.id !in r.joined) Pill("Te invitaron", OrangeSoft, OrangeDark, Icons.Filled.Mail)
        if (u.id in r.pending) Pill("Solicitud enviada", AmberSoft, Amber)
    }
}

@Composable
private fun StatusBadge(r: Rodada) {
    when (r.status) {
        "active" -> Row(verticalAlignment = Alignment.CenterVertically) { Ping(); Spacer(Modifier.width(6.dp)); Pill("En curso", GreenSoft, Green) }
        "done" -> Pill("Terminada")
        else -> Pill(r.whenLabel, Orange, Color.White)
    }
}

/** Botón para entrar a una rodada según el caso: invitación, aprobación o entrada directa. */
@Composable
private fun JoinButton(vm: AppViewModel, u: AppUser, r: Rodada, modifier: Modifier = Modifier) {
    val free = r.seats - r.joined.size > 0
    when {
        u.id in r.joined || r.status == "done" -> {}
        u.id in r.invited -> Btn("Unirme", modifier, Icons.Filled.Check, small = true, enabled = free) { vm.act("¡Estás dentro!") { vm.repo.joinRodada(r, u.id) } }
        u.id in r.pending -> Btn("Retirar solicitud", modifier, small = true, kind = BK.Ghost) { vm.act("Solicitud retirada") { vm.repo.cancelJoinRequest(r, u.id) } }
        r.approval -> Btn("Pedir unirme", modifier, Icons.Filled.HowToReg, small = true, enabled = free) { vm.act("Le avisamos al creador") { vm.repo.requestJoin(r, u.id) } }
        else -> Btn("Unirme", modifier, Icons.Filled.Check, small = true, enabled = free) { vm.act("¡Estás dentro!") { vm.repo.joinRodada(r, u.id) } }
    }
}

@Composable
private fun RodadaCard(vm: AppViewModel, nav: NavController, u: AppUser, r: Rodada, i: Int) {
    val joined = u.id in r.joined
    val fill by animateFloatAsState(if (r.seats > 0) r.joined.size / r.seats.toFloat() else 0f, tween(600, easing = EaseOutExpo), label = "motos")
    Card2(index = i, onClick = { nav.navigate("rodada/${r.id}") }) {
        Row(verticalAlignment = Alignment.Top) {
            Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
            Spacer(Modifier.width(8.dp)); StatusBadge(r)
        }
        Lbl("${r.captain} · ${r.style}${if (r.routeName.isNotBlank()) " · ${r.routeName}" else ""}")
        IconLine(Icons.Filled.Place, r.meet, Muted, bold = false)
        RodadaPills(r, u)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Surf3)) {
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(if (r.joined.size >= r.seats) Red else Green))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) { Num("${r.joined.size}/${r.seats}", OrangeDark, 18); Spacer(Modifier.width(6.dp)); Lbl("motos") }
            if (joined) Btn(if (r.status == "active") "Ver el mapa" else "Ver", small = true, icon = Icons.Filled.Map, kind = BK.Ghost) { nav.navigate("rodada/${r.id}") }
            else JoinButton(vm, u, r)
        }
    }
}

@Composable
private fun CreateRodadaSheet(vm: AppViewModel, nav: NavController, u: AppUser, onDismiss: () -> Unit) {
    val routes by vm.savedRoutes.collectAsState()
    val profile by vm.myProfile.collectAsState()
    var name by remember { mutableStateOf("") }; var whenL by remember { mutableStateOf("Sábado 7:00") }
    var meet by remember { mutableStateOf("") }; var style by remember { mutableStateOf("Turismo") }; var seats by remember { mutableStateOf("8") }
    var visibility by remember { mutableStateOf("public") }; var approval by remember { mutableStateOf(true) }
    var routeId by remember { mutableStateOf("") }
    Sheet(onDismiss) {
        H("Nueva rodada", Icons.Filled.GroupAdd)
        Field("Nombre", name, { name = it.take(80) }, hint = "Ej: Rumbo al Calima")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Cuándo", whenL, { whenL = it.take(40) }, Modifier.weight(1f))
            Field("Motos", seats, { seats = it.filter(Char::isDigit).take(2) }, Modifier.weight(0.6f), kb = KeyboardType.Number)
        }
        Field("Punto de encuentro", meet, { meet = it.take(160) }, hint = "Ej: Peaje Cencar, Cali")
        Lbl("Estilo")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C.RIDE_STYLES.forEach { s -> Chip(s, style == s, accent = Cyan) { style = s } }
        }
        Lbl("¿Quién la ve?")
        Segmented(listOf(Triple("public", "Pública", Icons.Filled.Public), Triple("friends", "Solo amigos", Icons.Filled.Lock)), visibility) {
            visibility = it; approval = it == "public"
        }
        SwitchRow("Aprobar a quien pida unirse",
            if (visibility == "public") "Recomendado: en el mapa del grupo los miembros se ven entre sí." else "Los amigos que invites entran directo.", approval) { approval = it }
        if (routes.isNotEmpty()) Drop("Ruta (opcional)", listOf("" to "Sin ruta") + routes.map { it.id to it.name }, routeId, { routeId = it })
        else Sub("Para llevar una ruta con paradas, guárdala antes en la pestaña Rutas.")
        Btn("Publicar rodada", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = name.isNotBlank() && meet.isNotBlank()) {
            val route = routes.firstOrNull { it.id == routeId }
            val captain = profile?.let { "@${it.username}" } ?: ("@" + u.name.split(" ").first().lowercase())
            onDismiss()
            vm.act("Rodada publicada") {
                val id = vm.repo.createRodada(Rodada(creatorId = u.id, name = name.trim(), whenLabel = whenL.trim(), captain = captain.take(40), meet = meet.trim(),
                    style = style, seats = seats.toIntOrNull()?.coerceIn(2, 40) ?: 8, visibility = visibility, approval = approval,
                    routeName = route?.name?.take(160).orEmpty(),
                    stops = route?.stops.orEmpty().filter { it.lat != 0.0 }.take(30).map { RodadaStop(it.name, it.lat, it.lng) }))
                nav.navigate("rodada/$id")
            }
        }
    }
}

/** Detalle de una rodada: mapa del grupo en vivo, miembros con líder y cierre, solicitudes e invitaciones. */
@Composable
fun RodadaScreen(vm: AppViewModel, nav: NavController, u: AppUser, id: String) {
    val rState by remember(id) { vm.repo.rodada(id) }.collectAsState(initial = null)
    val ro = rState
    if (ro == null) { Screen { Empty("Cargando la rodada… Si no aparece, puede que ya no la puedas ver.", Icons.Filled.Groups) }; return }
    val isCreator = ro.creatorId == u.id
    val member = u.id in ro.joined
    val active = ro.status == "active"
    val people by remember(ro.joined, ro.pending) { vm.repo.profiles(ro.joined + ro.pending) }.collectAsState(initial = emptyList())
    val livePs by remember(id, active && member) { if (active && member) vm.repo.livePositions(id) else flowOf(emptyList<LivePos>()) }.collectAsState(initial = emptyList())
    val friends by vm.friendProfiles.collectAsState()
    val sharing = vm.sharingRodada == id

    // Compartir la ubicación exige el aviso aceptado y el permiso de Android, como el guiado.
    var hasPerm by remember { mutableStateOf(vm.container.location.hasPermission()) }
    val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = vm.container.location.hasPermission()
        if (hasPerm && vm.locationConsented) vm.startSharing(id)
    }
    var disclose by remember { mutableStateOf(false) }
    if (disclose) LocationDisclosure(onAccept = { disclose = false; vm.acceptLocation(); launcher.launch(perms) }, onDismiss = { disclose = false })
    fun share(on: Boolean) {
        if (!on) { vm.stopSharing(u.id); return }
        when { !vm.locationConsented -> disclose = true; !hasPerm -> launcher.launch(perms); else -> vm.startSharing(id) }
    }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val me = livePs.firstOrNull { it.id == u.id }
    fun nameOf(uid: String) = people.firstOrNull { it.id == uid }?.name ?: livePs.firstOrNull { it.id == uid }?.name ?: if (uid == u.id) u.name else "Rider"

    Screen {
        val markers = buildList {
            ro.stops.forEachIndexed { i, s -> add(MapMarker("stop$i", LatLng(s.lat, s.lng), s.name, "Parada ${i + 1}")) }
            livePs.forEach { p ->
                val ring = when { p.sos -> "#FFFFFF"; p.id == ro.leaderId -> "#D94600"; p.id == ro.sweeperId -> "#0E7490"; else -> null }
                val role = when (p.id) { ro.leaderId -> " · líder"; ro.sweeperId -> " · cierra"; else -> "" }
                add(MapMarker("live_${p.id}", LatLng(p.lat, p.lng), p.name + role + if (p.sos) " · S.O.S." else "", agoText(p.updatedAt),
                    color = if (p.sos) "#D13B3B" else Avatars.hex(p.color), ring = ring, sizePx = if (p.sos) 72 else 54))
            }
        }
        if (markers.isNotEmpty()) {
            val mapState = remember { OsmMapState() }
            val center = livePs.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: ro.stops.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: LatLng(C.CALI_LAT, C.CALI_LNG)
            Card2(padding = 0.dp) {
                OsmMap(Modifier.fillMaxWidth().height(280.dp), mapState, center = center, mapType = vm.mapType, markers = markers,
                    route = ro.stops.map { LatLng(it.lat, it.lng) })
            }
            LaunchedEffect(livePs.size, ro.stops.size) {
                delay(300)
                mapState.fit(livePs.map { LatLng(it.lat, it.lng) } + ro.stops.map { LatLng(it.lat, it.lng) })
            }
            if (livePs.any { it.sos }) Card2(tone = RedSoft) {
                IconLine(Icons.Filled.Sos, "${livePs.filter { it.sos }.joinToString { it.name }} pidió auxilio. Revisa el mapa.", Red)
            }
        }

        Card2 {
            Row(verticalAlignment = Alignment.Top) {
                Text(ro.name, Modifier.weight(1f), fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp)); StatusBadge(ro)
            }
            Lbl("${ro.whenLabel} · ${ro.style} · ${ro.captain}")
            IconLine(Icons.Filled.Place, ro.meet, Muted, bold = false)
            if (ro.routeName.isNotBlank()) IconLine(Icons.Filled.Route, ro.routeName, CyanDeep, bold = false)
            RodadaPills(ro, u)
            Lbl("${ro.joined.size} de ${ro.seats} motos")
            if (isCreator && ro.status == "planned") Btn("Iniciar rodada", Modifier.fillMaxWidth(), Icons.Filled.PlayArrow) {
                vm.act("¡Rodada en curso! Ya pueden compartir su ubicación.") { vm.repo.startRodada(ro) }
            }
            if (active && member) SwitchRow("Compartir mi ubicación con el grupo",
                "Solo la ven los miembros, y solo mientras ApexMoto esté abierta. Se borra al terminar.", sharing) { share(it) }
            if (isCreator && active) Btn("Terminar rodada", Modifier.fillMaxWidth(), Icons.Filled.Flag, kind = BK.Danger) { confirmEnd = true }
            if (ro.stops.isNotEmpty() && member && ro.status != "done") Btn("Guiar la ruta", Modifier.fillMaxWidth(), Icons.Filled.Navigation, kind = BK.Ghost) {
                vm.navSession = NavSession(ro.routeName.ifBlank { ro.name }, ro.stops.map { NavStop(it.name, it.lat, it.lng) }); nav.navigate("navigate")
            }
            if (!member) JoinButton(vm, u, ro, Modifier.fillMaxWidth())
            if (u.id in ro.invited && !member && ro.status != "done") TextButton({ vm.act("Invitación rechazada") { vm.repo.declineInvite(ro, u.id); nav.popBackStack() } }) {
                Text("No voy, gracias", color = Muted)
            }
        }

        H("Miembros (${ro.joined.size})", Icons.Filled.Groups)
        ro.joined.sortedWith(compareBy<String>({ it != ro.leaderId }, { it != ro.sweeperId }, { nameOf(it).lowercase() })).forEach { mid ->
            MemberRow(vm, ro, u, mid, people.firstOrNull { it.id == mid }, livePs.firstOrNull { it.id == mid }, me, active, isCreator, nameOf(mid))
        }

        if (isCreator && ro.pending.isNotEmpty() && ro.status != "done") {
            H("Quieren unirse", Icons.Filled.HowToReg)
            ro.pending.forEach { pid ->
                val p = people.firstOrNull { it.id == pid }
                Card2(tone = Surf1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p != null) MotoAvatar(p.moto, p.color, 40.dp) else Avatar(nameOf(pid), 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(nameOf(pid), fontWeight = FontWeight.Bold, maxLines = 1); p?.let { Lbl("@${it.username}") } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Aprobar", Modifier.weight(1f), Icons.Filled.Check, small = true) { vm.act("Aprobado") { vm.repo.approveJoin(ro, pid) } }
                        Btn("Rechazar", Modifier.weight(1f), kind = BK.Ghost, small = true) { vm.act { vm.repo.rejectJoin(ro, pid) } }
                    }
                }
            }
        }

        if (isCreator && ro.status != "done") {
            val candidates = friends.filter { it.id !in ro.joined && it.id !in ro.invited && it.id !in ro.pending }
            H("Invitar amigos", Icons.Filled.PersonAdd)
            when {
                friends.isEmpty() -> Sub("Agrega amigos en Comunidad → Amigos para invitarlos con un toque.")
                candidates.isEmpty() -> Sub("Ya invitaste a todos tus amigos.")
            }
            candidates.sortedBy { it.name.lowercase() }.forEach { f ->
                Card2(tone = Surf1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MotoAvatar(f.moto, f.color, 40.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(f.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Lbl("@${f.username}") }
                        Btn("Invitar", small = true, icon = Icons.Filled.Send) { vm.act("Invitación enviada a ${f.name}") { vm.repo.invite(ro, f.id) } }
                    }
                }
            }
            val waiting = ro.invited.count { it !in ro.joined }
            if (waiting > 0) Lbl("Invitaciones sin responder: $waiting")
        }

        if (member && !isCreator && ro.status != "done") Btn("Salir de la rodada", Modifier.fillMaxWidth(), Icons.Filled.Close, kind = BK.Danger) { confirmLeave = true }
        if (isCreator && !active) Btn("Eliminar rodada", Modifier.fillMaxWidth(), Icons.Filled.Delete, kind = BK.Danger) {
            vm.act("Rodada eliminada") { vm.repo.deleteRodada(ro); nav.popBackStack() }
        }
    }

    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false }, containerColor = Color.White,
        title = { Text("¿Terminar la rodada?", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Se cierra el mapa del grupo y se borran las ubicaciones de todos.", color = Muted) },
        confirmButton = { Btn("Terminar", kind = BK.Danger) { confirmEnd = false; vm.stopSharing(u.id); vm.act("Rodada terminada") { vm.repo.endRodada(ro) } } },
        dismissButton = { TextButton({ confirmEnd = false }) { Text("Cancelar", color = Muted) } },
    )
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false }, containerColor = Color.White,
        title = { Text("¿Salir de la rodada?", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Dejarás de ver el mapa del grupo y se borra tu ubicación compartida.", color = Muted) },
        confirmButton = { Btn("Salir", kind = BK.Danger) { confirmLeave = false; vm.stopSharing(u.id); vm.act("Saliste de la rodada") { vm.repo.leaveRodada(ro, u.id); nav.popBackStack() } } },
        dismissButton = { TextButton({ confirmLeave = false }) { Text("Cancelar", color = Muted) } },
    )
}

@Composable
private fun MemberRow(vm: AppViewModel, ro: Rodada, u: AppUser, uid: String, p: Profile?, live: LivePos?, me: LivePos?, active: Boolean, isCreator: Boolean, name: String) {
    var menu by remember { mutableStateOf(false) }
    val leader = uid == ro.leaderId
    val sweeper = uid == ro.sweeperId
    Card2(tone = if (live?.sos == true) RedSoft else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ring = when { live?.sos == true -> Red; leader -> Orange; sweeper -> Cyan; else -> null }
            if (p != null) MotoAvatar(p.moto, p.color, 46.dp, ring) else Avatar(name, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name + if (uid == u.id) " (tú)" else "", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (leader) Pill("Líder", OrangeSoft, OrangeDark, Icons.Filled.Flag)
                    if (sweeper) Pill("Cierra el grupo", CyanSoft, CyanDeep, Icons.Filled.OutlinedFlag)
                    if (uid == ro.creatorId) Pill("Creó la rodada")
                    if (live?.sos == true) Pill("S.O.S.", Red, Color.White, Icons.Filled.Sos)
                }
                if (live != null) {
                    val dist = if (me != null && uid != u.id) " · a ${km(haversine(me.lat, me.lng, live.lat, live.lng))}" else ""
                    Lbl("En el mapa · ${agoText(live.updatedAt)}$dist", Green)
                } else if (active) Lbl("No está compartiendo su ubicación")
            }
            if (isCreator && ro.status != "done") Box {
                IconBtn(Icons.Filled.MoreVert, "Opciones de $name") { menu = true }
                DropdownMenu(menu, { menu = false }, containerColor = Color.White) {
                    if (!leader) DropdownMenuItem({ Text("Hacer líder") }, { menu = false
                        vm.act("$name es el líder") { vm.repo.setRoles(ro, uid, if (sweeper) "" else ro.sweeperId) } }, leadingIcon = { Icon(Icons.Filled.Flag, null) })
                    if (!sweeper && !leader) DropdownMenuItem({ Text("Que cierre el grupo") }, { menu = false
                        vm.act("$name cierra el grupo") { vm.repo.setRoles(ro, ro.leaderId, uid) } }, leadingIcon = { Icon(Icons.Filled.OutlinedFlag, null) })
                    if (sweeper) DropdownMenuItem({ Text("Ya no cierra el grupo") }, { menu = false
                        vm.act { vm.repo.setRoles(ro, ro.leaderId, "") } }, leadingIcon = { Icon(Icons.Filled.Close, null) })
                    if (uid != ro.creatorId) DropdownMenuItem({ Text("Quitar de la rodada", color = Red) }, { menu = false
                        vm.act("$name salió de la rodada") { vm.repo.kick(ro, uid) } }, leadingIcon = { Icon(Icons.Filled.PersonRemove, null, tint = Red) })
                }
            }
        }
    }
}
