package co.apexmoto.app.voice

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Token de Agora (formato AccessToken2, versión "007") para entrar a un canal de voz, creado en el teléfono con el
 * App Certificate. Es el mismo algoritmo del RtcTokenBuilder2 oficial de Agora (AgoraIO/Tools, DynamicKey).
 *
 * OJO: crearlo en la app deja el App Certificate dentro del APK (se puede extraer). Es aceptable para el grupo de
 * pruebas; antes de abrir al público, los tokens deben salir de un servidor que compruebe que la persona es miembro de
 * la rodada. Ver docs/seguridad.md.
 */
internal object AgoraToken {
    private const val VERSION = "007"
    private const val SERVICE_RTC: Short = 1
    private const val PRIV_JOIN_CHANNEL: Short = 1
    private const val PRIV_PUBLISH_AUDIO: Short = 2

    /** Token para [uid] en [channel], válido [expireSeconds] segundos desde ahora. Null si faltan las claves. */
    fun rtc(appId: String, appCert: String, channel: String, uid: Int, expireSeconds: Int = 24 * 3600): String? =
        rtc(appId, appCert, channel, uid, expireSeconds, (System.currentTimeMillis() / 1000).toInt(), SecureRandom().nextInt(99_999_999) + 1)

    /** Con fecha y sal fijas: solo para comprobar el resultado contra el generador oficial. */
    fun rtc(appId: String, appCert: String, channel: String, uid: Int, expireSeconds: Int, issueTs: Int, salt: Int): String? {
        if (appId.length != 32 || appCert.length != 32) return null

        // Firma: HMAC(issueTs, cert) y luego HMAC(salt, eso).
        var signing = hmac(Buf().int(issueTs).bytes(), appCert.toByteArray())
        signing = hmac(Buf().int(salt).bytes(), signing)

        val content = Buf().bytesWithLen(appId.toByteArray()).int(issueTs).int(expireSeconds).int(salt).short(1)
        // Servicio RTC: tipo, privilegios (ordenados por clave) y canal + uid como texto (uid 0 = "").
        content.short(SERVICE_RTC).short(2)
            .short(PRIV_JOIN_CHANNEL).int(expireSeconds)
            .short(PRIV_PUBLISH_AUDIO).int(expireSeconds)
            .bytesWithLen(channel.toByteArray())
            .bytesWithLen((if (uid == 0) "" else (uid.toLong() and 0xffffffffL).toString()).toByteArray())

        val signature = hmac(signing, content.bytes())
        val all = Buf().bytesWithLen(signature).raw(content.bytes()).bytes()
        return VERSION + java.util.Base64.getEncoder().encodeToString(deflate(all))
    }

    private fun hmac(key: ByteArray, msg: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(msg)

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater().apply { setInput(data); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    /** Búfer little-endian como el ByteBuf de Agora. */
    private class Buf {
        private val out = ByteArrayOutputStream()
        fun short(v: Short) = apply { out.write(v.toInt() and 0xff); out.write((v.toInt() shr 8) and 0xff) }
        fun short(v: Int) = short(v.toShort())
        fun int(v: Int) = apply { for (i in 0 until 4) out.write((v shr (8 * i)) and 0xff) }
        fun bytesWithLen(b: ByteArray) = apply { short(b.size); out.write(b) }
        fun raw(b: ByteArray) = apply { out.write(b) }
        fun bytes(): ByteArray = out.toByteArray()
    }
}
