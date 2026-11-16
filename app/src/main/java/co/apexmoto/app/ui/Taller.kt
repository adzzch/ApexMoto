@file:OptIn(ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import co.apexmoto.app.core.C
import co.apexmoto.app.core.cop
import co.apexmoto.app.core.haversine
import co.apexmoto.app.data.*
import co.apexmoto.app.maps.NavSession
import co.apexmoto.app.maps.NavStop
import co.apexmoto.app.maps.openInGoogleMaps
import co.apexmoto.app.ui.theme.*
import kotlin.math.roundToInt

// ───────────────── Espacio del taller mecánico ─────────────────
// El taller recibe los S.O.S. de los riders (atenciones), los atiende paso a paso y administra su catálogo de servicios.

fun sosIcon(kind: String): ImageVector = when (kind) {
    "flat" -> Icons.Filled.TireRepair; "battery" -> Icons.Filled.BatteryAlert; "fuel" -> Icons.Filled.LocalGasStation
    "tow" -> Icons.Filled.LocalShipping; else -> Icons.Filled.Build
}

/** "hace 5 min", "hace 2 h", "hace 3 días". */
fun ago(ms: Long): String {
    if (ms <= 0) return ""
    val m = ((System.currentTimeMillis() - ms) / 60000).coerceAtLeast(0)
    return when { m < 1 -> "ahora"; m < 60 -> "hace $m min"; m < 1440 -> "hace ${m / 60} h"; else -> "hace ${m / 1440} días" }
}

@Composable
private fun myShop(vm: AppViewModel, u: AppUser): Venue? { val vs by vm.venues.collectAsState(); return vs.firstOrNull { it.ownerId == u.id } }

/** Distancia en km del taller al S.O.S., o null si alguno no tiene ubicación. */
private fun distKm(v: Venue, s: Sos): Double? =
    if (v.lat == 0.0 || s.lat == 0.0) null else haversine(v.lat, v.lng, s.lat, s.lng) / 1000.0

/** S.O.S. nuevos que este taller puede atender: tipo cubierto y dentro de su radio (si hay ubicación). */
private fun available(v: Venue, all: List<Sos>) = all.filter { s ->
    s.status == "new" && s.kind in v.covers && (distKm(v, s)?.let { it <= v.radiusKm } ?: true)
        && System.currentTimeMillis() - s.createdAt < C.SOS_TTL_MS
}.sortedByDescending { it.createdAt }

// ───────────────── Resumen ─────────────────
@Composable
fun WorkshopDashboard(vm: AppViewModel, nav: NavController, u: AppUser) {
    val v = myShop(vm, u)
    val all by vm.sos.collectAsState()
    Screen {
        if (v == null) { Empty("Cargando tu taller…"); return@Screen }
        val news = available(v, all)
        val mine = all.filter { it.providerId == u.id }
        val active = mine.filter { it.status in listOf("ok", "go", "site") }
        val done = mine.filter { it.status == "fin" }
        Card2(index = 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(GreenTint), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Handyman, null, tint = Green) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(v.name, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp); Lbl("${C.corridor(v.corridor).name} · Km ${v.km}", CyanDeep) }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ping(v.open); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(if (v.open) "Taller abierto" else "Taller cerrado", fontWeight = FontWeight.Bold); Lbl("Los riders lo ven en Comercios") }
                ApexSwitch(v.open) { on -> vm.act { vm.repo.saveVenue(v.copy(open = on)) } }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Handyman, null, tint = if (v.sosOn) OrangeDark else Outline); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(if (v.sosOn) "Recibiendo asistencias" else "Asistencias en pausa", fontWeight = FontWeight.Bold); Lbl("Hasta ${v.radiusKm} km de tu taller") }
                ApexSwitch(v.sosOn) { on -> vm.act { vm.repo.saveVenue(v.copy(sosOn = on)) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(if (v.sosOn) "${news.size}" else "—", "Asistencias disponibles", Modifier.weight(1f), Red, 1)
            StatCard("${active.size}", "En curso", Modifier.weight(1f), CyanDeep, 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("${done.size}", "Atendidos", Modifier.weight(1f), Green, 3)
            StatCard(cop(done.sumOf { it.price }), "Ingresos asistencias", Modifier.weight(1f), OrangeDark, 4)
        }
        H("Reseñas", Icons.Filled.Star)
        Card2 { RatingLine(v.rating, v.reviews); ReviewsList(vm, "venue", v.id) }
        active.firstOrNull()?.let { s ->
            H("Atención en curso", Icons.Filled.Build)
            Card2(index = 5, border = CyanSoft, onClick = { nav.navigate("tsos") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${s.kindName} · ${s.fromName}", fontWeight = FontWeight.Bold); Lbl(s.locationText) }
                    StatusPill(TALLER_ST[s.status] ?: s.status, s.status)
                }
                Steps(4, stepOf(s.status))
            }
        }
        if (v.sosOn && news.isNotEmpty() && active.isEmpty()) Btn("Ver ${news.size} asistencia${if (news.size == 1) "" else "s"} disponible${if (news.size == 1) "" else "s"}", Modifier.fillMaxWidth(), Icons.Filled.Handyman) { nav.navigate("tsos") }
    }
}

/** Estados vistos desde el taller. */
val TALLER_ST = mapOf("new" to "Disponible", "ok" to "Aceptado", "go" to "En camino", "site" to "Atendiendo", "fin" to "Resuelto", "no" to "Cancelado")
private fun stepOf(st: String) = when (st) { "ok" -> 1; "go" -> 2; "site" -> 3; "fin" -> 4; else -> 0 }

// ───────────────── Atenciones (S.O.S.) ─────────────────
@Composable
fun WorkshopSosScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val ctx = LocalContext.current
    val v = myShop(vm, u)
    val all by vm.sos.collectAsState()
    var tab by rememberSaveable { mutableStateOf("new") }
    // Mostrarle al rider dónde va el taller exige el aviso de ubicación aceptado y el permiso de Android.
    var hasPerm by remember { mutableStateOf(vm.container.location.hasPermission()) }
    var pendingShare by remember { mutableStateOf<String?>(null) }
    val perms = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = vm.container.location.hasPermission()
        pendingShare?.let { if (hasPerm && vm.locationConsented) vm.startSosSharing(it) }
        pendingShare = null
    }
    var disclose by remember { mutableStateOf(false) }
    if (disclose) LocationDisclosure(onAccept = { disclose = false; vm.acceptLocation(); launcher.launch(perms) }, onDismiss = { disclose = false; pendingShare = null })
    fun shareWith(id: String, on: Boolean) {
        if (!on) { vm.stopSosSharing(); return }
        pendingShare = id
        when { !vm.locationConsented -> disclose = true; !hasPerm -> launcher.launch(perms); else -> { pendingShare = null; vm.startSosSharing(id) } }
    }
    if (v == null) { Screen { Empty("Cargando tu taller…") }; return }
    val news = if (v.sosOn) available(v, all) else emptyList()
    val mine = all.filter { it.providerId == u.id }
    val active = mine.filter { it.status in listOf("ok", "go", "site") }.sortedByDescending { it.acceptedAt }
    val history = mine.filter { it.status in listOf("fin", "no") }.sortedByDescending { maxOf(it.doneAt, it.createdAt) }
    Screen {
        Segmented(listOf(Triple("new", "Nuevos · ${news.size}", null), Triple("act", "En curso · ${active.size}", null), Triple("his", "Historial", null)), tab) { tab = it }
        AnimatedContent(tab, label = "tsos", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { t ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (t) {
                    "new" -> {
                        if (!v.sosOn) Card2(tone = Surf1, border = RedSoft) {
                            IconLine(Icons.Filled.PauseCircle, "Tienes las asistencias en pausa", Red)
                            Sub("Actívalos para ver las emergencias de los riders cerca de tu taller.")
                            Btn("Recibir asistencias", Modifier.fillMaxWidth(), Icons.Filled.Handyman) { vm.act("Ya estás recibiendo pedidos de asistencia") { vm.repo.saveVenue(v.copy(sosOn = true)) } }
                        } else if (news.isEmpty()) Empty("No hay pedidos de asistencia ahora. Te aparecerán aquí en tiempo real.", Icons.Filled.Handyman)
                        news.forEachIndexed { i, s ->
                            var eta by remember(s.id) { mutableIntStateOf(30) }
                            Card2(index = i, border = RedSoft) {
                                SosHeader(s, distKm(v, s))
                                if (s.note.isNotBlank()) Text("“${s.note}”", style = MaterialTheme.typography.bodyMedium)
                                Lbl("¿En cuánto llegas?")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(15, 30, 45, 60, 90).forEach { m -> Chip(if (m < 60) "$m min" else "${m / 60} h${if (m % 60 > 0) " ${m % 60}" else ""}", eta == m, accent = Cyan) { eta = m } }
                                }
                                if (active.isNotEmpty()) Lbl("Termina o libera la asistencia que estás atendiendo para aceptar otra.", Muted)
                                Btn("Aceptar · llego en $eta min", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = active.isEmpty()) {
                                    vm.act("Asistencia aceptada. El rider ya sabe que vas en camino") { vm.repo.acceptSos(s.id, u.id, v.name, eta, v.id) }
                                    tab = "act"
                                }
                                if (active.isNotEmpty()) Lbl("Termina la atención en curso para aceptar otra.", Muted)
                            }
                        }
                    }
                    "act" -> {
                        if (active.isEmpty()) Empty("No tienes atenciones en curso.", Icons.Filled.Build)
                        active.forEachIndexed { i, s ->
                            Card2(index = i, border = CyanSoft) {
                                SosHeader(s, distKm(v, s))
                                Steps(4, stepOf(s.status))
                                Row { listOf("Aceptado", "En camino", "Atendiendo", "Resuelto").forEach { Lbl(it, modifier = Modifier.weight(1f)) } }
                                if (s.status in listOf("ok", "go")) SwitchRow("Mostrarle al rider dónde voy",
                                    "Solo lo ve este rider y solo con ApexMoto abierta. Se borra al llegar.", vm.sharingSos == s.id) { shareWith(s.id, it) }
                                if (s.note.isNotBlank()) Text("“${s.note}”", style = MaterialTheme.typography.bodyMedium)
                                when (s.status) {
                                    "ok" -> Btn("Voy en camino", Modifier.fillMaxWidth(), Icons.Filled.TwoWheeler) {
                                        vm.act("El rider ve que vas en camino") { vm.repo.setSosStatus(s.id, "go") }
                                        if (vm.sharingSos != s.id) shareWith(s.id, true)
                                    }
                                    "go" -> Btn("Llegué, empezar atención", Modifier.fillMaxWidth(), Icons.Filled.Handyman) { vm.stopSosSharing(); vm.act { vm.repo.arriveSos(s.id) } }
                                    else -> Btn("Marcar resuelto · ${cop(s.price)}", Modifier.fillMaxWidth(), Icons.Filled.CheckCircle) { vm.act("¡Atención resuelta!") { vm.repo.finishSos(s.id) } }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Btn("Chat", Modifier.weight(1f), Icons.Filled.Chat, kind = BK.Ghost) { nav.chat("sos_${s.id}", s.fromName, s.fromId) }
                                    IconBtn(Icons.Filled.Navigation, "Guiar hasta el rider", enabled = s.lat != 0.0) {
                                        vm.navSession = NavSession("Asistencia · ${s.fromName}", listOf(NavStop(s.locationText.ifBlank { s.fromName }, s.lat, s.lng))); nav.navigate("navigate")
                                    }
                                }
                                if (s.lat != 0.0) GoogleMapsButton(Modifier.fillMaxWidth()) { openInGoogleMaps(ctx, s.lat, s.lng) }
                                if (s.status == "ok") TextButton({ vm.stopSosSharing(); vm.act("Liberaste la asistencia; otro taller puede tomarla") { vm.repo.releaseSos(s.id) } }, Modifier.align(Alignment.CenterHorizontally)) {
                                    Text("No puedo ir, liberar asistencia", color = Red)
                                }
                            }
                        }
                    }
                    else -> {
                        if (history.isEmpty()) Empty("Aquí verás las atenciones que termines.", Icons.Filled.History)
                        history.forEachIndexed { i, s ->
                            Card2(index = i) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).clip(CircleShape).background(Surf3), contentAlignment = Alignment.Center) { Icon(sosIcon(s.kind), null, Modifier.size(20.dp), tint = Muted) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) { Text("${s.kindName} · ${s.fromName}", fontWeight = FontWeight.Bold); Lbl("${s.locationText} · ${ago(maxOf(s.doneAt, s.createdAt))}") }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Num(cop(s.price), if (s.status == "fin") Green else Muted, 14); StatusPill(TALLER_ST[s.status] ?: s.status, s.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SosHeader(s: Sos, km: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(RedSoft), contentAlignment = Alignment.Center) { Icon(sosIcon(s.kind), null, tint = Red) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.kindName, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Lbl("${s.fromName} · ${ago(s.createdAt)}")
        }
        Column(horizontalAlignment = Alignment.End) { Num(cop(s.price), OrangeDark, 16); if (km != null) Lbl("a ${if (km < 10) "%.1f".format(km) else km.roundToInt().toString()} km", CyanDeep) }
    }
    IconLine(Icons.Filled.Place, s.locationText.ifBlank { "Ubicación por GPS" }, Muted, bold = false)
}

// ───────────────── Servicios y cobertura ─────────────────
@Composable
fun WorkshopServicesScreen(vm: AppViewModel, u: AppUser) {
    val v = myShop(vm, u)
    var edit by remember { mutableStateOf<MenuItem?>(null) }
    Screen {
        if (v == null) { Empty("Cargando tu taller…"); return@Screen }
        Card2(index = 0) {
            H("Qué asistencias atiendes", Icons.Filled.Handyman)
            Sub("Solo te llegan las emergencias que marques y que estén dentro de tu radio.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                C.SOS_KINDS.forEach { k ->
                    val on = k.key in v.covers
                    Chip(k.name, on, sosIcon(k.key), accent = Red) { vm.act { vm.repo.saveVenue(v.copy(covers = if (on) v.covers - k.key else v.covers + k.key)) } }
                }
            }
            var radius by remember(v.id) { mutableFloatStateOf(v.radiusKm.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Radio de cobertura", modifier = Modifier.weight(1f)); Num("${radius.roundToInt()} km", CyanDeep, 15) }
            Slider(radius, { radius = it }, valueRange = 5f..150f, onValueChangeFinished = { vm.act { vm.repo.saveVenue(v.copy(radiusKm = radius.roundToInt())) } },
                colors = SliderDefaults.colors(thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = Surf4))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { H("Servicios y precios", Icons.Filled.Handyman); Sub("Lo que los riders ven al abrir tu taller") }
            Btn("Agregar", icon = Icons.Filled.Add, small = true) { edit = MenuItem(id = "s" + System.currentTimeMillis(), cat = "Servicios") }
        }
        val services = v.menu
        if (services.isEmpty()) Empty("Agrega tu primer servicio: despinche, cambio de aceite, revisión de frenos…", Icons.Filled.Handyman)
        services.forEachIndexed { i, m ->
            Card2(index = i + 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(m.name, fontWeight = FontWeight.Bold, fontSize = 15.sp); if (m.desc.isNotBlank()) Sub(m.desc); Num(cop(m.price), OrangeDark, 15) }
                    ApexSwitch(m.on) { on -> vm.act { vm.repo.setMenu(v.id, v.menu.map { if (it.id == m.id) it.copy(on = on) else it }) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Editar", Modifier.weight(1f), Icons.Filled.Edit, kind = BK.Ghost, small = true) { edit = m }
                    Btn("Eliminar", Modifier.weight(1f), Icons.Filled.Delete, kind = BK.Danger, small = true) { vm.act("Servicio eliminado") { vm.repo.setMenu(v.id, v.menu.filter { it.id != m.id }) } }
                }
            }
        }
    }
    val shop = v
    edit?.let { m ->
        if (shop == null) return@let
        var name by remember(m.id) { mutableStateOf(m.name) }; var desc by remember(m.id) { mutableStateOf(m.desc) }
        var price by remember(m.id) { mutableStateOf(if (m.price > 0) m.price.toString() else "") }
        Sheet({ edit = null }) {
            H(if (shop.menu.any { it.id == m.id }) "Editar servicio" else "Nuevo servicio", Icons.Filled.Handyman)
            Field("Servicio", name, { name = it }, hint = "Ej: Cambio de aceite")
            Field("Detalle o duración (opcional)", desc, { desc = it }, hint = "Ej: 30 min, incluye filtro")
            Field("Precio (COP)", price, { price = it.filter(Char::isDigit) }, kb = KeyboardType.Number, hint = "45000")
            Missing(buildList { if (name.isBlank()) add("el nombre del servicio"); if ((price.toLongOrNull() ?: 0) <= 0) add("el precio") })
            Btn("Guardar", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0) {
                val nm = m.copy(name = name, desc = desc, price = price.toLong(), cat = "Servicios")
                val list = if (shop.menu.any { it.id == m.id }) shop.menu.map { if (it.id == m.id) nm else it } else shop.menu + nm
                edit = null; vm.act("Servicios actualizados") { vm.repo.setMenu(shop.id, list) }
            }
        }
    }
}
