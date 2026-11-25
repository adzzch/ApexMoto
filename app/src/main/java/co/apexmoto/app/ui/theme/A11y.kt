package co.apexmoto.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ajustes de accesibilidad de ApexMoto (Mi perfil → Accesibilidad, o el botón de accesibilidad al ingresar).
 * Se guardan en el teléfono y valen para toda la app. Son estado de Compose: al cambiar uno, las pantallas se redibujan
 * con la letra, los colores o los botones nuevos, sin reiniciar.
 *
 * Enfoque: baja visión y daltonismo (letra, contraste, estados con ícono y palabra) y personas sordas o con pérdida
 * auditiva (vibración y avisos en pantalla en vez de solo voz). Lo mismo ayuda a cualquiera al sol, con casco o guantes.
 * Detalle y criterios WCAG en docs/accesibilidad.md.
 */
object A11y {
    /** Tamaño de letra propio de la app. Se multiplica por el del celular (Ajustes → Pantalla → Tamaño de fuente). */
    enum class TextSize(val key: String, val label: String, val scale: Float) {
        NORMAL("n", "Normal", 1f), BIG("g", "Grande", 1.3f), HUGE("mg", "Muy grande", 1.6f);
        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: NORMAL }
    }

    var text by mutableStateOf(TextSize.NORMAL); private set
    /** Alto contraste ("modo sol"): textos y colores más oscuros (7:1), fondo blanco y tarjetas con borde. */
    var highContrast by mutableStateOf(false); private set
    /** Botones grandes (guantes o motricidad fina): lo que se toca pasa de 48 a 56 dp. */
    var bigTouch by mutableStateOf(false); private set
    /** Vibrar en cada giro del guiado y con las alertas (señales de la rodada): aviso que no depende de oír. */
    var vibrate by mutableStateOf(true); private set
    /** Quita las animaciones decorativas (se suma a la opción del celular). */
    var reduceMotion by mutableStateOf(false); private set

    private var sp: SharedPreferences? = null

    /** Se llama una vez al abrir la app (ApexApp.onCreate). */
    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("apex", Context.MODE_PRIVATE).also { sp = it }
        text = TextSize.of(p.getString("a11y_text", null))
        highContrast = p.getBoolean("a11y_hc", false)
        bigTouch = p.getBoolean("a11y_big", false)
        vibrate = p.getBoolean("a11y_vibrate", true)
        reduceMotion = p.getBoolean("a11y_motion", false)
    }

    fun updateText(v: TextSize) { text = v; sp?.edit()?.putString("a11y_text", v.key)?.apply() }
    fun updateHighContrast(v: Boolean) { highContrast = v; sp?.edit()?.putBoolean("a11y_hc", v)?.apply() }
    fun updateBigTouch(v: Boolean) { bigTouch = v; sp?.edit()?.putBoolean("a11y_big", v)?.apply() }
    fun updateVibrate(v: Boolean) { vibrate = v; sp?.edit()?.putBoolean("a11y_vibrate", v)?.apply() }
    fun updateReduceMotion(v: Boolean) { reduceMotion = v; sp?.edit()?.putBoolean("a11y_motion", v)?.apply() }

    /** Vuelve a los valores de fábrica. */
    fun reset() {
        updateText(TextSize.NORMAL); updateHighContrast(false); updateBigTouch(false); updateVibrate(true); updateReduceMotion(false)
    }

    /** true si algo está distinto de los valores de fábrica. */
    val customized: Boolean get() = text != TextSize.NORMAL || highContrast || bigTouch || !vibrate || reduceMotion
}

/** Alto mínimo de lo que se toca: 48 dp (guía de Android) o 56 dp con "Botones grandes". */
val TouchMin: Dp get() = if (A11y.bigTouch) 56.dp else 48.dp
/** Botones pequeños (dentro de filas): 40 dp visibles (el área táctil de Material ya es de 48) o 48 dp con "Botones grandes". */
val TouchSmall: Dp get() = if (A11y.bigTouch) 48.dp else 40.dp
