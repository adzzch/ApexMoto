@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.material.icons.outlined.Lock
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

/** Pasos del ingreso. La tarjeta cambia de contenido deslizándose y crece o se encoge suave, sin saltos. */
private enum class AuthStep { Login, Register, Phone, Code }

@Composable
fun AuthScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var step by rememberSaveable { mutableStateOf(AuthStep.Login) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pw by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf(Role.RIDER) }
    var biz by rememberSaveable { mutableStateOf("") }
    var cor by rememberSaveable { mutableStateOf("calima") }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sentTo by remember { mutableStateOf<String?>(null) }     // aviso "código enviado"
    var smsError by remember { mutableStateOf<String?>(null) }   // aviso de error al enviar
    val emailOk = email.trim().matches(Regex("^\\S+@\\S+\\.\\S+$"))
    val digits = phone.filter { it.isDigit() }

    // Entrada de la marca: el logo cae en su lugar con un pequeño rebote.
    val reduce = reducedMotion()
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }

    fun sendSms() {
        val activity = ctx.findActivity()
        if (activity == null) { vm.toast("No se pudo iniciar la verificación. Cierra y abre la app."); return }
        keyboard?.hide()   // cierra el teclado sin mover el foco (moverlo hacía que un Enter físico tocara "Volver")
        busy = true
        val full = if (phone.trim().startsWith("+")) "+" + digits else "+57$digits"
        vm.container.phone.send(activity, full,
            onCode = { verificationId = it; busy = false; code = ""; sentTo = prettyPhone(full) },
            onAuto = { busy = false; vm.signInCredential(it) },
            onError = { busy = false; smsError = smsErrorText(it) })
    }
    fun login() { focus.clearFocus(); busy = true; vm.login(email, pw).invokeOnCompletion { busy = false } }
    fun register() { focus.clearFocus(); busy = true; vm.register(name, email, pw, role, biz, cor).invokeOnCompletion { busy = false } }
    fun verify(c: String) { val vid = verificationId ?: return; focus.clearFocus(); busy = true; vm.verifyCode(vid, c).invokeOnCompletion { busy = false } }

    BackHandler(step != AuthStep.Login) { step = if (step == AuthStep.Code) AuthStep.Phone else AuthStep.Login }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFFE6D6), Bg), center = Offset(540f, 0f), radius = 1400f))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Marca
            Column(
                Modifier.fillMaxWidth().padding(top = 20.dp).graphicsLayer {
                    val e = enter.value
                    alpha = 0.3f + 0.7f * e.coerceIn(0f, 1f); translationY = (1f - e) * -40f; scaleX = 0.85f + 0.15f * e; scaleY = scaleX
                },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BrandMark(60.dp)
                Spacer(Modifier.height(6.dp))
                Text("ApexMoto", fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Text("La red de rutas, comercios y pilotos para moteros.", color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            // Tarjeta que cambia de paso
            Card2(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)), padding = 20.dp) {
                AnimatedContent(step, label = "auth", transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(320, easing = EaseOutExpo)) { it / 4 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 4 * dir } + fadeOut(tween(140))) using SizeTransform(clip = false)
                }) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (s) {
                            AuthStep.Login -> {
                                StepTitle("Ingresar", "Qué bueno verte de nuevo en la ruta.")
                                Field("Correo", email, { email = it.trim() }, kb = KeyboardType.Email, hint = "tucorreo@email.com",
                                    leading = Icons.Filled.MailOutline, ime = ImeAction.Next, onIme = { focus.moveFocus(FocusDirection.Down) })
                                Field("Contraseña", pw, { pw = it }, hint = "Tu contraseña", password = !showPw, kb = KeyboardType.Password,
                                    leading = Icons.Outlined.Lock, ime = ImeAction.Done, onIme = { if (emailOk && pw.length >= 6) login() },
                                    trailing = { PwToggle(showPw) { showPw = !showPw } })
                                Text("¿Olvidaste tu contraseña?", Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp))
                                    .clickable { if (emailOk) vm.resetPassword(email) else vm.toast("Escribe tu correo arriba y te enviamos un enlace para cambiarla.") }
                                    .padding(horizontal = 6.dp, vertical = 4.dp), color = CyanDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Btn("Entrar", Modifier.fillMaxWidth(), enabled = emailOk && pw.length >= 6, loading = busy) { login() }
                            }
                            AuthStep.Register -> {
                                StepTitle("Crear cuenta", "Únete a la red motera en un minuto.", onBack = { step = AuthStep.Login })
                                Field("Nombre", name, { name = it }, hint = "Cómo te llaman en la ruta", leading = Icons.Filled.PersonOutline,
                                    ime = ImeAction.Next, onIme = { focus.moveFocus(FocusDirection.Down) })
                                Field("Correo", email, { email = it.trim() }, kb = KeyboardType.Email, hint = "tucorreo@email.com",
                                    leading = Icons.Filled.MailOutline, ime = ImeAction.Next, onIme = { focus.moveFocus(FocusDirection.Down) })
                                Field("Contraseña", pw, { pw = it }, hint = "Mínimo 6 caracteres", password = !showPw, kb = KeyboardType.Password,
                                    leading = Icons.Outlined.Lock, ime = ImeAction.Done, onIme = { focus.clearFocus() },
                                    trailing = { PwToggle(showPw) { showPw = !showPw } })
                                Lbl("¿Cómo vas a usar ApexMoto?")
                                RoleGrid(role) { role = it }
                                AnimatedVisibility(role == Role.SHOP) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Field("Nombre del negocio", biz, { biz = it }, leading = Icons.Filled.Storefront)
                                        Drop("Corredor", C.CORRIDORS.map { it.key to it.name }, cor, { cor = it })
                                    }
                                }
                                Btn("Crear cuenta", Modifier.fillMaxWidth(), loading = busy,
                                    enabled = name.isNotBlank() && emailOk && pw.length >= 6 && (role != Role.SHOP || biz.isNotBlank())) { register() }
                            }
                            AuthStep.Phone -> {
                                StepTitle("Entrar con tu celular", "Te enviamos un código por SMS. Sin contraseñas.", onBack = { step = AuthStep.Login })
                                Field("Número de celular", phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) }, kb = KeyboardType.Phone,
                                    hint = "300 123 4567", leading = Icons.Filled.PhoneIphone, prefix = if (phone.trim().startsWith("+")) null else "+57 ",
                                    ime = ImeAction.Done, onIme = { if (digits.length >= 10) sendSms() })
                                Btn("Enviar código", Modifier.fillMaxWidth(), Icons.Filled.Sms, enabled = digits.length >= 10, loading = busy) { sendSms() }
                                Lbl("Si tu número no es de Colombia, escríbelo con su indicativo, por ejemplo +1.")
                            }
                            AuthStep.Code -> {
                                val shown = (if (phone.trim().startsWith("+")) "+" else "+57 ") + digits
                                StepTitle("Revisa tus mensajes", "Escribe el código de 6 dígitos que enviamos al $shown.", onBack = { step = AuthStep.Phone })
                                OtpField(code) { c -> code = c; if (c.length == 6 && !busy) verify(c) }
                                Btn("Verificar y entrar", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = code.length == 6, loading = busy) { verify(code) }
                                Text("¿No te llegó? Reenviar código", Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !busy) { sendSms() }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = CyanDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Otras formas de entrar (solo en Ingresar, para no distraer en los demás pasos)
            AnimatedVisibility(step == AuthStep.Login, enter = fadeIn(tween(260)) + expandVertically(tween(300, easing = EaseOutExpo)), exit = fadeOut(tween(120)) + shrinkVertically(tween(220))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = Surf4); Lbl("  o entra con  "); HorizontalDivider(Modifier.weight(1f), color = Surf4)
                    }
                    GoogleButton { vm.googleSignIn(ctx) }
                    AltButton("Continuar con celular", Icons.Filled.PhoneIphone) { step = AuthStep.Phone }
                }
            }

            Spacer(Modifier.height(4.dp))
            // Enlace al pie: crear cuenta o volver a ingresar
            val (q, a) = if (step == AuthStep.Register) "¿Ya tienes cuenta?" to "Ingresar" else "¿No tienes cuenta?" to "Crear una"
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(q, color = Muted, fontSize = 14.sp)
                Text(a, Modifier.clip(RoundedCornerShape(8.dp)).clickable { step = if (step == AuthStep.Register) AuthStep.Login else AuthStep.Register }
                    .padding(horizontal = 6.dp, vertical = 8.dp), color = OrangeDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    sentTo?.let { to ->
        NoticeDialog(
            icon = Icons.Filled.MarkChatRead, tint = Green, bg = GreenSoft, title = "Código enviado",
            text = "Enviamos un código de 6 dígitos por SMS al", highlight = to,
            foot = "Puede tardar hasta un minuto en llegar.",
            onOk = { sentTo = null; step = AuthStep.Code },
        )
    }
    smsError?.let { msg ->
        NoticeDialog(
            icon = Icons.Filled.SmsFailed, tint = Red, bg = RedSoft, title = "No pudimos enviar el código",
            text = msg, onOk = { smsError = null },
        )
    }
}

/** +573158724062 → +57 315 872 4062 */
private fun prettyPhone(full: String): String {
    val d = full.filter { it.isDigit() }
    return if (full.startsWith("+57") && d.length == 12) "+57 ${d.substring(2, 5)} ${d.substring(5, 8)} ${d.substring(8)}" else full
}

/** Traduce los errores de Firebase Phone Auth a algo que la persona entienda y pueda resolver. */
private fun smsErrorText(raw: String): String = when {
    "BILLING_NOT_ENABLED" in raw ->
        "El envío de SMS a números reales no está activo en este proyecto: Firebase lo exige con el plan de facturación. " +
            "Mientras tanto, entra con tu correo o con Google, o usa un número de prueba configurado en Firebase."
    "TOO_MANY_REQUESTS" in raw || "blocked" in raw.lowercase() ->
        "Hiciste demasiados intentos desde este dispositivo. Espera unos minutos y vuelve a intentarlo."
    "INVALID_PHONE_NUMBER" in raw || "format" in raw.lowercase() ->
        "Ese número no parece válido. Revisa que tenga 10 dígitos, por ejemplo 300 123 4567."
    "QUOTA_EXCEEDED" in raw || "quota" in raw.lowercase() ->
        "Se alcanzó el límite de SMS por hoy. Entra con tu correo o con Google, o inténtalo mañana."
    "network" in raw.lowercase() -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    else -> "Algo falló al enviar el SMS. Inténtalo de nuevo en un momento o entra con tu correo o con Google."
}

/** Aviso centrado con ícono, mensaje y un solo botón Aceptar. Se ve encima del teclado. */
@Composable
private fun NoticeDialog(icon: ImageVector, tint: Color, bg: Color, title: String, text: String, highlight: String? = null, foot: String? = null, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOk, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
        icon = { Box(Modifier.size(56.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(28.dp), tint = tint) } },
        title = { Text(title, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                if (highlight != null) Text(highlight, color = Ink, fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                if (foot != null) Text(foot, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Btn("Aceptar", Modifier.fillMaxWidth()) { onOk() } },
    )
}

@Composable
private fun StepTitle(title: String, sub: String, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Surf3).clickable(onClickLabel = "Volver", onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", Modifier.size(20.dp), tint = Ink)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(sub, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PwToggle(shown: Boolean, onClick: () -> Unit) {
    IconButton(onClick) { Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (shown) "Ocultar contraseña" else "Mostrar contraseña", tint = Muted) }
}

/** Botón alterno con el mismo formato del de Google, para que ambas opciones pesen igual. */
@Composable
private fun AltButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick, Modifier.fillMaxWidth().height(48.dp).pressScale(src), interactionSource = src,
        shape = CircleShape, border = BorderStroke(1.dp, Color(0xFF747775)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = CyanDeep); Spacer(Modifier.width(12.dp))
        Text(text, fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/** Seis casillas para el código SMS. El teclado se abre solo y el código del SMS se puede pegar o autocompletar. */
@Composable
private fun OtpField(code: String, onChange: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BasicTextField(
        code, { onChange(it.filter(Char::isDigit).take(6)) }, Modifier.fillMaxWidth().focusRequester(fr).semantics { contentDescription = "Código de verificación" },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val ch = code.getOrNull(i)
                    val active = i == code.length
                    val border by animateColorAsState(if (active) CyanDeep else if (ch != null) Surf4 else Color.Transparent, tween(160), label = "otp")
                    val s by animateFloatAsState(if (ch != null) 1f else 0.6f, spring(dampingRatio = 0.5f), label = "otpS")
                    Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).background(Surf0).border(2.dp, border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        if (ch != null) Text("$ch", Modifier.graphicsLayer { scaleX = s; scaleY = s }, fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Ink)
                    }
                }
            }
        },
    )
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
