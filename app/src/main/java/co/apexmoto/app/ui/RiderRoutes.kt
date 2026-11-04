@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

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
import co.apexmoto.app.core.usd
import co.apexmoto.app.data.*
import co.apexmoto.app.maps.*
import co.apexmoto.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun RoutesScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    var tab by rememberSaveable { mutableStateOf("ia") }
    Screen {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Ruta IA", tab == "ia") { tab = "ia" }
            Chip("Presupuesto", tab == "bud") { tab = "bud" }
        }
        if (tab == "ia") RouteAI(vm, nav, u) else BudgetCalc()
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
    var budget by remember { mutableFloatStateOf(60000f) }
    var cuisines by remember { mutableStateOf(setOf<String>()) }
    var cats by remember { mutableStateOf(setOf<String>()) }
    var result by remember { mutableStateOf<RoutePlanResult?>(null) }
    var tried by remember { mutableStateOf(false) }

    Card2 {
        H("¿Qué ruta quieres hacer?")
        Field("Cuéntame (ej: asado con vista al lago, algo barato)", text, { text = it }, lines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Drop("Zona", listOf("any" to "Cualquiera") + C.CORRIDORS.map { it.key to it.name }, cor, { cor = it }, Modifier.weight(1f))
            Field("Salida (HH:mm)", start, { start = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Drop("Duración", listOf("half" to "Medio día (3)", "full" to "Día completo (5)"), if (full) "full" else "half", { full = it == "full" }, Modifier.weight(1f))
            Drop("Ritmo", listOf("t" to "Tranquilo", "m" to "Moderado", "r" to "Rápido"), pace, { pace = it }, Modifier.weight(1f))
        }
        SwitchRow("Moto de alto cilindraje", "Afecta el cálculo de gasolina", big) { big = it }
        Lbl("Tipo de parada")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C.CATS.forEach { (k, v) -> Chip(v.first, k in cats) { cats = if (k in cats) cats - k else cats + k } }
        }
        Lbl("Cocina")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C.CUISINES.forEach { c -> Chip(c, c in cuisines) { cuisines = if (c in cuisines) cuisines - c else cuisines + c } }
        }
        Lbl("Presupuesto de comida por persona: ${cop(budget.roundToInt())}")
        Slider(budget, { budget = it }, valueRange = 20000f..120000f, steps = 9)
        Btn("Generar ruta", Modifier.fillMaxWidth(), Icons.Filled.AutoAwesome) {
            tried = true
            result = RoutePlanner.generate(venues, RouteFilters(text, cor, start, full, pace, big, cuisines, cats, budget.roundToLong()))
        }
    }

    val r = result
    if (r != null) {
        val c = C.corridor(r.corridor)
        Card2(border = Orange) {
            H("Ruta ${c.name}")
            if (r.inferred.isNotEmpty()) Sub("Entendí: ${r.inferred.joinToString(", ")}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${r.km} km", "ida y vuelta", Modifier.weight(1f)); Stat("${r.mins / 60} h ${r.mins % 60} m", "total", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(cop(r.fuelCost), "gasolina", Modifier.weight(1f)); Stat(cop(r.food), "comida p/p", Modifier.weight(1f))
            }
            Sub("Salida $start · regreso aprox. ${r.end}")
            r.stops.forEachIndexed { i, s ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${s.venue.name}", fontWeight = FontWeight.Bold)
                        Lbl("Llegas ${s.eta} · km ${s.venue.km}")
                        if (s.why.isNotEmpty()) Sub(s.why.joinToString(" · "))
                    }
                    if (s.venue.menu.isNotEmpty() && s.venue.ownerId.isNotBlank()) IconButton({ nav.navigate("menu/${s.venue.id}") }) { Icon(Icons.Filled.Restaurant, "Pedir") }
                }
            }
            Btn("Iniciar guiado", Modifier.fillMaxWidth(), Icons.Filled.Navigation, enabled = r.stops.all { it.venue.lat != 0.0 }) {
                startGuide(vm, nav, "Ruta ${c.name}", r.stops.map { it.venue })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Guardar", Modifier.weight(1f), Icons.Filled.Bookmark, kind = BK.Ghost) {
                    vm.act("Ruta guardada") {
                        vm.repo.saveRoute(SavedRoute(userId = u.id, name = "Ruta ${c.name} · ${r.stops.size} paradas", corridor = r.corridor, km = r.km, mins = r.mins,
                            stops = r.stops.map { SavedStop(it.venue.id, it.venue.name, it.eta, it.venue.lat, it.venue.lng) }))
                    }
                }
                Btn("Con conductor", Modifier.weight(1f), Icons.Filled.SportsMotorsports, kind = BK.Ghost) {
                    vm.preRoute = "Ruta ${c.name}"; vm.rentMode = "cond"; nav.navigate("rent")
                }
            }
        }
    } else if (tried) Empty("No encontré paradas con esos filtros. Prueba otra zona o quita algún filtro.")

    if (saved.isNotEmpty()) {
        H("Mis rutas guardadas")
        saved.sortedByDescending { it.createdAt }.forEach { s ->
            Card2 {
                Text(s.name, fontWeight = FontWeight.Bold)
                Sub("${s.km} km · ${s.mins / 60} h ${s.mins % 60} m · " + s.stops.joinToString(" → ") { it.name })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Guiar", Modifier.weight(1f), Icons.Filled.Navigation, enabled = s.stops.all { it.lat != 0.0 }) {
                        vm.navSession = NavSession(s.name, s.stops.map { NavStop(it.name, it.lat, it.lng) }); nav.navigate("navigate")
                    }
                    Btn("Borrar", Modifier.weight(1f), Icons.Filled.Delete, kind = BK.Danger) { vm.act { vm.repo.deleteRoute(s.id) } }
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
    val tierPrice = mapOf("b" to 35000, "s" to 60000, "p" to 95000)[tier] ?: 0
    val total = fuel + food + if (escoba) tierPrice else 0
    Card2 {
        H("Presupuesto de tu salida")
        SwitchRow("Moto de alto cilindraje", checked = big) { big = it }
        Lbl("Distancia total: ${km.roundToInt()} km"); Slider(km, { km = it }, valueRange = 40f..600f)
        Lbl("Personas: ${people.roundToInt()}"); Slider(people, { people = it }, valueRange = 1f..6f, steps = 4)
        Lbl("Comidas: ${meals.roundToInt()} · ${cop(perMeal.roundToInt())} c/u"); Slider(meals, { meals = it }, valueRange = 0f..4f, steps = 3)
        Slider(perMeal, { perMeal = it }, valueRange = 15000f..100000f)
        SwitchRow("Escoba / asistencia mecánica", "Cubre varadas en carretera", escoba) { escoba = it }
        if (escoba) Drop("Plan", listOf("b" to "Básico ${cop(35000)}", "s" to "Plus ${cop(60000)}", "p" to "Total ${cop(95000)}"), tier, { tier = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(cop(fuel), "gasolina", Modifier.weight(1f)); Stat(cop(food), "comida", Modifier.weight(1f))
        }
        Text("Total estimado ${cop(total)}", style = MaterialTheme.typography.titleLarge, color = Orange)
        Sub("Referencia: 1 galón ≈ ${cop(16000)}. Los valores son estimados.")
    }
}
