@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

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

@Composable
fun RoutesScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    var tab by rememberSaveable { mutableStateOf("ia") }
    Screen {
        Segmented(listOf(Triple("ia", "Ruta IA", Icons.Filled.AutoAwesome), Triple("bud", "Presupuesto", Icons.Filled.Calculate)), tab) { tab = it }
        AnimatedContent(tab, label = "rutas", transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) }) { t ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { if (t == "ia") RouteAI(vm, nav, u) else BudgetCalc() }
        }
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
    var dests by rememberSaveable { mutableStateOf(listOf<String>()) }
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
        Segmented(listOf(Triple("ia", "Sorpréndeme", Icons.Filled.AutoAwesome), Triple("dest", "Yo elijo destino", Icons.Filled.Place)), mode) { mode = it; result = null; tried = false }
        AnimatedContent(mode, label = "modo", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) }) { md ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (md == "ia") {
                    Field("", text, { text = it }, lines = 3, hint = "Ej: trucha con vista al lago, parqueo vigilado y económico")
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
                    Btn("Generar ruta", Modifier.fillMaxWidth(), Icons.Filled.AutoAwesome) {
                        tried = true
                        result = RoutePlanner.generate(venues, RouteFilters(text, cor, start, full, pace, big, cuisines, cats, budget.roundToLong()))
                    }
                } else {
                    Lbl("¿A dónde quieres ir?")
                    val picked = dests.mapNotNull { id -> venues.firstOrNull { it.id == id } }
                    if (picked.isEmpty()) Text("Elige uno o varios lugares: un mirador, un sitio turístico, un restaurante… La IA arma el recorrido.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    picked.forEachIndexed { i, v ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(venueBadge(v)), contentAlignment = Alignment.Center) { Num("${i + 1}", Color.White, 13) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(v.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Lbl("${C.corridor(v.corridor).name} · ${v.kind}") }
                            IconButton({ dests = dests - v.id }) { Icon(Icons.Filled.Close, "Quitar ${v.name}", tint = Muted) }
                        }
                    }
                    Btn(if (picked.isEmpty()) "Elegir destino" else "Agregar otro destino", Modifier.fillMaxWidth(), Icons.Filled.AddLocationAlt, kind = BK.Ghost) { picking = true }
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

    if (picking) DestinationPicker(venues, dests, onDone = { dests = it; picking = false })

    val r = result
    if (r != null) {
        val c = C.corridor(r.corridor)
        val title = r.title.ifBlank { "Ruta ${c.name}" }
        Card2(Modifier.reveal(0), border = OrangeSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { H(title, Icons.Filled.Route); Lbl("Salida $start · regreso aprox. ${r.end}") }
            }
            if (r.inferred.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lbl("Entendí:"); r.inferred.forEach { Pill(it, CyanSoft, CyanDeep) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${r.km} km", "ida y vuelta", Modifier.weight(1f)); Stat("${r.mins / 60} h ${r.mins % 60} m", "tiempo total", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(cop(r.fuelCost), "gasolina", Modifier.weight(1f), OrangeDark); Stat(cop(r.food), "comida p/p", Modifier.weight(1f), OrangeDark)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Guardar", Modifier.weight(1f), Icons.Filled.Bookmark, kind = BK.Ghost) {
                    vm.act("Ruta guardada") {
                        vm.repo.saveRoute(SavedRoute(userId = u.id, name = "$title · ${r.stops.size} paradas", corridor = r.corridor, km = r.km, mins = r.mins,
                            stops = r.stops.map { SavedStop(it.venue.id, it.venue.name, it.eta, it.venue.lat, it.venue.lng) }))
                    }
                }
                Btn("Con guía", Modifier.weight(1f), Icons.Filled.SportsMotorsports, kind = BK.Ghost) {
                    vm.preRoute = title; vm.rentMode = "cond"; nav.navigate("rent")
                }
            }
        }
    } else if (tried) Empty(if (mode == "dest") "Elige al menos un destino para armar la ruta." else "No encontré paradas con esos filtros. Prueba otra zona o quita algún filtro.", Icons.Filled.SearchOff)
    else Text("Te armamos la ruta con comercios aliados según lo que escribas y tus filtros.", color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

    if (saved.isNotEmpty()) {
        H("Mis rutas guardadas", Icons.Filled.Bookmark)
        saved.sortedByDescending { it.createdAt }.forEachIndexed { i, s ->
            Card2(index = i) {
                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Lbl("${s.km} km · ${s.mins / 60} h ${s.mins % 60} m")
                Sub(s.stops.joinToString(" → ") { it.name })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Guiar", Modifier.weight(1f), Icons.Filled.Navigation, enabled = s.stops.all { it.lat != 0.0 }) {
                        vm.navSession = NavSession(s.name, s.stops.map { NavStop(it.name, it.lat, it.lng) }); nav.navigate("navigate")
                    }
                    IconBtn(Icons.Filled.Delete, "Borrar ruta", tint = Red, bg = RedSoft) { vm.act("Ruta borrada") { vm.repo.deleteRoute(s.id) } }
                }
            }
        }
    }
}


/** Buscador de destinos agrupado por zona. Se pueden marcar varios; el orden de selección se conserva. */
@Composable
private fun DestinationPicker(venues: List<Venue>, initial: List<String>, onDone: (List<String>) -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("all") }
    val nq = norm(q.trim())
    val shown = venues.filter { v ->
        (cat == "all" || cat in v.types) && (nq.isEmpty() || norm(v.name + " " + v.kind + " " + v.tags.joinToString(" ")).contains(nq))
    }
    Sheet({ onDone(sel) }) {
        H("¿A dónde quieres ir?", Icons.Filled.Place)
        Field("", q, { q = it }, hint = "Busca: Cristo Rey, trucha, mirador…", leading = Icons.Filled.Search)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Todos", cat == "all", accent = Cyan) { cat = "all" }
            C.CATS.forEach { (k, v) -> Chip(v.first, cat == k, accent = Cyan) { cat = k } }
        }
        if (shown.isEmpty()) Empty("No encontramos lugares con esa búsqueda.", Icons.Filled.SearchOff)
        C.CORRIDORS.forEach { c ->
            val xs = shown.filter { it.corridor == c.key }.sortedBy { it.km }
            if (xs.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(corridorIcon(c.key), null, Modifier.size(16.dp), tint = OrangeDark); Spacer(Modifier.width(6.dp)); Lbl(c.name, OrangeDark)
                }
                xs.forEach { v ->
                    val on = v.id in sel
                    val n = sel.indexOf(v.id) + 1
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) CyanSoft.copy(alpha = 0.15f) else Surf0)
                            .clickable { sel = if (on) sel - v.id else sel + v.id }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(venueTint(v)), contentAlignment = Alignment.Center) { Icon(venueIcon(v), null, Modifier.size(18.dp), tint = venueBadge(v)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(v.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Lbl("${v.kind} · Km ${v.km}") }
                        if (on) Box(Modifier.size(28.dp).clip(CircleShape).background(CyanDeep), contentAlignment = Alignment.Center) { Num("$n", Color.White, 13) }
                        else Icon(Icons.Filled.AddCircleOutline, "Agregar ${v.name}", tint = Outline)
                    }
                }
            }
        }
        Btn(if (sel.isEmpty()) "Cerrar" else "Listo · ${sel.size} destino${if (sel.size == 1) "" else "s"}", Modifier.fillMaxWidth(), Icons.Filled.Check) { onDone(sel) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(cop(fuel), "gasolina", Modifier.weight(1f)); Stat(cop(food), "comida", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OrangeSoft).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Lbl("Total estimado", OrangeDark, Modifier.weight(1f)); Num(cop(animatedMoney(Math.round(total.toDouble()))), OrangeDark, 24)
        }
        Sub("Referencia: 1 galón ≈ ${cop(16000)}. Los valores son estimados.")
    }
}
