package co.apexmoto.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * App Check en producción: Play Integrity comprueba que la petición viene de la app original, instalada desde
 * Google Play, en un dispositivo íntegro. Con la verificación exigida en Firestore, un script o una app modificada no pasan.
 */
object AppCheckInstaller {
    fun install() = FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
