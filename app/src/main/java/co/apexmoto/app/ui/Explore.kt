package co.apexmoto.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import co.apexmoto.app.core.C
import co.apexmoto.app.data.*
import co.apexmoto.app.maps.NavSession
import co.apexmoto.app.maps.NavStop
import co.apexmoto.app.maps.openInGoogleMaps
import co.apexmoto.app.ui.theme.*

/** Tipos de lugar: (clave, nombre en plural para el filtro, nombre en singular, ícono). */
private data class Kind(val key: String, val plural: String, val one: String, val icon: ImageVector)

private val KINDS = listOf(
    Kind("mirador", "Miradores", "Mirador", Icons.Filled.Landscape),
    Kind("pueblo", "Pueblos", "Pueblo", Icons.Filled.HolidayVillage),
    Kind("cascada", "Cascadas", "Cascada", Icons.Filled.Water),
    Kind("naturaleza", "Naturaleza", "Naturaleza", Icons.Filled.Forest),
    Kind("agua", "Lagos y ríos", "Lago o río", Icons.Filled.Sailing),
    Kind("termales", "Termales", "Termales", Icons.Filled.HotTub),
    Kind("playa", "Playas", "Playa", Icons.Filled.BeachAccess),
    Kind("cultura", "Cultura", "Cultura e historia", Icons.Filled.Museum),
    Kind("religioso", "Religiosos", "Sitio religioso", Icons.Filled.Church),
    Kind("parque", "Parques", "Parque temático", Icons.Filled.Attractions),
    Kind("desierto", "Desiertos", "Desierto", Icons.Filled.WbSunny),
    Kind("atractivo", "Otros", "Atractivo", Icons.Filled.Place),
)
private fun kindOf(k: String) = KINDS.firstOrNull { it.key == k } ?: KINDS.last()

private val LEVEL = mapOf("facil" to ("Fácil" to Green), "media" to ("Media" to Amber), "exigente" to ("Exigente" to Red))
private val ROAD = mapOf("pavimentada" to "Pavimentada", "mixta" to "Mixta", "destapada" to "Destapada")
private val RADII = listOf(30, 80, 150, 300)

private fun fmtKm(km: Double) = if (km < 1) "${(km * 1000).toInt()} m" else if (km < 10) "%.1f km".format(km) else "${km.toInt()} km"
private fun fmtHours(h: Double) = if (h < 1) "${(h * 60).toInt()} min" else if (h % 1.0 == 0.0) "${h.toInt()} h" else "%.1f h".format(h)

/**
 * Explorar: rodadas famosas y lugares para visitar cerca de donde está la persona, en toda Colombia.
 * La ciudad sale de la ubicación del teléfono (si ya dio permiso) o de la que elija a mano; la ubicación nunca sale
 * del teléfono: la ciudad se calcula con el catálogo que viene dentro de la app.
 */
@Composable
fun ColumnScope.ExploreSection(vm: AppViewModel, nav: NavController, u: AppUser) {
    var cat by remember { mutableStateOf<PlacesCatalog?>(null) }
    LaunchedEffect(Unit) { cat = runCatching { vm.container.places.get() }.getOrNull() }
    val c = cat
    if (c == null) { Card2 { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Sub("Cargando lugares de Colombia…") } }; return }

    // Dónde está la persona: GPS (sin guardar) o ciudad elegida (se guarda solo el nombre).
    var lat by rememberSaveable { mutableDoubleStateOf(C.CALI_LAT) }
    var lng by rememberSaveable { mutableDoubleStateOf(C.CALI_LNG) }
    var where by rememberSaveable { mutableStateOf("") }
    var fromGps by rememberSaveable { mutableStateOf(false) }
    fun useTown(t: Town, save: Boolean) { lat = t.lat; lng = t.lng; where = t.label; fromGps = false; if (save) vm.container.prefs.exploreTown = t.label }
    val askLocation = rememberLocationRequest(vm) { la, ln ->
        lat = la; lng = ln; fromGps = true
        where = c.nearestTown(la, ln)?.label ?: "tu ubicación"
        vm.container.prefs.exploreTown = ""
    }
    LaunchedEffect(c) {
        if (where.isNotEmpty()) return@LaunchedEffect
        val saved = vm.container.prefs.exploreTown.takeIf { it.isNotBlank() }?.let { c.town(it) }
        when {
            saved != null -> useTown(saved, save = false)
            vm.locationConsented && vm.container.location.hasPermission() -> {
                val l = vm.container.location.last()
                if (l != null) { lat = l.latitude; lng = l.longitude; fromGps = true; where = c.nearestTown(l.latitude, l.longitude)?.label ?: "tu ubicación" }
                else c.town("Cali")?.let { useTown(it, save = false) }
            }
            else -> { where = c.town("Cali")?.label ?: "Cali"; lat = C.CALI_LAT; lng = C.CALI_LNG }
        }
    }

    var pickCity by remember { mutableStateOf(false) }
    var openRide by remember { mutableStateOf<FamousRide?>(null) }
    var openPlace by remember { mutableStateOf<Near<Place>?>(null) }
    var allRides by remember { mutableStateOf(false) }
    var rodadaPreset by remember { mutableStateOf<RodadaPreset?>(null) }

    // ── Dónde estoy ──
    Card2(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(OrangeSoft), contentAlignment = Alignment.Center) {
                Icon(if (fromGps) Icons.Filled.MyLocation else Icons.Filled.LocationCity, null, tint = OrangeDark)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Lbl(if (fromGps) "Estás cerca de" else "Recomendaciones para")
                Text(where.ifBlank { "…" }, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton({ pickCity = true }) { Text("Cambiar", color = CyanDeep, fontWeight = FontWeight.Bold) }
        }
        if (!fromGps) Text("Usar mi ubicación", Modifier.clip(RoundedCornerShape(8.dp)).clickable { askLocation() }.padding(vertical = 4.dp, horizontal = 2.dp),
            color = CyanDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }

    // ── Rodadas famosas cerca ──
    val rides = remember(lat, lng) { c.ridesNear(lat, lng) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { H("Rodadas famosas", Icons.Filled.TwoWheeler) }
        TextButton({ allRides = true }) { Text("Ver todas (${rides.size})", color = CyanDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
        items(rides.take(10), key = { it.item.id }) { n -> RideCard(n) { openRide = n.item } }
    }

    // ── Lugares para visitar ──
    var radius by rememberSaveable { mutableIntStateOf(80) }
    var kinds by remember { mutableStateOf(setOf<String>()) }
    var q by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableIntStateOf(15) }
    H("Para visitar", Icons.Filled.Explore)
    Field("", q, { q = it.take(60); shown = 15 }, hint = "Buscar en toda Colombia: cascada, Salento, Boyacá…", leading = Icons.Filled.Search,
        trailing = clearButton(q.isNotEmpty()) { q = "" })
    val searching = q.trim().length >= 2
    if (!searching) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RADII.forEach { r -> Chip("A $r km", radius == r, accent = Orange) { radius = r; shown = 15 } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Todos", kinds.isEmpty(), accent = Cyan) { kinds = emptySet(); shown = 15 }
            KINDS.forEach { k -> Chip(k.plural, k.key in kinds, k.icon, accent = Cyan) { kinds = if (k.key in kinds) kinds - k.key else kinds + k.key; shown = 15 } }
        }
    }
    val list = remember(lat, lng, radius, kinds, q) { if (searching) c.search(q, lat, lng) else c.placesNear(lat, lng, radius, kinds) }
    if (list.isEmpty()) Card2 {
        Sub(if (searching) "No encontramos lugares con «${q.trim()}»." else "No hay lugares de ese tipo a menos de $radius km. Prueba con una distancia mayor.")
    }
    list.take(shown).forEachIndexed { i, n -> PlaceRow(n, i) { openPlace = n } }
    if (list.size > shown) Btn("Ver más (${list.size - shown})", Modifier.fillMaxWidth(), kind = BK.Ghost, small = true) { shown += 20 }

    // Aviso y créditos
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Info, null, Modifier.size(15.dp).padding(top = 2.dp), tint = Muted); Spacer(Modifier.width(6.dp))
        Text("Antes de rodar revisa el estado de la vía (#767 desde el celular) y el orden público. Distancias en línea recta. ${c.attribution}",
            color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }

    // ── Hojas ──
    if (pickCity) CityPicker(c, onDismiss = { pickCity = false }, onGps = { pickCity = false; askLocation() }) { t -> pickCity = false; useTown(t, save = true) }
    openPlace?.let { n ->
        PlaceSheet(n, onDismiss = { openPlace = null },
            onGuide = { openPlace = null; vm.navSession = NavSession(n.item.name, listOf(NavStop(n.item.name, n.item.lat, n.item.lng))); nav.navigate("navigate") })
    }
    openRide?.let { r ->
        RideSheet(r, distanceKm(lat, lng, r.start.lat, r.start.lng), onDismiss = { openRide = null },
            onGuide = { openRide = null; vm.navSession = NavSession(r.name, r.pts.map { NavStop(it.name, it.lat, it.lng) }); nav.navigate("navigate") },
            onRodada = { openRide = null; rodadaPreset = RodadaPreset(r.name, r.start.name, r.name, r.pts.map { RodadaStop(it.name, it.lat, it.lng) }) })
    }
    if (allRides) AllRidesSheet(rides, onDismiss = { allRides = false }) { r -> allRides = false; openRide = r }
    rodadaPreset?.let { p -> CreateRodadaSheet(vm, nav, u, onDismiss = { rodadaPreset = null }, preset = p) }
}

@Composable
private fun RideCard(n: Near<FamousRide>, onClick: () -> Unit) {
    val r = n.item
    val (lv, lc) = LEVEL[r.level] ?: ("" to Muted)
    Card2(Modifier.width(250.dp), onClick = onClick, padding = 12.dp) {
        Text(r.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
            modifier = Modifier.heightIn(min = 38.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(lv, lc.copy(alpha = 0.12f), lc)
            Pill(ROAD[r.road].orEmpty())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Num("${r.km} km", size = 13); Num(fmtHours(r.hours), color = Muted, size = 13)
        }
        Lbl(if (n.km < 15) "Sale desde aquí" else "Sale a ${fmtKm(n.km)} · ${r.dept}")
    }
}

@Composable
private fun PlaceRow(n: Near<Place>, index: Int, onClick: () -> Unit) {
    val p = n.item; val k = kindOf(p.kind)
    Card2(onClick = onClick, index = index, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (p.famous) OrangeSoft else Surf3), contentAlignment = Alignment.Center) {
                Icon(k.icon, null, tint = if (p.famous) OrangeDark else CyanDeep)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Lbl(listOf(k.one, p.town.takeIf { it.isNotBlank() && it != p.name }?.let { "cerca de $it" }).filterNotNull().joinToString(" · "))
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Num(fmtKm(n.km), size = 13)
                if (p.famous) Pill("Recomendado", OrangeSoft, OrangeDark)
            }
        }
    }
}

@Composable
private fun PlaceSheet(n: Near<Place>, onDismiss: () -> Unit, onGuide: () -> Unit) {
    val ctx = LocalContext.current
    val p = n.item; val k = kindOf(p.kind)
    Sheet(onDismiss) {
        H(p.name, k.icon)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(k.one); if (p.famous) Pill("Recomendado", OrangeSoft, OrangeDark); Pill(fmtKm(n.km) + " en línea recta")
        }
        Sub(listOf(p.town, p.dept).filter { it.isNotBlank() }.distinct().joinToString(", "))
        if (p.desc.isNotBlank()) Text(p.desc, style = MaterialTheme.typography.bodyMedium)
        if (p.tip.isNotBlank()) IconLine(Icons.Filled.TwoWheeler, p.tip, OrangeDark, bold = false)
        if (!p.famous) Lbl("Lugar tomado de OpenStreetMap: confirma horarios y acceso antes de ir.")
        Btn("Guiarme hasta allá", Modifier.fillMaxWidth(), Icons.Filled.Navigation) { onGuide() }
        Btn("Abrir en Google Maps", Modifier.fillMaxWidth(), Icons.Filled.Map, kind = BK.Ghost) { openInGoogleMaps(ctx, p.lat, p.lng) }
    }
}

@Composable
private fun RideSheet(r: FamousRide, fromMe: Double, onDismiss: () -> Unit, onGuide: () -> Unit, onRodada: () -> Unit) {
    val (lv, lc) = LEVEL[r.level] ?: ("" to Muted)
    Sheet(onDismiss) {
        H(r.name, Icons.Filled.Route)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(lv, lc.copy(alpha = 0.12f), lc); Pill(ROAD[r.road].orEmpty()); Pill("${r.km} km"); Pill("≈ " + fmtHours(r.hours))
        }
        Text(r.desc, style = MaterialTheme.typography.bodyMedium)
        if (r.tip.isNotBlank()) IconLine(Icons.Filled.Warning, r.tip, Amber, bold = false)
        Lbl(if (fromMe < 15) "Sale desde donde estás." else "El punto de salida está a ${fmtKm(fromMe)} de ti.")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.pts.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (i == 0) Orange else Surf3), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = if (i == 0) Color.White else Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp)); Text(p.name, fontSize = 14.sp)
                }
            }
        }
        Btn("Guiar la ruta", Modifier.fillMaxWidth(), Icons.Filled.Navigation) { onGuide() }
        Btn("Crear una rodada con esta ruta", Modifier.fillMaxWidth(), Icons.Filled.GroupAdd, kind = BK.Ghost) { onRodada() }
    }
}

@Composable
private fun AllRidesSheet(rides: List<Near<FamousRide>>, onDismiss: () -> Unit, onPick: (FamousRide) -> Unit) {
    Sheet(onDismiss) {
        H("Rodadas famosas de Colombia", Icons.Filled.TwoWheeler)
        Sub("Ordenadas por qué tan cerca empiezan de ti.")
        rides.forEach { n ->
            val (lv, lc) = LEVEL[n.item.level] ?: ("" to Muted)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(n.item) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Lbl("${n.item.dept} · ${n.item.km} km · ${ROAD[n.item.road]}")
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) { Pill(lv, lc.copy(alpha = 0.12f), lc); Lbl(fmtKm(n.km)) }
            }
        }
    }
}

@Composable
private fun CityPicker(c: PlacesCatalog, onDismiss: () -> Unit, onGps: () -> Unit, onPick: (Town) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(q) { c.searchTowns(q) }
    Sheet(onDismiss) {
        H("¿Desde dónde vas a rodar?", Icons.Filled.LocationCity)
        Btn("Usar mi ubicación", Modifier.fillMaxWidth(), Icons.Filled.MyLocation, kind = BK.Ghost) { onGps() }
        Field("", q, { q = it.take(40) }, hint = "Escribe tu ciudad o municipio", leading = Icons.Filled.Search)
        AnimatedVisibility(list.isEmpty()) { Sub("No encontramos «$q».") }
        list.forEach { t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(t) }.padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.city) Icons.Filled.LocationCity else Icons.Filled.HolidayVillage, null, Modifier.size(18.dp), tint = Muted)
                Spacer(Modifier.width(10.dp))
                Column { Text(t.name, fontWeight = FontWeight.SemiBold); if (t.dept.isNotBlank()) Lbl(t.dept) }
            }
        }
    }
}

/** Botón para borrar la búsqueda, solo cuando hay texto. */
private fun clearButton(show: Boolean, onClear: () -> Unit): (@Composable () -> Unit)? =
    if (!show) null else { { IconButton(onClear) { Icon(Icons.Filled.Close, "Borrar búsqueda", tint = Muted) } } }
