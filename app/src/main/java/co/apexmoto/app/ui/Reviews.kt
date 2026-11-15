package co.apexmoto.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.data.Review
import co.apexmoto.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HourFmt = SimpleDateFormat("h:mm a", Locale("es", "CO"))
private val DayFmt = SimpleDateFormat("d MMM", Locale("es", "CO"))

/** Hora corta en formato colombiano: "1:40 p. m.". */
fun hhmm(ms: Long): String = HourFmt.format(Date(ms))

/** Promedio con estrellas, o "Nuevo" si todavía no hay reseñas (un 5,0 sin reseñas engañaría). */
@Composable
fun RatingLine(rating: Double, reviews: Int) {
    if (reviews <= 0) Pill("Nuevo", CyanSoft, CyanDeep)
    else Row(verticalAlignment = Alignment.CenterVertically) { Stars(rating); Spacer(Modifier.width(6.dp)); Lbl("$reviews reseña${if (reviews == 1) "" else "s"}") }
}

/** Calificar un servicio: de 1 a 5 estrellas y un comentario opcional de hasta 200 caracteres. */
@Composable
fun ReviewSheet(title: String, onDismiss: () -> Unit, onSend: (stars: Int, comment: String) -> Unit) {
    var stars by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    Sheet(onDismiss) {
        Text(title, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1..5).forEach { n ->
                IconButton({ stars = n }, Modifier.size(52.dp)) {
                    Icon(if (n <= stars) Icons.Filled.Star else Icons.Filled.StarBorder, "$n estrella${if (n == 1) "" else "s"}", Modifier.size(36.dp), tint = OrangeDark)
                }
            }
        }
        Field("Comentario (opcional)", comment, { comment = it.take(200) }, lines = 3, hint = "Cuenta cómo te fue")
        Lbl("${comment.length}/200 · Tu nombre aparece junto a tu reseña. Nada ofensivo ni datos de otras personas.")
        Btn("Publicar reseña", Modifier.fillMaxWidth(), Icons.Filled.Check) { onSend(stars, comment.trim()); onDismiss() }
    }
}

/** Últimas reseñas de un comercio, taller o moto. */
@Composable
fun ReviewsList(vm: AppViewModel, targetType: String, targetId: String, max: Int = 3) {
    val list by remember(targetType, targetId) { vm.repo.reviewsOf(targetType, targetId) }.collectAsState(initial = emptyList<Review>())
    val shown = list.sortedByDescending { it.createdAt }.take(max)
    if (shown.isEmpty()) { Sub("Todavía no hay reseñas."); return }
    shown.forEach { r ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { i -> Icon(if (i < r.stars) Icons.Filled.Star else Icons.Filled.StarBorder, null, Modifier.size(14.dp), tint = OrangeDark) }
                Spacer(Modifier.width(8.dp)); Lbl("${r.authorName} · ${DayFmt.format(Date(r.createdAt))}")
            }
            if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (list.size > max) Lbl("y ${list.size - max} más")
}
