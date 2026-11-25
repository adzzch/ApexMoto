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

/** Por qué un comercio no recibe pedidos todavía (el rider no tiene cómo saberlo si solo ve "Ver detalles"). */
fun noOrderReason(v: Venue) = if (v.ownerId.isBlank()) "Aún no está en ApexMoto: no recibe pedidos por la app, pero puedes ir con la guía."
    else "Todavía no ha subido su menú para pedir por la app. Puedes ir con la guía."

/** Guía dentro de la app hacia un punto que no es un comercio (p. ej. una gasolinera). */
fun guideTo(vm: AppViewModel, nav: NavController, name: String, lat: Double, lng: Double) {
    vm.navSession = NavSession(name, listOf(NavStop(name, lat, lng)))
    nav.navigate("navigate")
}

/** Lista de gasolineras cercanas, con distancia en línea recta. */
@Composable
private fun FuelList(fuel: List<Near<MeetPoint>>, loading: Boolean, where: String, onGo: (MeetPoint) -> Unit) {
    val ctx = LocalContext.current
    Column { H("Gasolineras $where", Icons.Filled.LocalGasStation); Lbl("Distancia en línea recta. Datos de © OpenStreetMap.") }
    if (loading) Empty("Cargando…", Icons.Filled.LocalGasStation)
    else if (fuel.isEmpty()) Empty("No hay gasolineras de esta zona en el catálogo.", Icons.Filled.LocalGasStation)
    fuel.forEachIndexed { i, f ->
        val m = f.item
        Card2(index = i) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(GreenTint), contentAlignment = Alignment.Center) { Icon(Icons.Filled.LocalGasStation, null, tint = Green) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Lbl(listOf(fmtKmShort(f.km), m.area, m.town).filter { it.isNotBlank() }.distinct().joinToString(" · "))
                }
                IconBtn(Icons.Filled.TurnRight, "Ir con guía") { onGo(m) }
                Spacer(Modifier.width(8.dp))
                IconBtn(Icons.Filled.Map, "Abrir en Google Maps") { openInGoogleMaps(ctx, m.lat, m.lng) }
            }
        }
    }
}

/** Inicia el guiado dentro de la app hacia uno o varios comercios. */
fun startGuide(vm: AppViewModel, nav: NavController, title: String, vs: List<Venue>) {
    vs.forEach { v -> vm.act { vm.repo.addVisit(v.id) } }
    vm.navSession = NavSession(title, vs.map { NavStop(it.name, it.lat, it.lng, it.id) })
    nav.navigate("navigate")
}

fun venueTint(v: Venue) = when (v.types.firstOrNull()) { "cafe" -> CyanTint; "moto" -> GreenTint; "tour" -> Color(0xFFE6E1F5); else -> OrangeSoft }
fun venueBadge(v: Venue) = when (v.types.firstOrNull()) { "cafe" -> CyanDeep; "moto" -> Green; "tour" -> Color(0xFF5B4B9A); else -> Orange }
fun venueIcon(v: Venue) = when (v.types.firstOrNull()) { "cafe" -> Icons.Filled.LocalCafe; "moto" -> Icons.Filled.Build; "tour" -> Icons.Filled.AccountBalance; else -> Icons.Filled.Restaurant }
fun corridorIcon(key: String) = when (key) { "cali" -> Icons.Filled.LocationCity; "linea" -> Icons.Filled.Landscape; "palmas" -> Icons.Filled.Speed; "chica" -> Icons.Filled.Terrain; else -> Icons.Filled.NearMe }

/** Hasta qué distancia de la ciudad elegida se muestran comercios ("en la ciudad y alrededores"). */
private const val VENUE_RADIUS_KM = 40.0

/** Tipos de comercio que se pueden filtrar. Los sitios turísticos no son comercios: van en Explorar. */
private val SHOP_TYPES = listOf("food", "cafe", "moto")

/** Un comercio de verdad: come, café o taller. Lo que solo es sitio turístico no sale en Comercios. */
fun isShop(v: Venue) = v.types.any { it in SHOP_TYPES }

/** Dónde está el comercio; si el negocio no ha puesto su punto, el centro de su zona. */
private fun venuePos(v: Venue) = if (v.lat != 0.0) LatLng(v.lat, v.lng) else C.corridor(v.corridor).let { LatLng(it.lat, it.lng) }

@Composable
fun VenuesScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val ctx = LocalContext.current
    val venues by vm.venues.collectAsState()
    val orders by vm.myOrders.collectAsState()
    var fl by rememberSaveable { mutableStateOf("all") }
    var asMap by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Venue?>(null) }
    var coupon by remember { mutableStateOf<Coupon?>(null) }
    var fuelPick by remember { mutableStateOf<Near<MeetPoint>?>(null) }
    // La ciudad es la misma de Inicio: si se cambia aquí o allá, Comercios muestra los de esa ciudad.
    val cp = rememberCatalogAndPlace(vm)
    val catalog = cp?.first
    val place = cp?.second
    val center = place?.let { LatLng(it.lat, it.lng) } ?: LatLng(C.CALI_LAT, C.CALI_LNG)
    val cityName = place?.town?.name ?: place?.label ?: "tu ciudad"
    val here by produceState<LatLng?>(null, vm.locationConsented) {
        value = if (vm.locationConsented && vm.container.location.hasPermission()) runCatching { vm.container.location.last() }.getOrNull() else null
    }
    // Gasolineras: salen del catálogo de OpenStreetMap que trae la app, cerca de la ciudad elegida.
    val fuelMode = fl == "fuel"
    val fuel = rememberInBackground(catalog, center, fuelMode, initial = emptyList<Near<MeetPoint>>()) {
        if (fuelMode) catalog?.fuelNear(center.latitude, center.longitude).orEmpty() else emptyList()
    }
    // Solo comercios (nada de sitios turísticos), a menos de VENUE_RADIUS_KM de la ciudad, del más cercano al más lejano.
    val near = venues.filter { isShop(it) && (fl == "all" || fl in it.types) }
        .map { v -> venuePos(v).let { p -> v to distanceKm(center.latitude, center.longitude, p.latitude, p.longitude) } }
        .filter { it.second <= VENUE_RADIUS_KM }.sortedBy { it.second }
    val list = near.map { it.first }
    val distOf = near.associate { it.first.id to it.second }
    val active = orders.filter { it.status !in listOf("done", "no", "can") }
    BackHandler(asMap) { asMap = false }   // el botón Atrás del teléfono vuelve a la lista

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = Gap.screen, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(Gap.between)) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (catalog != null && place != null) CityBar(vm, catalog, place, title = "Comercios en") else LoadingCard("Buscando tu ciudad…")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (asMap) "Ver lista" else "Ver mapa", asMap, if (asMap) Icons.Filled.ViewAgenda else Icons.Filled.Map, accent = Ink) { asMap = !asMap }
                Chip("Todos", fl == "all", accent = Cyan) { fl = "all" }
                SHOP_TYPES.forEach { k -> C.CATS[k]?.let { v -> Chip(v.first, fl == k, accent = Cyan) { fl = k } } }
                Chip("Gasolineras", fuelMode, Icons.Filled.LocalGasStation, accent = Cyan) { fl = "fuel" }
            }
        }
        AnimatedContent(asMap, Modifier.weight(1f), label = "map", transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }) { showMap ->
            if (showMap) {
                val located = if (fuelMode) emptyList() else list.filter { it.lat != 0.0 }
                val pins = if (fuelMode) fuel.map { MapMarker(it.item.id, LatLng(it.item.lat, it.item.lng), it.item.name, fmtKmShort(it.km)) }
                           else located.map { MapMarker(it.id, LatLng(it.lat, it.lng), it.name, it.promo) }
                val mapState = remember(center) { OsmMapState() }
                LaunchedEffect(center, pins.size, fuelMode) { delay(350); mapState.fit(pins.map { it.pos }) }
                Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    key(center) { // al cambiar de ciudad se crea un mapa centrado en ella
                        OsmMap(
                            Modifier.fillMaxSize(), state = mapState, center = center, zoom = 12.0, mapType = vm.mapType,
                            markers = pins,
                            myPos = here, onMarkerClick = { id ->
                                located.firstOrNull { it.id == id }?.let { detail = it }
                                fuel.firstOrNull { it.item.id == id }?.let { fuelPick = it }
                            },
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
                        Lbl("${pins.size} ${if (fuelMode) "gasolinera" else "comercio"}${if (pins.size == 1) "" else "s"} · $cityName", Ink)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).wrapContentWidth().widthIn(max = 640.dp).padding(horizontal = Gap.screen, vertical = Gap.between), verticalArrangement = Arrangement.spacedBy(Gap.between)) {
                    if (active.isNotEmpty()) Card2(tone = Surf1, border = OrangeSoft) {
                        IconLine(Icons.Filled.ReceiptLong, "Pedidos activos")
                        active.forEach { o ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(o.venueName, fontWeight = FontWeight.Bold); Lbl("${o.items.sumOf { it.qty }} producto(s) · ${cop(o.total)} · llegas ${o.eta}") }
                                StatusPill(ORD_ST[o.status] ?: o.status, o.status)
                                if (o.ownerId.isNotBlank()) { Spacer(Modifier.width(8.dp)); IconBtn(Icons.Filled.Chat, "Escribir al comercio") { nav.chat("ord_${o.id}", o.venueName, o.ownerId) } }
                                // Mientras el comercio no lo acepte, el pedido se cancela sin costo.
                                if (o.status == "new") { Spacer(Modifier.width(8.dp)); IconBtn(Icons.Filled.Close, "Cancelar pedido", tint = Red, bg = RedSoft) { vm.act("Pedido cancelado") { vm.repo.cancelOrder(o.id) } } }
                            }
                        }
                    }
                    if (fuelMode) FuelList(fuel, catalog == null, "cerca de $cityName") { f -> guideTo(vm, nav, f.name, f.lat, f.lng) }
                    else if (list.isEmpty()) Empty("Todavía no hay comercios aliados en $cityName. Prueba con otra ciudad.", Icons.Filled.Storefront)
                    if (!fuelMode) list.forEachIndexed { i, v ->
                        val canOrder = v.menu.any { it.on && it.cat != "Servicios" } && v.ownerId.isNotBlank()
                        Card2(index = i, padding = 0.dp, onClick = { detail = v }) {
                            MediaHeader(venueTint(v), venueIcon(v), 116.dp) {
                                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.76f).padding(start = Gap.card, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill(v.badge, venueBadge(v), Color.White)
                                        if (!v.open) Pill("Cerrado", RedSoft, Red)
                                    }
                                    Text(v.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Lbl("${v.kind} · ${fmtKmShort(distOf[v.id] ?: 0.0)}", CyanDeep)
                                }
                                Box(Modifier.align(Alignment.BottomEnd).padding(end = Gap.card, bottom = 12.dp)) { if (v.reviews > 0) Stars(v.rating) else Pill("Nuevo", CyanSoft, CyanDeep) }
                            }
                            Column(Modifier.padding(start = Gap.card, end = Gap.card, bottom = Gap.card), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (v.promo.isNotBlank()) IconLine(Icons.Filled.LocalOffer, v.promo)
                                if (canOrder) IconLine(Icons.Filled.RestaurantMenu, if (v.open) "Pide desde la app y te lo tienen listo al llegar" else "Recibe pedidos cuando abra", Green)
                                else if ("food" in v.types || "cafe" in v.types) IconLine(Icons.Filled.Info, noOrderReason(v), Muted, bold = false)
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
            Row(verticalAlignment = Alignment.CenterVertically) { RatingLine(v.rating, v.reviews); Spacer(Modifier.width(10.dp)); Lbl("${v.kind} · Km ${v.km}") }
            if (v.desc.isNotBlank()) Text(v.desc, style = MaterialTheme.typography.bodyLarge)
            if (v.promo.isNotBlank()) IconLine(Icons.Filled.LocalOffer, v.promo)
            val preview = v.menu.filter { it.on }.take(5)
            if (preview.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lbl(if ("moto" in v.types) "Servicios" else "Del menú")
                preview.forEach { m -> Row(verticalAlignment = Alignment.CenterVertically) { Text(m.name, Modifier.weight(1f)); Num(cop(m.price), OrangeDark, 14) } }
            }
            H("Reseñas", Icons.Filled.Star)
            ReviewsList(vm, "venue", v.id)
            Btn("Guiar en la app", Modifier.fillMaxWidth(), Icons.Filled.Navigation, enabled = v.lat != 0.0) { detail = null; startGuide(vm, nav, v.name, listOf(v)) }
            GoogleMapsButton(Modifier.fillMaxWidth(), enabled = v.lat != 0.0) { openInGoogleMaps(ctx, v.lat, v.lng) }
            if (canOrder) Btn("Ver menú y pedir", Modifier.fillMaxWidth(), Icons.Filled.RestaurantMenu, kind = BK.Ghost) { detail = null; nav.navigate("menu/${v.id}") }
            else if ("food" in v.types || "cafe" in v.types) IconLine(Icons.Filled.Info, noOrderReason(v), Muted, bold = false)
        }
    }
    fuelPick?.let { f ->
        val m = f.item
        Sheet({ fuelPick = null }) {
            H(m.name, Icons.Filled.LocalGasStation)
            Lbl(listOf(fmtKmShort(f.km) + " en línea recta", m.area, m.town).filter { it.isNotBlank() }.distinct().joinToString(" · "))
            Btn("Guiar en la app", Modifier.fillMaxWidth(), Icons.Filled.Navigation) { fuelPick = null; guideTo(vm, nav, m.name, m.lat, m.lng) }
            GoogleMapsButton(Modifier.fillMaxWidth()) { openInGoogleMaps(ctx, m.lat, m.lng) }
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
    var note by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    // Hora a la que el rider quiere el pedido listo. Si viene del guiado, nunca antes de su hora estimada de llegada.
    val now0 = remember { System.currentTimeMillis() }
    val arrival = vm.arrivals[venueId]
    val minReady = maxOf(now0 + 10 * 60_000L, arrival ?: 0L)
    var readyAt by remember { mutableLongStateOf(if (arrival != null) minReady else now0 + 30 * 60_000L) }
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
            Column(Modifier.padding(horizontal = Gap.screen, vertical = Gap.between), verticalArrangement = Arrangement.spacedBy(Gap.between)) {
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
                    Lbl("¿Para qué hora lo quieres listo?")
                    if (arrival != null) IconLine(Icons.Filled.Navigation, "Según tu ruta llegas a eso de las ${hhmm(arrival)}", CyanDeep, bold = false)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (arrival != null) Chip("Cuando llegue", kotlin.math.abs(readyAt - minReady) < 60_000, accent = Cyan) { readyAt = minReady }
                        listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 h", 90 to "1 h 30", 120 to "2 h").forEach { (m, label) ->
                            val t = now0 + m * 60_000L
                            if (t >= minReady) Chip(label, kotlin.math.abs(readyAt - t) < 60_000, accent = Cyan) { readyAt = t }
                        }
                        Chip("Otra hora", false, Icons.Filled.Schedule, accent = Cyan) {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = readyAt }
                            android.app.TimePickerDialog(ctx, { _, h, m ->
                                val c = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m); set(java.util.Calendar.SECOND, 0) }
                                var t = c.timeInMillis
                                if (t < System.currentTimeMillis()) t += 86_400_000L
                                when {
                                    t < minReady -> vm.toast("Elige una hora desde las ${hhmm(minReady)}.")
                                    t > System.currentTimeMillis() + 12 * 3_600_000L -> vm.toast("Elige una hora dentro de las próximas 12 horas.")
                                    else -> readyAt = t
                                }
                            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), false).show()
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) { Lbl("Listo a las "); Spacer(Modifier.width(4.dp)); Num(hhmm(readyAt), OrangeDark, 18) }
                    Field("Nota para el comercio (opcional)", note, { note = it }, hint = "Ej: sin cebolla, vamos 3 motos")
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        BottomBar {
            Column(Modifier.weight(1f)) {
                Lbl("${lines.sumOf { it.qty }} producto(s)"); Num(cop(animatedMoney(total)), Ink, 20)
                if (lines.isEmpty()) Text("Falta: agregar al menos un producto.", color = Muted, fontSize = 12.sp, lineHeight = 15.sp)
            }
            Btn("Enviar pedido", icon = Icons.AutoMirrored.Filled.Send, enabled = lines.isNotEmpty()) {
                val t = readyAt.coerceAtLeast(System.currentTimeMillis() + 5 * 60_000L)
                vm.act("¡Pedido enviado para las ${hhmm(t)}!") { vm.repo.placeOrder(v, u, lines, hhmm(t), t, note) }
                nav.popBackStack()
            }
        }
    }
}
