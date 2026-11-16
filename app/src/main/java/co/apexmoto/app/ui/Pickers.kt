@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.core.C
import co.apexmoto.app.core.norm
import co.apexmoto.app.data.*
import co.apexmoto.app.maps.LatLng
import co.apexmoto.app.ui.theme.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────────── Punto de referencia ─────────────────
/**
 * Desde dónde recomendar cosas: la ubicación del teléfono si la persona ya dio permiso, si no la ciudad que eligió en
 * Explorar, y si no Cali. La ubicación no sale del teléfono.
 */
@Composable
fun rememberRefPoint(vm: AppViewModel): LatLng {
    val fallback = LatLng(C.CALI_LAT, C.CALI_LNG)
    val p by produceState(fallback) {
        val town = vm.container.prefs.exploreTown.takeIf { it.isNotBlank() }?.let { runCatching { vm.container.places.get().town(it) }.getOrNull() }
        if (town != null) value = LatLng(town.lat, town.lng)
        if (vm.locationConsented && vm.container.location.hasPermission()) {
            runCatching { vm.container.location.last() }.getOrNull()?.let { value = LatLng(it.latitude, it.longitude) }
        }
    }
    return p
}

@Composable
fun rememberCatalog(vm: AppViewModel): PlacesCatalog? =
    produceState<PlacesCatalog?>(null) { value = runCatching { vm.container.places.get() }.getOrNull() }.value

// ───────────────── Punto de encuentro ─────────────────
data class MeetChoice(val name: String, val lat: Double, val lng: Double)

private fun meetIcon(kind: String): ImageVector = when (kind) {
    "bomba" -> Icons.Filled.LocalGasStation; "super" -> Icons.Filled.ShoppingCart; "centro" -> Icons.Filled.Storefront
    "peaje" -> Icons.Filled.Toll; else -> Icons.Filled.Place
}
private val MEET_KIND = mapOf("bomba" to "Estación de servicio", "super" to "Supermercado", "centro" to "Centro comercial", "peaje" to "Peaje", "lugar" to "Lugar conocido")

/** Campo que abre el buscador de puntos de encuentro seguros. No hay texto libre: se elige de la lista. */
@Composable
fun MeetField(vm: AppViewModel, value: MeetChoice?, onPick: (MeetChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Lbl("Punto de encuentro")
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(Surf0).clickable { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, null, tint = if (value != null) OrangeDark else Muted); Spacer(Modifier.width(10.dp))
            Text(value?.name ?: "Busca una bomba, supermercado o lugar conocido", Modifier.weight(1f), color = if (value != null) Ink else Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.Search, null, tint = Muted)
        }
    }
    if (open) MeetSheet(vm, onDismiss = { open = false }) { open = false; onPick(it) }
}

@Composable
private fun MeetSheet(vm: AppViewModel, onDismiss: () -> Unit, onPick: (MeetChoice) -> Unit) {
    val cat = rememberCatalog(vm)
    val ref = rememberRefPoint(vm)
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val list = remember(cat, q, ref) { cat?.meetSuggestions(q, ref.latitude, ref.longitude).orEmpty() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Surf4) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            H("¿Dónde se encuentran?", Icons.Filled.Place)
            Sub("Elige un lugar público y fácil de ubicar: estaciones de servicio, supermercados, centros comerciales o peajes.")
            OutlinedTextField(q, { q = it.take(60) }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true,
                placeholder = { Text("Ej: D1 Los Chorros, Terpel, Éxito La Flora", color = Muted) }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted) },
                shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Surf0, unfocusedContainerColor = Surf0,
                    focusedBorderColor = CyanDeep, unfocusedBorderColor = Color.Transparent))
            if (cat == null) Sub("Cargando lugares…")
            else if (list.isEmpty()) Sub("No encontramos «${q.trim()}». Prueba con el nombre de la bomba, el supermercado o el barrio.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.item.id }) { n ->
                    val m = n.item
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(MeetChoice(m.label.take(160), m.lat, m.lng)) }
                        .padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Surf3), contentAlignment = Alignment.Center) { Icon(meetIcon(m.kind), null, Modifier.size(19.dp), tint = CyanDeep) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Lbl(listOf(MEET_KIND[m.kind], m.area.ifBlank { null }, m.town.ifBlank { null }).filterNotNull().distinct().joinToString(" · "))
                        }
                        Spacer(Modifier.width(6.dp)); Num(fmtKmShort(n.km), Muted, 12)
                    }
                }
            }
        }
    }
}

fun fmtKmShort(km: Double) = if (km < 1) "${(km * 1000).toInt()} m" else if (km < 10) "%.1f km".format(km).replace('.', ',') else "${km.toInt()} km"

// ───────────────── Paradas (destinos y restaurantes) ─────────────────
/** Una parada elegida: un lugar del catálogo o un restaurante aliado (venueId). */
data class PickedStop(val id: String, val name: String, val lat: Double, val lng: Double, val food: Boolean, val venueId: String = "", val subtitle: String = "")

/**
 * Selector de paradas: primero se elige a dónde van (miradores, pueblos, cascadas…, sin restaurantes). Al elegir uno, la
 * lista se oculta y quedan las paradas elegidas con "Agregar otra parada" y "Agregar restaurante" (aliados), separados.
 * El botón para terminar queda fijo abajo, sin tener que bajar.
 */
@Composable
fun StopPickerSheet(vm: AppViewModel, initial: List<PickedStop>, title: String = "¿A dónde van?", onDone: (List<PickedStop>) -> Unit) {
    val cat = rememberCatalog(vm)
    val ref = rememberRefPoint(vm)
    val venues by vm.venues.collectAsState()
    var sel by remember { mutableStateOf(initial) }
    // browse: null = ver las elegidas; "place" = buscando parada; "food" = buscando restaurante
    var browse by remember { mutableStateOf<String?>(if (initial.isEmpty()) "place" else null) }
    var q by remember { mutableStateOf("") }
    val places = remember(cat, q, ref, browse) {
        if (browse != "place" || cat == null) emptyList()
        else (if (q.trim().length >= 2) cat.search(q, ref.latitude, ref.longitude, 60) else cat.placesNear(ref.latitude, ref.longitude, 150).take(60))
            .map { PickedStop(it.item.id, it.item.name, it.item.lat, it.item.lng, false, subtitle = listOf(placeKindLabel(it.item.kind), it.item.town, fmtKmShort(it.km)).filter { s -> s.isNotBlank() }.joinToString(" · ")) }
    }
    val foods = remember(venues, q, ref, browse) {
        if (browse != "food") emptyList()
        else venues.filter { v -> v.open && v.lat != 0.0 && v.types.any { it == "food" || it == "cafe" } && (q.isBlank() || norm(v.name + " " + v.cuisines.joinToString(" ")).contains(norm(q.trim()))) }
            .map { v -> val d = distanceKm(ref.latitude, ref.longitude, v.lat, v.lng); Triple(v, d, PickedStop("v_" + v.id, v.name, v.lat, v.lng, true, v.id, listOf(v.kind, fmtKmShort(d)).filter { it.isNotBlank() }.joinToString(" · "))) }
            .sortedBy { it.second }.map { it.third }
    }
    ModalBottomSheet(onDismissRequest = { onDone(sel) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Surf4) }) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { H(title, Icons.Filled.Route) }
                // Paradas elegidas, en orden.
                if (sel.isNotEmpty()) {
                    item { Lbl("Tu recorrido") }
                    items(sel, key = { "sel_" + it.id }) { s ->
                        val i = sel.indexOf(s)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(if (s.food) Orange else CyanDeep), contentAlignment = Alignment.Center) {
                                if (s.food) Icon(Icons.Filled.Restaurant, null, Modifier.size(15.dp), tint = Color.White) else Num("${i + 1}", Color.White, 12)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Lbl(if (s.food) "Restaurante · " + s.subtitle else s.subtitle)
                            }
                            if (i > 0) IconButton({ sel = sel.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) } }) { Icon(Icons.Filled.ArrowUpward, "Subir ${s.name}", tint = Muted) }
                            IconButton({ sel = sel - s; if (sel.isEmpty()) browse = "place" }) { Icon(Icons.Filled.Close, "Quitar ${s.name}", tint = Muted) }
                        }
                    }
                }
                if (browse == null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Agregar parada", Modifier.weight(1f), Icons.Filled.AddLocationAlt, kind = BK.Ghost, small = true) { q = ""; browse = "place" }
                            Btn("Restaurante", Modifier.weight(1f), Icons.Filled.Restaurant, kind = BK.Ghost, small = true) { q = ""; browse = "food" }
                        }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Lbl(if (browse == "food") "Restaurantes aliados" else if (sel.isEmpty()) "Elige el destino" else "Elige otra parada", OrangeDark, Modifier.weight(1f))
                            if (sel.isNotEmpty()) TextButton({ browse = null }) { Text("Cancelar", color = Muted) }
                        }
                    }
                    item { Field("", q, { q = it.take(60) }, hint = if (browse == "food") "Busca: parrilla, trucha, café…" else "Busca: mirador, Calima, cascada…", leading = Icons.Filled.Search) }
                    val opts = if (browse == "food") foods else places
                    if (cat == null && browse == "place") item { Sub("Cargando lugares…") }
                    else if (opts.isEmpty()) item { Sub(if (browse == "food") "No hay restaurantes aliados con esa búsqueda." else "No encontramos lugares con esa búsqueda.") }
                    items(opts.filter { o -> sel.none { it.id == o.id } }, key = { "opt_" + it.id }) { o ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { sel = sel + o; browse = null; q = "" }.padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(if (o.food) OrangeSoft else Surf3), contentAlignment = Alignment.Center) {
                                Icon(if (o.food) Icons.Filled.Restaurant else Icons.Filled.Place, null, Modifier.size(18.dp), tint = if (o.food) OrangeDark else CyanDeep)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(o.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Lbl(o.subtitle) }
                            Icon(Icons.Filled.AddCircleOutline, "Agregar ${o.name}", tint = Outline)
                        }
                    }
                }
            }
            // Botón fijo abajo: no hay que bajar para terminar.
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color.White, shadowElevation = 12.dp) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) {
                    val n = sel.size
                    Btn(if (n == 0) "Cerrar" else "Listo · $n parada${if (n == 1) "" else "s"}", Modifier.fillMaxWidth(), Icons.Filled.Check) { onDone(sel) }
                }
            }
        }
    }
}

fun placeKindLabel(k: String) = when (k) {
    "mirador" -> "Mirador"; "pueblo" -> "Pueblo"; "cascada" -> "Cascada"; "naturaleza" -> "Naturaleza"; "agua" -> "Lago o río"
    "termales" -> "Termales"; "playa" -> "Playa"; "cultura" -> "Cultura"; "religioso" -> "Sitio religioso"; "parque" -> "Parque"
    "desierto" -> "Desierto"; else -> "Lugar"
}

// ───────────────── Fecha y hora ─────────────────
private val ES = Locale.forLanguageTag("es-CO")

/** "sáb 22 nov · 7:00 a. m." (máximo 40 caracteres, como exige whenLabel). */
fun whenLabelOf(ms: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    return (t.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)).replace(".", "") + " · " + t.format(DateTimeFormatter.ofPattern("h:mm a", ES))).take(40)
}

/** Fecha y hora de salida: calendario y reloj del sistema. Solo fechas desde hoy. */
@Composable
fun DateTimeField(label: String, millis: Long, modifier: Modifier = Modifier, onPick: (Long) -> Unit) {
    val ctx = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Lbl(label)
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(Surf0).clickable {
            val cur = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            val dlg = DatePickerDialog(ctx, { _, y, m, d ->
                TimePickerDialog(ctx, { _, h, min ->
                    onPick(LocalDateTime.of(y, m + 1, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                }, cur.hour, cur.minute, false).show()
            }, cur.year, cur.monthValue - 1, cur.dayOfMonth)
            dlg.datePicker.minDate = System.currentTimeMillis() - 60_000
            dlg.show()
        }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Event, null, tint = OrangeDark); Spacer(Modifier.width(10.dp))
            Text(whenLabelOf(millis), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
        }
    }
}
