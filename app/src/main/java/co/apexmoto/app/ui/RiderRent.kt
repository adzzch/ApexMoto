@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.apexmoto.app.core.*
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*

// ───────────────── Comunidad / rodadas ─────────────────
@Composable
fun CommunityScreen(vm: AppViewModel, u: AppUser) {
    val rodadas by vm.rodadas.collectAsState()
    var create by remember { mutableStateOf(false) }
    Screen {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            H("Rodadas", Icons.Filled.Groups)
            Sub("Salidas en grupo con cupos limitados. Únete o arma la tuya.")
        }
        if (rodadas.isEmpty()) Empty("Aún no hay rodadas. Crea la primera y reúne a tu grupo.", Icons.Filled.Groups)
        rodadas.forEachIndexed { i, r ->
            val joined = u.id in r.joined
            val free = (r.seats - r.joined.size).coerceAtLeast(0)
            val fill by animateFloatAsState(if (r.seats > 0) r.joined.size / r.seats.toFloat() else 0f, tween(600, easing = EaseOutExpo), label = "seats")
            Card2(index = i) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
                    Spacer(Modifier.width(8.dp)); Pill(r.whenLabel, Orange, Color.White)
                }
                Lbl("${r.captain} · ${r.style}")
                IconLine(Icons.Filled.Place, r.meet, Muted, bold = false)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Surf3)) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(if (free == 0) Red else Green))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Num("$free", OrangeDark, 20); Spacer(Modifier.width(6.dp)); Lbl(if (free == 1) "cupo libre de ${r.seats}" else "cupos libres de ${r.seats}")
                    }
                    Btn(if (joined) "Salir" else "Unirme", icon = if (joined) Icons.Filled.Close else Icons.Filled.Check, kind = if (joined) BK.Ghost else BK.Primary,
                        small = true, enabled = joined || free > 0) { vm.act(if (joined) "Saliste de la rodada" else "¡Estás dentro!") { vm.repo.toggleJoin(r, u.id) } }
                }
            }
        }
        Btn("Crear rodada", Modifier.fillMaxWidth(), Icons.Filled.GroupAdd) { create = true }
    }
    if (create) {
        var name by remember { mutableStateOf("") }; var whenL by remember { mutableStateOf("Sábado 7:00") }
        var meet by remember { mutableStateOf("") }; var style by remember { mutableStateOf("Turismo") }; var seats by remember { mutableStateOf("8") }
        Sheet({ create = false }) {
            H("Nueva rodada", Icons.Filled.GroupAdd)
            Field("Nombre", name, { name = it }, hint = "Ej: Rumbo al Calima")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Cuándo", whenL, { whenL = it }, Modifier.weight(1f))
                Field("Cupos", seats, { seats = it.filter(Char::isDigit) }, Modifier.weight(0.6f), kb = androidx.compose.ui.text.input.KeyboardType.Number)
            }
            Field("Punto de encuentro", meet, { meet = it }, hint = "Ej: Peaje Cencar, Cali")
            Lbl("Estilo")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C.RIDE_STYLES.forEach { s -> Chip(s, style == s, accent = Cyan) { style = s } }
            }
            Btn("Publicar rodada", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = name.isNotBlank() && meet.isNotBlank()) {
                create = false
                vm.act("Rodada publicada") { vm.repo.createRodada(Rodada(creatorId = u.id, name = name, whenLabel = whenL, captain = "@" + u.name.split(" ").first().lowercase(),
                    meet = meet, style = style, seats = seats.toIntOrNull()?.coerceIn(2, 40) ?: 8, joined = listOf(u.id))) }
            }
        }
    }
}

// ───────────────── Rentar: motos y conductores ─────────────────
fun bikeTint(b: Bike) = when (b.category) { "Deportiva" -> OrangeSoft; "Turismo" -> CyanTint; "Urbana" -> Surf3; else -> GreenTint }

@Composable
fun RentScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    Screen {
        Segmented(listOf(Triple("moto", "Solo moto", Icons.Filled.TwoWheeler), Triple("cond", "Conductor guía", Icons.Filled.SportsMotorsports)), vm.rentMode) { vm.rentMode = it }
        AnimatedContent(vm.rentMode, label = "rent", transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) }) { mode ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { if (mode == "moto") BikesList(vm, nav) else DriversList(vm, nav, u) }
        }
    }
}

@Composable
private fun ColumnScope.BikesList(vm: AppViewModel, nav: NavController) {
    val bikes by vm.bikes.collectAsState()
    var city by rememberSaveable { mutableStateOf("all") }
    var cat by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("rec") }
    val days = maxOf(1, daysBetween(vm.rentFrom, vm.rentTo) + 1)
    Card2 {
        Drop("¿Dónde?", listOf("all" to "Todas las ciudades") + bikes.map { it.city }.distinct().sorted().map { it to it }, city, { city = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Recoges", vm.rentFrom, today(), Modifier.weight(1f)) { vm.rentFrom = it; if (vm.rentTo < it) vm.rentTo = addDays(it, 1) }
            DateField("Devuelves", vm.rentTo, vm.rentFrom, Modifier.weight(1f)) { vm.rentTo = it }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Todas", cat == "all", accent = Cyan) { cat = "all" }
        bikes.map { it.category }.distinct().sorted().forEach { c -> Chip(c, cat == c, accent = Cyan) { cat = c } }
    }
    val list = bikes.filter { it.available && (city == "all" || it.city == city) && (cat == "all" || it.category == cat) }
        .let { l -> when (sort) { "pa" -> l.sortedBy { it.pricePerDay }; "pd" -> l.sortedByDescending { it.pricePerDay }; "rt" -> l.sortedByDescending { it.rating }; else -> l.sortedByDescending { it.rating * 10 + it.trips / 100.0 } } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lbl("${list.size} moto${if (list.size == 1) "" else "s"} · $days día${if (days == 1) "" else "s"}", modifier = Modifier.weight(1f))
        Drop("", listOf("rec" to "Recomendadas", "pa" to "Menor precio", "pd" to "Mayor precio", "rt" to "Mejor calificadas"), sort, { sort = it }, Modifier.width(190.dp))
    }
    if (list.isEmpty()) Empty("No hay motos con esos filtros.", Icons.Filled.TwoWheeler)
    list.forEachIndexed { i, b ->
        val busy = overlaps(b.booked, vm.rentFrom, vm.rentTo)
        Card2(Modifier.alpha(if (busy) 0.6f else 1f), index = i, padding = 0.dp, onClick = { nav.navigate("bike/${b.id}") }) {
            MediaHeader(bikeTint(b), Icons.Filled.TwoWheeler, 150.dp) {
                if (b.instant) Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { Pill("Instantánea", Color.White, OrangeDark, Icons.Filled.Bolt) }
                Row(Modifier.align(Alignment.BottomEnd).padding(10.dp).glow(Orange, RoundedCornerShape(9.dp), elevation = 6.dp).clip(RoundedCornerShape(9.dp)).background(Orange).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Num(cop(b.pricePerDay), Color.White, 16); Text(" /día", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.trips > 0) Stars(b.rating) else Pill("Nueva")
                }
                Lbl("${b.city}${if (b.trips > 0) " · ${b.trips} viajes" else ""}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${b.cc} cc"); Pill(b.category); b.specs.take(2).forEach { Pill(it) }
                }
                if (busy) Pill("No disponible en tus fechas", RedSoft, Red)
                else Lbl("$days día${if (days == 1) "" else "s"} · ${cop(days * b.pricePerDay)}", OrangeDark)
            }
        }
    }
}

@Composable
fun BikeDetailScreen(vm: AppViewModel, nav: NavController, u: AppUser, id: String) {
    val bikes by vm.bikes.collectAsState()
    val b = bikes.firstOrNull { it.id == id }
    var plan by remember { mutableStateOf("s") }
    var extras by remember { mutableStateOf(setOf<String>()) }
    var delivery by remember { mutableStateOf(false) }
    if (b == null) { Screen { Empty("Cargando moto…") }; return }
    val days = maxOf(1, daysBetween(vm.rentFrom, vm.rentTo) + 1)
    val pl = C.PLANS.first { it.key == plan }
    val ex = C.EXTRAS.filter { it.key in extras }
    val base = b.pricePerDay * days
    val total = base + pl.perDay * days + ex.sumOf { it.price } * days + if (delivery) C.DELIVERY_FEE else 0L
    val busy = overlaps(b.booked, vm.rentFrom, vm.rentTo)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MediaHeader(bikeTint(b), Icons.Filled.TwoWheeler, 190.dp) {
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (b.instant) Pill("Reserva instantánea", Color.White, OrangeDark, Icons.Filled.Bolt)
                    Text(b.name, fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { Stars(b.rating); Spacer(Modifier.width(10.dp)); Lbl("${b.trips} viajes · ${b.city}") }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${b.cc} cc"); Pill(b.category); b.specs.forEach { Pill(it) }
                }
                Card2 {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField("Recoges", vm.rentFrom, today(), Modifier.weight(1f)) { vm.rentFrom = it; if (vm.rentTo < it) vm.rentTo = addDays(it, 1) }
                        DateField("Devuelves", vm.rentTo, vm.rentFrom, Modifier.weight(1f)) { vm.rentTo = it }
                    }
                    if (busy) Pill("La moto no está disponible en esas fechas", RedSoft, Red)
                }
                Card2 {
                    H("Protección", Icons.Filled.Shield)
                    C.PLANS.forEach { p ->
                        OptionTile(plan == p.key, { plan = p.key }, Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp); Sub(p.desc) }
                                if (p.perDay == 0L) Pill("Incluida", GreenSoft, Green) else Num("+${cop(p.perDay)}", CyanDeep, 14)
                            }
                        }
                    }
                }
                Card2 {
                    H("Extras por día", Icons.Filled.AddCircle)
                    C.EXTRAS.chunked(2).forEach { pair ->
                        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { e ->
                                val on = e.key in extras
                                OptionTile(on, { extras = if (on) extras - e.key else extras + e.key }, Modifier.weight(1f).fillMaxHeight()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(e.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline, null, Modifier.size(20.dp), tint = if (on) CyanDeep else Outline)
                                    }
                                    Num("+${cop(e.price)}", Muted, 13)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (b.delivery) SwitchRow("Entrega a domicilio", "+${cop(C.DELIVERY_FEE)} una vez", delivery) { delivery = it }
                }
                Card2(tone = Surf1) {
                    Row { Text("$days día${if (days == 1) "" else "s"} × ${cop(b.pricePerDay)}", Modifier.weight(1f), color = Muted); Num(cop(base), Ink, 14) }
                    if (pl.perDay > 0) Row { Text("Protección ${pl.name}", Modifier.weight(1f), color = Muted); Num(cop(pl.perDay * days), Ink, 14) }
                    if (ex.isNotEmpty()) Row { Text("Extras", Modifier.weight(1f), color = Muted); Num(cop(ex.sumOf { it.price } * days), Ink, 14) }
                    if (delivery) Row { Text("Entrega", Modifier.weight(1f), color = Muted); Num(cop(C.DELIVERY_FEE), Ink, 14) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        BottomBar {
            Column(Modifier.weight(1f)) { Lbl("Total"); Num(cop(animatedMoney(total)), OrangeDark, 22) }
            Btn(if (b.instant) "Reservar" else "Solicitar", icon = Icons.Filled.Check, enabled = !busy) {
                val req = ServiceRequest(type = "rent", fromId = u.id, fromName = u.name, toId = b.ownerId, itemId = b.id, itemName = b.name, days = days,
                    amount = base, total = total, dateFrom = vm.rentFrom, dateTo = vm.rentTo, plan = pl.name, extras = ex.map { it.name }, delivery = delivery,
                    status = if (b.instant) "ok" else "p")
                vm.act(if (b.instant) "¡Moto reservada!" else "Solicitud enviada al dueño") {
                    vm.repo.createRequest(req)
                    if (b.instant) vm.repo.addBooking(b.id, vm.rentFrom, vm.rentTo)
                }
                nav.popBackStack()
            }
        }
    }
}

@Composable
private fun ColumnScope.DriversList(vm: AppViewModel, nav: NavController, u: AppUser) {
    val drivers by vm.drivers.collectAsState()
    val mine by vm.reqFrom.collectAsState()
    var hire by remember { mutableStateOf<Driver?>(null) }
    var rate by remember { mutableStateOf<ServiceRequest?>(null) }
    if (vm.preRoute.isNotBlank()) Card2(tone = Surf1, border = CyanSoft) { IconLine(Icons.Filled.Route, "Buscas conductor para: ${vm.preRoute}", CyanDeep) }
    val online = drivers.filter { it.online && it.ownerId != u.id }
    if (online.isEmpty()) Empty("No hay conductores en línea ahora mismo.", Icons.Filled.SportsMotorsports)
    online.forEachIndexed { i, d ->
        Card2(index = i) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box { Avatar(d.name, 48.dp); Box(Modifier.align(Alignment.BottomEnd).clip(CircleShape).background(Color.White).padding(2.dp)) { Ping() } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
                    Lbl("${d.bike} · ${d.zone}")
                }
                Column(horizontalAlignment = Alignment.End) { Num(cop(d.rate), OrangeDark, 16); Lbl("por día") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Stars(d.rating); Spacer(Modifier.width(8.dp)); Lbl("${d.reviews} reseñas") }
            if (d.bio.isNotBlank()) Text(d.bio, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { d.services.forEach { Pill(it, CyanSoft, CyanDeep) } }
            Btn("Contratar", Modifier.fillMaxWidth(), Icons.Filled.SportsMotorsports) { hire = d }
        }
    }
    val svc = mine.filter { it.type == "drv" }.sortedByDescending { it.createdAt }
    if (svc.isNotEmpty()) {
        H("Mis servicios con conductor")
        svc.forEach { r ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${r.driverName} · ${r.service}", fontWeight = FontWeight.Bold); Lbl("${prettyDate(r.dateFrom)} · ${r.days} día(s) · ${cop(r.amount)}") }
                    StatusPill(REQ_ST[r.status] ?: r.status, r.status)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Chat", Modifier.weight(1f), Icons.Filled.Chat, kind = BK.Ghost) { nav.chat("req_${r.id}", r.driverName, r.toId) }
                    if (r.status == "fin" && r.rated == 0) Btn("Calificar", Modifier.weight(1f), Icons.Filled.Star) { rate = r }
                }
            }
        }
    }
    hire?.let { d ->
        var service by remember { mutableStateOf(d.services.firstOrNull() ?: "Ruta guiada") }
        var meet by remember { mutableStateOf("") }; var date by remember { mutableStateOf(vm.rentFrom) }; var days by remember { mutableStateOf("1") }
        val n = days.toIntOrNull()?.coerceAtLeast(1) ?: 1
        Sheet({ hire = null }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(d.name, 44.dp); Spacer(Modifier.width(12.dp))
                Column { Text("Contratar a ${d.name}", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp); Lbl("${cop(d.rate)} por día") }
            }
            Lbl("Servicio")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { d.services.forEach { s -> Chip(s, service == s, accent = Cyan) { service = s } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Fecha", date, today(), Modifier.weight(1f)) { date = it }
                Field("Días", days, { days = it.filter(Char::isDigit) }, Modifier.weight(0.6f), kb = androidx.compose.ui.text.input.KeyboardType.Number)
            }
            Field("Punto de encuentro", meet, { meet = it }, hint = "Ej: Estación Terpel, salida a Buga")
            Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Total", modifier = Modifier.weight(1f)); Num(cop(animatedMoney(n * d.rate)), OrangeDark, 20) }
            Btn("Enviar solicitud", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Send, enabled = meet.isNotBlank()) {
                val req = ServiceRequest(type = "drv", fromId = u.id, fromName = u.name, toId = d.ownerId, itemId = d.id, itemName = vm.preRoute.ifBlank { service },
                    days = n, amount = n * d.rate, total = n * d.rate, dateFrom = date, meet = meet, service = service, driverName = d.name)
                hire = null; vm.preRoute = ""
                vm.act("Solicitud enviada a ${d.name}") { vm.repo.createRequest(req) }
            }
        }
    }
    rate?.let { r ->
        Sheet({ rate = null }) {
            Text("¿Cómo te fue con ${r.driverName}?", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..5).forEach { n -> IconButton({ rate = null; vm.act("¡Gracias por calificar!") { vm.repo.rateDriver(r, n) } }, Modifier.size(52.dp)) { Icon(Icons.Filled.Star, "$n estrellas", Modifier.size(36.dp), tint = OrangeDark) } }
            }
        }
    }
}
