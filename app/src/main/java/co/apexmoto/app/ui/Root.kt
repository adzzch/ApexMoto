@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
        NavTab("cmenu", Icons.Filled.RestaurantMenu, "Menú"), NavTab("cneg", Icons.Filled.Storefront, "Negocio"), NavTab("ccan", Icons.Filled.QrCodeScanner, "Canjes"))
    Role.OWNER -> listOf(NavTab("ofl", Icons.Filled.Garage, "Mi flota"), NavTab("oreq", Icons.Filled.Inbox, "Solicitudes"), NavTab("oing", Icons.Filled.Payments, "Ingresos"))
    else -> buildList {
        add(NavTab("rut", Icons.Filled.Route, "Rutas")); add(NavTab("com", Icons.Filled.Storefront, "Comercios"))
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
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)) { d ->
            Snackbar(d, containerColor = Ink, contentColor = Color.White, shape = RoundedCornerShape(14.dp))
        }
    }
}

@Composable
fun MainScaffold(vm: AppViewModel, u: AppUser) {
    val nav = rememberNavController()
    val tabs = tabsFor(u.role)
    val start = tabs.first().route
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val isTab = route == null || tabs.any { it.route == route }
    val section = if (isTab) tabs.firstOrNull { it.route == route }?.label ?: tabs.first().label else titleFor(route)
    Scaffold(
        topBar = { ApexHeader(isTab, section, spaceFor(u.role), onBack = { nav.popBackStack() }, onProfile = { nav.navigate("profile") }, onSettings = { nav.navigate("settings") }) },
        bottomBar = { if (isTab) ApexNavBar(tabs, route ?: start) { t -> nav.navigate(t) { popUpTo(start) { saveState = true }; launchSingleTop = true; restoreState = true } } },
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

private fun spaceFor(role: String) = when (role) { Role.SHOP -> "Espacio Negocio"; Role.OWNER -> "Espacio Dueño"; Role.DRIVER -> "Espacio Conductor"; else -> "Espacio Rider" }

/** Encabezado de marca: logo, nombre y "Espacio · sección". En pantallas internas muestra Volver y el título. */
@Composable
private fun ApexHeader(isTab: Boolean, section: String, space: String, onBack: () -> Unit, onProfile: () -> Unit, onSettings: () -> Unit) {
    Surface(color = Color.White) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isTab) {
                    Spacer(Modifier.width(4.dp)); BrandMark(36.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ApexMoto", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 20.sp)
                        AnimatedContent(section, label = "sec", transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }) togetherWith fadeOut(tween(120))
                        }) { sec -> Text("$space · $sec", color = OrangeDark, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1) }
                    }
                    IconButton(onSettings) { Icon(Icons.Filled.Settings, "Configuración", tint = Muted) }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Surf4).clickable(onClickLabel = "Perfil", onClick = onProfile), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, "Perfil", tint = CyanDeep)
                    }
                    Spacer(Modifier.width(4.dp))
                } else {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Ink) }
                    Text(section, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                }
            }
            HorizontalDivider(color = Surf3)
        }
    }
}

/** Barra inferior: el destino activo se pinta naranja y su ícono crece un poco. */
@Composable
private fun ApexNavBar(tabs: List<NavTab>, current: String, onGo: (String) -> Unit) {
    Column {
        HorizontalDivider(color = Surf3)
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            tabs.forEach { t ->
                val sel = current == t.route
                val scale by animateFloatAsState(if (sel) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "nav")
                NavigationBarItem(
                    selected = sel, onClick = { onGo(t.route) },
                    icon = { Icon(t.icon, null, Modifier.size(25.dp).graphicsLayer { scaleX = scale; scaleY = scale }) },
                    label = { Text(t.label, maxLines = 1, fontWeight = FontWeight.SemiBold, fontSize = if (tabs.size > 5) 10.sp else 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Orange, selectedTextColor = Orange, indicatorColor = OrangeSoft,
                        unselectedIconColor = Muted, unselectedTextColor = Muted,
                    ),
                )
            }
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
    // Entrada de la marca: el logo cae en su lugar con un pequeño rebote.
    val reduce = reducedMotion()
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFFE6D6), Bg), center = Offset(540f, 0f), radius = 1400f))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).graphicsLayer {
                    val e = enter.value
                    alpha = 0.3f + 0.7f * e.coerceIn(0f, 1f); translationY = (1f - e) * -40f; scaleX = 0.85f + 0.15f * e; scaleY = scaleX
                },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandMark(68.dp)
                Spacer(Modifier.height(4.dp))
                Text("ApexMoto", fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 32.sp)
                Text("La red de rutas, comercios y pilotos para moteros.", color = Muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Segmented(listOf(Triple("login", "Ingresar", null), Triple("register", "Crear cuenta", null), Triple("phone", "Celular", null)), mode) { mode = it }
            AnimatedContent(mode, label = "auth", transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) }) { m ->
                Card2 {
                    if (m == "phone") {
                        val vid = verificationId
                        Field("Celular", phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, kb = KeyboardType.Phone, hint = "300 123 4567")
                        Lbl("Si no escribes el indicativo, usamos +57 (Colombia).")
                        if (vid == null) {
                            Btn("Enviar código por SMS", Modifier.fillMaxWidth(), Icons.Filled.Sms, enabled = phone.filter { it.isDigit() }.length >= 10 && !sending) {
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
                            Field("Código de verificación", code, { code = it.filter(Char::isDigit) }, kb = KeyboardType.Number, hint = "6 dígitos")
                            Btn("Verificar y entrar", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = code.length >= 6 && !busy) {
                                busy = true
                                vm.verifyCode(vid, code).invokeOnCompletion { busy = false }
                            }
                            TextButton({ verificationId = null; code = "" }) { Text("Cambiar de número", color = CyanDeep) }
                        }
                    } else {
                        if (m == "register") {
                            Field("Nombre", name, { name = it }, hint = "Cómo te llaman en la ruta")
                            Lbl("¿Cómo vas a usar ApexMoto?")
                            RoleGrid(role) { role = it }
                            AnimatedVisibility(role == Role.SHOP) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field("Nombre del negocio", biz, { biz = it })
                                    Drop("Corredor", C.CORRIDORS.map { it.key to it.name }, cor, { cor = it })
                                }
                            }
                        }
                        Field("Correo", email, { email = it }, kb = KeyboardType.Email, hint = "tucorreo@email.com")
                        Field("Contraseña", pw, { pw = it }, password = true, hint = "Mínimo 6 caracteres")
                        Spacer(Modifier.height(2.dp))
                        Btn(if (m == "register") "Crear cuenta" else "Entrar", Modifier.fillMaxWidth(), enabled = !busy) {
                            busy = true
                            val j = if (m == "register") vm.register(name, email, pw, role, biz, cor) else vm.login(email, pw)
                            j.invokeOnCompletion { busy = false }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = Surf4); Lbl("  o continúa con  "); HorizontalDivider(Modifier.weight(1f), color = Surf4)
            }
            GoogleButton { vm.googleSignIn(ctx) }
        }
    }
}

/** Las cuatro formas de usar ApexMoto, en tarjetas de 2 columnas. */
@Composable
private fun RoleGrid(role: String, onPick: (String) -> Unit) {
    val roles = listOf(
        listOf(Role.RIDER, "Rider", "Rutas, comercios, comunidad y renta."),
        listOf(Role.SHOP, "Comercio aliado", "Publica tu menú y recibe pedidos."),
        listOf(Role.OWNER, "Dueño de moto", "Renta tus motos a otros riders."),
        listOf(Role.DRIVER, "Conductor guía", "Ofrece salidas guiadas."),
    )
    val icons = mapOf(Role.RIDER to Icons.Filled.TwoWheeler, Role.SHOP to Icons.Filled.Storefront, Role.OWNER to Icons.Filled.Garage, Role.DRIVER to Icons.Filled.SportsMotorsports)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        roles.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (k, t, d) ->
                    OptionTile(role == k, { onPick(k) }, Modifier.weight(1f).fillMaxHeight()) {
                        Icon(icons[k]!!, null, Modifier.size(26.dp), tint = CyanDeep)
                        Text(t, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text(d, color = Muted, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
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
    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandMark(52.dp)
        Text("Un último paso", fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text("Entraste como ${p.contact.ifBlank { "nuevo usuario" }}. Elige cómo vas a usar ApexMoto.", color = Muted, style = MaterialTheme.typography.bodyLarge)
        Card2 {
            Field("Tu nombre", name, { name = it })
            Lbl("¿Cómo vas a usar ApexMoto?")
            RoleGrid(role) { role = it }
            AnimatedVisibility(role == Role.SHOP) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Nombre del negocio", biz, { biz = it })
                    Drop("Corredor", C.CORRIDORS.map { it.key to it.name }, cor, { cor = it })
                }
            }
            Btn("Continuar", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.ArrowForward, enabled = name.isNotBlank() && !busy && (role != Role.SHOP || biz.isNotBlank())) {
                busy = true
                vm.completeProfile(name, role, biz, cor).invokeOnCompletion { busy = false }
            }
        }
        TextButton({ vm.logout() }) { Text("Usar otra cuenta", color = CyanDeep) }
    }
}
