@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import co.apexmoto.app.maps.GeminiPlanner
import co.apexmoto.app.maps.AiGuard
import androidx.compose.animation.SizeTransform
import co.apexmoto.app.core.norm
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.apexmoto.app.core.C
import co.apexmoto.app.core.cop
import co.apexmoto.app.core.fmtMin
import co.apexmoto.app.data.*
import co.apexmoto.app.maps.*
import co.apexmoto.app.ui.theme.*
import kotlin.math.roundToInt

/**
 * Inicio del rider. Primero la portada (ciudad, "¿A dónde vamos?" y rutas recomendadas); al elegir Explorar o Ruta IA
 * se abre esa vista, con un botón (y el Atrás del teléfono) para volver a la portada.
 */
@Composable
fun RoutesScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    var tab by rememberSaveable { mutableStateOf("home") }
    BackHandler(tab != "home") { tab = if (tab == "bud") "ia" else "home" }
    Screen {
        if (tab != "home") Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(CircleShape).background(Color.White).clickable(onClickLabel = "Volver al inicio") { tab = "home" }.padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp), tint = Ink); Spacer(Modifier.width(6.dp))
                Text("Inicio", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                Segmented(listOf(Triple("exp", "Explorar", Icons.Filled.Explore), Triple("ia", "Ruta IA", Icons.Filled.AutoAwesome)), if (tab == "bud") "ia" else tab) { tab = it }
            }
        }
        AnimatedContent(tab, label = "rutas", transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) }) { t ->
            Column(verticalArrangement = Arrangement.spacedBy(Gap.between)) {
                when (t) {
                    "home" -> HomeSection(vm, nav, u) { tab = it }
                    "exp" -> ExploreSection(vm, nav, u)
                    "ia" -> { RouteAI(vm, nav, u); BudgetLink { tab = "bud" } }
                    else -> BudgetCalc()
                }
            }
        }
    }
}

/** Acceso discreto a la calculadora de presupuesto (antes era una pestaña más). */
@Composable
private fun BudgetLink(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Calcular presupuesto", onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Calculate, null, Modifier.size(18.dp), tint = CyanDeep); Spacer(Modifier.width(8.dp))
        Text("¿Cuánto cuesta la salida? Calcular presupuesto", color = CyanDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun ColumnScope.RouteAI(vm: AppViewModel, nav: NavController, u: AppUser) {
    val venues by vm.venues.collectAsState()
    val saved by vm.savedRoutes.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var cor by rememberSaveable { mutableStateOf("any") }
    var start by rememberSaveable { mutableStateOf("07:00") }
    var full by rememberSaveable { mutableStateOf(false) }
    var pace by rememberSaveable { mutableStateOf("m") }
    var big by rememberSaveable { mutableStateOf(true) }
    var open by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf("ia") }           // ia = Sorpréndeme · dest = yo elijo destino
    var dests by remember { mutableStateOf(listOf<PickedStop>()) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<GeminiPlanner.Plan?>(null) }
    val scope = rememberCoroutineScope()
    val ref = rememberRefPoint(vm)
    val catalog = rememberCatalog(vm)
    var extras by rememberSaveable { mutableIntStateOf(1) }
    var picking by remember { mutableStateOf(false) }
    var budget by remember { mutableFloatStateOf(60000f) }
    var cuisines by remember { mutableStateOf(setOf<String>()) }
    var cats by remember { mutableStateOf(setOf<String>()) }
    var result by remember { mutableStateOf<RoutePlanResult?>(null) }
    var tried by remember { mutableStateOf(false) }
    val chev by animateFloatAsState(if (open) 180f else 0f, tween(220), label = "chev")

    Card2 {
        H("¿Qué ruta quieres hacer?", Icons.Filled.AutoAwesome)
        Segmented(listOf(Triple("ia", "Sorpréndeme", Icons.Filled.AutoAwesome), Triple("dest", "Yo elijo destino", Icons.Filled.Place)), mode) { mode = it; result = null; tried = false; aiNote = null }
        AnimatedContent(mode, label = "modo", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) }) { md ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (md == "ia") {
                    Field("", text, { text = it.take(AiGuard.MAX_INPUT) }, lines = 3, hint = "Ej: primero al Km 18, luego almorzar y terminar en el Lago Calima")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tune, null, Modifier.size(18.dp), tint = Muted); Spacer(Modifier.width(8.dp))
                        Lbl("Personalizar", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ExpandMore, if (open) "Ocultar opciones" else "Mostrar opciones", Modifier.rotate(chev), tint = Ink)
                    }
                    AnimatedVisibility(open, enter = expandVertically(tween(320, easing = EaseOutExpo)) + fadeIn(tween(240)), exit = shrinkVertically(tween(220)) + fadeOut(tween(120))) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Drop("Zona", listOf("any" to "Donde sea") + C.CORRIDORS.map { it.key to it.name }, cor, { cor = it }, Modifier.weight(1f))
                                Field("Hora de salida", start, { start = it }, Modifier.weight(1f), hint = "07:00")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Drop("Duración", listOf("half" to "Medio día · 3", "full" to "Día completo · 5"), if (full) "full" else "half", { full = it == "full" }, Modifier.weight(1f))
                                Drop("Ritmo", listOf("t" to "Tranquilo", "m" to "Medio", "r" to "Rápido"), pace, { pace = it }, Modifier.weight(1f))
                            }
                            SwitchRow("Moto de alto cilindraje", "Afecta el cálculo de gasolina", big) { big = it }
                            Lbl("Tipo de parada")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                C.CATS.forEach { (k, v) -> Chip(v.first, k in cats, accent = Cyan) { cats = if (k in cats) cats - k else cats + k } }
                            }
                            Lbl("Tipo de comida")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                C.CUISINES.forEach { c -> Chip(c, c in cuisines, accent = Cyan) { cuisines = if (c in cuisines) cuisines - c else cuisines + c } }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Comida por persona", modifier = Modifier.weight(1f)); Num(cop(budget.roundToInt()), OrangeDark, 15) }
                            ApexSlider(budget, { budget = it }, 20000f..120000f, steps = 9)
                        }
                    }
                    Btn("Generar ruta con IA", Modifier.fillMaxWidth(), Icons.Filled.AutoAwesome, loading = aiBusy) {
                        val f = RouteFilters(text, cor, start, full, pace, big, cuisines, cats, budget.roundToLong())
                        requestAiRoute(vm, scope, catalog, ref, venues, f, onStart = { aiBusy = true; aiNote = null }) { r, note, ok ->
                            result = r; aiNote = note; tried = ok; aiBusy = false
                        }
                    }
                    Text("Usa Gemini de Google: lo que escribas y la ciudad aproximada se envían para armar la ruta. No escribas datos personales.",
                        color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                } else {
                    Lbl("¿A dónde quieres ir?")
                    // Destinos elegidos: lugares (miradores, pueblos…) y, aparte, restaurantes aliados.
                    val picked = dests.map { d ->
                        venues.firstOrNull { it.id == d.venueId }?.takeIf { d.food }
                            ?.copy(km = co.apexmoto.app.data.distanceKm(ref.latitude, ref.longitude, d.lat, d.lng).roundToInt())
                            ?: Venue(id = d.id, name = d.name, types = listOf("tour"), kind = d.subtitle.substringBefore(" · "), lat = d.lat, lng = d.lng,
                                km = co.apexmoto.app.data.distanceKm(ref.latitude, ref.longitude, d.lat, d.lng).roundToInt())
                    }
                    if (dests.isEmpty()) Text("Elige a dónde quieres ir: un mirador, un pueblo, una cascada… y si quieres, restaurantes aliados para parar.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    dests.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(if (d.food) Orange else CyanDeep), contentAlignment = Alignment.Center) {
                                if (d.food) Icon(Icons.Filled.Restaurant, null, Modifier.size(16.dp), tint = Color.White) else Num("${i + 1}", Color.White, 13)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(d.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Lbl(d.subtitle) }
                            IconButton({ dests = dests - d }) { Icon(Icons.Filled.Close, "Quitar ${d.name}", tint = Muted) }
                        }
                    }
                    Btn(if (dests.isEmpty()) "Elegir destino" else "Agregar o cambiar paradas", Modifier.fillMaxWidth(), Icons.Filled.AddLocationAlt, kind = BK.Ghost) { picking = true }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Hora de salida", start, { start = it }, Modifier.weight(1f), hint = "07:00")
                        Drop("Ritmo", listOf("t" to "Tranquilo", "m" to "Medio", "r" to "Rápido"), pace, { pace = it }, Modifier.weight(1f))
                    }
                    Lbl("Paradas para comer en el camino")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Ninguna", 1 to "Una", 2 to "Dos").forEach { (n, t) -> Chip(t, extras == n, accent = Cyan) { extras = n } }
                    }
                    AnimatedVisibility(extras > 0) {
                        Field("¿Qué se te antoja? (opcional)", text, { text = it }, hint = "Ej: algo típico y barato")
                    }
                    SwitchRow("Moto de alto cilindraje", "Afecta el cálculo de gasolina", big) { big = it }
                    Btn("Armar mi ruta", Modifier.fillMaxWidth(), Icons.Filled.Route, enabled = picked.isNotEmpty()) {
                        tried = true
                        result = RoutePlanner.generateTo(venues, picked, RouteFilters(text, "any", start, false, pace, big, cuisines, emptySet(), budget.roundToLong()), extras)
                    }
                }
            }
        }
    }

    if (picking) StopPickerSheet(vm, dests, title = "¿A dónde quieres ir?") { dests = it; picking = false }

    val r = result
    if (r != null) AiRouteCard(vm, nav, u, r, aiNote, start)
    else if (tried) Empty(if (mode == "dest") "Elige al menos un destino para armar la ruta." else "No encontré paradas con esos filtros. Prueba otra zona o quita algún filtro.", Icons.Filled.SearchOff)
    else Text("Escribe qué se te antoja y la IA te arma la ruta con lugares reales y comercios aliados cerca de ti.", color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

    if (saved.isNotEmpty()) {
        H("Mis rutas guardadas", Icons.Filled.Bookmark)
        saved.sortedByDescending { it.createdAt }.forEachIndexed { i, s ->
            Card2(index = i) {
                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Lbl("${s.km} km · ${s.mins / 60} h ${s.mins % 60} m")
                Sub(s.stops.joinToString(" → ") { it.name })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Guiar", Modifier.weight(1f), Icons.Filled.Navigation, enabled = s.stops.all { it.lat != 0.0 }) {
                        vm.navSession = NavSession(s.name, s.stops.map { NavStop(it.name, it.lat, it.lng, it.venueId) }); nav.navigate("navigate")
                    }
                    IconBtn(Icons.Filled.Delete, "Borrar ruta", tint = Red, bg = RedSoft) { vm.act("Ruta borrada") { vm.repo.deleteRoute(s.id) } }
                }
            }
        }
    }
}

/**
 * Pide la ruta a Gemini con los mismos controles de siempre: se bloquea lo que no es de rutas (ni se envía), hay tope
 * diario y, si la IA no está disponible, se arma con el planificador local. [onDone] recibe la ruta (o null), la nota
 * de Gemini y si hubo intento. Se usa en Ruta IA y en el campo "¿A dónde vamos?" de Inicio.
 */
internal fun requestAiRoute(vm: AppViewModel, scope: kotlinx.coroutines.CoroutineScope, catalog: PlacesCatalog?, ref: LatLng, venues: List<Venue>, f: RouteFilters,
                            onStart: () -> Unit, onDone: (RoutePlanResult?, GeminiPlanner.Plan?, Boolean) -> Unit) {
    val text = f.text
    val prefs = vm.container.prefs
    val today = java.time.LocalDate.now().toString()
    // Fuera de tema o con trucos: ni se envía a Gemini.
    AiGuard.checkInput(AiGuard.cleanInput(text))?.let { msg -> vm.toast(msg); onDone(null, null, false); return }
    val (ok, why) = AiGuard.allow(prefs.aiLastAt, prefs.aiDay, prefs.aiCount, today)
    if (!ok) { vm.toast(why ?: AiGuard.ONLY_ROUTES); return }
    prefs.aiLastAt = System.currentTimeMillis()
    prefs.aiCount = (if (prefs.aiDay == today) prefs.aiCount else 0) + 1; prefs.aiDay = today
    onStart()
    scope.launch {
        val city = catalog?.nearestTown(ref.latitude, ref.longitude)?.label ?: "Cali"
        runCatching { GeminiPlanner.plan(text, f, ref.latitude, ref.longitude, city, catalog, venues) }
            .onSuccess { onDone(it.result, it, true) }
            .onFailure { e ->
                if (e is AiGuard.OffTopic) { vm.toast(AiGuard.ONLY_ROUTES); onDone(null, null, false); return@launch }
                // Sin IA (sin conexión, AI Logic sin activar o sin cupo): se arma con el planificador local.
                android.util.Log.w("RutaIA", "Gemini falló", e)
                vm.toast("La IA no respondió a tiempo; te armé una ruta cerca de ${city.substringBefore(",")} con lugares recomendados." +
                    if (co.apexmoto.app.BuildConfig.PRUEBAS) " (${e.javaClass.simpleName}: ${e.message?.take(120)})" else "")
                // Respaldo desde la misma ciudad (no desde los corredores del Valle); si no hay nada cerca, el planificador de siempre.
                val local = runCatching { GeminiPlanner.localPlan(text, f, ref.latitude, ref.longitude, city, catalog, venues) }.getOrNull()
                onDone(local ?: RoutePlanner.generate(venues, f), null, true)
            }
    }
}

/** Tarjeta con la ruta armada: paradas con hora, km, gasolina, comida, guiar y guardar. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AiRouteCard(vm: AppViewModel, nav: NavController, u: AppUser, r: RoutePlanResult, aiNote: GeminiPlanner.Plan?, start: String) {
    Column {
        val c = C.corridor(r.corridor)
        val title = r.title.ifBlank { "Ruta ${c.name}" }
        Card2(Modifier.reveal(0), border = OrangeSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { H(title, Icons.Filled.Route); Lbl("Salida $start · regreso aprox. ${r.end}") }
            }
            aiNote?.let { a ->
                if (a.summary.isNotBlank()) Text(a.summary, style = MaterialTheme.typography.bodyMedium)
                a.tips.forEach { t -> IconLine(Icons.Filled.TipsAndUpdates, t, CyanDeep, bold = false) }
                Lbl("Ruta armada con Gemini. Revisa el estado de la vía antes de salir.")
            }
            if (r.inferred.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lbl("Entendí:"); r.inferred.forEach { Pill(it, CyanSoft, CyanDeep) }
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${r.km} km", "ida y vuelta", Modifier.weight(1f).fillMaxHeight()); Stat("${r.mins / 60} h ${r.mins % 60} m", "tiempo total", Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(cop(r.fuelCost), "gasolina", Modifier.weight(1f).fillMaxHeight(), OrangeDark); Stat(cop(r.food), "comida p/p", Modifier.weight(1f).fillMaxHeight(), OrangeDark)
            }
            Spacer(Modifier.height(4.dp))
            r.stops.forEachIndexed { i, s ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Num(s.eta, OrangeDark, 15, Modifier.width(58.dp).padding(top = 2.dp))
                    Column(Modifier.fillMaxHeight().width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape).background(Orange))
                        if (i < r.stops.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(OrangeSoft))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.venue.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Lbl("Km ${s.venue.km} · ${s.venue.kind}", CyanDeep)
                        if (s.why.isNotEmpty()) Sub(s.why.joinToString(" · "))
                    }
                    if (s.venue.menu.isNotEmpty() && s.venue.ownerId.isNotBlank()) IconBtn(Icons.Filled.RestaurantMenu, "Pedir en ${s.venue.name}") { nav.navigate("menu/${s.venue.id}") }
                }
            }
            Btn("Iniciar guiado", Modifier.fillMaxWidth(), Icons.Filled.Navigation, enabled = r.stops.all { it.venue.lat != 0.0 }) {
                startGuide(vm, nav, title, r.stops.map { it.venue })
            }
            Btn("Guardar ruta", Modifier.fillMaxWidth(), Icons.Filled.Bookmark, kind = BK.Ghost) {
                vm.act("Ruta guardada") {
                    vm.repo.saveRoute(SavedRoute(userId = u.id, name = "$title · ${r.stops.size} paradas", corridor = r.corridor, km = r.km, mins = r.mins,
                        stops = r.stops.map { SavedStop(it.venue.id, it.venue.name, it.eta, it.venue.lat, it.venue.lng) }))
                }
            }
        }
    }
}

private fun Float.roundToLong() = this.toDouble().let { Math.round(it) }

/** Calculadora de presupuesto (misma lógica del prototipo). */
@Composable
private fun BudgetCalc() {
    var big by remember { mutableStateOf(true) }
    var km by remember { mutableFloatStateOf(180f) }
    var people by remember { mutableFloatStateOf(1f) }
    var perMeal by remember { mutableFloatStateOf(35000f) }
    var meals by remember { mutableFloatStateOf(2f) }
    var escoba by remember { mutableStateOf(false) }
    var tier by remember { mutableStateOf("b") }
    val gal = km / (if (big) 34f else 48f)
    val fuel = gal * 16000
    val food = perMeal * meals * people
    val tiers = listOf(Triple("b", "Básico", 35000), Triple("s", "Plus", 60000), Triple("p", "Total", 95000))
    val tierPrice = tiers.first { it.first == tier }.third
    val total = fuel + food + if (escoba) tierPrice else 0
    Card2 {
        H("Presupuesto de tu salida", Icons.Filled.Calculate)
        SwitchRow("Moto de alto cilindraje", "Gasta más gasolina por km", big) { big = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Distancia total", modifier = Modifier.weight(1f)); Num("${km.roundToInt()} km", CyanDeep, 15) }
        ApexSlider(km, { km = it }, 40f..600f)
        Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Personas", modifier = Modifier.weight(1f)); Num("${people.roundToInt()}", CyanDeep, 15) }
        ApexSlider(people, { people = it }, 1f..6f, steps = 4)
        Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Comidas por persona", modifier = Modifier.weight(1f)); Num("${meals.roundToInt()}", CyanDeep, 15) }
        ApexSlider(meals, { meals = it }, 0f..4f, steps = 3)
        Row(verticalAlignment = Alignment.CenterVertically) { Lbl("Precio por comida", modifier = Modifier.weight(1f)); Num(cop(perMeal.roundToInt()), CyanDeep, 15) }
        ApexSlider(perMeal, { perMeal = it }, 15000f..100000f)
        SwitchRow("Coche escoba", "Asistencia mecánica durante la ruta", escoba) { escoba = it }
        AnimatedVisibility(escoba) {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiers.forEach { (k, n, p) ->
                    OptionTile(tier == k, { tier = k }, Modifier.weight(1f).fillMaxHeight()) { Lbl(n); Num(cop(p), Ink, 14) }
                }
            }
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(cop(fuel), "gasolina", Modifier.weight(1f).fillMaxHeight()); Stat(cop(food), "comida", Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OrangeSoft).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Lbl("Total estimado", OrangeDark, Modifier.weight(1f)); Num(cop(animatedMoney(Math.round(total.toDouble()))), OrangeDark, 24)
        }
        Sub("Referencia: 1 galón ≈ ${cop(16000)}. Los valores son estimados.")
    }
}
