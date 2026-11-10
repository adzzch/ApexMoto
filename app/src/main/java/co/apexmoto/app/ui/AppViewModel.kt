@file:OptIn(ExperimentalCoroutinesApi::class)

package co.apexmoto.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.apexmoto.app.AppContainer
import co.apexmoto.app.auth.SignInCancelled
import co.apexmoto.app.core.addDays
import co.apexmoto.app.core.today
import co.apexmoto.app.data.*
import co.apexmoto.app.legal.AcceptMethod
import co.apexmoto.app.legal.Legal
import co.apexmoto.app.legal.LegalDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import co.apexmoto.app.maps.NavSession
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel único de la app. Expone los flujos de Firestore (solo activos con sesión iniciada)
 * y el estado compartido entre pantallas. Las pantallas llaman a vm.act { vm.repo.xxx() }.
 */
class AppViewModel(val container: AppContainer) : ViewModel() {
    val repo = container.repo

    val user = MutableStateFlow<AppUser?>(null)
    var ready by mutableStateOf(false); private set
    /** Cuenta autenticada (Google o teléfono) que aún debe elegir su rol. */
    var pending by mutableStateOf<PendingProfile?>(null); private set
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    // Estado compartido entre pantallas
    var navSession by mutableStateOf<NavSession?>(null)
    var mapType by mutableIntStateOf(container.prefs.mapType)
    var voice by mutableStateOf(container.prefs.voice)
    var rentMode by mutableStateOf("moto")
    var rentFrom by mutableStateOf(addDays(today(), 2))
    var rentTo by mutableStateOf(addDays(today(), 4))
    var preRoute by mutableStateOf("")
    /** true cuando la persona ya autorizó el uso de la ubicación (aviso vigente). Sin esto la app no la consulta. */
    var locationConsented by mutableStateOf(false); private set

    init {
        viewModelScope.launch { user.collect { locationConsented = it != null && Legal.isCurrent(it.legal, Legal.LOCATION) } }
        viewModelScope.launch {
            try {
                user.value = repo.currentUser()
                if (user.value == null) pending = repo.authInfo()
            } catch (_: Exception) {}
            ready = true
        }
    }

    fun toast(m: String) { messages.tryEmit(m) }

    fun act(ok: String? = null, block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block(); ok?.let { toast(it) } } catch (e: SignInCancelled) {
            // La persona cerró el selector de cuentas: no se muestra nada.
        } catch (e: kotlinx.coroutines.CancellationException) { throw e   // tarea cancelada (se cerró la pantalla): no es un error
        } catch (e: com.google.firebase.auth.FirebaseAuthException) { toast(authError(e.errorCode))
        } catch (e: com.google.firebase.FirebaseNetworkException) { toast("Sin conexión. Revisa tu internet e inténtalo de nuevo.")
        } catch (e: FirebaseFirestoreException) {
            // Las reglas del servidor rechazaron la operación: no es tuya, ya cambió de estado o la repetiste muy rápido.
            toast(when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No se pudo completar. Si acabas de hacer lo mismo, espera unos segundos e inténtalo de nuevo."
                FirebaseFirestoreException.Code.UNAVAILABLE -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
                else -> "Algo salió mal. Inténtalo de nuevo."
            })
        } catch (e: Exception) { toast(e.message ?: "Algo salió mal. Inténtalo de nuevo.") }
    }

    private fun <T> byUser(f: (AppUser) -> Flow<List<T>>): StateFlow<List<T>> =
        user.flatMapLatest { u -> if (u == null) flowOf(emptyList()) else f(u) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val venues = byUser { repo.venues() }
    val bikes = byUser { repo.bikes() }
    val drivers = byUser { repo.drivers() }
    val rodadas = byUser { repo.rodadas() }
    val myOrders = byUser { repo.ordersFrom(it.id) }
    val shopOrders = byUser { repo.ordersOfOwner(it.id) }
    val reqFrom = byUser { repo.requestsFrom(it.id) }
    val reqTo = byUser { repo.requestsTo(it.id) }
    val shopCoupons = byUser { repo.couponsOfOwner(it.id) }
    val sos = byUser { if (it.role == Role.WORKSHOP) repo.sosForWorkshop(it.id) else repo.sosMine(it.id) }
    val savedRoutes = byUser { repo.routesOf(it.id) }

    // Sesión
    private suspend fun afterAuth() {
        val u = repo.currentUser()
        if (u != null) { user.value = u; pending = null } else pending = repo.authInfo()
    }

    fun login(email: String, pw: String) = act { repo.signInEmail(email, pw); afterAuth() }
    fun resetPassword(email: String) = act("Te enviamos un enlace a $email para cambiar tu contraseña") { repo.resetPassword(email) }

    private fun authError(code: String) = when (code) {
        "ERROR_INVALID_EMAIL" -> "Ese correo no es válido."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Correo o contraseña incorrectos."
        "ERROR_USER_NOT_FOUND" -> "No hay una cuenta con ese correo. Crea una abajo."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ese correo ya tiene cuenta. Ingresa con él."
        "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> "La contraseña no es segura: usa mínimo 8 caracteres con una mayúscula, un número y un carácter especial."
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera un momento y vuelve a intentarlo."
        "ERROR_INVALID_VERIFICATION_CODE" -> "El código no es correcto. Revísalo e inténtalo de nuevo."
        "ERROR_SESSION_EXPIRED" -> "El código venció. Pide uno nuevo."
        "ERROR_USER_DISABLED" -> "Esta cuenta está desactivada."
        else -> "No pudimos completar el ingreso. Inténtalo de nuevo."
    }
    fun register(name: String, email: String, pw: String, role: String, biz: String, corridor: String) =
        act { user.value = repo.register(name, email, pw, role, biz, corridor, AcceptMethod.REGISTER); pending = null }
    fun signInCredential(c: AuthCredential) = act { repo.signInWithCredential(c); afterAuth() }
    fun googleSignIn(activityCtx: Context) = act {
        val token = container.google.idToken(activityCtx)
        repo.signInWithCredential(GoogleAuthProvider.getCredential(token, null)); afterAuth()
    }
    fun verifyCode(verificationId: String, code: String) = signInCredential(PhoneAuthProvider.getCredential(verificationId, code.trim()))
    fun completeProfile(name: String, role: String, biz: String, corridor: String) =
        act { user.value = repo.completeProfile(name, role, biz, corridor, AcceptMethod.FIRST_LOGIN); pending = null }
    fun logout() { repo.logout(); user.value = null; pending = null }
    /** Para activar el modo guía la pantalla muestra antes las condiciones del rol; aquí queda el registro de esa aceptación. */
    fun setDriverMode(on: Boolean) = act {
        val u = user.value ?: return@act
        user.value = repo.setRole(u, if (on) Role.DRIVER else Role.RIDER, AcceptMethod.ROLE)
    }

    // Legal y privacidad
    fun acceptDocs(docs: List<LegalDoc>, method: String) = act {
        val u = user.value ?: return@act
        user.value = repo.acceptDocs(u, docs, method)
    }
    /** La persona tocó "Aceptar" en el aviso de ubicación: se habilita de inmediato y se guarda el registro. */
    fun acceptLocation() { locationConsented = true; acceptDocs(listOf(Legal.doc(Legal.LOCATION)), AcceptMethod.LOCATION) }
    fun updateName(name: String) = act("Nombre actualizado") { val u = user.value ?: return@act; user.value = repo.updateName(u, name) }
    fun deleteAccount() = act {
        val u = user.value ?: return@act
        repo.deleteAccount(u); user.value = null; pending = null
        toast("Tu cuenta fue eliminada.")
    }

    fun updateMapType(t: Int) { mapType = t; container.prefs.mapType = t }
    fun updateVoice(v: Boolean) { voice = v; container.prefs.voice = v }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(c) as T
    }
}
