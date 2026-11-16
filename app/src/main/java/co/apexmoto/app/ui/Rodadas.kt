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
    var byCode by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        H("Rodadas", Icons.Filled.Groups)
        Sub("Salidas en grupo, cada quien en su moto. Quien la crea elige al líder, que va adelante.")
    }
    val visible = rodadas.filter { it.status != "done" || u.id in it.joined }
        .sortedWith(compareBy<Rodada>({ it.status != "active" }, { it.status == "done" }, { u.id !in it.joined && u.id !in it.invited },
            { if (it.startsAt > 0) it.startsAt else Long.MAX_VALUE }, { it.name.lowercase() }))
    if (visible.isEmpty()) Empty("Aún no hay rodadas. Crea la primera y reúne a tu grupo.", Icons.Filled.Groups)
    visible.forEachIndexed { i, r -> RodadaCard(vm, nav, u, r, i) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("Crear rodada", Modifier.weight(1f), Icons.Filled.GroupAdd) { create = true }
        Btn("Unirme con código", Modifier.weight(1f), Icons.Filled.Link, kind = BK.Ghost) { byCode = true }
    }
    if (create) CreateRodadaSheet(vm, nav, u, onDismiss = { create = false })
    if (byCode) JoinByCodeSheet(vm, nav, u) { byCode = false }
}

@Composable
private fun RodadaPills(r: Rodada, u: AppUser) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (r.visibility == "friends") Pill("Privada", CyanSoft, CyanDeep, Icons.Filled.Lock) else Pill("Pública", Surf3, Muted, Icons.Filled.Public)
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
    if (!free && u.id !in r.joined && r.status != "done") { Pill("Completa", RedSoft, Red); return }
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

/** Escribir el código que le compartieron a uno, ver la invitación y unirse (sirve también para rodadas privadas). */
@Composable
fun JoinByCodeSheet(vm: AppViewModel, nav: NavController, u: AppUser, initial: String = "", onDismiss: () -> Unit) {
    var code by remember { mutableStateOf(initial) }
    var found by remember { mutableStateOf<RodadaCode?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val clean = code.uppercase().filter { it.isLetterOrDigit() }.take(8)
    // Si llegó por enlace, busca la rodada de una vez.
    LaunchedEffect(initial) {
        if (initial.length == 8) { busy = true; runCatching { vm.repo.inviteByCode(initial) }.onSuccess { found = it; notFound = it == null }; busy = false }
    }
    Sheet(onDismiss) {
        H("Unirme con código", Icons.Filled.Link)
        Sub("Escribe el código de 8 letras y números que te compartieron.")
        Field("", clean, { code = it; found = null; notFound = false }, hint = "Ej: K7QM 2XPA", leading = Icons.Filled.Key,
            cap = androidx.compose.ui.text.input.KeyboardCapitalization.Characters)
        if (notFound) Text("No encontramos una rodada con ese código. Revísalo.", color = Red, fontSize = 12.sp)
        val f = found
        if (f == null) {
            Missing(if (clean.isNotEmpty() && clean.length < 8) listOf("${8 - clean.length} caracteres del código") else emptyList())
            Btn("Buscar rodada", Modifier.fillMaxWidth(), Icons.Filled.Search, enabled = clean.length == 8, loading = busy) {
                busy = true
                vm.act { val r = vm.repo.inviteByCode(clean); found = r; notFound = r == null }.invokeOnCompletion { busy = false }
            }
        } else {
            Card2(tone = Surf1) {
                Text(f.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                if (f.whenLabel.isNotBlank()) IconLine(Icons.Filled.Event, f.whenLabel, OrangeDark, bold = false)
                if (f.meet.isNotBlank()) IconLine(Icons.Filled.Place, f.meet, Muted, bold = false)
            }
            Btn("Unirme a la rodada", Modifier.fillMaxWidth(), Icons.Filled.Check, loading = busy) {
                busy = true
                vm.act("¡Estás dentro!") { vm.repo.joinByCode(f, u.id); onDismiss(); nav.navigate("rodada/${f.rodadaId}") }.invokeOnCompletion { busy = false }
            }
        }
    }
}

/** Ruta ya armada para una rodada nueva (por ejemplo, una rodada famosa de Explorar). */
data class RodadaPreset(val name: String, val meet: String, val routeName: String, val stops: List<RodadaStop>)

/** Próximo sábado a las 7:00 a. m.: la fecha que se propone al crear una rodada. */
private fun nextSaturday7(): Long {
    var d = java.time.LocalDate.now().plusDays(1)
    while (d.dayOfWeek != java.time.DayOfWeek.SATURDAY) d = d.plusDays(1)
    return d.atTime(7, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@Composable
fun CreateRodadaSheet(vm: AppViewModel, nav: NavController, u: AppUser, onDismiss: () -> Unit, preset: RodadaPreset? = null) {
    val profile by vm.myProfile.collectAsState()
    var name by remember { mutableStateOf(preset?.name?.take(80).orEmpty()) }
    var startsAt by remember { mutableLongStateOf(nextSaturday7()) }
    var meet by remember { mutableStateOf(preset?.stops?.firstOrNull()?.let { MeetChoice(it.name, it.lat, it.lng) }) }
    var style by remember { mutableStateOf("Turismo") }; var seats by remember { mutableStateOf("8") }
    var visibility by remember { mutableStateOf("public") }
    // Paradas: las de la rodada famosa (sin el punto de salida, que ya es el encuentro) o las que elija la persona.
    var stops by remember { mutableStateOf(preset?.stops.orEmpty().drop(1).mapIndexed { i, s -> PickedStop("p$i", s.name, s.lat, s.lng, false) }) }
    var picking by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        H("Nueva rodada", Icons.Filled.GroupAdd)
        Field("Nombre", name, { name = it.take(80) }, hint = "Ej: Rumbo al Calima", cap = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
            error = if (name.isBlank()) "Ponle un nombre" else null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateTimeField("Salida", startsAt, Modifier.weight(1f)) { startsAt = it }
            Field("Motos", seats, { seats = it.filter(Char::isDigit).take(2) }, Modifier.weight(0.45f), kb = KeyboardType.Number)
        }
        MeetField(vm, meet) { meet = it }
        // ¿A dónde van? Destino y paradas, que todos ven en el mapa de la rodada.
        Lbl("¿A dónde van?")
        if (stops.isEmpty()) Sub("Elige el destino y las paradas (miradores, pueblos, restaurantes aliados…). Todos las verán en el mapa.")
        stops.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (s.food) Orange else CyanDeep), contentAlignment = Alignment.Center) {
                    Text(if (s.food) "R" else "${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp)); Text(s.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Btn(if (stops.isEmpty()) "Elegir destino y paradas" else "Cambiar paradas", Modifier.fillMaxWidth(), Icons.Filled.AddLocationAlt, kind = BK.Ghost, small = true) { picking = true }
        Lbl("Estilo")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C.RIDE_STYLES.forEach { st -> Chip(st, style == st, accent = Cyan) { style = st } }
        }
        Lbl("¿Quién se puede unir?")
        Segmented(listOf(Triple("public", "Pública", Icons.Filled.Public), Triple("friends", "Privada", Icons.Filled.Lock)), visibility) { visibility = it }
        Sub(if (visibility == "public") "Aparece en Comunidad y cualquiera se une. También puedes invitar con el código o a tus amigos."
            else "No aparece en Comunidad: solo entra quien invites con el código o tus amigos.")
        val missing = buildList { if (name.isBlank()) add("el nombre"); if (meet == null) add("el punto de encuentro"); if (stops.isEmpty()) add("el destino") }
        Missing(missing)
        Btn("Publicar rodada", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = missing.isEmpty()) {
            val m = meet ?: return@Btn
            val captain = profile?.let { "@${it.username}" } ?: ("@" + u.name.split(" ").first().lowercase())
            val dest = stops.lastOrNull { !it.food } ?: stops.last()
            onDismiss()
            vm.act("Rodada publicada") {
                val id = vm.repo.createRodada(Rodada(creatorId = u.id, name = name.trim(), whenLabel = whenLabelOf(startsAt), startsAt = startsAt,
                    captain = captain.take(40), meet = m.name.take(160), meetLat = m.lat, meetLng = m.lng,
                    style = style, seats = seats.toIntOrNull()?.coerceIn(2, 40) ?: 8, visibility = visibility, approval = false,
                    routeName = (preset?.routeName ?: "Hacia ${dest.name}").take(160),
                    stops = stops.take(30).map { RodadaStop(it.name.take(120), it.lat, it.lng) }))
                nav.navigate("rodada/$id")
            }
        }
    }
    if (picking) StopPickerSheet(vm, stops) { stops = it; picking = false }
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
        val meetPt = if (ro.meetLat != 0.0) LatLng(ro.meetLat, ro.meetLng) else null
        val routePts = listOfNotNull(meetPt) + ro.stops.map { LatLng(it.lat, it.lng) }
        val markers = buildList {
            meetPt?.let { add(MapMarker("meet", it, ro.meet, "Punto de encuentro", color = "#0E7490")) }
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
            val center = livePs.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: routePts.firstOrNull() ?: LatLng(C.CALI_LAT, C.CALI_LNG)
            Card2(padding = 0.dp) {
                // Trazado por la vía; mientras llega (o si no hay conexión o clave de rutas), una línea recta entre puntos.
                val road by produceState(routePts, routePts) {
                    if (routePts.size >= 2) runCatching { vm.container.routes.route(routePts.first(), routePts.last(), routePts.drop(1).dropLast(1)) }
                        .onSuccess { if (it.points.size >= 2) value = it.points }
                }
                OsmMap(Modifier.fillMaxWidth().height(280.dp), mapState, center = center, mapType = vm.mapType, markers = markers,
                    route = road)
            }
            LaunchedEffect(livePs.size, ro.stops.size) {
                delay(300)
                mapState.fit(livePs.map { LatLng(it.lat, it.lng) } + routePts)
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
            // El recorrido en palabras: encuentro y paradas en orden (también están en el mapa).
            if (ro.stops.isNotEmpty()) Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (listOf(ro.meet) + ro.stops.map { it.name }).forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(if (i == 0) CyanDeep else Orange), contentAlignment = Alignment.Center) {
                            Text(if (i == 0) "E" else "$i", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(n, fontSize = 13.sp, color = if (i == 0) Muted else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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
            if (ro.status == "planned" && meetPt != null && member) Btn("Llevarme al punto de encuentro", Modifier.fillMaxWidth(), Icons.Filled.Place, kind = BK.Ghost) {
                vm.navSession = NavSession(ro.meet, listOf(NavStop(ro.meet, ro.meetLat, ro.meetLng))); nav.navigate("navigate")
            }
            if (!member) JoinButton(vm, u, ro, Modifier.fillMaxWidth())
            if (u.id in ro.invited && !member && ro.status != "done") TextButton({ vm.act("Invitación rechazada") { vm.repo.declineInvite(ro, u.id); nav.popBackStack() } }) {
                Text("No voy, gracias", color = Muted)
            }
        }

        H("Miembros (${ro.joined.size})", Icons.Filled.Groups)
        ro.joined.sortedWith(compareBy<String>({ it != ro.leaderId }, { it != ro.sweeperId }, { nameOf(it).lowercase() })).forEach { mid ->
            MemberRow(vm, nav, ro, u, mid, people.firstOrNull { it.id == mid }, livePs.firstOrNull { it.id == mid }, me, active, isCreator, nameOf(mid),
                onShare = { share(it) }, sharing = sharing, onLeave = { confirmLeave = true })
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

        // Invitar con el código ("enlace"): en las públicas cualquier miembro; en las privadas solo quien la creó.
        if (member && ro.status != "done" && (isCreator || ro.visibility == "public")) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Card2(tone = Surf1) {
                IconLine(Icons.Filled.Link, "Invita con un código", CyanDeep)
                Sub("Comparte el enlace por WhatsApp o donde quieras: con un toque abre la invitación en ApexMoto. También pueden escribir el código en Comunidad → Unirme con código" +
                    if (ro.visibility == "friends") ", aunque la rodada sea privada." else ".")
                if (ro.inviteCode.isNotBlank()) Num(ro.inviteCode.chunked(4).joinToString(" "), OrangeDark, 22)
                if (ro.inviteCode.isNotBlank() || isCreator) Btn("Compartir invitación", Modifier.fillMaxWidth(), Icons.Filled.Share, small = true) {
                    vm.act {
                        val code = if (isCreator) vm.repo.ensureInviteCode(ro) else ro.inviteCode
                        shareText(ctx, "¡Vamos a rodar! \"${ro.name}\" · ${ro.whenLabel}\nEncuentro: ${ro.meet}\n" +
                            "Únete aquí: https://${co.apexmoto.app.BuildConfig.INVITE_HOST}/r/$code\n" +
                            "(o en ApexMoto: Comunidad → Unirme con código → $code)")
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
private fun MemberRow(vm: AppViewModel, nav: NavController, ro: Rodada, u: AppUser, uid: String, p: Profile?, live: LivePos?, me: LivePos?,
                      active: Boolean, isCreator: Boolean, name: String, onShare: (Boolean) -> Unit, sharing: Boolean, onLeave: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val leader = uid == ro.leaderId
    val sweeper = uid == ro.sweeperId
    val self = uid == u.id
    val myProfile by vm.myProfile.collectAsState()
    val friends by vm.friendProfiles.collectAsState()
    val reqOut by vm.friendReqOut.collectAsState()
    val reqIn by vm.friendReqIn.collectAsState()
    val isFriend = friends.any { it.id == uid }
    val asked = reqOut.any { it.to == uid } || reqIn.any { it.from == uid }
    Card2(tone = if (live?.sos == true) RedSoft else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ring = when { live?.sos == true -> Red; leader -> Orange; sweeper -> Cyan; else -> null }
            if (p != null) MotoAvatar(p.moto, p.color, 46.dp, ring) else Avatar(name, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name + if (self) " (tú)" else "", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (leader) Pill("Líder", OrangeSoft, OrangeDark, Icons.Filled.Flag)
                    if (sweeper) Pill("Cierra el grupo", CyanSoft, CyanDeep, Icons.Filled.OutlinedFlag)
                    if (uid == ro.creatorId) Pill("Creó la rodada")
                    if (isFriend) Pill("Amigo", GreenSoft, Green)
                    if (live?.sos == true) Pill("S.O.S.", Red, Color.White, Icons.Filled.Sos)
                }
                if (live != null) {
                    val dist = if (me != null && !self) " · a ${km(haversine(me.lat, me.lng, live.lat, live.lng))}" else ""
                    Lbl("En el mapa · ${agoText(live.updatedAt)}$dist", Green)
                } else if (active) Lbl("No está compartiendo su ubicación")
            }
            Box {
                IconBtn(Icons.Filled.MoreVert, "Opciones de $name") { menu = true }
                DropdownMenu(menu, { menu = false }, containerColor = Color.White) {
                    if (self) {
                        MenuItem("Ver mi perfil", Icons.Filled.Person) { menu = false; nav.navigate("profile") }
                        MenuItem("Editar mi avatar y @usuario", Icons.Filled.Edit) { menu = false; vm.communityTab = "amigos"; nav.navigate("grp") }
                        if (active) MenuItem(if (sharing) "Dejar de compartir mi ubicación" else "Compartir mi ubicación", Icons.Filled.MyLocation) { menu = false; onShare(!sharing) }
                        if (!isCreator && ro.status != "done") MenuItem("Salir de la rodada", Icons.Filled.ExitToApp, danger = true) { menu = false; onLeave() }
                    } else {
                        if (p != null) MenuItem("Ver perfil", Icons.Filled.Person) { menu = false; nav.navigate("rider/$uid") }
                        if (isCreator && ro.status != "done") {
                            if (!leader) MenuItem("Asignar como líder (guía)", Icons.Filled.Flag) { menu = false
                                vm.act("$name es el líder") { vm.repo.setRoles(ro, uid, if (sweeper) "" else ro.sweeperId) } }
                            if (!sweeper && !leader) MenuItem("Que cierre el grupo", Icons.Filled.OutlinedFlag) { menu = false
                                vm.act("$name cierra el grupo") { vm.repo.setRoles(ro, ro.leaderId, uid) } }
                            if (sweeper) MenuItem("Ya no cierra el grupo", Icons.Filled.Close) { menu = false; vm.act { vm.repo.setRoles(ro, ro.leaderId, "") } }
                        }
                        val mine = myProfile
                        if (p != null && mine != null && !isFriend) {
                            if (asked) DropdownMenuItem({ Text("Solicitud de amistad pendiente", color = Muted) }, {}, enabled = false,
                                leadingIcon = { Icon(Icons.Filled.HourglassTop, null, tint = Muted) })
                            else MenuItem("Añadir amigo", Icons.Filled.PersonAdd) { menu = false; vm.act("Solicitud enviada a ${p.name}") { vm.repo.sendFriendRequest(mine, p) } }
                        }
                        if (p == null && !isCreator) DropdownMenuItem({ Text("Aún no tiene perfil de rider", color = Muted) }, {}, enabled = false)
                        if (isCreator && uid != ro.creatorId && ro.status != "done") MenuItem("Expulsar de la rodada", Icons.Filled.PersonRemove, danger = true) { menu = false
                            vm.act("$name salió de la rodada") { vm.repo.kick(ro, uid) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) =
    DropdownMenuItem({ Text(text, color = if (danger) Red else Ink) }, onClick, leadingIcon = { Icon(icon, null, tint = if (danger) Red else CyanDeep) })
