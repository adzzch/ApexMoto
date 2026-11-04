@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.apexmoto.app.core.C
import co.apexmoto.app.core.cop
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*
import kotlinx.coroutines.launch

/** Pide la ubicación actual y la entrega en callback (con permiso en tiempo de ejecución). */
@Composable
fun rememberLocationRequest(vm: AppViewModel, onResult: (Double, Double) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val fetch: () -> Unit = {
        scope.launch {
            val l = vm.container.location.last()
            if (l == null) vm.toast("No pude obtener tu ubicación. Activa el GPS e inténtalo de nuevo.") else onResult(l.latitude, l.longitude)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { fetch() }
    return {
        if (vm.container.location.hasPermission()) fetch()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

@Composable
fun WorkshopScreen(vm: AppViewModel, u: AppUser) {
    val all by vm.sos.collectAsState()
    val mine = all.filter { it.fromId == u.id }.sortedByDescending { it.createdAt }
    val current = mine.firstOrNull { it.status in listOf("new", "ok", "go") }
    var kind by remember { mutableStateOf("flat") }
    var place by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(0.0) }
    var lng by remember { mutableStateOf(0.0) }
    val getLoc = rememberLocationRequest(vm) { a, b -> lat = a; lng = b; if (place.isBlank()) place = "Mi ubicación actual" }

    Screen {
        if (current != null) {
            Card2(border = Orange) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("S.O.S · ${current.kindName}", style = MaterialTheme.typography.titleMedium); Sub(current.locationText) }
                    StatusPill(SOS_ST[current.status] ?: current.status, current.status)
                }
                if (current.status == "new") Sub("Estamos avisando a los talleres cercanos…")
                else Text("${current.providerName} llega en ${current.eta} min", fontWeight = FontWeight.Bold)
                Text("Tarifa estimada ${cop(current.price)}", color = Orange, fontWeight = FontWeight.Bold)
                Btn("Cancelar S.O.S.", Modifier.fillMaxWidth(), kind = BK.Danger) { vm.act("S.O.S. cancelado") { vm.repo.setSosStatus(current.id, "no") } }
            }
        } else {
            Card2 {
                H("Taller móvil S.O.S."); Sub("Cuéntanos qué pasó y dónde estás.")
                Drop("¿Qué pasó?", C.SOS_KINDS.map { it.key to "${it.name} · ${cop(it.price)}" }, kind, { kind = it })
                Field("¿Dónde estás?", place, { place = it }, hint = "Km 12 vía al mar, junto a la gasolinera")
                Btn(if (lat != 0.0) "Ubicación fijada ✓" else "Usar mi ubicación", Modifier.fillMaxWidth(), Icons.Filled.MyLocation, kind = BK.Ghost) { getLoc() }
                Field("Detalle (opcional)", note, { note = it }, lines = 2)
                Btn("Pedir ayuda", Modifier.fillMaxWidth(), Icons.Filled.Warning, enabled = place.isNotBlank()) {
                    val k = C.SOS_KINDS.first { it.key == kind }
                    vm.act("S.O.S. enviado") { vm.repo.createSos(Sos(fromId = u.id, fromName = u.name, kind = k.key, kindName = k.name, locationText = place, lat = lat, lng = lng, note = note, price = k.price)) }
                    place = ""; note = ""; lat = 0.0; lng = 0.0
                }
            }
        }
        Card2 {
            H("Escoba para rodadas")
            Sub("Un vehículo de apoyo acompaña tu grupo durante la ruta. Pídelo al organizar tu rodada.")
        }
        val past = mine.filter { it.status in listOf("fin", "no") }
        if (past.isNotEmpty()) {
            H("Historial")
            past.take(5).forEach { s -> Card2 { Row(verticalAlignment = Alignment.CenterVertically) { Text("${s.kindName} · ${cop(s.price)}", Modifier.weight(1f)); StatusPill(SOS_ST[s.status] ?: s.status, s.status) } } }
        }
    }
}
