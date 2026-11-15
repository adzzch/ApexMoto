@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.apexmoto.app.core.bikeDocsOk
import co.apexmoto.app.core.cop
import co.apexmoto.app.core.today
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
        mine.forEachIndexed { i, b ->
            Card2(index = i) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(bikeTint(b)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.TwoWheeler, null, tint = Ink.copy(alpha = 0.6f)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(b.name, style = MaterialTheme.typography.titleMedium); Lbl("${b.cc} cc · ${b.category} · ${b.city}") }
                    Num(cop(b.pricePerDay) + "/día", OrangeDark, 15)
                }
                if (!bikeDocsOk(b.soatUntil, b.rtmUntil, today())) Pill("SOAT o revisión vencidos: no se puede rentar", RedSoft, Red)
                SwitchRow(if (b.available) "Disponible" else "Pausada", "", b.available) { on ->
                    // Las reglas del servidor tampoco dejan publicar una moto sin SOAT y revisión declarados.
                    if (on && (b.soatUntil.isBlank() || b.rtmUntil.isBlank())) vm.toast("Edita la moto y agrega las fechas de SOAT y revisión para publicarla.")
                    else vm.act { vm.repo.saveBike(b.copy(available = on)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Editar", Modifier.weight(1f), Icons.Filled.Edit, kind = BK.Ghost) { edit = b }
                    Btn("Eliminar", Modifier.weight(1f), Icons.Filled.Delete, kind = BK.Danger) { vm.act("Moto eliminada") { vm.repo.deleteBike(b.id) } }
                }
            }
        }
    }
    edit?.let { b ->
        var name by remember(b.id) { mutableStateOf(b.name) }; var cc by remember(b.id) { mutableStateOf(b.cc.toString()) }
        var price by remember(b.id) { mutableStateOf(b.pricePerDay.toString()) }; var city by remember(b.id) { mutableStateOf(b.city) }
        var cat by remember(b.id) { mutableStateOf(b.category) }; var specs by remember(b.id) { mutableStateOf(b.specs.joinToString(", ")) }
        var instant by remember(b.id) { mutableStateOf(b.instant) }; var delivery by remember(b.id) { mutableStateOf(b.delivery) }
        var soat by remember(b.id) { mutableStateOf(b.soatUntil) }; var rtm by remember(b.id) { mutableStateOf(b.rtmUntil) }
        AlertDialog(
            onDismissRequest = { edit = null }, title = { Text(if (b.id.isBlank()) "Nueva moto" else "Editar moto") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Modelo", name, { name = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Cilindraje", cc, { cc = it.filter(Char::isDigit) }, Modifier.weight(1f), KeyboardType.Number)
                    Field("COP por día", price, { price = it.filter(Char::isDigit) }, Modifier.weight(1f), KeyboardType.Number)
                }
                Field("Ciudad", city, { city = it })
                Drop("Tipo", listOf("Aventura", "Deportiva", "Turismo", "Urbana").map { it to it }, cat, { cat = it })
                Field("Características (separadas por coma)", specs, { specs = it })
                SwitchRow("Reserva inmediata", "Sin aprobar cada solicitud", instant) { instant = it }
                SwitchRow("Entrega a domicilio", "", delivery) { delivery = it }
                // Sin SOAT y revisión técnico-mecánica vigentes la moto no puede circular (Ley 769 de 2002, arts. 42 y 50 a 52).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("SOAT hasta", soat, today(), Modifier.weight(1f)) { soat = it }
                    DateField("Revisión hasta", rtm, today(), Modifier.weight(1f)) { rtm = it }
                }
                Lbl("Moto nueva sin revisión todavía: pon la fecha en que le toca la primera.")
            } },
            confirmButton = { Btn("Guardar", enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && soat.isNotBlank() && rtm.isNotBlank()) {
                edit = null
                vm.act("Moto guardada") { vm.repo.saveBike(b.copy(name = name, cc = cc.toIntOrNull() ?: 0, pricePerDay = price.toLong(), city = city, category = cat,
                    specs = specs.split(",").map { it.trim() }.filter { it.isNotEmpty() }, instant = instant, delivery = delivery, soatUntil = soat, rtmUntil = rtm)) }
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
        list.forEachIndexed { i, r ->
            Card2(index = i) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${r.itemName} · ${r.fromName}", fontWeight = FontWeight.Bold); Sub("${prettyDate(r.dateFrom)} → ${prettyDate(r.dateTo)} · ${r.plan}") }
                    StatusPill(REQ_ST[r.status] ?: r.status, r.status)
                }
                if (r.extras.isNotEmpty()) Sub("Extras: ${r.extras.joinToString(", ")}")
                if (r.delivery) Sub("Con entrega a domicilio")
                if (r.licenseDeclared && r.status in listOf("p", "ok")) IconLine(Icons.Filled.Badge, "Declaró licencia ${r.licenseCat}. Verifícala antes de entregar la moto.", CyanDeep, bold = false)
                if (r.status == "can") Sub(if (r.cancelKind == "retracto") "El rider ejerció su derecho de retracto: devuélvele todo lo que haya pagado, en máximo 30 días." else "El rider canceló la solicitud.")
                Num("Total ${cop(r.total)}", OrangeDark, 16)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (r.status) {
                        "p" -> {
                            Btn("Aceptar", Modifier.weight(1f), Icons.Filled.Check) { vm.act("Solicitud aceptada") { vm.repo.setRequestStatus(r.id, "ok"); vm.repo.addBooking(r.itemId, r.dateFrom, r.dateTo) } }
                            Btn("Rechazar", Modifier.weight(1f), kind = BK.Danger) { vm.act { vm.repo.setRequestStatus(r.id, "no") } }
                        }
                        "ok" -> Btn("Entregué la moto", Modifier.weight(1f)) { vm.act { vm.repo.setRequestStatus(r.id, "ride") } }
                        "ride" -> Btn("Recibí la moto", Modifier.weight(1f)) { vm.act("Renta finalizada") { vm.repo.setRequestStatus(r.id, "fin") } }
                    }
                    if (r.status !in listOf("no", "can")) Btn("Chat", Modifier.weight(1f), Icons.Filled.Chat, kind = BK.Ghost) { nav.chat("req_${r.id}", r.fromName, r.fromId) }
                }
            }
        }
    }
}

/** Ingresos del dueño: sus rentas confirmadas. */
@Composable
fun EarningsScreen(vm: AppViewModel) {
    val reqs by vm.reqTo.collectAsState()
    val done = reqs.filter { it.type == "rent" && it.status in listOf("ok", "ride", "fin") }
    Screen {
        H("Ingresos")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(cop(done.sumOf { it.total }), "Rentas confirmadas", Modifier.weight(1.4f), Green, 0)
            StatCard("${done.size}", "Operaciones", Modifier.weight(1f), CyanDeep, 1)
        }
        if (done.isEmpty()) Empty("Todavía no tienes movimientos.")
        done.sortedByDescending { it.createdAt }.forEach { r ->
            Card2 { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(r.itemName, fontWeight = FontWeight.Bold); Sub("${r.fromName} · ${prettyDate(r.dateFrom)}") }
                Num(cop(r.total), Green, 15)
            } }
        }
    }
}
