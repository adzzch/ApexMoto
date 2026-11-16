package co.apexmoto.app.voice

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import co.apexmoto.app.BuildConfig
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Intercomunicador de voz de una rodada (como una llamada de grupo de WhatsApp o Discord), con Agora.
 *
 * - Un canal por rodada ("apex_<id de la rodada>"); cada persona entra con un número que sale de su uid de Firebase.
 * - Dos modos: micrófono abierto (con botón de silenciar) o pulsar para hablar (walkie-talkie).
 * - Supresión de ruido con IA en modo fuerte (viento y motor).
 * - Mientras está conectado corre [IntercomService] (servicio en primer plano con notificación), así sigue sonando
 *   con la pantalla apagada o con otra app abierta, y por el intercom Bluetooth del casco si está conectado.
 * - La voz pasa por los servidores de Agora y no se graba. Ver docs/seguridad.md (modo de prueba sin token).
 */
class Intercom(private val ctx: Context) {

    enum class Status { IDLE, CONNECTING, ON, RECONNECTING }

    data class State(
        val rodadaId: String? = null,
        val rodadaName: String = "",
        val status: Status = Status.IDLE,
        /** Pulsar para hablar (true) o micrófono abierto (false). */
        val pushToTalk: Boolean = false,
        /** Silenciado en modo micrófono abierto. */
        val muted: Boolean = false,
        /** Sosteniendo el botón en modo pulsar para hablar. */
        val pressing: Boolean = false,
        /** Números de Agora de las otras personas conectadas. */
        val peers: Set<Int> = emptySet(),
        /** Quién está hablando ahora (0 = yo). */
        val speaking: Set<Int> = emptySet(),
        val error: String? = null,
    ) {
        val active get() = rodadaId != null
        /** Si mi micrófono está saliendo al grupo ahora mismo. */
        val transmitting get() = if (pushToTalk) pressing else !muted
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Sin AGORA_APP_ID en local.properties el intercomunicador no se ofrece. */
    val available get() = BuildConfig.AGORA_APP_ID.isNotBlank()

    private var engine: RtcEngine? = null
    /** Si ya se pidió encender [IntercomService]. Solo se enciende cuando la llamada conectó de verdad. */
    private var serviceOn = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private val handler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            _state.update { it.copy(status = Status.ON, error = null) }
            main.post { startService() }
        }
        override fun onUserJoined(uid: Int, elapsed: Int) { _state.update { it.copy(peers = it.peers + uid) } }
        override fun onUserOffline(uid: Int, reason: Int) { _state.update { it.copy(peers = it.peers - uid, speaking = it.speaking - uid) } }
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            when (state) {
                Constants.CONNECTION_STATE_CONNECTED -> _state.update { it.copy(status = Status.ON, error = null) }
                Constants.CONNECTION_STATE_RECONNECTING -> _state.update { it.copy(status = Status.RECONNECTING) }
                Constants.CONNECTION_STATE_FAILED -> {
                    val why = when (reason) {
                        Constants.CONNECTION_CHANGED_INVALID_APP_ID -> "La clave de Agora no es válida."
                        Constants.CONNECTION_CHANGED_INVALID_TOKEN -> "El proyecto de Agora pide token: créalo en modo de prueba (solo App ID)."
                        else -> "No se pudo conectar la llamada. Revisa la señal e intenta de nuevo."
                    }
                    Log.w(TAG, "conexión fallida, razón $reason")
                    leave(why)
                }
            }
        }
        override fun onError(err: Int) {
            Log.w(TAG, "error $err")
            when (err) {
                Constants.ERR_INVALID_APP_ID -> leave("La clave de Agora no es válida.")
                Constants.ERR_INVALID_TOKEN, Constants.ERR_TOKEN_EXPIRED -> leave("El proyecto de Agora pide token: créalo en modo de prueba (solo App ID).")
            }
        }
        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            // vad = 1 cuando hay voz (no solo ruido). Para los demás basta el volumen.
            val now = speakers.orEmpty().filter { s -> if (s.uid == 0) s.vad == 1 && s.volume > 20 else s.volume > 20 }.map { it.uid }.toSet()
            _state.update { st -> st.copy(speaking = if (st.transmitting) now else now - 0) }
        }
    }

    /** Entra a la llamada de la rodada. Si ya estaba en otra, sale de esa primero. Requiere el permiso de micrófono. */
    fun join(rodadaId: String, rodadaName: String, userId: String) {
        if (!available) return
        val cur = _state.value
        if (cur.rodadaId == rodadaId && cur.status != Status.IDLE) return
        if (cur.active) leave()
        val e = engine ?: runCatching {
            RtcEngine.create(RtcEngineConfig().apply {
                mContext = ctx.applicationContext
                mAppId = BuildConfig.AGORA_APP_ID
                mEventHandler = handler
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                mAudioScenario = Constants.AUDIO_SCENARIO_CHATROOM
            })
        }.onFailure { Log.w(TAG, "no se pudo crear el motor", it) }.getOrNull()
        if (e == null) { _state.value = State(error = "Este teléfono no pudo iniciar la llamada."); return }
        engine = e
        e.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD, Constants.AUDIO_SCENARIO_CHATROOM)
        e.setAINSMode(true, 1) // 1 = fuerte: viento y motor
        e.setDefaultAudioRoutetoSpeakerphone(true) // sin audífonos se oye por el parlante; con Bluetooth va al casco
        e.enableAudioVolumeIndication(300, 3, true)
        _state.value = State(rodadaId = rodadaId, rodadaName = rodadaName, status = Status.CONNECTING, pushToTalk = cur.pushToTalk)
        e.muteLocalAudioStream(!_state.value.transmitting)
        val opts = ChannelMediaOptions().apply {
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            publishMicrophoneTrack = true
            autoSubscribeAudio = true
        }
        val r = e.joinChannel(null, channelOf(rodadaId), uidOf(userId), opts)
        if (r != 0) { Log.w(TAG, "joinChannel $r"); leave("No se pudo entrar a la llamada (código $r)."); return }
    }

    /**
     * Enciende el servicio en primer plano cuando la llamada ya conectó (así un fallo inmediato no deja a Android
     * esperando un servicio que se apaga antes de arrancar, lo que cierra la app).
     */
    private fun startService() {
        if (serviceOn || !_state.value.active) return
        serviceOn = true
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, IntercomService::class.java)) }
            .onFailure { serviceOn = false; Log.w(TAG, "no se pudo encender el servicio", it) }
    }

    /** Sale de la llamada y apaga el micrófono y el servicio. */
    fun leave(error: String? = null) {
        val e = engine
        engine = null
        if (e != null) runCatching { e.leaveChannel() }
        // destroy() libera el micrófono; se hace en otro hilo porque puede tardar y no debe correr dentro de un callback.
        if (e != null) Thread { runCatching { RtcEngine.destroy() } }.start()
        _state.update { State(pushToTalk = it.pushToTalk, error = error) }
        // Se le pide al servicio que se apague él mismo: si aún estaba arrancando, primero cumple con startForeground.
        if (serviceOn) {
            serviceOn = false
            main.post {
                runCatching { ctx.startService(Intent(ctx, IntercomService::class.java).setAction(IntercomService.ACTION_STOP)) }
                    .onFailure { runCatching { ctx.stopService(Intent(ctx, IntercomService::class.java)) } }
            }
        }
    }

    fun setPushToTalk(on: Boolean) { _state.update { it.copy(pushToTalk = on, pressing = false) }; apply() }
    fun setMuted(muted: Boolean) { _state.update { it.copy(muted = muted) }; apply() }
    fun press(down: Boolean) { _state.update { it.copy(pressing = down) }; apply() }
    fun clearError() { _state.update { it.copy(error = null) } }

    private fun apply() {
        val st = _state.value
        engine?.muteLocalAudioStream(!st.transmitting)
        if (!st.transmitting) _state.update { it.copy(speaking = it.speaking - 0) }
    }

    companion object {
        private const val TAG = "Intercom"
        fun channelOf(rodadaId: String) = "apex_$rodadaId"
        /** Número de Agora de una persona: sale de su uid de Firebase (siempre el mismo, positivo y distinto de 0). */
        fun uidOf(userId: String): Int = (userId.hashCode() and 0x7fffffff).let { if (it == 0) 1 else it }
    }
}
