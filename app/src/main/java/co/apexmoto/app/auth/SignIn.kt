package co.apexmoto.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/** La persona cerró el selector de cuentas: no es un error que haya que mostrar. */
class SignInCancelled : Exception()

/** Inicio de sesión con Google mediante Credential Manager. Devuelve el ID token para Firebase. */
class GoogleSignIn {
    suspend fun idToken(activityCtx: Context): String {
        val resId = activityCtx.resources.getIdentifier("default_web_client_id", "string", activityCtx.packageName)
        if (resId == 0) error(
            "Falta el ID de cliente web de Google. En Firebase agrega el SHA-1, activa Google en Authentication y " +
                "vuelve a descargar google-services.json."
        )
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activityCtx.getString(resId))
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(activityCtx).getCredential(activityCtx, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelled()
        } catch (e: NoCredentialException) {
            error("No hay una cuenta de Google en este dispositivo. Agrégala en los ajustes del celular o del emulador e inténtalo de nuevo.")
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("No se pudo obtener la cuenta de Google")
    }
}

/** Verificación por SMS con Firebase Phone Auth. */
class PhoneAuth(private val auth: FirebaseAuth) {
    fun send(
        activity: Activity, phone: String,
        onCode: (String) -> Unit, onAuto: (PhoneAuthCredential) -> Unit, onError: (String) -> Unit,
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = onAuto(credential)
            override fun onVerificationFailed(e: FirebaseException) = onError(e.message ?: "No se pudo enviar el código")
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) = onCode(verificationId)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity).setCallbacks(callbacks).build()
        )
    }
}
