@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.saveable.rememberSaveable
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
    var asking by rememberSaveable { mutableStateOf(false) }
    var kind by remember { mutableStateOf("flat") }
    var place by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(0.0) }
    var lng by remember { mutableStateOf(0.0) }
    val getLoc = rememberLocationRequest(vm) { a, b -> lat = a; lng = b; if (place.isBlank()) place = "Mi ubicación actual" }

    Screen {
        if (current != null) {
            Card2(border = RedSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(RedSoft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Build, null, tint = Red) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { H("S.O.S · ${current.kindName}"); Lbl(current.locationText) }
                }
                Steps(3, when (current.status) { "new" -> 1; "ok" -> 2; else -> 3 })
                Row { StatusPill(SOS_ST[current.status] ?: current.status, current.status) }
                if (current.status == "new") Row(verticalAlignment = Alignment.CenterVertically) { Ping(); Spacer(Modifier.width(8.dp)); Sub("Estamos avisando a los talleres cercanos…") }
                else Text("${current.providerName} llega en ${current.eta} min", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Tarifa estimada", modifier = Modifier.weight(1f)); Num(cop(current.price), OrangeDark, 18) }
                Btn("Cancelar S.O.S.", Modifier.fillMaxWidth(), Icons.Filled.Close, kind = BK.Danger) { vm.act("S.O.S. cancelado") { vm.repo.setSosStatus(current.id, "no") } }
            }
        } else {
            Card2 {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(64.dp).glow(Red, CircleShape, elevation = 10.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Build, null, Modifier.size(30.dp), tint = Color.White)
                    }
                    Text("¿Varado en la ruta?", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("Un taller móvil llega hasta donde estés.", color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                AnimatedVisibility(!asking) { Btn("Pedir taller móvil", Modifier.fillMaxWidth(), Icons.Filled.Warning, kind = BK.Sos) { asking = true } }
                AnimatedVisibility(asking, enter = expandVertically(tween(320, easing = EaseOutExpo)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Lbl("¿Qué pasó?")
                        C.SOS_KINDS.chunked(2).forEach { pair ->
                            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { k ->
                                    OptionTile(kind == k.key, { kind = k.key }, Modifier.weight(1f).fillMaxHeight()) {
                                        Text(k.name, fontWeight = FontWeight.Bold, fontSize = 14.sp); Num(cop(k.price), Muted, 13)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Field("¿Dónde estás?", place, { place = it }, hint = "Km 12 vía al mar, junto a la gasolinera")
                        Btn(if (lat != 0.0) "Ubicación fijada" else "Usar mi ubicación", Modifier.fillMaxWidth(), if (lat != 0.0) Icons.Filled.CheckCircle else Icons.Filled.MyLocation, kind = BK.Ghost) { getLoc() }
                        Field("Detalle (opcional)", note, { note = it }, lines = 2, hint = "Ej: llanta trasera, tengo repuesto")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Cancelar", Modifier.weight(0.7f), kind = BK.Ghost) { asking = false }
                            Btn("Enviar S.O.S.", Modifier.weight(1f), Icons.Filled.Warning, kind = BK.Sos, enabled = place.isNotBlank()) {
                                val k = C.SOS_KINDS.first { it.key == kind }
                                vm.act("S.O.S. enviado") { vm.repo.createSos(Sos(fromId = u.id, fromName = u.name, kind = k.key, kindName = k.name, locationText = place, lat = lat, lng = lng, note = note, price = k.price)) }
                                place = ""; note = ""; lat = 0.0; lng = 0.0; asking = false
                            }
                        }
                    }
                }
            }
        }
        Card2(index = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { H("Coche escoba", Icons.Filled.LocalShipping) }
                Pill("Para rodadas", CyanSoft, CyanDeep)
            }
            Text("Un taller móvil acompaña a tu grupo durante toda la ruta: despinchado, guayas, batería y remolque. Pídelo al organizar tu rodada.",
                color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        val past = mine.filter { it.status in listOf("fin", "no") }
        if (past.isNotEmpty()) {
            H("Historial", Icons.Filled.History)
            past.take(5).forEachIndexed { i, s ->
                Card2(index = i + 2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(s.kindName, fontWeight = FontWeight.Bold); Lbl(s.locationText) }
                        Num(cop(s.price), Ink, 14); Spacer(Modifier.width(8.dp)); StatusPill(SOS_ST[s.status] ?: s.status, s.status)
                    }
                }
            }
        }
    }
}
