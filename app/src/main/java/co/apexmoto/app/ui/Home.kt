package co.apexmoto.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime

/**
 * Inicio del rider, de arriba abajo: dónde está (con la opción de cambiar de ciudad), "¿A dónde vamos?" (se escribe
 * ahí mismo y la IA responde con la ruta debajo; Explorar abre su vista) y unas pocas rutas recomendadas.
 * El saludo va en el encabezado.
 */
@Composable
fun ColumnScope.HomeSection(vm: AppViewModel, nav: NavController, u: AppUser, onGo: (String) -> Unit) {
    val cp = rememberCatalogAndPlace(vm)
    // 1. Dónde está
    if (cp == null) LoadingCard("Buscando dónde estás…") else CityBar(vm, cp.first, cp.second)

    // 2. ¿A dónde vamos? Se escribe aquí mismo y la IA responde con la ruta debajo. Explorar abre su vista aparte.
    val venues by vm.venues.collectAsState()
    val ref = rememberRefPoint(vm)
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<co.apexmoto.app.maps.RoutePlanResult?>(null) }
    var note by remember { mutableStateOf<co.apexmoto.app.maps.GeminiPlanner.Plan?>(null) }
    val focus = LocalFocusManager.current
    fun ask() {
        if (q.isBlank() || busy) return
        focus.clearFocus()
        val f = co.apexmoto.app.maps.RouteFilters(q, "any", "07:00", false, "m", true, emptySet(), emptySet(), 60000L)
        // Desde la ciudad elegida en Inicio (o el GPS si la persona lo usó).
        val from = cp?.second?.let { co.apexmoto.app.maps.LatLng(it.lat, it.lng) } ?: ref
        requestAiRoute(vm, scope, cp?.first, from, venues, f, onStart = { busy = true; note = null }) { r, n, _ -> result = r; note = n; busy = false }
    }
    Card2 {
        Text("¿A dónde vamos?", fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Surf0).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(20.dp), tint = Orange)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                q, { q = it.take(co.apexmoto.app.maps.AiGuard.MAX_INPUT) }, Modifier.weight(1f).padding(vertical = 16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink), cursorBrush = SolidColor(Orange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { ask() }),
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Ej: un mirador y almorzar trucha", color = Muted, style = MaterialTheme.typography.bodyLarge); inner() } },
            )
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (q.isNotBlank()) Orange else Surf4).clickable(enabled = q.isNotBlank() && !busy, onClickLabel = "Armar ruta") { ask() },
                contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, "Armar ruta", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Orange, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
            Text("Armando tu ruta con lugares reales…", color = OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else Text("Escríbelo como se lo dirías a un amigo: la IA te arma la ruta con lugares reales. No escribas datos personales.",
            color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        HorizontalDivider(color = Surf3)
        // Explorar: la otra forma de elegir, en su propia vista.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Explorar") { onGo("exp") }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(CyanSoft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Explore, null, tint = CyanDeep) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Explorar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Lbl("Lugares y rodadas famosas cerca")
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
        }
    }
    // La respuesta de la IA, aquí mismo.
    result?.let { r ->
        AiRouteCard(vm, nav, u, r, note, "07:00")
        TextButton({ result = null; note = null; q = "" }, Modifier.align(Alignment.CenterHorizontally)) { Text("Armar otra ruta", color = CyanDeep, fontWeight = FontWeight.Bold) }
    }

    // 3. Rutas recomendadas: solo unas pocas, las que salen más cerca.
    if (cp != null) {
        val (c, p) = cp
        var open by remember { mutableStateOf<FamousRide?>(null) }
        var preset by remember { mutableStateOf<RodadaPreset?>(null) }
        val rides = rememberInBackground(c, p.lat, p.lng, initial = emptyList<Near<FamousRide>>()) { c.ridesNear(p.lat, p.lng).take(3) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { H("Rutas recomendadas", Icons.Filled.ThumbUp) }
            TextButton({ onGo("exp") }, contentPadding = EdgeLink) { Text("Ver más", color = CyanDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        rides.forEachIndexed { i, n -> RideRow(n, i) { open = n.item } }
        open?.let { r -> RideSheetFor(vm, nav, r, p.lat, p.lng, onDismiss = { open = null }) { preset = it } }
        preset?.let { pr -> CreateRodadaSheet(vm, nav, u, onDismiss = { preset = null }, preset = pr) }
    }
}

// ───────────────── Saludo ─────────────────
/** "Alejandro" de "Alejandro Zamora". */
fun firstName(name: String) = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

/** Línea bajo el saludo según la hora y el rol. Cambia cada día, pero no mientras se usa la app. */
fun greetingLine(role: String, now: LocalTime = LocalTime.now(), day: LocalDate = LocalDate.now()): String {
    val part = when (now.hour) { in 5..11 -> 0; in 12..17 -> 1; else -> 2 }
    val opts: List<String> = when (role) {
        Role.SHOP -> listOf(
            listOf("Buenos días. Que hoy lleguen muchos riders.", "Buen día. Tu negocio ya está en la ruta."),
            listOf("Buenas tardes. Hora pico de rodadas.", "Buenas tardes. Revisa si hay pedidos nuevos."),
            listOf("Buenas noches. Deja listo el menú de mañana.", "Buenas noches. Buen trabajo hoy."),
        )[part]
        Role.WORKSHOP -> listOf(
            listOf("Buenos días. Hay riders contando contigo en la vía.", "Buen día. Listos para cualquier varada."),
            listOf("Buenas tardes. La vía está movida.", "Buenas tardes. Tus servicios ya están a la vista."),
            listOf("Buenas noches. Gracias por cuidar a los riders.", "Buenas noches. Mañana hay más ruta."),
        )[part]
        Role.OWNER -> listOf(
            listOf("Buenos días. Tus motos listas para rodar.", "Buen día. Revisa si hay solicitudes nuevas."),
            listOf("Buenas tardes. Así va tu flota.", "Buenas tardes. Un rider puede estar buscando tu moto."),
            listOf("Buenas noches. Tu flota descansa.", "Buenas noches. Mañana más kilómetros."),
        )[part]
        else -> listOf(
            listOf("Buenos días. Buen día para rodar.", "Buen día. ¿Para dónde salimos hoy?"),
            listOf("Buenas tardes. La vía te espera.", "Buenas tardes. ¿Una rodada corta?"),
            listOf("Buenas noches. Planea la rodada de mañana.", "Buenas noches. Descansa, mañana hay ruta."),
        )[part]
    }
    return opts[day.dayOfYear % opts.size]
}
