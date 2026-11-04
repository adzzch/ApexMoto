@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.core.today
import co.apexmoto.app.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId

val Mono = FontFamily.Monospace

val REQ_ST = mapOf("p" to "Pendiente", "ok" to "Aceptada", "go" to "En camino", "ride" to "En ruta", "fin" to "Finalizada", "no" to "Rechazada")
val ORD_ST = mapOf("new" to "Enviado", "prep" to "Preparando", "ready" to "¡Listo!", "done" to "Entregado", "no" to "Rechazado")
val SOS_ST = mapOf("new" to "Buscando taller", "ok" to "Taller asignado", "go" to "En camino", "fin" to "Resuelto", "no" to "Cancelado")

@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
fun Card2(modifier: Modifier = Modifier, border: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    val shape = RoundedCornerShape(14.dp)
    val colors = CardDefaults.cardColors(containerColor = Color.White)
    val b = border?.let { BorderStroke(1.dp, it) }
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) Card(onClick = onClick, modifier = m, shape = shape, colors = colors, border = b, content = inner)
    else Card(modifier = m, shape = shape, colors = colors, border = b, content = inner)
}

@Composable fun Lbl(text: String, color: Color = Muted) = Text(text, color = color, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
@Composable fun H(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)
@Composable fun Sub(text: String) = Text(text, color = Muted, fontSize = 13.sp)

@Composable
fun Pill(text: String, bg: Color = Surf3, fg: Color = Ink) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
}

@Composable
fun StatusPill(text: String, key: String) {
    val (bg, fg) = when (key) {
        "p", "new" -> Color(0x33FFB703) to Color(0xFF8A5A00)
        "ok", "fin", "ready", "done" -> Color(0x2E1B8A3A) to Green
        "no" -> Color(0x2ED13B3B) to Red
        else -> Color(0x2E0E7490) to CyanDeep
    }
    Pill(text, bg, fg)
}

enum class BK { Primary, Ghost, Danger }

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, kind: BK = BK.Primary, enabled: Boolean = true, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (kind) {
        BK.Primary -> Button(onClick, modifier, enabled = enabled, content = content)
        BK.Ghost -> OutlinedButton(onClick, modifier, enabled = enabled, content = content)
        BK.Danger -> OutlinedButton(onClick, modifier, enabled = enabled, colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
            border = BorderStroke(1.dp, Red), content = content)
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, kb: KeyboardType = KeyboardType.Text,
          lines: Int = 1, hint: String = "", password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), label = { Text(label) },
        placeholder = { if (hint.isNotBlank()) Text(hint) }, singleLine = lines == 1, minLines = lines,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** Lista desplegable. options = pares (clave, texto). */
@Composable
fun Drop(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotBlank()) Lbl(label)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.first == selected }?.second ?: selected, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (k, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { onSelect(k); open = false }) }
            }
        }
    }
}

@Composable
fun DateField(label: String, value: String, min: String? = null, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(modifier) {
        Lbl(label)
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            val d = LocalDate.parse(value.ifBlank { today() })
            val dlg = DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth)
            min?.let { dlg.datePicker.minDate = LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            dlg.show()
        }) { Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(value.ifBlank { "Elegir fecha" }) }
    }
}

@Composable
fun SwitchRow(title: String, sub: String = "", checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (sub.isNotBlank()) Sub(sub) }
        Switch(checked, onChange)
    }
}

@Composable
fun Empty(text: String) {
    Card2 { Text(text, color = Muted, modifier = Modifier.fillMaxWidth()) }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Surf3).padding(10.dp)) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Lbl(label)
    }
}

@Composable
fun Stars(r: Double) = Text("★ " + "%.1f".format(r), color = Color(0xFFB87900), fontWeight = FontWeight.Bold, fontSize = 13.sp)
