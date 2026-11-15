@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import co.apexmoto.app.BuildConfig
import co.apexmoto.app.core.cop
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*

@Composable
fun ProfileScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val orders by vm.myOrders.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val myReviews by vm.myReviews.collectAsState()
    var reviewOrder by remember { mutableStateOf<Order?>(null) }
    reviewOrder?.let { o ->
        ReviewSheet("¿Cómo te fue en ${o.venueName}?", { reviewOrder = null }) { stars, comment ->
            vm.act("¡Gracias por tu reseña!") { vm.repo.submitReview(Review(kind = "order", serviceId = o.id, targetType = "venue", targetId = o.venueId,
                authorId = u.id, authorName = u.name, stars = stars, comment = comment)) }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("¿Reiniciar los datos demo?") },
        text = { Text("Se borran los comercios, motos y rodadas de demostración (los que no tienen dueño) y se cargan de nuevo sin repetidos. Lo creado por usuarios reales no se toca.") },
        confirmButton = { TextButton({ confirmReset = false; vm.act("Datos demo reiniciados") { vm.repo.resetDemo() } }) { Text("Reiniciar") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancelar") } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("¿Borrar los datos demo?") },
        text = { Text("Se borran los comercios, motos y rodadas de demostración (los que no tienen dueño) y la app queda solo con lo que crearon ustedes. Puedes volver a cargarlos cuando quieras.") },
        confirmButton = { TextButton({ confirmClear = false; vm.act("Datos demo borrados") { vm.repo.clearDemo() } }) { Text("Borrar") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancelar") } },
    )
    val profile by vm.myProfile.collectAsState()
    Screen {
        Card2(index = 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val p = profile
                if (p != null) MotoAvatar(p.moto, p.color, 60.dp) else Avatar(u.name, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(u.name, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                    if (p != null) Lbl("@${p.username}", CyanDeep)
                    Pill(Role.label(u.role), OrangeSoft, OrangeDark)
                    if (u.email.isNotBlank()) Lbl(u.email)
                }
            }
        }
        if (u.role == Role.RIDER) Card2 {
            val p = profile
            if (p == null) {
                IconLine(Icons.Filled.People, "Crea tu perfil de rider para agregar amigos y unirte al mapa de las rodadas.", CyanDeep, bold = false)
                Btn("Crear mi perfil de rider", Modifier.fillMaxWidth(), Icons.Filled.Badge, kind = BK.Ghost) { vm.communityTab = "amigos"; nav.navigate("grp") }
            } else {
                SwitchRow("Mostrar mi estado a mis amigos", "En línea o en rodada. Nadie más lo ve.", p.showStatus) { on -> vm.act { vm.repo.setShowStatus(p, on) } }
                Btn("Mis amigos", Modifier.fillMaxWidth(), Icons.Filled.People, kind = BK.Ghost) { vm.communityTab = "amigos"; nav.navigate("grp") }
            }
        }
        if (u.role == Role.RIDER) RiderProfileCard(vm, u)
        if (orders.isNotEmpty()) {
            H("Mis pedidos")
            val reviewed = myReviews.map { it.id }.toSet()
            orders.sortedByDescending { it.createdAt }.take(6).forEach { o ->
                Card2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(o.venueName, fontWeight = FontWeight.Bold); Sub("${cop(o.total)} · ${o.items.sumOf { it.qty }} ítems") }
                        StatusPill(ORD_ST[o.status] ?: o.status, o.status)
                    }
                    if (o.status == "done" && "order_${o.id}" !in reviewed) Btn("Calificar", Modifier.fillMaxWidth(), Icons.Filled.Star, kind = BK.Ghost, small = true) { reviewOrder = o }
                }
            }
        }
        MyRentals(vm, nav, u)
        Btn("Legal y privacidad", Modifier.fillMaxWidth(), Icons.Filled.Gavel, kind = BK.Ghost) { nav.navigate("legal") }
        Btn("Configuración", Modifier.fillMaxWidth(), Icons.Filled.Settings, kind = BK.Ghost) { nav.navigate("settings") }
        // Los datos demo solo existen en la versión de pruebas, y las reglas solo se los permiten a un administrador.
        if (BuildConfig.PRUEBAS) Card2(tone = Surf1) {
            H("Datos demo", Icons.Filled.Science)
            Sub("Comercios, motos y rodadas de ejemplo para presentar la app. Solo un administrador puede cargarlos o borrarlos.")
            Btn("Cargar datos demo", Modifier.fillMaxWidth(), Icons.Filled.CloudUpload, kind = BK.Ghost) { vm.act("Datos demo cargados") { vm.repo.seedDemo() } }
            Btn("Reiniciar datos demo (sin repetidos)", Modifier.fillMaxWidth(), Icons.Filled.Refresh, kind = BK.Ghost) { confirmReset = true }
            Btn("Borrar datos demo", Modifier.fillMaxWidth(), Icons.Filled.DeleteSweep, kind = BK.Danger) { confirmClear = true }
        }
        Btn("Cerrar sesión", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Logout, kind = BK.Danger) { vm.logout() }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val key = BuildConfig.ORS_API_KEY
    Screen {
        // Diagnóstico de la clave: solo en la versión de pruebas. En la app real no se muestra nada de la clave.
        if (BuildConfig.PRUEBAS) Card2(border = if (key.isBlank()) RedSoft else GreenSoft) {
            H("Clave de OpenRouteService")
            if (key.isBlank()) {
                IconLine(Icons.Filled.ErrorOutline, "No hay clave configurada.", Red)
                Sub("Los mapas funcionan, pero las rutas y la guía necesitan una clave gratuita. Regístrate en openrouteservice.org, crea un token y agrega ORS_API_KEY=tu_clave en el archivo local.properties del proyecto; luego sincroniza y vuelve a compilar.")
            } else {
                IconLine(Icons.Filled.CheckCircle, "Configurada (…${key.takeLast(4)})", Green)
                Sub("Se usa para calcular rutas e instrucciones giro a giro. Los mapas vienen de OpenStreetMap.")
            }
        }
        Card2 {
            H("Estilo del mapa")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Estándar", vm.mapType != 3) { vm.updateMapType(1) }
                Chip("Topográfico", vm.mapType == 3) { vm.updateMapType(3) }
            }
            SwitchRow("Guía por voz", "Lee en voz alta cada instrucción mientras manejas", vm.voice) { vm.updateVoice(it) }
        }
        Sub("ApexMoto ${BuildConfig.VERSION_NAME}")
    }
}

@Composable
fun ChatScreen(vm: AppViewModel, u: AppUser, thread: String, to: String) {
    val flow = remember(thread) { vm.repo.messages(thread, u.id) }
    val msgs by flow.collectAsState(initial = emptyList())
    val list = msgs.sortedBy { it.createdAt }
    var text by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    LaunchedEffect(list.size) { if (list.isNotEmpty()) state.animateScrollToItem(list.lastIndex) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = state, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            if (list.isEmpty()) item { Sub("Todavía no hay mensajes. Escribe el primero.") }
            items(list, key = { it.id }) { m ->
                val mine = m.fromId == u.id
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    val shape = if (mine) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                    Column(Modifier.reveal(0).widthIn(max = 290.dp).clip(shape).background(if (mine) Orange else Color.White).padding(horizontal = 12.dp, vertical = 9.dp)) {
                        if (!mine) Text(m.fromName, color = CyanDeep, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                        Text(m.text, color = if (mine) Color.White else Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        BottomBar {
            OutlinedTextField(text, { text = it.take(1000) }, Modifier.weight(1f), placeholder = { Text("Escribe un mensaje", color = Muted) }, maxLines = 3,
                shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surf0, unfocusedContainerColor = Surf0, focusedBorderColor = CyanDeep, unfocusedBorderColor = Color.Transparent, cursorColor = Orange))
            Box(Modifier.size(48.dp).glow(Orange, CircleShape, on = text.isNotBlank(), elevation = 8.dp).clip(CircleShape).background(if (text.isNotBlank()) Orange else Surf4)
                .clickable(onClickLabel = "Enviar") { val t = text; if (t.isNotBlank()) { text = ""; vm.act { vm.repo.send(thread, u, to, t) } } }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White)
            }
        }
    }
}
