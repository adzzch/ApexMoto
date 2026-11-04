@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.apexmoto.app.core.usd
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*

// ───────────────── Dueño de moto ─────────────────
@Composable
fun OwnerFleetScreen(vm: AppViewModel, u: AppUser) {
    val bikes by vm.bikes.collectAsState()
    val mine = bikes.filter { it.ownerId == u.id }
    var edit by remember { mutableStateOf<Bike?>(null) }
    Screen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { H("Mi flota"); Sub("Motos que publicas para rentar") }
            Btn("Agregar", icon = Icons.Filled.Add) { edit = Bike(ownerId = u.id) }
        }
        if (mine.isEmpty()) Empty("Publica tu primera moto para empezar a recibir solicitudes.")
        mine.forEach { b ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(b.name, style = MaterialTheme.typography.titleMedium); Lbl("${b.cc} cc · ${b.category} · ${b.city}") }
                    Text(usd(b.usdPerDay) + "/día", color = Orange, fontWeight = FontWeight.ExtraBold)
                }
                SwitchRow(if (b.available) "Disponible" else "Pausada", "", b.available) { on -> vm.act { vm.repo.saveBike(b.copy(available = on)) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Editar", Modifier.weight(1f), Icons.Filled.Edit, kind = BK.Ghost) { edit = b }
                    Btn("Eliminar", Modifier.weight(1f), Icons.Filled.Delete, kind = BK.Danger) { vm.act("Moto eliminada") { vm.repo.deleteBike(b.id) } }
                }
            }
        }
    }
    edit?.let { b ->
        var name by remember(b.id) { mutableStateOf(b.name) }; var cc by remember(b.id) { mutableStateOf(b.cc.toString()) }
        var price by remember(b.id) { mutableStateOf(b.usdPerDay.toString()) }; var city by remember(b.id) { mutableStateOf(b.city) }
        var cat by remember(b.id) { mutableStateOf(b.category) }; var specs by remember(b.id) { mutableStateOf(b.specs.joinToString(", ")) }
        var instant by remember(b.id) { mutableStateOf(b.instant) }; var delivery by remember(b.id) { mutableStateOf(b.delivery) }
        AlertDialog(
            onDismissRequest = { edit = null }, title = { Text(if (b.id.isBlank()) "Nueva moto" else "Editar moto") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Modelo", name, { name = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Cilindraje", cc, { cc = it.filter(Char::isDigit) }, Modifier.weight(1f), KeyboardType.Number)
                    Field("USD/día", price, { price = it.filter(Char::isDigit) }, Modifier.weight(1f), KeyboardType.Number)
                }
                Field("Ciudad", city, { city = it })
                Drop("Tipo", listOf("Aventura", "Deportiva", "Turismo", "Urbana").map { it to it }, cat, { cat = it })
                Field("Características (separadas por coma)", specs, { specs = it })
                SwitchRow("Reserva inmediata", "Sin aprobar cada solicitud", instant) { instant = it }
                SwitchRow("Entrega a domicilio", "", delivery) { delivery = it }
            } },
            confirmButton = { Btn("Guardar", enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0) {
                edit = null
                vm.act("Moto guardada") { vm.repo.saveBike(b.copy(name = name, cc = cc.toIntOrNull() ?: 0, usdPerDay = price.toLong(), city = city, category = cat,
                    specs = specs.split(",").map { it.trim() }.filter { it.isNotEmpty() }, instant = instant, delivery = delivery)) }
            } },
            dismissButton = { TextButton({ edit = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun OwnerRequestsScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val reqs by vm.reqTo.collectAsState()
    val list = reqs.filter { it.type == "rent" }.sortedByDescending { it.createdAt }
    Screen {
        H("Solicitudes de renta")
        if (list.isEmpty()) Empty("Cuando un rider pida tu moto, la solicitud aparecerá aquí.")
        list.forEach { r ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${r.itemName} · ${r.fromName}", fontWeight = FontWeight.Bold); Sub("${r.dateFrom} → ${r.dateTo} · ${r.plan}") }
                    StatusPill(REQ_ST[r.status] ?: r.status, r.status)
                }
                if (r.extras.isNotEmpty()) Sub("Extras: ${r.extras.joinToString(", ")}")
                if (r.delivery) Sub("Con entrega a domicilio")
                Text("Total ${usd(r.total)}", fontWeight = FontWeight.Bold, color = Orange)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (r.status) {
                        "p" -> {
                            Btn("Aceptar", Modifier.weight(1f), Icons.Filled.Check) { vm.act("Solicitud aceptada") { vm.repo.setRequestStatus(r.id, "ok"); vm.repo.addBooking(r.itemId, r.dateFrom, r.dateTo) } }
                            Btn("Rechazar", Modifier.weight(1f), kind = BK.Danger) { vm.act { vm.repo.setRequestStatus(r.id, "no") } }
                        }
                        "ok" -> Btn("Entregué la moto", Modifier.weight(1f)) { vm.act { vm.repo.setRequestStatus(r.id, "ride") } }
                        "ride" -> Btn("Recibí la moto", Modifier.weight(1f)) { vm.act("Renta finalizada") { vm.repo.setRequestStatus(r.id, "fin") } }
                    }
                    if (r.status != "no") Btn("Chat", Modifier.weight(1f), Icons.Filled.Chat, kind = BK.Ghost) { nav.chat("req_${r.id}", r.fromName, r.fromId) }
                }
            }
        }
    }
}

/** Ingresos del dueño (rentas) o ganancias del conductor (servicios finalizados). */
@Composable
fun EarningsScreen(vm: AppViewModel, u: AppUser) {
    val reqs by vm.reqTo.collectAsState()
    val isDriver = u.role == Role.DRIVER
    val done = reqs.filter { if (isDriver) it.type == "drv" && it.status == "fin" else it.type == "rent" && it.status in listOf("ok", "ride", "fin") }
    val total = done.sumOf { if (isDriver) it.amount else it.total }
    Screen {
        H(if (isDriver) "Ganancias" else "Ingresos")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Stat(usd(total), if (isDriver) "servicios finalizados" else "rentas confirmadas", Modifier.weight(1f)); Stat("${done.size}", "operaciones", Modifier.weight(1f)) }
        if (done.isEmpty()) Empty("Todavía no tienes movimientos.")
        done.sortedByDescending { it.createdAt }.forEach { r ->
            Card2 { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(r.itemName, fontWeight = FontWeight.Bold); Sub("${r.fromName} · ${r.dateFrom}") }
                Text(usd(if (isDriver) r.amount else r.total), fontWeight = FontWeight.ExtraBold, color = Green)
            } }
        }
    }
}

// ───────────────── Conductor guía ─────────────────
@Composable
fun DriverTripsScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val reqs by vm.reqTo.collectAsState()
    val list = reqs.filter { it.type == "drv" }.sortedByDescending { it.createdAt }
    Screen {
        H("Salidas solicitadas")
        if (list.isEmpty()) Empty("Cuando un rider te contrate, la salida aparecerá aquí.")
        list.forEach { r ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${r.fromName} · ${r.service}", fontWeight = FontWeight.Bold); Sub("${r.dateFrom} · ${r.days} día(s)"); Sub("Encuentro: ${r.meet}") }
                    StatusPill(REQ_ST[r.status] ?: r.status, r.status)
                }
                Text(usd(r.amount), fontWeight = FontWeight.ExtraBold, color = Orange)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (r.status) {
                        "p" -> {
                            Btn("Aceptar", Modifier.weight(1f), Icons.Filled.Check) { vm.act("Salida aceptada") { vm.repo.setRequestStatus(r.id, "ok") } }
                            Btn("Rechazar", Modifier.weight(1f), kind = BK.Danger) { vm.act { vm.repo.setRequestStatus(r.id, "no") } }
                        }
                        "ok" -> Btn("Voy al punto de encuentro", Modifier.weight(1f)) { vm.act { vm.repo.setRequestStatus(r.id, "go") } }
                        "go" -> Btn("Iniciar ruta", Modifier.weight(1f), Icons.Filled.Navigation) { vm.act { vm.repo.setRequestStatus(r.id, "ride") } }
                        "ride" -> Btn("Finalizar servicio", Modifier.weight(1f), Icons.Filled.Check) { vm.act("Servicio finalizado") { vm.repo.setRequestStatus(r.id, "fin") } }
                    }
                    if (r.status != "no") Btn("Chat", Modifier.weight(1f), Icons.Filled.Chat, kind = BK.Ghost) { nav.chat("req_${r.id}", r.fromName, r.fromId) }
                }
            }
        }
    }
}

@Composable
fun DriverServiceScreen(vm: AppViewModel, u: AppUser) {
    val drivers by vm.drivers.collectAsState()
    val d = drivers.firstOrNull { it.ownerId == u.id }
    if (d == null) { Screen { Empty("Cargando tu perfil de conductor…") }; return }
    var bike by remember(d.id) { mutableStateOf(d.bike) }; var zone by remember(d.id) { mutableStateOf(d.zone) }
    var rate by remember(d.id) { mutableStateOf(d.rate.toString()) }; var bio by remember(d.id) { mutableStateOf(d.bio) }
    var services by remember(d.id) { mutableStateOf(d.services.toSet()) }; var online by remember(d.id) { mutableStateOf(d.online) }
    val all = listOf("Ruta guiada", "Escolta", "Fotografía", "Instructor")
    Screen {
        H("Mi servicio"); Stars(d.rating); Sub("${d.reviews} reseñas")
        SwitchRow(if (online) "En línea" else "Fuera de línea", "Los riders solo ven conductores en línea", online) { online = it }
        Field("Moto que usas", bike, { bike = it }); Field("Zona", zone, { zone = it })
        Field("Tarifa por día (USD)", rate, { rate = it.filter(Char::isDigit) }, kb = KeyboardType.Number)
        Field("Presentación", bio, { bio = it }, lines = 3)
        Lbl("Servicios")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { all.forEach { s -> Chip(s, s in services) { services = if (s in services) services - s else services + s } } }
        Btn("Guardar", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = services.isNotEmpty() && (rate.toLongOrNull() ?: 0) > 0) {
            vm.act("Perfil actualizado") { vm.repo.saveDriver(d.copy(name = u.name, bike = bike, zone = zone, rate = rate.toLong(), bio = bio, services = services.toList(), online = online)) }
        }
    }
}
