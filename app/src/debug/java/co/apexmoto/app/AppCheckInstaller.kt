package co.apexmoto.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * App Check en desarrollo: usa un token de depuración. La primera vez, Logcat muestra el token
 * ("Enter this debug secret into the allow list…"); regístralo en Firebase → App Check → Apps → Administrar tokens de depuración.
 */
object AppCheckInstaller {
    fun install() = FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
