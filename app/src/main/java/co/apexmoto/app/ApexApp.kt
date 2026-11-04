package co.apexmoto.app

import android.app.Application
import android.content.Context
import co.apexmoto.app.auth.GoogleSignIn
import co.apexmoto.app.auth.PhoneAuth
import co.apexmoto.app.data.Repo
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
}

/** Contenedor de dependencias (inyección manual, sin Hilt, para mantener el proyecto simple). */
class AppContainer(ctx: Context) {
    val repo = Repo(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
    val routes = RoutesClient(BuildConfig.ORS_API_KEY)
    val location = LocationHelper(ctx)
    val prefs = Prefs(ctx)
    val google = GoogleSignIn()
    val phone = PhoneAuth(repo.auth)
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
        container = AppContainer(this)
    }
}
