package co.apexmoto.app.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import co.apexmoto.app.ApexApp
import co.apexmoto.app.MainActivity

/**
 * Mantiene viva la llamada del intercomunicador con la pantalla apagada o con otra app abierta (servicio en primer
 * plano de tipo micrófono). Muestra una notificación fija con "Salir"; no hace nada más: la llamada vive en [Intercom].
 */
class IntercomService : Service() {

    private val intercom get() = (application as ApexApp).container.intercom

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Siempre primero startForeground: Android cierra la app si un servicio pedido con startForegroundService no lo hace.
        val n = notification(intercom.state.value.rodadaName)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(ID, n)
        }
        when {
            intent?.action == ACTION_LEAVE -> { intercom.leave(); stop() }
            intent?.action == ACTION_STOP || !intercom.state.value.active -> stop()
        }
        return START_NOT_STICKY
    }

    // Si la persona cierra la app desde recientes, se sale de la llamada (no queda el micrófono abierto).
    override fun onTaskRemoved(rootIntent: Intent?) { intercom.leave(); stop() }

    private fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun notification(rodada: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Intercomunicador", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aviso fijo mientras estás en la llamada de una rodada"
            })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val leave = PendingIntent.getService(this, 1, Intent(this, IntercomService::class.java).setAction(ACTION_LEAVE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Intercom de la rodada")
            .setContentText(rodada.ifBlank { "En llamada con tu grupo" })
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Salir", leave)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    companion object {
        private const val ID = 4107
        private const val CHANNEL = "intercom"
        private const val ACTION_LEAVE = "co.apexmoto.intercom.LEAVE"
        const val ACTION_STOP = "co.apexmoto.intercom.STOP"
    }
}
