package co.apexmoto.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta tomada del prototipo apexmoto.html
val Orange = Color(0xFFD94600)
val OrangeDark = Color(0xFFC2410C)
val Cyan = Color(0xFF0E7490)
val CyanDeep = Color(0xFF0E6A82)
val Green = Color(0xFF1B8A3A)
val Red = Color(0xFFD13B3B)
val Ink = Color(0xFF1C2230)
val Muted = Color(0xFF566074)
val Bg = Color(0xFFF3F4F8)
val Surf3 = Color(0xFFE9EBF2)
val Surf4 = Color(0xFFD5D9E4)

private val Scheme = lightColorScheme(
    primary = Orange, onPrimary = Color.White, secondary = Cyan, onSecondary = Color.White,
    background = Bg, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Surf3, onSurfaceVariant = Muted, error = Red, outline = Surf4,
)

private val Type = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
)

@Composable
fun ApexTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
