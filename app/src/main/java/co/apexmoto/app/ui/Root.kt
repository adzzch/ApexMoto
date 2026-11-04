@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.apexmoto.app.core.C
import co.apexmoto.app.data.AppUser
import co.apexmoto.app.data.PendingProfile
import co.apexmoto.app.data.Role
import co.apexmoto.app.ui.theme.*

data class NavTab(val route: String, val icon: ImageVector, val label: String)

fun NavController.chat(thread: String, title: String, to: String? = null) =
    navigate("chat?thread=${Uri.encode(thread)}&title=${Uri.encode(title)}&to=${Uri.encode(to.orEmpty())}")

fun tabsFor(role: String): List<NavTab> = when (role) {
    Role.SHOP -> listOf(NavTab("cpan", Icons.Filled.Dashboard, "Resumen"), NavTab("cped", Icons.Filled.ReceiptLong, "Pedidos"),
        NavTab("cmenu", Icons.Filled.Restaurant, "Menú"), NavTab("cneg", Icons.Filled.Storefront, "Negocio"), NavTab("ccan", Icons.Filled.QrCode, "Canjes"))
    Role.OWNER -> listOf(NavTab("ofl", Icons.Filled.TwoWheeler, "Flota"), NavTab("oreq", Icons.Filled.Inbox, "Solicitudes"), NavTab("oing", Icons.Filled.Payments, "Ingresos"))
    else -> buildList {
        add(NavTab("rut", Icons.Filled.Directions, "Rutas")); add(NavTab("com", Icons.Filled.Storefront, "Comercios"))
        add(NavTab("grp", Icons.Filled.Groups, "Comunidad")); add(NavTab("rent", Icons.Filled.TwoWheeler, "Rentar")); add(NavTab("tal", Icons.Filled.Build, "Taller"))
        if (role == Role.DRIVER) add(NavTab("dreq", Icons.Filled.SportsMotorsports, "Salidas"))
    }
}

@Composable
fun ApexRoot(vm: AppViewModel) {
    val user by vm.user.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    Box(Modifier.fillMaxSize()) {
        val u = user
        when {
            !vm.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            u == null && vm.pending != null -> RoleSelectScreen(vm, vm.pending!!)
            u == null -> AuthScreen(vm)
            else -> MainScaffold(vm, u)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

@Composable
fun MainScaffold(vm: AppViewModel, u: AppUser) {
    val nav = rememberNavController()
    val tabs = tabsFor(u.role)
    val start = tabs.first().route
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val isTab = route == null || tabs.any { it.route == route }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isTab) tabs.firstOrNull { it.route == route }?.label ?: "ApexMoto" else titleFor(route), fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) },
                navigationIcon = { if (!isTab) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (isTab) {
                        IconButton({ nav.navigate("profile") }) { Icon(Icons.Filled.Person, "Perfil") }
                        IconButton({ nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Configuración") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route, label = { Text(t.label, maxLines = 1) }, icon = { Icon(t.icon, t.label) },
                        onClick = { nav.navigate(t.route) { popUpTo(start) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    )
                }
            }
        },
        containerColor = Bg,
    ) { pad ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(pad)) {
            // Rider / conductor
            composable("rut") { RoutesScreen(vm, nav, u) }
            composable("com") { VenuesScreen(vm, nav, u) }
            composable("grp") { CommunityScreen(vm, u) }
            composable("rent") { RentScreen(vm, nav, u) }
            composable("tal") { WorkshopScreen(vm, u) }
            composable("dreq") { DriverTripsScreen(vm, nav, u) }
            // Comercio
            composable("cpan") { ShopDashboard(vm, u) }
            composable("cped") { ShopOrdersScreen(vm, nav, u) }
            composable("cmenu") { ShopMenuScreen(vm, u) }
            composable("cneg") { ShopBusinessScreen(vm, u) }
            composable("ccan") { ShopRedeemScreen(vm, u) }
            // Dueño de moto
            composable("ofl") { OwnerFleetScreen(vm, u) }
            composable("oreq") { OwnerRequestsScreen(vm, nav, u) }
            composable("oing") { EarningsScreen(vm, u) }
            // Comunes
            composable("profile") { ProfileScreen(vm, nav, u) }
            composable("settings") { SettingsScreen(vm) }
            composable("dsv") { DriverServiceScreen(vm, u) }
            composable("ding") { EarningsScreen(vm, u) }
            composable("navigate") { NavigationScreen(vm) }
            composable("menu/{id}") { b -> MenuScreen(vm, nav, u, b.arguments?.getString("id").orEmpty()) }
            composable("bike/{id}") { b -> BikeDetailScreen(vm, nav, u, b.arguments?.getString("id").orEmpty()) }
            composable(
                "chat?thread={thread}&title={title}&to={to}",
                arguments = listOf("thread", "title", "to").map { navArgument(it) { type = NavType.StringType; defaultValue = "" } },
            ) { b -> ChatScreen(vm, u, b.arguments?.getString("thread").orEmpty()) }
        }
    }
}

private fun titleFor(route: String?) = when {
    route == null -> "ApexMoto"
    route.startsWith("menu") -> "Menú"
    route.startsWith("bike") -> "Moto"
    route.startsWith("chat") -> "Chat"
    route == "navigate" -> "Guiado"
    route == "profile" -> "Mi perfil"
    route == "settings" -> "Configuración"
    route == "dsv" -> "Mi servicio"
    route == "ding" -> "Ganancias"
    else -> "ApexMoto"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AuthScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("login") } // login | register | phone
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pw by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.RIDER) }
    var biz by rememberSaveable { mutableStateOf("") }
    var cor by rememberSaveable { mutableStateOf("calima") }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Screen {
        Spacer(Modifier.height(24.dp))
        Text("ApexMoto", style = MaterialTheme.typography.headlineSmall, color = Orange)
        Sub("Rutas, comercios aliados y servicios para moteros.")
        Card2 {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Ingresar", mode == "login") { mode = "login" }
                Chip("Crear cuenta", mode == "register") { mode = "register" }
                Chip("Teléfono", mode == "phone") { mode = "phone" }
            }
            if (mode == "phone") {
                val vid = verificationId
                Field("Celular", phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, kb = KeyboardType.Phone, hint = "3001234567")
                Sub("Si no escribes el código del país, usamos +57 (Colombia).")
                if (vid == null) {
                    Btn("Enviar código por SMS", Modifier.fillMaxWidth(), enabled = phone.filter { it.isDigit() }.length >= 10 && !sending) {
                        val activity = ctx.findActivity()
                        if (activity == null) vm.toast("No se pudo iniciar la verificación. Cierra y abre la app.") else {
                            sending = true
                            val full = if (phone.startsWith("+")) phone else "+57$phone"
                            vm.container.phone.send(activity, full,
                                onCode = { verificationId = it; sending = false; vm.toast("Te enviamos un código por SMS") },
                                onAuto = { sending = false; vm.signInCredential(it) },
                                onError = { sending = false; vm.toast(it) })
                        }
                    }
                } else {
                    Field("Código de verificación", code, { code = it.filter(Char::isDigit) }, kb = KeyboardType.Number)
                    Btn("Verificar y entrar", Modifier.fillMaxWidth(), enabled = code.length >= 6 && !busy) {
                        busy = true
                        vm.verifyCode(vid, code).invokeOnCompletion { busy = false }
                    }
                    TextButton({ verificationId = null; code = "" }) { Text("Cambiar de número") }
                }
            } else {
                if (mode == "register") {
                    Field("Nombre", name, { name = it })
                    Lbl("¿Cómo vas a usar Apex?")
                    Drop("", listOf(Role.RIDER to "Rider (rutas, comercios, rentas)", Role.SHOP to "Comercio aliado", Role.OWNER to "Dueño de moto", Role.DRIVER to "Conductor guía"), role, { role = it })
                    if (role == Role.SHOP) {
                        Field("Nombre del negocio", biz, { biz = it })
                        Drop("Corredor", C.CORRIDORS.map { it.key to it.name }, cor, { cor = it })
                    }
                }
                Field("Correo", email, { email = it }, kb = KeyboardType.Email)
                Field("Contraseña (mínimo 6 caracteres)", pw, { pw = it }, password = true)
                Btn(if (mode == "register") "Crear cuenta" else "Ingresar", Modifier.fillMaxWidth(), enabled = !busy) {
                    busy = true
                    val j = if (mode == "register") vm.register(name, email, pw, role, biz, cor) else vm.login(email, pw)
                    j.invokeOnCompletion { busy = false }
                }
            }
        }
        Lbl("o continúa con")
        Btn("Continuar con Google", Modifier.fillMaxWidth(), Icons.Filled.AccountCircle, kind = BK.Ghost) { vm.googleSignIn(ctx) }
    }
}

/** Primer ingreso con Google o teléfono: la persona elige su rol y completa su perfil. */
@Composable
fun RoleSelectScreen(vm: AppViewModel, p: PendingProfile) {
    var name by remember { mutableStateOf(p.name) }
    var role by remember { mutableStateOf(Role.RIDER) }
    var biz by remember { mutableStateOf("") }
    var cor by remember { mutableStateOf("calima") }
    var busy by remember { mutableStateOf(false) }
    val roles = listOf(
        Triple(Role.RIDER, "Rider", "Rutas, comercios aliados, renta de motos y taller móvil"),
        Triple(Role.SHOP, "Comercio aliado", "Publica tu negocio y menú, y recibe pedidos y cupones"),
        Triple(Role.OWNER, "Dueño de moto", "Renta tus motos a otros riders"),
        Triple(Role.DRIVER, "Conductor guía", "Ofrece salidas guiadas a otros riders"),
    )
    Screen {
        Spacer(Modifier.height(24.dp))
        Text("Un último paso", style = MaterialTheme.typography.headlineSmall, color = Orange)
        Sub("Entraste como ${p.contact.ifBlank { "nuevo usuario" }}. Elige cómo vas a usar ApexMoto.")
        Card2 {
            Field("Tu nombre", name, { name = it })
            roles.forEach { (k, title, desc) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(role == k, { role = k })
                    Column(Modifier.weight(1f)) { Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold); Sub(desc) }
                }
            }
            if (role == Role.SHOP) {
                Field("Nombre del negocio", biz, { biz = it })
                Drop("Corredor", C.CORRIDORS.map { it.key to it.name }, cor, { cor = it })
            }
            Btn("Continuar", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = name.isNotBlank() && !busy && (role != Role.SHOP || biz.isNotBlank())) {
                busy = true
                vm.completeProfile(name, role, biz, cor).invokeOnCompletion { busy = false }
            }
        }
        TextButton({ vm.logout() }) { Text("Usar otra cuenta") }
    }
}
