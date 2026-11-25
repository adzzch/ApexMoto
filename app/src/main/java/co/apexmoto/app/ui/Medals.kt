@file:OptIn(ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Medallas por rutas completadas con el guiado: de principiante (5) a rutero experimentado (50). */
/** [short]: nombre que cabe debajo de la insignia; [name]: el rango completo. */
data class Medal(val routes: Int, val name: String, val icon: ImageVector, val color: Color, val short: String = name)

val MEDALS = listOf(
    Medal(5, "Principiante", Icons.Filled.TwoWheeler, Color(0xFF8D6E63)),
    Medal(10, "Explorador", Icons.Filled.Explore, Color(0xFF0E7490)),
    Medal(20, "Rodador", Icons.Filled.Route, Color(0xFF2E7D32)),
    Medal(35, "Trotamundos", Icons.Filled.Public, Color(0xFF6A1B9A)),
    Medal(50, "Rutero experimentado", Icons.Filled.EmojiEvents, Color(0xFFD94600), short = "Rutero experto"),
)

fun medalOf(routes: Int): Medal? = MEDALS.lastOrNull { routes >= it.routes }

/** "hace 3 meses", "hace 1 año"… desde una fecha en milisegundos. */
private fun sinceText(ms: Long): String {
    val start = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val months = ChronoUnit.MONTHS.between(start, today)
    val days = ChronoUnit.DAYS.between(start, today)
    val ago = when {
        months >= 24 -> "hace ${months / 12} años"; months >= 12 -> "hace 1 año"
        months >= 2 -> "hace $months meses"; months == 1L -> "hace 1 mes"
        days >= 2 -> "hace $days días"; days == 1L -> "desde ayer"; else -> "desde hoy"
    }
    return start.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"))) + " · " + ago
}

/** Tarjeta de estadísticas y medallas. [sinceFallback]: fecha de creación de la cuenta si aún no hay estadísticas. */
@Composable
fun RiderStatsCard(stats: RiderStats?, sinceFallback: Long?, own: Boolean) {
    Card2 { RiderStats(stats, sinceFallback, own) }
}

/** Contenido de la trayectoria (rutas, rango, medallas), para ponerlo dentro de otra tarjeta, como la del perfil propio. */
@Composable
fun ColumnScope.RiderStats(stats: RiderStats?, sinceFallback: Long?, own: Boolean) {
    val routes = stats?.routes ?: 0
    val since = stats?.since?.takeIf { it > 0 } ?: sinceFallback
    val current = medalOf(routes)
    val next = MEDALS.firstOrNull { routes < it.routes }
    run {
        H("Trayectoria", Icons.Filled.EmojiEvents)
        // Los dos recuadros con la misma altura aunque la cifra de uno sea más corta.
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("$routes", if (routes == 1) "ruta completada" else "rutas completadas", Modifier.weight(1f).fillMaxHeight(), OrangeDark)
            Stat(current?.name ?: "Sin medalla", "rango", Modifier.weight(1f).fillMaxHeight(), current?.color ?: Muted)
        }
        if (since != null) IconLine(Icons.Filled.CalendarMonth, "En ApexMoto desde ${sinceText(since)}", CyanDeep, bold = false)
        // Medallas: las ganadas a color, las que faltan en gris.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MEDALS.forEach { m -> MedalBadge(m, routes >= m.routes, Modifier.weight(1f)) }
        }
        if (next != null) {
            val prev = MEDALS.lastOrNull { it.routes <= routes }?.routes ?: 0
            val frac = ((routes - prev).toFloat() / (next.routes - prev)).coerceIn(0f, 1f)
            Lbl("Faltan ${next.routes - routes} para «${next.name}»")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Surf3)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(next.color))
            }
        } else Lbl("¡Tienes todas las medallas!", OrangeDark)
        if (own) Sub("Suma una ruta cada vez que llegas al destino con el guiado de ApexMoto (rutas de 2 km o más).")
    }
}

@Composable
private fun MedalBadge(m: Medal, earned: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (earned) m.color else Surf3), contentAlignment = Alignment.Center) {
            Icon(if (earned) m.icon else Icons.Filled.Lock, m.name + if (earned) "" else " (bloqueada)", Modifier.size(22.dp), tint = if (earned) Color.White else Muted)
        }
        // Siempre dos renglones de alto: así "50 rutas" queda a la misma altura en todas las medallas.
        Text(m.short, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, color = if (earned) Ink else Muted, minLines = 2, maxLines = 2)
        Text("${m.routes} rutas", fontSize = 12.sp, color = Muted, maxLines = 1)
    }
}

/** Perfil público de otro rider: avatar, @usuario, medallas y la opción de agregarlo como amigo. */
@Composable
fun RiderPublicScreen(vm: AppViewModel, nav: NavController, u: AppUser, uid: String) {
    val p by remember(uid) { vm.repo.profile(uid) }.collectAsState(initial = null)
    val stats by remember(uid) { vm.repo.riderStats(uid) }.collectAsState(initial = null)
    val mine by vm.myProfile.collectAsState()
    val friends by vm.friendProfiles.collectAsState()
    val reqOut by vm.friendReqOut.collectAsState()
    val reqIn by vm.friendReqIn.collectAsState()
    Screen {
        val pr = p
        if (pr == null) { Empty("Este rider no tiene perfil público.", Icons.Filled.PersonOff); return@Screen }
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MotoAvatar(pr.moto, pr.color, 64.dp, medalOf(stats?.routes ?: 0)?.color)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pr.name, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                    Lbl("@${pr.username}", CyanDeep)
                    medalOf(stats?.routes ?: 0)?.let { Pill(it.name, it.color.copy(alpha = 0.14f), it.color, it.icon) }
                }
            }
            val isFriend = friends.any { it.id == uid }
            val pendingIn = reqIn.firstOrNull { it.from == uid }
            val m = mine
            when {
                uid == u.id -> {}
                isFriend -> IconLine(Icons.Filled.People, "Son amigos", Green)
                reqOut.any { it.to == uid } -> IconLine(Icons.Filled.HourglassTop, "Le enviaste una solicitud de amistad", Muted, bold = false)
                pendingIn != null -> Btn("Aceptar solicitud de amistad", Modifier.fillMaxWidth(), Icons.Filled.PersonAdd) { vm.act("Ahora son amigos") { vm.repo.acceptFriend(pendingIn) } }
                m == null -> Sub("Crea tu perfil de rider en Comunidad → Amigos para agregarlo.")
                else -> Btn("Añadir amigo", Modifier.fillMaxWidth(), Icons.Filled.PersonAdd) { vm.act("Solicitud enviada a ${pr.name}") { vm.repo.sendFriendRequest(m, pr) } }
            }
        }
        RiderStatsCard(stats, null, own = false)
    }
}
