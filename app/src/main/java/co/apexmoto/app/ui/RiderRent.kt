@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { H("Rodadas"); Sub("Salidas grupales con cupos limitados") }
            Btn("Crear", icon = Icons.Filled.Add) { create = true }
        }
        if (rodadas.isEmpty()) Empty("Aún no hay rodadas. Crea la primera y reúne a tu grupo.")
        rodadas.forEach { r ->
            val joined = u.id in r.joined
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(r.name, style = MaterialTheme.typography.titleMedium); Lbl("${r.whenLabel} · ${r.style}") }
                    Pill("${r.joined.size}/${r.seats} cupos")
                }
                Sub("Capitán ${r.captain}")
                Sub("Punto de encuentro: ${r.meet}")
                Btn(if (joined) "Salir de la rodada" else "Unirme", Modifier.fillMaxWidth(), if (joined) Icons.Filled.Close else Icons.Filled.Check,
                    kind = if (joined) BK.Ghost else BK.Primary) { vm.act(if (joined) "Saliste de la rodada" else "¡Estás dentro!") { vm.repo.toggleJoin(r, u.id) } }
            }
        }
    }
    if (create) {
        var name by remember { mutableStateOf("") }; var whenL by remember { mutableStateOf("Sábado 7:00") }
        var meet by remember { mutableStateOf("") }; var style by remember { mutableStateOf("Turismo") }; var seats by remember { mutableStateOf("8") }
        AlertDialog(
            onDismissRequest = { create = false }, title = { Text("Nueva rodada") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Nombre", name, { name = it }); Field("Cuándo", whenL, { whenL = it }); Field("Punto de encuentro", meet, { meet = it })
                Drop("Estilo", C.RIDE_STYLES.map { it to it }, style, { style = it })
                Field("Cupos", seats, { seats = it.filter(Char::isDigit) }, kb = androidx.compose.ui.text.input.KeyboardType.Number)
            } },
            confirmButton = { Btn("Publicar", enabled = name.isNotBlank() && meet.isNotBlank()) {
                create = false
                vm.act("Rodada publicada") { vm.repo.createRodada(Rodada(creatorId = u.id, name = name, whenLabel = whenL, captain = "@" + u.name.split(" ").first().lowercase(),
                    meet = meet, style = style, seats = seats.toIntOrNull()?.coerceIn(2, 40) ?: 8, joined = listOf(u.id))) }
            } },
            dismissButton = { TextButton({ create = false }) { Text("Cancelar") } },
        )
    }
}

// ───────────────── Rentar: motos y conductores ─────────────────
@Composable
fun RentScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    Screen {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Solo moto", vm.rentMode == "moto") { vm.rentMode = "moto" }
            Chip("Conductor guía", vm.rentMode == "cond") { vm.rentMode = "cond" }
        }
        if (vm.rentMode == "moto") BikesList(vm, nav) else DriversList(vm, nav, u)
    }
}

@Composable
private fun ColumnScope.BikesList(vm: AppViewModel, nav: NavController) {
    val bikes by vm.bikes.collectAsState()
    var city by rememberSaveable { mutableStateOf("all") }
    var cat by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("price") }
    Card2 {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Desde", vm.rentFrom, today(), Modifier.weight(1f)) { vm.rentFrom = it; if (vm.rentTo < it) vm.rentTo = addDays(it, 1) }
            DateField("Hasta", vm.rentTo, vm.rentFrom, Modifier.weight(1f)) { vm.rentTo = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Drop("Ciudad", listOf("all" to "Todas") + bikes.map { it.city }.distinct().sorted().map { it to it }, city, { city = it }, Modifier.weight(1f))
            Drop("Tipo", listOf("all" to "Todos") + bikes.map { it.category }.distinct().sorted().map { it to it }, cat, { cat = it }, Modifier.weight(1f))
        }
        Drop("Ordenar", listOf("price" to "Precio", "rating" to "Calificación", "cc" to "Cilindraje"), sort, { sort = it })
    }
    val list = bikes.filter { it.available && (city == "all" || it.city == city) && (cat == "all" || it.category == cat) }
        .let { l -> when (sort) { "rating" -> l.sortedByDescending { it.rating }; "cc" -> l.sortedByDescending { it.cc }; else -> l.sortedBy { it.usdPerDay } } }
    if (list.isEmpty()) Empty("No hay motos con esos filtros.")
    list.forEach { b ->
        val busy = overlaps(b.booked, vm.rentFrom, vm.rentTo)
        Card2(onClick = { nav.navigate("bike/${b.id}") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(b.name, style = MaterialTheme.typography.titleMedium); Lbl("${b.cc} cc · ${b.category} · ${b.city}") }
                Text(usd(b.usdPerDay) + "/día", color = Orange, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Stars(b.rating); Sub("${b.trips} viajes")
                if (b.instant) Pill("Reserva inmediata", Color(0x2E1B8A3A), Green)
                if (busy) Pill("No disponible", Color(0x2ED13B3B), Red)
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
    val base = b.usdPerDay * days
    val total = base + pl.perDay * days + ex.sumOf { it.price } * days + if (delivery) 15 else 0
    val busy = overlaps(b.booked, vm.rentFrom, vm.rentTo)
    Screen {
        H(b.name); Lbl("${b.cc} cc · ${b.category} · ${b.city}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Stars(b.rating); Sub("${b.trips} viajes") }
        if (b.specs.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { b.specs.forEach { Pill(it) } }
        Card2 {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Desde", vm.rentFrom, today(), Modifier.weight(1f)) { vm.rentFrom = it; if (vm.rentTo < it) vm.rentTo = addDays(it, 1) }
                DateField("Hasta", vm.rentTo, vm.rentFrom, Modifier.weight(1f)) { vm.rentTo = it }
            }
            if (busy) Text("La moto no está disponible en esas fechas.", color = Red, fontWeight = FontWeight.SemiBold)
        }
        Card2 {
            Lbl("Protección")
            C.PLANS.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(plan == p.key, { plan = p.key })
                    Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.SemiBold); Sub(p.desc) }
                    Text(if (p.perDay == 0L) "Incluida" else "+${usd(p.perDay)}/día")
                }
            }
            Lbl("Extras")
            C.EXTRAS.forEach { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(e.key in extras, { extras = if (e.key in extras) extras - e.key else extras + e.key })
                    Text(e.name, Modifier.weight(1f)); Text("+${usd(e.price)}/día")
                }
            }
            if (b.delivery) SwitchRow("Entrega a domicilio", "+${usd(15)}", delivery) { delivery = it }
        }
        Card2(border = Orange) {
            Text("$days día(s) × ${usd(b.usdPerDay)} = ${usd(base)}")
            Text("Total ${usd(total)}", style = MaterialTheme.typography.titleLarge, color = Orange)
            Btn(if (b.instant) "Reservar ahora" else "Enviar solicitud", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = !busy) {
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
    if (vm.preRoute.isNotBlank()) Card2(border = Cyan) { Text("Quieres un conductor para: ${vm.preRoute}", fontWeight = FontWeight.SemiBold) }
    drivers.filter { it.online && it.ownerId != u.id }.forEach { d ->
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(d.name, style = MaterialTheme.typography.titleMedium); Lbl("${d.bike} · ${d.zone}") }
                Text(usd(d.rate) + "/día", color = Orange, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Stars(d.rating); Sub("${d.reviews} reseñas") }
            Sub(d.bio)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { d.services.forEach { Pill(it) } }
            Btn("Contratar", Modifier.fillMaxWidth(), Icons.Filled.SportsMotorsports) { hire = d }
        }
    }
    val svc = mine.filter { it.type == "drv" }.sortedByDescending { it.createdAt }
    if (svc.isNotEmpty()) {
        H("Mis servicios con conductor")
        svc.forEach { r ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${r.driverName} · ${r.service}", fontWeight = FontWeight.Bold); Sub("${r.dateFrom} · ${r.days} día(s) · ${usd(r.amount)}") }
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
        AlertDialog(
            onDismissRequest = { hire = null }, title = { Text("Contratar a ${d.name}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Drop("Servicio", d.services.map { it to it }, service, { service = it })
                DateField("Fecha", date, today()) { date = it }
                Field("Días", days, { days = it.filter(Char::isDigit) }, kb = androidx.compose.ui.text.input.KeyboardType.Number)
                Field("Punto de encuentro", meet, { meet = it })
            } },
            confirmButton = { Btn("Enviar solicitud", enabled = meet.isNotBlank()) {
                val n = days.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val req = ServiceRequest(type = "drv", fromId = u.id, fromName = u.name, toId = d.ownerId, itemId = d.id, itemName = vm.preRoute.ifBlank { service },
                    days = n, amount = n * d.rate, total = n * d.rate, dateFrom = date, meet = meet, service = service, driverName = d.name)
                hire = null; vm.preRoute = ""
                vm.act("Solicitud enviada a ${d.name}") { vm.repo.createRequest(req) }
            } },
            dismissButton = { TextButton({ hire = null }) { Text("Cancelar") } },
        )
    }
    rate?.let { r ->
        AlertDialog(
            onDismissRequest = { rate = null }, title = { Text("¿Cómo te fue con ${r.driverName}?") },
            text = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { n -> IconButton({ rate = null; vm.act("¡Gracias por calificar!") { vm.repo.rateDriver(r, n) } }) { Icon(Icons.Filled.Star, "$n", tint = Color(0xFFB87900)) } }
            } },
            confirmButton = { TextButton({ rate = null }) { Text("Cerrar") } },
        )
    }
}
