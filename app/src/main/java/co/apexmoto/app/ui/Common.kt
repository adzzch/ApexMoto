@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import android.app.DatePickerDialog
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.apexmoto.app.R
import co.apexmoto.app.core.today
import co.apexmoto.app.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val Mono = JetBrainsMono

val REQ_ST = mapOf("p" to "Pendiente", "ok" to "Aceptada", "go" to "En camino", "ride" to "En ruta", "fin" to "Finalizada", "no" to "Rechazada")
val ORD_ST = mapOf("new" to "Enviado", "prep" to "Preparando", "ready" to "¡Listo!", "done" to "Entregado", "no" to "Rechazado")
val SOS_ST = mapOf("new" to "Buscando taller", "ok" to "Taller asignado", "go" to "En camino", "fin" to "Resuelto", "no" to "Cancelado")

// ───────────────── Movimiento ─────────────────
/** Curva de salida exponencial: arranca rápido y se asienta suave. */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** true si el sistema tiene las animaciones desactivadas (Opciones de desarrollador o accesibilidad). */
@Composable
fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Entrada escalonada de las tarjetas de una lista: suben 16 dp y terminan de aparecer. Parte visible, nunca de cero. */
fun Modifier.reveal(index: Int): Modifier = composed {
    val reduce = reducedMotion()
    val a = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) { delay(index.coerceAtMost(8) * 45L); a.animateTo(1f, tween(420, easing = EaseOutExpo)) } }
    val shift = with(LocalDensity.current) { 16.dp.toPx() }
    graphicsLayer { alpha = 0.35f + 0.65f * a.value; translationY = (1f - a.value) * shift }
}

/** Al presionar, el elemento se hunde un poco (2 %). */
fun Modifier.pressScale(source: MutableInteractionSource, to: Float = 0.97f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) to else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Sombra de marca: suave, con desplazamiento, teñida del color que la proyecta. */
fun Modifier.glow(color: Color, shape: Shape, on: Boolean = true, elevation: Dp = 10.dp): Modifier =
    if (!on) this else shadow(elevation, shape, clip = false, ambientColor = color.copy(alpha = 0.35f), spotColor = color.copy(alpha = 0.55f))

fun Modifier.cardShadow(shape: Shape): Modifier =
    shadow(3.dp, shape, clip = false, ambientColor = Ink.copy(alpha = 0.10f), spotColor = Ink.copy(alpha = 0.16f))

// ───────────────── Estructura ─────────────────
@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

/** Tarjeta blanca de la app. index >= 0 la hace entrar con la cascada de la lista. */
@Composable
fun Card2(
    modifier: Modifier = Modifier, border: Color? = null, onClick: (() -> Unit)? = null, index: Int = -1,
    padding: Dp = 14.dp, tone: Color = Color.White, content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val src = remember { MutableInteractionSource() }
    var m = modifier.fillMaxWidth()
    if (index >= 0) m = m.reveal(index)
    if (onClick != null) m = m.pressScale(src, 0.985f)
    m = m.then(if (tone == Color.White) Modifier.cardShadow(shape) else Modifier).clip(shape).background(tone)
    if (border != null) m = m.border(BorderStroke(1.5.dp, border), shape)
    if (onClick != null) m = m.clickable(src, OrangeRipple, onClick = onClick)
    Column(m.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

private val OrangeRipple = ripple(color = Orange)

// ───────────────── Texto ─────────────────
@Composable fun Lbl(text: String, color: Color = Muted, modifier: Modifier = Modifier) =
    Text(text, modifier, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)

@Composable
fun H(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = OrangeDark); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
    }
}

@Composable fun Sub(text: String) = Text(text, color = Muted, style = MaterialTheme.typography.bodySmall)

/** Cifra en monoespaciada (precios, distancias, tiempos). */
@Composable
fun Num(text: String, color: Color = Ink, size: Int = 15, modifier: Modifier = Modifier) =
    Text(text, modifier, color = color, fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, letterSpacing = (-0.02).em, maxLines = 1)

// ───────────────── Etiquetas ─────────────────
@Composable
fun Pill(text: String, bg: Color = Surf3, fg: Color = Muted, icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(12.dp), tint = fg); Spacer(Modifier.width(4.dp)) }
        Text(text, color = fg, fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 0.02.em, maxLines = 1)
    }
}

@Composable
fun StatusPill(text: String, key: String) {
    val (bg, fg) = when (key) {
        "p", "new" -> AmberSoft to Amber
        "ok", "fin", "ready", "done" -> GreenSoft to Green
        "no" -> RedSoft to Red
        else -> CyanSoft to CyanDeep
    }
    Pill(text, bg, fg)
}

/** Punto verde que late: algo está en vivo. */
@Composable
fun Ping(on: Boolean = true) {
    val reduce = reducedMotion()
    val t = rememberInfiniteTransition(label = "ping")
    val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(10.dp).graphicsLayer { alpha = if (on && !reduce) a else 1f }.clip(CircleShape).background(if (on) Green else Surf4))
}

/** Barra de progreso por pasos (estado de un S.O.S., de una renta…). */
@Composable
fun Steps(total: Int, done: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val c by animateColorAsState(if (i < done) Green else Surf4, tween(500), label = "step")
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(c))
        }
    }
}

// ───────────────── Botones ─────────────────
enum class BK { Primary, Ghost, Danger, Sos }

@Composable
fun Btn(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, kind: BK = BK.Primary, enabled: Boolean = true,
    small: Boolean = false, loading: Boolean = false, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val src = remember { MutableInteractionSource() }
    val (bg, fg) = when (kind) {
        BK.Primary -> Orange to Color.White
        BK.Sos -> Red to Color.White
        BK.Ghost -> Surf3 to CyanDeep
        BK.Danger -> RedSoft to Red
    }
    val solid = kind == BK.Primary || kind == BK.Sos
    Button(
        { if (!loading) onClick() }, modifier.heightIn(min = if (small) 40.dp else 48.dp).glow(bg, shape, on = solid && enabled, elevation = 8.dp).pressScale(src),
        enabled = enabled, shape = shape, interactionSource = src,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = bg.copy(alpha = 0.4f), disabledContentColor = fg.copy(alpha = 0.7f)),
        contentPadding = PaddingValues(horizontal = if (small) 14.dp else 18.dp, vertical = 0.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        else if (icon != null) { Icon(icon, null, Modifier.size(if (small) 17.dp else 19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = if (small) 13.sp else 15.sp, letterSpacing = 0.02.em, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón cuadrado solo con ícono (ir, cupón, chat…). */
@Composable
fun IconBtn(icon: ImageVector, desc: String, enabled: Boolean = true, tint: Color = CyanDeep, bg: Color = Surf3, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).pressScale(src, 0.92f).clip(RoundedCornerShape(12.dp)).background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(src, OrangeRipple, enabled = enabled, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.4f)) }
}

/** Botón de Google según su guía de marca: fondo blanco, borde gris, logo "G" a color y texto oscuro en Roboto. */
@Composable
fun GoogleButton(text: String = "Continuar con Google", modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick, modifier.fillMaxWidth().height(48.dp).pressScale(src), enabled = enabled, interactionSource = src,
        shape = CircleShape, border = BorderStroke(1.dp, Color(0xFF747775)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Image(painterResource(R.drawable.ic_google_g), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.01.em)
    }
}

/** Botón que abre la navegación en la app de Google Maps, con su ícono. */
@Composable
fun GoogleMapsButton(modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick, modifier.height(48.dp).pressScale(src), enabled = enabled, interactionSource = src,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Surf4),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Image(painterResource(R.drawable.ic_google_maps), null, Modifier.height(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Abrir en Google Maps", fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
    }
}

/** Marca de ApexMoto: cuadro naranja con la moto. */
@Composable
fun BrandMark(size: Dp = 36.dp) {
    Box(Modifier.size(size).glow(Orange, RoundedCornerShape(size * 0.25f), elevation = size / 5).clip(RoundedCornerShape(size * 0.25f)).background(Orange), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.TwoWheeler, null, Modifier.size(size * 0.58f), tint = Color.White)
    }
}

// ───────────────── Selectores ─────────────────
/** Filtro tipo píldora. accent = color cuando está activo (naranja para corredores, cian para categorías). */
@Composable
fun Chip(label: String, selected: Boolean, icon: ImageVector? = null, accent: Color = Orange, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) accent else Surf3, tween(220), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else Muted, tween(220), label = "chipFg")
    Row(
        Modifier.heightIn(min = 40.dp).pressScale(src, 0.95f).glow(accent, shape, on = selected, elevation = 6.dp).clip(shape).background(bg)
            .clickable(src, OrangeRipple, onClick = onClick).padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
        Text(label, color = fg, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

/** Interruptor de 2 o 3 opciones con un indicador naranja que se desliza. options = (clave, texto, ícono). */
@Composable
fun Segmented(options: List<Triple<String, String, ImageVector?>>, selected: String, onSelect: (String) -> Unit) {
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf0).padding(4.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * idx, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(w).height(44.dp).glow(Orange, RoundedCornerShape(11.dp), elevation = 8.dp).clip(RoundedCornerShape(11.dp)).background(Orange))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (k, t, ic) ->
                val fg by animateColorAsState(if (i == idx) Color.White else Muted, tween(200), label = "segFg")
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).clickable { onSelect(k) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ic != null) { Icon(ic, null, Modifier.size(17.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
                    Text(t, color = fg, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                }
            }
        }
    }
}

// ───────────────── Campos ─────────────────
private val FieldShape = RoundedCornerShape(10.dp)

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, kb: KeyboardType = KeyboardType.Text,
          lines: Int = 1, hint: String = "", password: Boolean = false, leading: ImageVector? = null, trailing: (@Composable () -> Unit)? = null,
          prefix: String? = null, ime: ImeAction = ImeAction.Default, onIme: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label.isNotBlank()) Lbl(label)
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { if (hint.isNotBlank()) Text(hint, color = Muted) }, singleLine = lines == 1, minLines = lines,
            leadingIcon = leading?.let { { Icon(it, null, tint = Muted) } }, trailingIcon = trailing,
            prefix = prefix?.let { { Text(it, color = Ink, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) } },
            keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ime),
            keyboardActions = KeyboardActions(onAny = { onIme?.invoke() ?: defaultKeyboardAction(ime) }),
            shape = FieldShape, textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surf0, unfocusedContainerColor = Surf0, disabledContainerColor = Surf0,
                focusedBorderColor = CyanDeep, unfocusedBorderColor = Color.Transparent, cursorColor = Orange,
                focusedLeadingIconColor = CyanDeep,
            ),
        )
    }
}

/** Caja rellena que abre algo al tocarla (lista, calendario). */
@Composable
private fun FilledBox(text: String, leading: ImageVector?, open: Boolean, onClick: () -> Unit, trailing: Boolean = true) {
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(220), label = "chev")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(FieldShape).background(Surf0).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { Icon(leading, null, Modifier.size(18.dp), tint = CyanDeep); Spacer(Modifier.width(10.dp)) }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing) Icon(Icons.Filled.ExpandMore, null, Modifier.rotate(rot), tint = Muted)
    }
}

/** Lista desplegable. options = pares (clave, texto). */
@Composable
fun Drop(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label.isNotBlank()) Lbl(label)
        Box {
            FilledBox(options.firstOrNull { it.first == selected }?.second ?: selected, null, open, { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White, shape = RoundedCornerShape(12.dp)) {
                options.forEach { (k, t) ->
                    DropdownMenuItem(
                        text = { Text(t, fontWeight = if (k == selected) FontWeight.Bold else FontWeight.Normal, color = if (k == selected) OrangeDark else Ink) },
                        trailingIcon = { if (k == selected) Icon(Icons.Filled.Check, null, tint = OrangeDark) },
                        onClick = { onSelect(k); open = false },
                    )
                }
            }
        }
    }
}

private val DateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "CO"))
fun prettyDate(iso: String): String = runCatching { LocalDate.parse(iso).format(DateFmt).replaceFirstChar { it.uppercase() } }.getOrDefault(iso)

@Composable
fun DateField(label: String, value: String, min: String? = null, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Lbl(label)
        FilledBox(if (value.isBlank()) "Elegir fecha" else prettyDate(value), Icons.Filled.CalendarMonth, false, {
            val d = LocalDate.parse(value.ifBlank { today() })
            val dlg = DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth)
            min?.let { dlg.datePicker.minDate = LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            dlg.show()
        }, trailing = false)
    }
}

@Composable
fun ApexSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked, onChange,
    colors = SwitchDefaults.colors(
        checkedTrackColor = Orange, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
        uncheckedTrackColor = Surf4, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
    ),
)

@Composable
fun SwitchRow(title: String, sub: String = "", checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)); if (sub.isNotBlank()) Lbl(sub) }
        Spacer(Modifier.width(12.dp))
        ApexSwitch(checked, onChange)
    }
}

/** Opción seleccionable en forma de tarjeta (plan de protección, rol…). */
@Composable
fun OptionTile(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val border by animateColorAsState(if (selected) CyanDeep else Color.Transparent, tween(200), label = "optB")
    val bg by animateColorAsState(if (selected) CyanSoft.copy(alpha = 0.12f) else Surf0, tween(200), label = "optBg")
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(src, 0.98f).clip(shape).background(bg).border(2.dp, border, shape).clickable(src, OrangeRipple, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp), content = content,
    )
}

// ───────────────── Datos ─────────────────
@Composable
fun Empty(text: String, icon: ImageVector = Icons.Filled.Inbox) {
    Card2(tone = Surf1) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(28.dp), tint = Outline)
            Text(text, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Cifra con etiqueta, para usar dentro de una tarjeta. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = CyanDeep) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Surf0).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Lbl(label); Num(value, color, 20)
    }
}

/** Cifra grande en su propia tarjeta, para tableros (comercio, dueño, conductor). */
@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier, color: Color = CyanDeep, index: Int = -1) {
    Card2(modifier, index = index) { Lbl(label); Num(value, color, 22) }
}

@Composable
fun Stars(r: Double) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Filled.Star, null, Modifier.size(15.dp), tint = OrangeDark); Spacer(Modifier.width(3.dp))
    Text("%.1f".format(r), color = OrangeDark, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

/** Cabecera de color para tarjetas de comercio y moto: degradado del tono al blanco con un ícono grande de fondo. */
@Composable
fun MediaHeader(tint: Color, icon: ImageVector, height: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth().height(height).background(Brush.linearGradient(listOf(tint, Color.White)))) {
        Icon(icon, null, Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = 4.dp).size(height * 0.95f), tint = Ink.copy(alpha = 0.06f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.White.copy(alpha = 0.85f))))
        content()
    }
}

/** Valor que se anima al cambiar (totales). */
@Composable
fun animatedMoney(value: Long): Long {
    val v by animateFloatAsState(value.toFloat(), tween(450, easing = EaseOutExpo), label = "money")
    return Math.round(v.toDouble())
}

// ───────────────── Hojas, barras y controles extra ─────────────────
/** Hoja inferior blanca (detalle de comercio, cupón…), como las del prototipo. */
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), dragHandle = { BottomSheetDefaults.DragHandle(color = Surf4) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

/** Barra fija al pie con el total y la acción principal. */
@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    Surface(color = Color.White, shadowElevation = 14.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun ApexSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int = 0) = Slider(
    value, onChange, valueRange = range, steps = steps,
    colors = SliderDefaults.colors(
        thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = Surf4,
        activeTickColor = Color.White.copy(alpha = 0.7f), inactiveTickColor = Outline.copy(alpha = 0.6f),
    ),
)

/** Contador − n + con el número que sube o baja al cambiar. */
@Composable
fun Stepper(q: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(Surf3).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        StepBtn(Icons.Filled.Remove, "Quitar", q > 0, Surf4, Ink, onMinus)
        AnimatedContent(q, label = "qty", transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(220)) { if (up) it else -it } + fadeIn(tween(220))) togetherWith (slideOutVertically(tween(220)) { if (up) -it else it } + fadeOut(tween(160)))
        }) { n -> Box(Modifier.widthIn(min = 30.dp), contentAlignment = Alignment.Center) { Num("$n", Ink, 15) } }
        StepBtn(Icons.Filled.Add, "Agregar", true, Orange, Color.White, onPlus)
    }
}

@Composable
private fun StepBtn(icon: ImageVector, desc: String, enabled: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(40.dp).pressScale(src, 0.88f).clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(src, OrangeRipple, enabled = enabled, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(20.dp), tint = if (enabled) fg else fg.copy(alpha = 0.4f)) }
}

/** Círculo con las iniciales de una persona. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val ini = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(Modifier.size(size).clip(CircleShape).background(CyanSoft), contentAlignment = Alignment.Center) {
        Text(ini, color = CyanDeep, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.38f).sp)
    }
}

/** Línea de detalle con ícono pequeño (promoción, punto de encuentro…). */
@Composable
fun IconLine(icon: ImageVector, text: String, color: Color = OrangeDark, bold: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color); Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
