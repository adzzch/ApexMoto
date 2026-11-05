package co.apexmoto.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.apexmoto.app.R

// Paleta de marca (compromiso, tomada del prototipo apexmoto.html). No agregar colores sueltos en las pantallas:
// si hace falta uno nuevo, se define aquí.
val Orange = Color(0xFFD94600)
val OrangeDark = Color(0xFFC2410C)
val Cyan = Color(0xFF0E7490)
val CyanDeep = Color(0xFF0E6A82)
val Green = Color(0xFF1B8A3A)
val Red = Color(0xFFD13B3B)
val Amber = Color(0xFF8A5A00)      // texto de estado "pendiente" y estrellas (contraste AA sobre blanco)
val Ink = Color(0xFF1C2230)
val Muted = Color(0xFF566074)
val Outline = Color(0xFF7A8396)
val Bg = Color(0xFFF3F4F8)
val Surf0 = Color(0xFFECEEF4)      // fondo de campos y cifras dentro de tarjetas
val Surf1 = Color(0xFFF8F9FC)
val Surf3 = Color(0xFFE9EBF2)
val Surf4 = Color(0xFFD5D9E4)
val NavAccent = Color(0xFFFFB27A)  // distancia a la próxima maniobra sobre el panel de guiado (Ink)

// Fondos suaves para pastillas de estado (el mismo tono del texto, con transparencia)
val GreenSoft = Green.copy(alpha = 0.18f)
val RedSoft = Red.copy(alpha = 0.18f)
val CyanSoft = Cyan.copy(alpha = 0.18f)
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

private val Scheme = lightColorScheme(
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

private fun head(size: Int, line: Int, w: FontWeight = FontWeight.ExtraBold) =
    TextStyle(fontFamily = Chivo, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.01).em)
private fun body(size: Int, line: Int, w: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = SpaceGrotesk, fontWeight = w, fontSize = size.sp, lineHeight = line.sp)

private val Type = Typography(
    displaySmall = head(32, 38, FontWeight.Black),
    headlineLarge = head(28, 34, FontWeight.Black),
    headlineMedium = head(26, 32),
    headlineSmall = head(24, 28),
    titleLarge = head(20, 24),
    titleMedium = head(16, 20, FontWeight.Bold),
    titleSmall = body(14, 20, FontWeight.Bold),
    bodyLarge = body(16, 23),
    bodyMedium = body(14, 20),
    bodySmall = body(13, 18),
    labelLarge = body(14, 20, FontWeight.Bold),
    labelMedium = body(12, 16, FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.07.em),
)

private val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun ApexTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, typography = Type, shapes = ApexShapes, content = content)
