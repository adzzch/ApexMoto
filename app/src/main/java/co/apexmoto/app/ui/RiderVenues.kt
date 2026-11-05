@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import co.apexmoto.app.maps.OsmMapState
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.material.icons.automirrored.filled.Send
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

fun venueTint(v: Venue) = when (v.types.firstOrNull()) { "cafe" -> CyanTint; "moto" -> GreenTint; "tour" -> Color(0xFFE6E1F5); else -> OrangeSoft }
fun venueBadge(v: Venue) = when (v.types.firstOrNull()) { "cafe" -> CyanDeep; "moto" -> Green; "tour" -> Color(0xFF5B4B9A); else -> Orange }
fun venueIcon(v: Venue) = when (v.types.firstOrNull()) { "cafe" -> Icons.Filled.LocalCafe; "moto" -> Icons.Filled.Build; "tour" -> Icons.Filled.AccountBalance; else -> Icons.Filled.Restaurant }
fun corridorIcon(key: String) = when (key) { "cali" -> Icons.Filled.LocationCity; "linea" -> Icons.Filled.Landscape; "palmas" -> Icons.Filled.Speed; "chica" -> Icons.Filled.Terrain; else -> Icons.Filled.NearMe }

@Composable
fun VenuesScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val ctx = LocalContext.current
    val venues by vm.venues.collectAsState()
    val orders by vm.myOrders.collectAsState()
    var cor by rememberSaveable { mutableStateOf("cali") }
    var fl by rememberSaveable { mutableStateOf("all") }
    var asMap by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Venue?>(null) }
    var coupon by remember { mutableStateOf<Coupon?>(null) }
    val list = venues.filter { it.corridor == cor && (fl == "all" || fl in it.types) }.sortedBy { it.km }
    val active = orders.filter { it.status != "done" && it.status != "no" }
    BackHandler(asMap) { asMap = false }   // el botón Atrás del teléfono vuelve a la lista

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C.CORRIDORS.forEach { c -> Chip(c.name, cor == c.key, corridorIcon(c.key)) { cor = c.key } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (asMap) "Ver lista" else "Ver mapa", asMap, if (asMap) Icons.Filled.ViewAgenda else Icons.Filled.Map, accent = Ink) { asMap = !asMap }
                Chip("Todos", fl == "all", accent = Cyan) { fl = "all" }
                C.CATS.forEach { (k, v) -> Chip(v.first, fl == k, accent = Cyan) { fl = k } }
            }
        }
        AnimatedContent(asMap, Modifier.weight(1f), label = "map", transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }) { showMap ->
            if (showMap) {
                val c = C.corridor(cor)
                val here by produceState<LatLng?>(null) { value = runCatching { vm.container.location.last() }.getOrNull() }
                val located = list.filter { it.lat != 0.0 }
                val mapState = remember(cor) { OsmMapState() }
                LaunchedEffect(cor, located.size) { delay(350); mapState.fit(located.map { LatLng(it.lat, it.lng) }) }
                Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    key(cor) { // al cambiar de corredor se crea un mapa centrado en él
                        OsmMap(
                            Modifier.fillMaxSize(), state = mapState, center = LatLng(c.lat, c.lng), zoom = 12.0, mapType = vm.mapType,
                            markers = located.map { MapMarker(it.id, LatLng(it.lat, it.lng), it.name, it.promo) },
                            myPos = here, onMarkerClick = { id -> located.firstOrNull { it.id == id }?.let { detail = it } },
                        )
                    }
                    // Volver a la lista
                    Row(
                        Modifier.align(Alignment.TopStart).padding(12.dp).cardShadow(CircleShape).clip(CircleShape).background(Color.White)
                            .clickable(onClickLabel = "Volver a la lista") { asMap = false }.padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp), tint = Ink); Spacer(Modifier.width(6.dp))
                        Text("Lista", fontWeight = FontWeight.Bold, color = Ink)
                    }
                    // Cuántos lugares hay en el mapa
                    Box(Modifier.align(Alignment.TopEnd).padding(12.dp).cardShadow(CircleShape).clip(CircleShape).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Lbl("${located.size} lugar${if (located.size == 1) "" else "es"} · ${c.name}", Ink)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).wrapContentWidth().widthIn(max = 640.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (active.isNotEmpty()) Card2(tone = Surf1, border = OrangeSoft) {
                        IconLine(Icons.Filled.ReceiptLong, "Pedidos activos")
                        active.forEach { o ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(o.venueName, fontWeight = FontWeight.Bold); Lbl("${o.items.sumOf { it.qty }} producto(s) · ${cop(o.total)} · llegas ${o.eta}") }
                                StatusPill(ORD_ST[o.status] ?: o.status, o.status)
                                if (o.ownerId.isNotBlank()) { Spacer(Modifier.width(8.dp)); IconBtn(Icons.Filled.Chat, "Escribir al comercio") { nav.chat("ord_${o.id}", o.venueName, o.ownerId) } }
                            }
                        }
                    }
                    if (list.isEmpty()) Empty("No hay comercios en este corredor todavía.", Icons.Filled.Storefront)
                    list.forEachIndexed { i, v ->
                        val canOrder = v.menu.any { it.on && it.cat != "Servicios" } && v.ownerId.isNotBlank()
                        Card2(index = i, padding = 0.dp, onClick = { detail = v }) {
                            MediaHeader(venueTint(v), venueIcon(v), 116.dp) {
                                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.76f).padding(start = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill(v.badge, venueBadge(v), Color.White)
                                        if (!v.open) Pill("Cerrado", RedSoft, Red)
                                    }
                                    Text(v.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Lbl("${v.kind} · Km ${v.km}", CyanDeep)
                                }
                                Box(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp)) { Stars(v.rating) }
                            }
                            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (v.promo.isNotBlank()) IconLine(Icons.Filled.LocalOffer, v.promo)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (canOrder) Btn("Menú", Modifier.weight(1f), Icons.Filled.RestaurantMenu) { nav.navigate("menu/${v.id}") }
                                    else Btn("Ver detalles", Modifier.weight(1f)) { detail = v }
                                    IconBtn(Icons.Filled.TurnRight, "Ir con guía", enabled = v.lat != 0.0) { startGuide(vm, nav, v.name, listOf(v)) }
                                    IconBtn(Icons.Filled.LocalOffer, "Pedir cupón", enabled = v.ownerId.isNotBlank()) { vm.act { coupon = vm.repo.couponFor(v, u) } }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    detail?.let { v ->
        val canOrder = v.menu.any { it.on && it.cat != "Servicios" } && v.ownerId.isNotBlank()
        Sheet({ detail = null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(v.badge, venueBadge(v), Color.White); if (!v.open) Pill("Cerrado", RedSoft, Red) }
            Text(v.name, fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 28.sp)
            Row(verticalAlignment = Alignment.CenterVertically) { Stars(v.rating); Spacer(Modifier.width(10.dp)); Lbl("${v.reviews} reseñas · ${v.kind} · Km ${v.km}") }
            if (v.desc.isNotBlank()) Text(v.desc, style = MaterialTheme.typography.bodyLarge)
            if (v.promo.isNotBlank()) IconLine(Icons.Filled.LocalOffer, v.promo)
            val preview = v.menu.filter { it.on }.take(5)
            if (preview.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lbl(if ("moto" in v.types) "Servicios" else "Del menú")
                preview.forEach { m -> Row(verticalAlignment = Alignment.CenterVertically) { Text(m.name, Modifier.weight(1f)); Num(cop(m.price), OrangeDark, 14) } }
            }
            Btn("Guiar en la app", Modifier.fillMaxWidth(), Icons.Filled.Navigation, enabled = v.lat != 0.0) { detail = null; startGuide(vm, nav, v.name, listOf(v)) }
            GoogleMapsButton(Modifier.fillMaxWidth(), enabled = v.lat != 0.0) { openInGoogleMaps(ctx, v.lat, v.lng) }
            if (canOrder) Btn("Ver menú y pedir", Modifier.fillMaxWidth(), Icons.Filled.RestaurantMenu, kind = BK.Ghost) { detail = null; nav.navigate("menu/${v.id}") }
        }
    }
    coupon?.let { c ->
        Sheet({ coupon = null }) {
            H("Tu cupón", Icons.Filled.LocalOffer)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OrangeSoft).border(2.dp, Orange.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                Text(c.code, color = OrangeDark, fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = 0.08.em)
            }
            Sub("Muéstralo en el comercio para canjear tu beneficio.")
            Btn("Listo", Modifier.fillMaxWidth()) { coupon = null }
        }
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
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MediaHeader(venueTint(v), venueIcon(v), 132.dp) {
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(v.badge, venueBadge(v), Color.White)
                    Text(v.name, fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    if (v.promo.isNotBlank()) IconLine(Icons.Filled.LocalOffer, v.promo)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var n = 0
                C.MENU_CATS.forEach { cat ->
                    val xs = items.filter { it.cat == cat }
                    if (xs.isNotEmpty()) {
                        H(cat)
                        xs.forEach { m ->
                            Card2(index = n++) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(m.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        if (m.desc.isNotBlank()) Sub(m.desc)
                                        Num(cop(m.price), OrangeDark, 15)
                                    }
                                    val q = cart[m.id] ?: 0
                                    Stepper(q, { if (q > 0) cart[m.id] = q - 1 }, { cart[m.id] = q + 1 })
                                }
                            }
                        }
                    }
                }
                if (items.isEmpty()) Empty("Este comercio aún no publica su menú.", Icons.Filled.RestaurantMenu)
                Card2 {
                    Lbl("¿En cuánto llegas?")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("15 min", "30 min", "45 min", "1 h", "2 h").forEach { t -> Chip(t, eta == t, accent = Cyan) { eta = t } }
                    }
                    Field("Nota para el comercio (opcional)", note, { note = it }, hint = "Ej: sin cebolla, vamos 3 motos")
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        BottomBar {
            Column(Modifier.weight(1f)) { Lbl("${lines.sumOf { it.qty }} producto(s)"); Num(cop(animatedMoney(total)), Ink, 20) }
            Btn("Enviar pedido", icon = Icons.AutoMirrored.Filled.Send, enabled = lines.isNotEmpty()) {
                vm.act("¡Pedido enviado! El comercio lo está revisando") { vm.repo.placeOrder(v, u, lines, eta, note) }
                nav.popBackStack()
            }
        }
    }
}
