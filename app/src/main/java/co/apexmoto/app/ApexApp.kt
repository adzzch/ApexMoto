package co.apexmoto.app

import android.app.Application
import android.content.Context
import co.apexmoto.app.auth.GoogleSignIn
import co.apexmoto.app.auth.PhoneAuth
import co.apexmoto.app.data.Repo
import co.apexmoto.app.legal.ConsentRecorder
import co.apexmoto.app.maps.LocationHelper
import co.apexmoto.app.maps.RoutesClient
import com.google.firebase.auth.FirebaseAuth
import org.osmdroid.config.Configuration
import com.google.firebase.firestore.FirebaseFirestore

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("apex", Context.MODE_PRIVATE)
    var mapType: Int
        get() = sp.getInt("mapType", 1)
        set(v) { sp.edit().putInt("mapType", v).apply() }
    var voice: Boolean
        get() = sp.getBoolean("voice", true)
        set(v) { sp.edit().putBoolean("voice", v).apply() }
    /** Ciudad elegida a mano en Explorar (solo el nombre, nunca la ubicación GPS). Vacío = sin elegir. */
    /** Límite de la Ruta IA en este teléfono: última consulta, día y cuántas van ese día. */
    var aiLastAt: Long
        get() = sp.getLong("aiLastAt", 0L)
        set(v) { sp.edit().putLong("aiLastAt", v).apply() }
    var aiDay: String
        get() = sp.getString("aiDay", "").orEmpty()
        set(v) { sp.edit().putString("aiDay", v).apply() }
    var aiCount: Int
        get() = sp.getInt("aiCount", 0)
        set(v) { sp.edit().putInt("aiCount", v).apply() }
    /** Contactos de emergencia del S.O.S. (JSON). Solo en este teléfono: no se suben a Firestore. */
    var emergencyContacts: String
        get() = sp.getString("emergencyContacts", "[]").orEmpty()
        set(v) { sp.edit().putString("emergencyContacts", v).apply() }
    var exploreTown: String
        get() = sp.getString("exploreTown", "").orEmpty()
        set(v) { sp.edit().putString("exploreTown", v).apply() }
}

/** Contenedor de dependencias (inyección manual, sin Hilt, para mantener el proyecto simple). */
class AppContainer(ctx: Context) {
    val consent = ConsentRecorder(ctx)
    val repo = Repo(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance(), consent)
    val routes = RoutesClient(BuildConfig.ORS_API_KEY)
    val location = LocationHelper(ctx)
    val prefs = Prefs(ctx)
    val google = GoogleSignIn()
    val phone = PhoneAuth(repo.auth)
    val places = co.apexmoto.app.data.PlacesLoader(ctx.applicationContext)
    val intercom = co.apexmoto.app.voice.Intercom(ctx.applicationContext)
}

class ApexApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        // Mapa OpenStreetMap: identifica la app ante el servidor de teselas y guarda la caché en la carpeta privada.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = java.io.File(cacheDir, "tiles")
        }
        // App Check (antifraude): solo se activa si APP_CHECK_ENABLED=true en local.properties. Ver docs/seguridad.md.
        if (BuildConfig.APP_CHECK_ENABLED) AppCheckInstaller.install()
        container = AppContainer(this)
    }
}
