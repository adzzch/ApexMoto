@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.core.C
import co.apexmoto.app.core.fmtDist
import co.apexmoto.app.core.fmtDur
import co.apexmoto.app.core.haversine
import co.apexmoto.app.maps.LatLng
import co.apexmoto.app.maps.MapMarker
import co.apexmoto.app.maps.OsmMap
import co.apexmoto.app.maps.OsmMapState
import co.apexmoto.app.maps.RoutePlan
import co.apexmoto.app.maps.openInGoogleMaps
import co.apexmoto.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Guiado dentro de la app: calcula la ruta con OpenRouteService, la dibuja en el mapa,
 * sigue tu ubicación en vivo, avanza las instrucciones, avisa por voz y recalcula si te sales del camino.
 */
@Composable
fun NavigationScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = vm.container
    val session = vm.navSession
    if (session == null || session.stops.isEmpty()) {
        Screen { Empty("No hay una ruta activa. Elige un comercio o una ruta y toca Ir o Iniciar guiado.") }
        return
    }
    val scope = rememberCoroutineScope()
    val stops = remember(session) { session.stops.map { LatLng(it.lat, it.lng) } }

    var hasPerm by remember { mutableStateOf(c.location.hasPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasPerm = c.location.hasPermission() }
    var plan by remember { mutableStateOf<RoutePlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var guiding by remember { mutableStateOf(false) }
    var arrived by remember { mutableStateOf(false) }
    var idx by remember { mutableIntStateOf(0) }
    var reached by remember { mutableIntStateOf(0) }
    var pos by remember { mutableStateOf<LatLng?>(null) }
    var lastReroute by remember { mutableLongStateOf(0L) }
    val mapState = remember { OsmMapState() }
    // Se llena cuando la ubicación real está demasiado lejos del destino y la ruta se calcula desde Cali
    var originNote by remember { mutableStateOf<String?>(null) }

    suspend fun load(from: LatLng) {
        loading = true; error = null
        try {
            val rem = stops.drop(reached)
            plan = c.routes.route(from, rem.last(), rem.dropLast(1)); idx = 0
        } catch (e: CancellationException) {
            throw e   // la pantalla se cerró o el cálculo se reemplazó por otro: no es un error para mostrar
        } catch (e: Exception) {
            error = e.message ?: "No pudimos calcular la ruta. Revisa tu conexión y toca Recalcular."
        } finally { loading = false }
    }
    // Recálculo al salirse del camino: corre aparte para no frenar el GPS; uno nuevo cancela al anterior.
    var rerouteJob by remember { mutableStateOf<Job?>(null) }
    fun reroute(from: LatLng) { rerouteJob?.cancel(); rerouteJob = scope.launch { load(from) } }

    val tts = remember {
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx) { s -> if (s == TextToSpeech.SUCCESS) t?.setLanguage(Locale("es", "CO")) }
        t
    }
    DisposableEffect(Unit) { onDispose { tts?.stop(); tts?.shutdown() } }

    LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    LaunchedEffect(hasPerm) {
        val cali = LatLng(C.CALI_LAT, C.CALI_LNG)
        var o = (if (hasPerm) c.location.last() else null) ?: cali
        val dest = stops.last()
        // Ubicación a más de 300 km del destino (p. ej. el emulador en California): la ruta se muestra desde el centro de Cali
        originNote = if (haversine(o.latitude, o.longitude, dest.latitude, dest.longitude) > 300_000) {
            o = cali
            "Tu ubicación está muy lejos del destino: la ruta se calculó desde el centro de Cali. El guiado en vivo se activa cuando estés cerca."
        } else null
        pos = o; load(o)
    }
    LaunchedEffect(plan) {
        val p = plan
        if (p != null && !guiding && p.points.isNotEmpty()) {
            kotlinx.coroutines.delay(300) // espera a que el mapa tenga tamaño
            mapState.fit(p.points)
        }
    }
    LaunchedEffect(guiding) { if (!guiding) mapState.resetRotation() }

    val steps = plan?.steps.orEmpty()
    LaunchedEffect(idx, guiding) {
        if (guiding && vm.voice) steps.getOrNull(idx)?.let { tts?.speak(it.text, TextToSpeech.QUEUE_FLUSH, null, "step$idx") }
    }
    LaunchedEffect(guiding) {
        if (!guiding) return@LaunchedEffect
        c.location.updates().collect { l ->
            val here = LatLng(l.latitude, l.longitude); pos = here
            val p = plan ?: return@collect
            val dest = stops.last()
            if (haversine(here.latitude, here.longitude, dest.latitude, dest.longitude) < 40) {
                arrived = true; guiding = false
                if (vm.voice) tts?.speak("Llegaste a tu destino", TextToSpeech.QUEUE_FLUSH, null, "arrived")
                return@collect
            }
            if (reached < stops.size - 1 && haversine(here.latitude, here.longitude, stops[reached].latitude, stops[reached].longitude) < 60) {
                vm.toast("Llegaste a ${session.stops[reached].name}"); reached++
            }
            val nextStart = p.steps.getOrNull(idx + 1)?.start
            if (nextStart != null && haversine(here.latitude, here.longitude, nextStart.latitude, nextStart.longitude) < 30) idx++
            val off = p.points.minOfOrNull { haversine(here.latitude, here.longitude, it.latitude, it.longitude) } ?: 0.0
            val now = System.currentTimeMillis()
            if (off > 150 && now - lastReroute > 15_000) { lastReroute = now; vm.toast("Recalculando ruta…"); reroute(here) }
            mapState.follow(here, l.bearing)
        }
    }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            Modifier.fillMaxSize(), state = mapState, center = stops.last(), zoom = 10.0, mapType = vm.mapType,
            markers = session.stops.mapIndexed { i, s -> MapMarker("stop$i", LatLng(s.lat, s.lng), "${i + 1}. ${s.name}") },
            route = plan?.points.orEmpty(), myPos = if (hasPerm) pos else null,
        )

        // Instrucción actual
        if (guiding || arrived) {
            val next = steps.getOrNull(idx + 1)?.start ?: stops.last()
            val dNext = pos?.let { haversine(it.latitude, it.longitude, next.latitude, next.longitude) }
            Card(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Ink), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (arrived) "¡Llegaste a tu destino!" else steps.getOrNull(idx)?.text ?: "Sigue la ruta", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
                    if (!arrived && dNext != null) Text("en ${fmtDist(dNext)}", color = NavAccent, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Panel inferior
        Card(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                if (session.stops.size > 1) Sub(session.stops.joinToString(" → ") { it.name })
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Sub("Calculando ruta…") }
                    error != null -> Text(error!!, color = Red, style = MaterialTheme.typography.bodySmall)
                    plan != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(fmtDist(plan!!.distanceM.toDouble()), "distancia", Modifier.weight(1f)); Stat(fmtDur(plan!!.durationS), "tiempo estimado", Modifier.weight(1f))
                    }
                }
                originNote?.let { Text(it, color = OrangeDark, style = MaterialTheme.typography.labelMedium) }
                if (!hasPerm) Btn("Dar permiso de ubicación", Modifier.fillMaxWidth(), Icons.Filled.MyLocation, kind = BK.Ghost) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!guiding) Btn(if (arrived) "Repetir" else "Iniciar guiado", Modifier.weight(1f), Icons.Filled.Navigation, enabled = plan != null && hasPerm && originNote == null) {
                        arrived = false; reached = 0; idx = 0; guiding = true
                    } else Btn("Detener", Modifier.weight(1f), Icons.Filled.Stop, kind = BK.Danger) { guiding = false }
                    Btn("Recalcular", Modifier.weight(1f), Icons.Filled.Refresh, kind = BK.Ghost, enabled = !loading) {
                        reroute(pos ?: LatLng(C.CALI_LAT, C.CALI_LNG))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoogleMapsButton(Modifier.weight(1f)) { stops.last().let { openInGoogleMaps(ctx, it.latitude, it.longitude) } }
                    IconBtn(if (vm.voice) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, if (vm.voice) "Silenciar voz" else "Activar voz") { vm.updateVoice(!vm.voice) }
                }
            }
        }
    }
}
