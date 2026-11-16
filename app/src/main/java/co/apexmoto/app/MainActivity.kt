package co.apexmoto.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import co.apexmoto.app.ui.AppViewModel
import co.apexmoto.app.ui.ApexRoot
import co.apexmoto.app.ui.theme.ApexTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory((application as ApexApp).container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema siempre con íconos oscuros: la app es clara aunque el teléfono esté en modo oscuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Algunas capas de fabricante (MIUI, etc.) oscurecen las apps a la fuerza: aquí se prohíbe también desde la ventana.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.decorView.isForceDarkAllowed = false
        readInvite(intent)
        setContent { ApexTheme { ApexRoot(vm) } }
    }

    // La app ya estaba abierta y llega otro enlace de invitación.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readInvite(intent)
    }

    /** Enlace de invitación: https://<proyecto>.web.app/r/K7QM2XPA o apexmoto://r/K7QM2XPA → abre "Unirme con código". */
    private fun readInvite(intent: Intent?) {
        val uri = intent?.data ?: return
        val code = when (uri.scheme) {
            "https" -> uri.pathSegments.takeIf { it.size >= 2 && it[0] == "r" }?.get(1)
            "apexmoto" -> uri.pathSegments.firstOrNull()
            else -> null
        }?.uppercase()?.filter { it.isLetterOrDigit() }?.take(8)
        if (code != null && code.length == 8) vm.pendingInvite = code
    }
}
