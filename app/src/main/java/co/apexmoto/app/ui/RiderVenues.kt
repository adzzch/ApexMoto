@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.apexmoto.app.core.C
import co.apexmoto.app.core.cop
import co.apexmoto.app.data.*
import co.apexmoto.app.maps.LatLng
import co.apexmoto.app.maps.MapMarker
import co.apexmoto.app.maps.NavSession
import co.apexmoto.app.maps.NavStop
import co.apexmoto.app.maps.OsmMap
import co.apexmoto.app.maps.openInGoogleMaps
import co.apexmoto.app.ui.theme.*

/** Inicia el guiado dentro de la app hacia uno o varios comercios. */
fun startGuide(vm: AppViewModel, nav: NavController, title: String, vs: List<Venue>) {
    vs.forEach { v -> vm.act { vm.repo.addVisit(v.id) } }
    vm.navSession = NavSession(title, vs.map { NavStop(it.name, it.lat, it.lng) })
    nav.navigate("navigate")
}

@Composable
fun VenuesScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val ctx = LocalContext.current
    val venues by vm.venues.collectAsState()
    val orders by vm.myOrders.collectAsState()
    var cor by rememberSaveable { mutableStateOf("calima") }
    var fl by rememberSaveable { mutableStateOf("all") }
    var asMap by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Venue?>(null) }
    var coupon by remember { mutableStateOf<Coupon?>(null) }
    val list = venues.filter { it.corridor == cor && (fl == "all" || fl in it.types) }.sortedBy { it.km }
    val active = orders.filter { it.status != "done" && it.status != "no" }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C.CORRIDORS.forEach { c -> Chip(c.name, cor == c.key) { cor = c.key } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Todos", fl == "all") { fl = "all" }
                C.CATS.forEach { (k, v) -> Chip(v.first, fl == k) { fl = k } }
                Chip(if (asMap) "Ver lista" else "Ver mapa", asMap) { asMap = !asMap }
            }
        }
        if (asMap) {
            val c = C.corridor(cor)
            val here by produceState<LatLng?>(null) { value = runCatching { vm.container.location.last() }.getOrNull() }
            val located = list.filter { it.lat != 0.0 }
            key(cor) { // al cambiar de corredor se crea un mapa centrado en él
                OsmMap(
                    Modifier.weight(1f).fillMaxWidth(), center = LatLng(c.lat, c.lng), zoom = 11.0, mapType = vm.mapType,
                    markers = located.map { MapMarker(it.id, LatLng(it.lat, it.lng), it.name, it.promo) },
                    myPos = here, onMarkerClick = { id -> located.firstOrNull { it.id == id }?.let { detail = it } },
                )
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                active.forEach { o ->
                    Card2(border = Orange) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Pedido en ${o.venueName}", fontWeight = FontWeight.Bold); Sub("${o.items.sumOf { it.qty }} ítems · ${cop(o.total)} · llegas ${o.eta}") }
                            StatusPill(ORD_ST[o.status] ?: o.status, o.status)
                        }
                        if (o.ownerId.isNotBlank()) Btn("Escribir al comercio", icon = Icons.Filled.Chat, kind = BK.Ghost) { nav.chat("ord_${o.id}", o.venueName, o.ownerId) }
                    }
                }
                if (list.isEmpty()) Empty("No hay comercios en este corredor todavía.")
                list.forEach { v ->
                    Card2(onClick = { detail = v }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(v.name, style = MaterialTheme.typography.titleMedium); Lbl("${v.badge} · km ${v.km}") }
                            Stars(v.rating)
                        }
                        Text(v.promo, color = Orange, fontWeight = FontWeight.SemiBold)
                        Sub(v.desc)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (v.menu.isNotEmpty() && v.ownerId.isNotBlank()) Btn("Pedir", Modifier.weight(1f), Icons.Filled.Restaurant) { nav.navigate("menu/${v.id}") }
                            Btn("Ir", Modifier.weight(1f), Icons.Filled.Navigation, kind = BK.Ghost, enabled = v.lat != 0.0) { startGuide(vm, nav, v.name, listOf(v)) }
                            Btn("Cupón", Modifier.weight(1f), Icons.Filled.LocalOffer, kind = BK.Ghost, enabled = v.ownerId.isNotBlank()) {
                                vm.act { coupon = vm.repo.couponFor(v, u) }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { v ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(v.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lbl("${v.kind} · km ${v.km} · ${v.reviews} reseñas")
                    Text(v.desc); Text(v.promo, color = Orange, fontWeight = FontWeight.Bold)
                    if (v.menu.isNotEmpty()) v.menu.filter { it.on }.take(5).forEach { Sub("${it.name} · ${cop(it.price)}") }
                    TextButton({ openInGoogleMaps(ctx, v.lat, v.lng) }, enabled = v.lat != 0.0) { Text("Abrir en otra app") }
                }
            },
            confirmButton = { Btn("Guiar en la app", icon = Icons.Filled.Navigation, enabled = v.lat != 0.0) { detail = null; startGuide(vm, nav, v.name, listOf(v)) } },
            dismissButton = { TextButton({ detail = null }) { Text("Cerrar") } },
        )
    }
    coupon?.let { c ->
        AlertDialog(
            onDismissRequest = { coupon = null },
            title = { Text("Tu cupón") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.code, style = MaterialTheme.typography.headlineSmall, color = Orange, fontFamily = Mono)
                Sub("Muéstralo en el comercio para canjear tu beneficio.")
            } },
            confirmButton = { TextButton({ coupon = null }) { Text("Listo") } },
        )
    }
}

@Composable
fun MenuScreen(vm: AppViewModel, nav: NavController, u: AppUser, venueId: String) {
    val venues by vm.venues.collectAsState()
    val v = venues.firstOrNull { it.id == venueId }
    val cart: SnapshotStateMap<String, Int> = remember { mutableStateMapOf() }
    var eta by remember { mutableStateOf("30 min") }
    var note by remember { mutableStateOf("") }
    if (v == null) { Screen { Empty("Cargando menú…") }; return }
    val items = v.menu.filter { it.on && it.cat != "Servicios" }
    val lines = items.filter { (cart[it.id] ?: 0) > 0 }.map { OrderItem(it.name, cart[it.id]!!, it.price) }
    val total = lines.sumOf { it.qty * it.price }
    Screen {
        H(v.name); Sub(v.promo)
        C.MENU_CATS.forEach { cat ->
            val xs = items.filter { it.cat == cat }
            if (xs.isNotEmpty()) {
                Lbl(cat)
                xs.forEach { m ->
                    Card2 {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(m.name, fontWeight = FontWeight.Bold); if (m.desc.isNotBlank()) Sub(m.desc); Text(cop(m.price), color = Orange, fontWeight = FontWeight.Bold) }
                            val q = cart[m.id] ?: 0
                            IconButton({ if (q > 0) cart[m.id] = q - 1 }) { Icon(Icons.Filled.Remove, "Quitar") }
                            Text("$q", fontWeight = FontWeight.Bold)
                            IconButton({ cart[m.id] = q + 1 }) { Icon(Icons.Filled.Add, "Agregar") }
                        }
                    }
                }
            }
        }
        if (items.isEmpty()) Empty("Este comercio aún no publica su menú.")
        Card2 {
            Drop("¿En cuánto llegas?", listOf("15 min", "30 min", "45 min", "1 h", "2 h").map { it to it }, eta, { eta = it })
            Field("Nota para el comercio (opcional)", note, { note = it })
            Text("Total ${cop(total)}", style = MaterialTheme.typography.titleLarge)
            Btn("Enviar pedido", Modifier.fillMaxWidth(), Icons.Filled.Send, enabled = lines.isNotEmpty()) {
                vm.act("¡Pedido enviado! El comercio lo está revisando") { vm.repo.placeOrder(v, u, lines, eta, note) }
                nav.popBackStack()
            }
        }
    }
}
