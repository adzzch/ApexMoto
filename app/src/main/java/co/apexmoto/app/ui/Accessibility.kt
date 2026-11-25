package co.apexmoto.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.ui.theme.*

/**
 * Pantalla de Accesibilidad (Mi perfil → Accesibilidad, Configuración, o el botón de accesibilidad al ingresar).
 * Tiene los mismos ajustes y textos que la pantalla del prototipo (tools/presentacion/prototipo.html).
 * Todo se aplica al instante en toda la app y se guarda en el teléfono (ver theme/A11y.kt).
 */
@Composable
fun AccessibilityScreen(vm: AppViewModel) = Screen { AccessibilityContent(vm) }

@Composable
fun AccessibilityContent(vm: AppViewModel) {
    val ctx = LocalContext.current

    // Vista previa: cambia en vivo con cada ajuste (letra, contraste, botones).
    Card2(index = 0) {
        Lbl("VISTA PREVIA")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pedido en Parador El Mirador", fontWeight = FontWeight.Bold)
                Sub("2 ítems · llegas a eso de las 10:40")
            }
            Spacer(Modifier.width(8.dp))
            StatusPill("¡Listo!", "ready")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("Pendiente", "p"); StatusPill("Cancelado", "can")
        }
        Btn("Así se ve un botón", Modifier.fillMaxWidth(), Icons.Filled.Navigation) { vm.toast("Así se ve un aviso.") }
    }

    Card2(index = 1) {
        H("Ver mejor", Icons.Filled.Visibility)
        Text("Tamaño de letra", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
        Segmented(A11y.TextSize.entries.map { Triple(it.key, it.label, null) }, A11y.text.key) { k -> A11y.updateText(A11y.TextSize.of(k)) }
        Sub("Se suma al tamaño de letra de tu celular. La app permite hasta el doble del normal.")
        SwitchRow("Alto contraste (modo sol)", "Textos y colores más oscuros, fondo blanco y tarjetas con borde. Para baja visión o pantalla al sol.",
            A11y.highContrast) { A11y.updateHighContrast(it) }
        IconLine(Icons.Filled.Palette, "Daltonismo: los estados llevan ícono y palabra, nunca solo color.", CyanDeep, bold = false)
    }

    Card2(index = 2) {
        H("Tocar fácil", Icons.Filled.TouchApp)
        SwitchRow("Botones grandes", "Para manejar con guantes o si te cuesta atinar: los botones pasan de 48 a 56 dp.",
            A11y.bigTouch) { A11y.updateBigTouch(it) }
    }

    Card2(index = 3) {
        H("Avisos sin sonido", Icons.Filled.Vibration)
        SwitchRow("Vibrar en giros y alertas", "Dos toques antes de cada giro, uno largo al llegar y tres con las señales de tu rodada.",
            A11y.vibrate) { A11y.updateVibrate(it) }
        SwitchRow("Guía por voz", "Lee las indicaciones en voz alta. Si no oyes bien, apágala: el texto grande y la vibración siguen.",
            vm.voice) { vm.updateVoice(it) }
        Btn("Probar vibración", Modifier.fillMaxWidth(), Icons.Filled.Vibration, kind = BK.Ghost) { Haptics.test(ctx) }
    }

    Card2(index = 4) {
        H("Movimiento", Icons.Filled.Animation)
        SwitchRow("Reducir animaciones", "Quita los movimientos decorativos de tarjetas y botones.", A11y.reduceMotion) { A11y.updateReduceMotion(it) }
    }

    Card2(index = 5) {
        H("Lector de pantalla", Icons.Filled.RecordVoiceOver)
        Sub("ApexMoto funciona con TalkBack: los botones tienen nombre, los títulos están marcados y los mapas se describen con palabras.")
        Btn("Abrir accesibilidad del celular", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.OpenInNew, kind = BK.Ghost) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { vm.toast("Búscalo en Ajustes → Accesibilidad.") }
        }
    }

    if (A11y.customized) Btn("Volver a los ajustes de fábrica", Modifier.fillMaxWidth(), Icons.Filled.Restore, kind = BK.Ghost) {
        A11y.reset(); vm.toast("Listo: ajustes de fábrica.")
    }
    Text("Estos ajustes se guardan en este teléfono.", Modifier.fillMaxWidth(), color = Muted, style = MaterialTheme.typography.bodySmall)
}
