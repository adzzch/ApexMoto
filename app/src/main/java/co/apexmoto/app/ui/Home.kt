package co.apexmoto.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime

/**
 * Inicio del rider, de arriba abajo: dónde está (con la opción de cambiar de ciudad), la pregunta "¿A dónde vamos?"
 * con solo dos caminos (Ruta con IA o Explorar) y unas pocas rutas recomendadas. El saludo va en el encabezado.
 */
@Composable
fun ColumnScope.HomeSection(vm: AppViewModel, nav: NavController, u: AppUser, onGo: (String) -> Unit) {
    val cp = rememberCatalogAndPlace(vm)
    // 1. Dónde está
    if (cp == null) LoadingCard("Buscando dónde estás…") else CityBar(vm, cp.first, cp.second)

    // 2. ¿A dónde vamos?
    Card2(padding = 18.dp) {
        Text("¿A dónde vamos?", fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp)
        Sub("Elige cómo quieres armar tu salida.")
        Spacer(Modifier.height(4.dp))
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WhereTile("Ruta con IA", "Dime qué se te antoja y te la armo", Icons.Filled.AutoAwesome, primary = true, Modifier.weight(1f).fillMaxHeight()) { onGo("ia") }
            WhereTile("Explorar", "Lugares y rodadas famosas cerca", Icons.Filled.Explore, primary = false, Modifier.weight(1f).fillMaxHeight()) { onGo("exp") }
        }
    }

    // 3. Rutas recomendadas: solo unas pocas, las que salen más cerca.
    if (cp != null) {
        val (c, p) = cp
        var open by remember { mutableStateOf<FamousRide?>(null) }
        var preset by remember { mutableStateOf<RodadaPreset?>(null) }
        val rides = rememberInBackground(c, p.lat, p.lng, initial = emptyList<Near<FamousRide>>()) { c.ridesNear(p.lat, p.lng).take(3) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { H("Rutas recomendadas", Icons.Filled.ThumbUp) }
            TextButton({ onGo("exp") }) { Text("Ver más", color = CyanDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        rides.forEachIndexed { i, n -> RideRow(n, i) { open = n.item } }
        open?.let { r -> RideSheetFor(vm, nav, r, p.lat, p.lng, onDismiss = { open = null }) { preset = it } }
        preset?.let { pr -> CreateRodadaSheet(vm, nav, u, onDismiss = { preset = null }, preset = pr) }
    }
}

/** Una de las dos opciones de "¿A dónde vamos?". La de IA va en naranja: es la acción principal. */
@Composable
private fun WhereTile(title: String, sub: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val src = remember { MutableInteractionSource() }
    var m = modifier.pressScale(src).clip(shape)
    m = if (primary) m.background(Brush.linearGradient(listOf(Orange, OrangeDark))) else m.background(Surf0).border(1.5.dp, Surf3, shape)
    Column(
        m.clickable(src, null, onClickLabel = title, onClick = onClick).padding(14.dp).heightIn(min = 112.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (primary) Color.White.copy(alpha = 0.22f) else CyanSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) Color.White else CyanDeep)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = if (primary) Color.White else Ink, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(sub, color = if (primary) Color.White.copy(alpha = 0.9f) else Muted, fontSize = 12.sp, lineHeight = 15.sp)
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
