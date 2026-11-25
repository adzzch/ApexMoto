package co.apexmoto.app.ui.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.apexmoto.app.R

// Paleta de marca (compromiso, tomada del prototipo apexmoto.html), ajustada para accesibilidad (WCAG 2.2 AA):
// todo texto pasa 4,5:1 sobre blanco, sobre el fondo y sobre su fondo suave. Por eso el naranja (#D94600 → #C84100),
// el naranja oscuro, el verde y el rojo quedaron un poco más oscuros, con el mismo tono. Con "Alto contraste"
// (Mi perfil → Accesibilidad) se oscurecen hasta 7:1 (AAA), el fondo pasa a blanco y las tarjetas llevan borde.
// Son propiedades con get(): al cambiar el ajuste, todas las pantallas se redibujan con los colores nuevos.
// No agregar colores sueltos en las pantallas: si hace falta uno nuevo, se define aquí. Ver docs/accesibilidad.md.
private fun pick(normal: Long, high: Long) = Color(if (A11y.highContrast) high else normal)

val Orange: Color get() = pick(0xFFC84100, 0xFFA03300)      // fondo de botones con letra blanca: 4,99 / 7,07
val OrangeDark: Color get() = pick(0xFFBA3E0B, 0xFF882E08)  // texto naranja: 5,55 sobre blanco, 4,54 sobre OrangeSoft
val Cyan: Color get() = pick(0xFF0E7490, 0xFF0C6078)
val CyanDeep: Color get() = pick(0xFF0E6A82, 0xFF0B5163)
val Green: Color get() = pick(0xFF177431, 0xFF115524)
val Red: Color get() = pick(0xFFBA2C2C, 0xFF8B2121)
val Amber: Color get() = pick(0xFF8A5A00, 0xFF704900)       // texto de estado "pendiente" y estrellas
val Ink: Color get() = pick(0xFF1C2230, 0xFF0F141F)
val Muted: Color get() = pick(0xFF555F73, 0xFF444B5B)       // texto secundario: 4,55 aun sobre Surf4
val Outline: Color get() = pick(0xFF7A8396, 0xFF4F586B)     // bordes (3:1 mínimo para lo que no es texto)
val Bg: Color get() = pick(0xFFF3F4F8, 0xFFFFFFFF)
val Surf0: Color get() = pick(0xFFECEEF4, 0xFFEEF0F4)       // fondo de campos y cifras dentro de tarjetas
val Surf1: Color get() = pick(0xFFF8F9FC, 0xFFFFFFFF)
val Surf3: Color get() = pick(0xFFE9EBF2, 0xFFE4E7EE)
val Surf4: Color get() = pick(0xFFD5D9E4, 0xFFAEB5C3)
val NavAccent: Color get() = pick(0xFFFFB27A, 0xFFFFC9A3)   // distancia a la próxima maniobra sobre el panel de guiado (Ink)

// Fondos suaves para pastillas de estado (el mismo tono del texto, con transparencia). En alto contraste, más claros.
private val softAlpha: Float get() = if (A11y.highContrast) 0.14f else 0.18f
val GreenSoft: Color get() = Green.copy(alpha = softAlpha)
val RedSoft: Color get() = Red.copy(alpha = softAlpha)
val CyanSoft: Color get() = Cyan.copy(alpha = softAlpha)
val OrangeSoft = Color(0xFFFFE3D3)
val CyanTint = Color(0xFFD3ECF2)
val GreenTint = Color(0xFFD5EEDC)
val AmberSoft = Color(0x33FFB703)

// Fuentes de marca (compromiso): Chivo para títulos, Space Grotesk para texto, JetBrains Mono para cifras y etiquetas.
val Chivo = FontFamily(
    Font(R.font.chivo_bold, FontWeight.Bold),
    Font(R.font.chivo_extrabold, FontWeight.ExtraBold),
    Font(R.font.chivo_black, FontWeight.Black),
)
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold),
)

// Se arma de nuevo cuando cambia el alto contraste (los colores son propiedades con get()).
private fun scheme() = lightColorScheme(
    primary = Orange, onPrimary = Color.White, secondary = Cyan, onSecondary = Color.White,
    background = Bg, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Surf3, onSurfaceVariant = Muted, error = Red, outline = Outline, outlineVariant = Surf4,
    // Contenedores derivados de la marca: sin esto Material usa su lila por defecto en chips, sliders y menús.
    primaryContainer = Color(0xFFFFE3D3), onPrimaryContainer = Color(0xFF7A2600),
    secondaryContainer = Color(0xFFD3ECF2), onSecondaryContainer = Color(0xFF0B4A5A),
    tertiary = Green, onTertiary = Color.White, tertiaryContainer = Color(0xFFD5EEDC), onTertiaryContainer = Color(0xFF0E4D20),
    errorContainer = Color(0xFFFBE0E0), onErrorContainer = Color(0xFF7A1B1B),
    surfaceTint = Orange, inverseSurface = Ink, inverseOnSurface = Bg, inversePrimary = Color(0xFFFFB27A),
    surfaceBright = Color.White, surfaceDim = Surf4,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Surf1, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Surf3,
)

// Interlineado proporcional (em): si una pantalla cambia el tamaño de un texto, el espacio entre renglones se ajusta
// solo. Con valores fijos en sp, un texto de 13 sp heredaba 23 sp de interlineado y los renglones quedaban separados.
private fun head(size: Int, line: Int, w: FontWeight = FontWeight.ExtraBold) =
    TextStyle(fontFamily = Chivo, fontWeight = w, fontSize = size.sp, lineHeight = (line.toFloat() / size).em, letterSpacing = (-0.01).em)
private fun body(size: Int, line: Int, w: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = SpaceGrotesk, fontWeight = w, fontSize = size.sp, lineHeight = (line.toFloat() / size).em)

// Alto contraste: el texto corrido pasa de Regular a Medium (trazo más grueso, se lee mejor con baja visión y al sol).
private fun typography(hc: Boolean): Typography {
    val w = if (hc) FontWeight.Medium else FontWeight.Normal
    return Typography(
        displaySmall = head(32, 38, FontWeight.Black),
        headlineLarge = head(28, 34, FontWeight.Black),
        headlineMedium = head(26, 32),
        headlineSmall = head(24, 28),
        titleLarge = head(20, 24),
        titleMedium = head(16, 20, FontWeight.Bold),
        titleSmall = body(14, 20, FontWeight.Bold),
        bodyLarge = body(16, 23, w),
        bodyMedium = body(14, 20, w),
        bodySmall = body(13, 18, w),
        labelLarge = body(14, 20, FontWeight.Bold),
        labelMedium = body(12, 16, FontWeight.Medium),
        // Etiquetas: 12 sp como mínimo (antes 11), en JetBrains Mono.
        labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 1.3.em, letterSpacing = 0.05.em),
    )
}

private val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(22.dp),
)

/**
 * Letra más grande permitida: 2x, lo que pide WCAG 1.4.4. Compose la aplica "no lineal" (como Android 14): el texto
 * normal casi se duplica, pero los títulos grandes crecen poco (24 sp → 36), así las pantallas no se desarman.
 */
const val MAX_FONT_SCALE = 2f

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    val hc = A11y.highContrast
    val colors = remember(hc) { scheme() }
    val type = remember(hc) { typography(hc) }
    MaterialTheme(colorScheme = colors, typography = type, shapes = ApexShapes) {
        // Color de texto por defecto explícito: ningún texto queda dependiendo del modo claro/oscuro del sistema.
        // Letra: la del celular (Ajustes → Tamaño de fuente) por la de la app (Accesibilidad), hasta 2x.
        // Antes había un tope de 1,15x que dejaba por fuera a quien necesita letra grande.
        val d = LocalDensity.current
        val scaled = Density(d.density, (d.fontScale * A11y.text.scale).coerceIn(0.85f, MAX_FONT_SCALE))
        CompositionLocalProvider(LocalContentColor provides Ink, LocalDensity provides scaled, content = content)
    }
}
