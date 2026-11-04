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
import co.apexmoto.app.core.C
import co.apexmoto.app.core.cop
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*
import co.apexmoto.app.maps.LatLng
import co.apexmoto.app.maps.MapMarker
import co.apexmoto.app.maps.OsmMap

/** Comercio del usuario (el primero cuyo ownerId coincide). */
@Composable
private fun myVenue(vm: AppViewModel, u: AppUser): Venue? { val vs by vm.venues.collectAsState(); return vs.firstOrNull { it.ownerId == u.id } }

@Composable
fun ShopDashboard(vm: AppViewModel, u: AppUser) {
    val v = myVenue(vm, u)
    val orders by vm.shopOrders.collectAsState()
    val coupons by vm.shopCoupons.collectAsState()
    Screen {
        if (v == null) { Empty("Cargando tu negocio…"); return@Screen }
        H(v.name)
        SwitchRow(if (v.open) "Abierto" else "Cerrado", "Los riders ven tu estado en tiempo real", v.open) { on -> vm.act { vm.repo.saveVenue(v.copy(open = on)) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${orders.count { it.status == "new" }}", "pedidos nuevos", Modifier.weight(1f)); Stat("${v.visits}", "visitas (Ir)", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(cop(orders.filter { it.status == "done" }.sumOf { it.total }), "vendido", Modifier.weight(1f)); Stat("${coupons.count { it.used }}/${coupons.size}", "cupones canjeados", Modifier.weight(1f))
        }
    }
}

@Composable
fun ShopOrdersScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val v = myVenue(vm, u)
    val orders by vm.shopOrders.collectAsState()
    val sos by vm.sos.collectAsState()
    val next = mapOf("new" to ("prep" to "Aceptar y preparar"), "prep" to ("ready" to "Marcar listo"), "ready" to ("done" to "Entregado"))
    Screen {
        if (v != null && "moto" in v.types) {
            val open = sos.filter { it.status == "new" || (it.providerId == u.id && it.status in listOf("ok", "go")) }
            H("S.O.S. cercanos")
            if (open.isEmpty()) Sub("No hay emergencias activas.")
            open.forEach { s ->
                Card2(border = Red) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("${s.kindName} · ${s.fromName}", fontWeight = FontWeight.Bold); Sub(s.locationText) }
                        StatusPill(SOS_ST[s.status] ?: s.status, s.status)
                    }
                    Text("Tarifa ${cop(s.price)}", color = Orange, fontWeight = FontWeight.Bold)
                    when (s.status) {
                        "new" -> Btn("Aceptar (llego en 20 min)", Modifier.fillMaxWidth(), Icons.Filled.Check) { vm.act("S.O.S. aceptado") { vm.repo.acceptSos(s.id, u.id, v.name, 20) } }
                        "ok" -> Btn("Voy en camino", Modifier.fillMaxWidth(), Icons.Filled.Navigation) { vm.act { vm.repo.setSosStatus(s.id, "go") } }
                        else -> Btn("Marcar resuelto", Modifier.fillMaxWidth(), Icons.Filled.Check) { vm.act("S.O.S. resuelto") { vm.repo.setSosStatus(s.id, "fin") } }
                    }
                }
            }
        }
        H("Pedidos")
        if (orders.isEmpty()) Empty("Cuando un rider haga un pedido aparecerá aquí.")
        orders.sortedByDescending { it.createdAt }.forEach { o ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(o.fromName, fontWeight = FontWeight.Bold); Sub("Llega en ${o.eta}") }
                    StatusPill(ORD_ST[o.status] ?: o.status, o.status)
                }
                o.items.forEach { Sub("${it.qty} × ${it.name} · ${cop(it.qty * it.price)}") }
                if (o.note.isNotBlank()) Sub("Nota: ${o.note}")
                Text("Total ${cop(o.total)}", fontWeight = FontWeight.Bold, color = Orange)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    next[o.status]?.let { (st, label) -> Btn(label, Modifier.weight(1f)) { vm.act { vm.repo.setOrderStatus(o.id, st) } } }
                    if (o.status == "new") Btn("Rechazar", Modifier.weight(1f), kind = BK.Danger) { vm.act { vm.repo.setOrderStatus(o.id, "no") } }
                    if (o.status in listOf("prep", "ready")) Btn("Chat", Modifier.weight(1f), Icons.Filled.Chat, kind = BK.Ghost) { nav.chat("ord_${o.id}", o.fromName, o.fromId) }
                }
            }
        }
    }
}

@Composable
fun ShopMenuScreen(vm: AppViewModel, u: AppUser) {
    val v = myVenue(vm, u)
    var edit by remember { mutableStateOf<MenuItem?>(null) }
    Screen {
        if (v == null) { Empty("Cargando tu negocio…"); return@Screen }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { H("Mi menú"); Sub("Lo que ven los riders al pedir") }
            Btn("Agregar", icon = Icons.Filled.Add) { edit = MenuItem(id = "m" + System.currentTimeMillis()) }
        }
        if (v.menu.isEmpty()) Empty("Agrega tu primer plato o servicio.")
        v.menu.forEach { m ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(m.name, fontWeight = FontWeight.Bold); Sub("${m.cat} · ${cop(m.price)}") }
                    Switch(m.on, { on -> vm.act { vm.repo.setMenu(v.id, v.menu.map { if (it.id == m.id) it.copy(on = on) else it }) } })
                    IconButton({ edit = m }) { Icon(Icons.Filled.Edit, "Editar") }
                    IconButton({ vm.act { vm.repo.setMenu(v.id, v.menu.filter { it.id != m.id }) } }) { Icon(Icons.Filled.Delete, "Eliminar") }
                }
            }
        }
        edit?.let { m ->
            var name by remember(m.id) { mutableStateOf(m.name) }; var desc by remember(m.id) { mutableStateOf(m.desc) }
            var price by remember(m.id) { mutableStateOf(if (m.price > 0) m.price.toString() else "") }; var cat by remember(m.id) { mutableStateOf(m.cat) }
            AlertDialog(
                onDismissRequest = { edit = null }, title = { Text("Plato o servicio") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Nombre", name, { name = it }); Field("Descripción", desc, { desc = it })
                    Field("Precio (COP)", price, { price = it.filter(Char::isDigit) }, kb = KeyboardType.Number)
                    Drop("Categoría", C.MENU_CATS.map { it to it }, cat, { cat = it })
                } },
                confirmButton = { Btn("Guardar", enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0) {
                    val nm = m.copy(name = name, desc = desc, price = price.toLong(), cat = cat)
                    val list = if (v.menu.any { it.id == m.id }) v.menu.map { if (it.id == m.id) nm else it } else v.menu + nm
                    edit = null; vm.act("Menú actualizado") { vm.repo.setMenu(v.id, list) }
                } },
                dismissButton = { TextButton({ edit = null }) { Text("Cancelar") } },
            )
        }
    }
}

@Composable
fun ShopBusinessScreen(vm: AppViewModel, u: AppUser) {
    val v = myVenue(vm, u)
    if (v == null) { Screen { Empty("Cargando tu negocio…") }; return }
    var name by remember(v.id) { mutableStateOf(v.name) }; var desc by remember(v.id) { mutableStateOf(v.desc) }
    var promo by remember(v.id) { mutableStateOf(v.promo) }; var cor by remember(v.id) { mutableStateOf(v.corridor) }
    var km by remember(v.id) { mutableStateOf(v.km.toString()) }; var types by remember(v.id) { mutableStateOf(v.types.toSet()) }
    var lat by remember(v.id) { mutableStateOf(v.lat) }; var lng by remember(v.id) { mutableStateOf(v.lng) }
    val getLoc = rememberLocationRequest(vm) { a, b -> lat = a; lng = b }
    Screen {
        H("Mi negocio")
        Field("Nombre", name, { name = it }); Field("Descripción", desc, { desc = it }, lines = 2); Field("Promoción para riders", promo, { promo = it })
        Drop("Corredor", C.CORRIDORS.map { it.key to it.name }, cor, { cor = it })
        Field("Distancia desde Cali (km)", km, { km = it.filter(Char::isDigit) }, kb = KeyboardType.Number)
        Lbl("Tipo de negocio")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { C.CATS.forEach { (k, c) -> Chip(c.first, k in types) { types = if (k in types) types - k else types + k } } }
        Card2 {
            H("Ubicación en el mapa"); Sub("Toca el mapa para fijar el punto. Los riders usarán esta ubicación para la guía.")
            val center = if (lat != 0.0) LatLng(lat, lng) else C.corridor(cor).let { LatLng(it.lat, it.lng) }
            key(v.id) {
                OsmMap(
                    Modifier.fillMaxWidth().height(240.dp), center = center, zoom = 13.0, mapType = vm.mapType,
                    markers = if (lat != 0.0) listOf(MapMarker("me", LatLng(lat, lng), name)) else emptyList(),
                    onTap = { p -> lat = p.latitude; lng = p.longitude },
                )
            }
            Btn("Usar mi ubicación actual", Modifier.fillMaxWidth(), Icons.Filled.MyLocation, kind = BK.Ghost) { getLoc() }
            if (lat != 0.0) Lbl("%.5f, %.5f".format(lat, lng))
        }
        Btn("Guardar cambios", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = name.isNotBlank() && types.isNotEmpty()) {
            val first = C.CATS[types.first()]!!
            vm.act("Negocio actualizado") { vm.repo.saveVenue(v.copy(name = name, desc = desc, promo = promo, corridor = cor, km = km.toIntOrNull() ?: v.km,
                types = types.toList(), badge = first.second, kind = first.third, lat = lat, lng = lng)) }
        }
    }
}

@Composable
fun ShopRedeemScreen(vm: AppViewModel, u: AppUser) {
    val coupons by vm.shopCoupons.collectAsState()
    var code by remember { mutableStateOf("") }
    Screen {
        Card2 {
            H("Canjear cupón"); Sub("Pídele al rider su código APX-XXXX.")
            Field("Código", code, { code = it.uppercase() }, hint = "APX-AB12")
            Btn("Canjear", Modifier.fillMaxWidth(), Icons.Filled.QrCode, enabled = code.length >= 4) {
                vm.act { val who = vm.repo.redeem(code, u.id); code = ""; vm.toast("Cupón canjeado para $who") }
            }
        }
        H("Historial")
        if (coupons.isEmpty()) Empty("Aún no se han generado cupones para tu negocio.")
        coupons.sortedByDescending { it.createdAt }.forEach { c ->
            Card2 { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(c.code, fontFamily = Mono, fontWeight = FontWeight.Bold); Sub(c.userName) }
                if (c.used) Pill("Canjeado", androidx.compose.ui.graphics.Color(0x2E1B8A3A), Green) else Pill("Disponible")
            } }
        }
    }
}
