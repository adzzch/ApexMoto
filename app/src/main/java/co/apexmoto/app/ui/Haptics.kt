package co.apexmoto.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import co.apexmoto.app.ui.theme.A11y

/**
 * Avisos por vibración: un canal que no depende de oír (personas sordas o con pérdida auditiva, o cualquiera con casco
 * y viento). Cada aviso tiene su patrón, para reconocerlo sin mirar. Se apagan en Accesibilidad → "Vibrar…".
 */
object Haptics {
    /** Dos toques cortos: viene un giro (nueva indicación del guiado). */
    fun turn(ctx: Context) = play(ctx, longArrayOf(0, 120, 90, 120))
    /** Uno largo: llegaste. */
    fun arrive(ctx: Context) = play(ctx, longArrayOf(0, 450))
    /** Tres toques: alerta de la rodada (señal de un compañero o emergencia). */
    fun alert(ctx: Context) = play(ctx, longArrayOf(0, 220, 110, 220, 110, 220))

    /** Para el botón "Probar vibración" de Accesibilidad: suena aunque el ajuste esté apagado. */
    fun test(ctx: Context) = play(ctx, longArrayOf(0, 120, 90, 120), force = true)

    private fun play(ctx: Context, pattern: LongArray, force: Boolean = false) {
        if (!force && !A11y.vibrate) return
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (v == null || !v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}
