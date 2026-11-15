package co.apexmoto.app.legal

import android.content.Context
import android.os.Build
import co.apexmoto.app.BuildConfig
import co.apexmoto.app.data.Role
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Date
import java.util.Locale

/**
 * Un documento legal versionado. El texto se escribe en app/src/main/legal/<asset>; al compilar se completan los datos
 * del responsable (legal.properties) y viaja dentro del APK como assets/legal/<asset>: se puede leer sin señal y lo que
 * la persona acepta es exactamente lo que vio (se guarda su hash).
 *
 * Al cambiar un texto: sube [version], archiva el texto anterior en docs/legal/historial/ y agrega la
 * versión nueva a la lista de versiones válidas de firestore.rules. Ver docs/legal/README.md.
 */
data class LegalDoc(val id: String, val title: String, val version: String, val asset: String, val accept: String)

object Legal {
    const val TERMS = "terminos"
    const val PRIVACY = "privacidad"
    const val TRACKING = "rastreo"
    const val REFUNDS = "reembolsos"
    const val LOCATION = "ubicacion"
    // "declaracion-guia" se retiró con el rol de guía (2026-11-15). firestore.rules la sigue reconociendo solo para no
    // invalidar los perfiles de prueba que la aceptaron.
    const val OWNER = "declaracion-propietario"
    const val LICENSES = "licencias"

    /** accept = texto de la casilla. Vacío en los documentos que solo se consultan. */
    val DOCS = listOf(
        LegalDoc(TERMS, "Términos y Condiciones", "1.1.0", "terminos.md", "Soy mayor de 18 años y acepto los Términos y Condiciones."),
        LegalDoc(PRIVACY, "Política de Privacidad", "1.1.0", "privacidad.md",
            "Autorizo de manera previa, expresa e informada el tratamiento de mis datos personales según la Política de Privacidad."),
        LegalDoc(REFUNDS, "Cancelaciones, retracto y reembolsos", "1.1.0", "reembolsos.md", ""),
        LegalDoc(TRACKING, "Identificadores, analítica y rastreo", "1.0.0", "rastreo.md", ""),
        LegalDoc(LOCATION, "Uso de la ubicación", "1.1.0", "ubicacion.md", "Autorizo el uso de mi ubicación para lo que explica este aviso."),
        LegalDoc(OWNER, "Condiciones para dueños de moto", "1.1.0", "declaracion-propietario.md",
            "Declaro que cumplo las Condiciones para dueños: soy propietario o estoy autorizado, la moto tiene SOAT y revisión vigentes, y en cada renta reviso la licencia y lleno las actas."),
        LegalDoc(LICENSES, "Licencias de terceros", "1.0.0", "licencias.md", ""),
    )

    fun doc(id: String): LegalDoc = DOCS.first { it.id == id }

    /** Documentos que se pueden consultar siempre desde el registro y desde el perfil. */
    val PUBLIC = listOf(TERMS, PRIVACY, REFUNDS, TRACKING, LOCATION).map(::doc)

    /** Lo que cada rol debe tener aceptado, en su versión vigente, para usar la aplicación. */
    fun requiredFor(role: String): List<LegalDoc> = buildList {
        add(doc(TERMS)); add(doc(PRIVACY))
        if (role == Role.OWNER) add(doc(OWNER))
    }

    /** Documentos obligatorios que a esta cuenta le falta aceptar (cuenta antigua o versión nueva). */
    fun pending(role: String, accepted: Map<String, String>): List<LegalDoc> = requiredFor(role).filter { accepted[it.id] != it.version }

    fun isCurrent(accepted: Map<String, String>, id: String): Boolean = accepted[id] == doc(id).version

    /** Los saltos de línea se normalizan para que el hash sea el mismo sin importar dónde se compiló la app. */
    fun text(ctx: Context, doc: LegalDoc): String =
        ctx.assets.open("legal/${doc.asset}").bufferedReader(Charsets.UTF_8).use { it.readText() }.replace("\r\n", "\n").trim()

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** ID determinista: una sola aceptación por persona, documento y versión. Las reglas de Firestore lo exigen así. */
    fun acceptanceId(uid: String, doc: LegalDoc) = "${uid}_${doc.id}_${doc.version}"
}

/** Cómo se aceptó. Queda en el registro para poder demostrar que fue un acto expreso de la persona. */
object AcceptMethod {
    const val REGISTER = "casilla_registro"
    const val FIRST_LOGIN = "casilla_primer_ingreso"
    const val REACCEPT = "casilla_reaceptacion"
    const val LOCATION = "boton_aviso_ubicacion"
}

/**
 * Registro de una aceptación (colección legal_acceptances). Solo se crea: las reglas de Firestore
 * impiden modificarlo o borrarlo desde la aplicación, y obligan a que acceptedAt sea la hora del servidor.
 */
data class Acceptance(
    @DocumentId var id: String = "", var userId: String = "", var docId: String = "", var version: String = "",
    /** SHA-256 del texto exacto que se mostró. Permite probar qué contenido se aceptó. */
    var docHash: String = "",
    @ServerTimestamp var acceptedAt: Date? = null,
    var method: String = "", var platform: String = "android", var appVersion: String = "", var os: String = "",
    /** Identificador de instalación de Firebase: propio de esta app, se reinicia al reinstalar. */
    var deviceId: String = "", var locale: String = "", var role: String = "",
)

/** Solicitud de un titular (Ley 1581, arts. 14 y 15) o PQR de consumo. La responde un administrador. */
data class PrivacyRequest(
    @DocumentId var id: String = "", var userId: String = "", var type: String = "", var detail: String = "",
    var contact: String = "", var status: String = "recibida", @ServerTimestamp var createdAt: Date? = null,
    var response: String = "", var respondedAt: Date? = null,
)

object RequestType {
    /** clave → (nombre, días hábiles de respuesta). Consultas: art. 14. Reclamos: art. 15. */
    val ALL = listOf(
        Triple("consulta", "Consultar qué datos tienen de mí", 10),
        Triple("rectificacion", "Corregir o actualizar mis datos", 15),
        Triple("supresion", "Suprimir mis datos", 15),
        Triple("revocatoria", "Revocar mi autorización", 15),
        Triple("prueba", "Pedir prueba de mi autorización", 10),
        Triple("oferente", "Datos de un aliado para reclamarle", 15),
        Triple("queja", "Queja o reclamo", 15),
    )
    const val ACCOUNT_DELETED = "supresion_cuenta"
    fun label(k: String) = ALL.firstOrNull { it.first == k }?.second ?: if (k == ACCOUNT_DELETED) "Eliminación de la cuenta" else k
    fun days(k: String) = ALL.firstOrNull { it.first == k }?.third ?: 15
}

/** Identificación del oferente (colección providers). La exige el art. 53 de la Ley 1480 a los portales de contacto. No es pública. */
data class Provider(
    @DocumentId var id: String = "", var legalName: String = "", var idType: String = "CC", var idNumber: String = "",
    var address: String = "", var phone: String = "", var updatedAt: Long = 0,
) {
    /** Calculado: no se guarda en Firestore. */
    @get:Exclude val complete: Boolean get() = legalName.isNotBlank() && idNumber.length >= 5 && address.isNotBlank() && phone.filter(Char::isDigit).length >= 7
}

/** Datos técnicos que acompañan cada aceptación. */
data class ConsentMeta(val deviceId: String, val appVersion: String, val os: String, val locale: String)

/** Reúne los metadatos y el hash de cada documento en el momento de aceptar. */
class ConsentRecorder(private val ctx: Context) {
    private val hashes = HashMap<String, String>()

    fun hash(doc: LegalDoc): String = hashes.getOrPut(doc.id) { Legal.sha256(Legal.text(ctx, doc)) }

    suspend fun meta(): ConsentMeta {
        // Si el identificador no está disponible (sin red en el primer arranque) no se bloquea el registro: queda vacío.
        val fid = runCatching { FirebaseInstallations.getInstance().id.await() }.getOrDefault("")
        return ConsentMeta(fid, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", Locale.getDefault().toLanguageTag())
    }
}
