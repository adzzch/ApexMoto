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
import androidx.compose.material.icons.automirrored.filled.Chat
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
import co.apexmoto.app.core.C
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
                Lbl("${agreementText(b.deposit, b.damageCap)}${if (b.minYears > 0) " · ${b.minYears}+ años manejando" else ""}")
                if (b.reviews > 0) RatingLine(b.rating, b.reviews)
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
        var minYears by remember(b.id) { mutableStateOf(if (b.minYears > 0) b.minYears.toString() else "") }
        var deposit by remember(b.id) { mutableStateOf(if (b.deposit > 0) b.deposit.toString() else "") }
        var cap by remember(b.id) { mutableStateOf(if (b.damageCap > 0) b.damageCap.toString() else "") }
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
                SwitchRow("Reserva inmediata", "Para riders con una renta terminada en ApexMoto; los demás te piden aprobación", instant) { instant = it }
                SwitchRow("Entrega a domicilio", "", delivery) { delivery = it }
                // Sin SOAT y revisión técnico-mecánica vigentes la moto no puede circular (Ley 769 de 2002, arts. 42 y 50 a 52).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("SOAT hasta", soat, today(), Modifier.weight(1f)) { soat = it }
                    DateField("Revisión hasta", rtm, today(), Modifier.weight(1f)) { rtm = it }
                }
                Lbl("Moto nueva sin revisión todavía: pon la fecha en que le toca la primera.")
                // Requisitos y acuerdo de daños: el rider los ve y los acepta al pedir la moto, y quedan copiados en su solicitud.
                H("Requisitos y acuerdo de daños", Icons.Filled.Handshake)
                Field("Años mínimos manejando (vacío: sin mínimo)", minYears, { minYears = it.filter(Char::isDigit).take(2) }, kb = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Depósito (COP)", deposit, { deposit = it.filter(Char::isDigit).take(8) }, Modifier.weight(1f), KeyboardType.Number)
                    Field("Tope por daños (COP)", cap, { cap = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), KeyboardType.Number)
                }
                Lbl("El depósito se devuelve al recibir la moto sin daños nuevos. El tope es lo máximo que el rider paga por daños (vacío: sin tope, responde según la ley). Es un acuerdo entre ustedes, no un seguro.")
            } },
            confirmButton = { Btn("Guardar", enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && soat.isNotBlank() && rtm.isNotBlank()) {
                edit = null
                vm.act("Moto guardada") { vm.repo.saveBike(b.copy(name = name, cc = cc.toIntOrNull() ?: 0, pricePerDay = price.toLong(), city = city, category = cat,
                    specs = specs.split(",").map { it.trim() }.filter { it.isNotEmpty() }, instant = instant, delivery = delivery, soatUntil = soat, rtmUntil = rtm,
                    minYears = (minYears.toIntOrNull() ?: 0).coerceIn(0, 30), deposit = (deposit.toLongOrNull() ?: 0L).coerceIn(0L, 20_000_000L),
                    damageCap = (cap.toLongOrNull() ?: 0L).coerceIn(0L, 200_000_000L))) }
            } },
            dismissButton = { TextButton({ edit = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun OwnerRequestsScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val reqs by vm.reqTo.collectAsState()
    val actas by vm.actas.collectAsState()
    val sos by vm.ownerSos.collectAsState()
    val reviews by vm.myReviews.collectAsState()
    var actaFor by remember { mutableStateOf<Pair<ServiceRequest, String>?>(null) }
    var cancelAt by remember { mutableStateOf<ServiceRequest?>(null) }
    var rate by remember { mutableStateOf<ServiceRequest?>(null) }
    actaFor?.let { (r, kind) -> ActaSheet(vm, r, kind) { actaFor = null } }
    cancelAt?.let { r -> OwnerCancelDialog(vm, r) { cancelAt = null } }
    rate?.let { r ->
        ReviewSheet("¿Cómo le fue a ${r.fromName} con tu moto?", { rate = null }) { stars, comment ->
            vm.act("¡Gracias! Tu reseña la ven solo los dueños y ${r.fromName}.") {
                vm.repo.submitReview(Review(kind = "renter", serviceId = r.id, targetType = "rider", targetId = r.fromId, authorId = u.id, authorName = u.name, stars = stars, comment = comment))
            }
        }
    }
    val list = reqs.filter { it.type == "rent" }.sortedByDescending { it.createdAt }
    val rated = reviews.map { it.id }.toSet()
    Screen {
        H("Solicitudes de renta")
        RentalSosAlert(sos, list, nav)
        if (list.isEmpty()) Empty("Cuando un rider pida tu moto, la solicitud aparecerá aquí.")
        list.forEachIndexed { i, r ->
            val entrega = actas.firstOrNull { it.id == "${r.id}_entrega" }
            val devolucion = actas.firstOrNull { it.id == "${r.id}_devolucion" }
            Card2(index = i) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${r.itemName} · ${r.fromName}", fontWeight = FontWeight.Bold); Sub("${prettyDate(r.dateFrom)} → ${prettyDate(r.dateTo)}${if (r.plan.isNotBlank()) " · ${r.plan}" else ""}") }
                    StatusPill(REQ_ST[r.status] ?: r.status, r.status)
                }
                if (r.status in listOf("p", "ok", "ride", "fin")) Steps(4, when (r.status) { "p" -> 1; "ok" -> 2; "ride" -> 3; else -> 4 })
                if (r.extras.isNotEmpty()) Sub("Extras: ${r.extras.joinToString(", ")}")
                if (r.delivery) Sub("Con entrega a domicilio")
                if (r.status in listOf("p", "ok", "ride")) RenterInfo(vm, r)
                if (r.damageAccepted) Lbl("Acuerdo aceptado: ${agreementText(r.deposit, r.damageCap)}")
                when (r.status) {
                    "ok" -> when {
                        entrega == null -> Sub("En la entrega: revisa la licencia física (categoría y vigencia), haz una prueba de manejo corta y llena el acta con ${r.fromName} al lado.")
                        !entrega.confirmed -> { ActaView(entrega); Sub("Esperando que ${r.fromName} confirme el acta en su app. Cuando la confirme, la renta empieza.") }
                    }
                    "ride" -> when {
                        devolucion == null -> Sub("Al recibir la moto, llena el acta de devolución con los daños nuevos, si los hay.")
                        !devolucion.confirmed -> { ActaView(devolucion); Sub("Esperando que ${r.fromName} confirme el acta. Cuando la confirme, la renta termina.") }
                    }
                    "fin" -> devolucion?.let { d ->
                        Sub(if (d.damageAmount > 0) "Daños anotados: ${cop(d.damageAmount)}. Devuelve el resto del depósito, si lo hubo." else "Sin daños nuevos: devuelve el depósito completo, si lo hubo.")
                    }
                    "no" -> Sub(C.OWNER_CANCEL.firstOrNull { it.first == r.ownerCancel }?.let { "Cancelaste en la entrega: ${it.second.lowercase()}." } ?: "Rechazaste la solicitud.")
                    "can" -> Sub(if (r.cancelKind == "retracto") "El rider ejerció su derecho de retracto: devuélvele todo lo que haya pagado, en máximo 30 días." else "El rider canceló la solicitud.")
                }
                Num("Total ${cop(r.total)}", OrangeDark, 16)
                when (r.status) {
                    "p" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Aceptar", Modifier.weight(1f), Icons.Filled.Check) { vm.act("Solicitud aceptada: las fechas quedaron apartadas") { vm.repo.acceptRequest(r) } }
                        Btn("Rechazar", Modifier.weight(1f), kind = BK.Danger) { vm.act("Solicitud rechazada") { vm.repo.rejectRequest(r) } }
                    }
                    "ok" -> {
                        if (entrega == null) Btn("Llenar acta de entrega", Modifier.fillMaxWidth(), Icons.Filled.Description) { actaFor = r to "entrega" }
                        else if (!entrega.confirmed) Btn("Corregir el acta", Modifier.fillMaxWidth(), Icons.Filled.Edit, kind = BK.Ghost, small = true) { vm.act("Acta borrada: llénala de nuevo") { vm.repo.deleteActa(entrega.id) } }
                        Btn("Cancelar la entrega", Modifier.fillMaxWidth(), Icons.Filled.Close, kind = BK.Danger, small = true) { cancelAt = r }
                    }
                    "ride" -> {
                        if (devolucion == null) Btn("Llenar acta de devolución", Modifier.fillMaxWidth(), Icons.Filled.Description) { actaFor = r to "devolucion" }
                        else if (!devolucion.confirmed) Btn("Corregir el acta", Modifier.fillMaxWidth(), Icons.Filled.Edit, kind = BK.Ghost, small = true) { vm.act("Acta borrada: llénala de nuevo") { vm.repo.deleteActa(devolucion.id) } }
                    }
                    "fin" -> if ("renter_${r.id}" !in rated) Btn("Calificar a ${r.fromName}", Modifier.fillMaxWidth(), Icons.Filled.Star, kind = BK.Ghost, small = true) { rate = r }
                }
                if (r.status !in listOf("no", "can")) Btn("Chat con ${r.fromName}", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Chat, kind = BK.Ghost, small = true) { nav.chat("req_${r.id}", r.fromName, r.fromId) }
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
