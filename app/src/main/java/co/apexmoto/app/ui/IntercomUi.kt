package co.apexmoto.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import co.apexmoto.app.data.AppUser
import co.apexmoto.app.data.Rodada
import co.apexmoto.app.ui.theme.*
import co.apexmoto.app.voice.Intercom

// Intercomunicador de voz de las rodadas (ver voice/Intercom.kt): tarjeta en la pantalla de la rodada y una franja
// fija bajo el encabezado mientras la llamada sigue abierta en otras pantallas (guiado, mapa, etc.).

/** Pide el micrófono (y, si aplica, Bluetooth y notificaciones) y entra a la llamada si se concedió el micrófono. */
@Composable
private fun rememberJoin(vm: AppViewModel, ro: Rodada, u: AppUser): () -> Unit {
    val ctx = LocalContext.current
    val ic = vm.container.intercom
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val mic = res[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (mic) ic.join(ro.id, ro.name, u.id) else vm.toast("Sin el micrófono no puedes hablar en el intercom. Actívalo en los permisos de ApexMoto.")
    }
    return {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) ic.join(ro.id, ro.name, u.id) else launcher.launch(missing.toTypedArray())
    }
}

/** Tarjeta del intercom en la pantalla de la rodada (solo miembros). [names]: número de Agora → nombre del miembro. */
@Composable
fun IntercomCard(vm: AppViewModel, ro: Rodada, u: AppUser, names: Map<Int, String>) {
    val ic = vm.container.intercom
    if (!ic.available) return
    val st by ic.state.collectAsState()
    val join = rememberJoin(vm, ro, u)
    LaunchedEffect(st.error) { st.error?.let { vm.toast(it); ic.clearError() } }
    val here = st.rodadaId == ro.id

    Card2(tone = if (here) CyanSoft else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { H("Intercom de la rodada", Icons.Filled.HeadsetMic) }
            if (here) Pill(when (st.status) { Intercom.Status.ON -> "En llamada"; Intercom.Status.RECONNECTING -> "Reconectando…"; else -> "Conectando…" },
                if (st.status == Intercom.Status.ON) Green else OrangeSoft, if (st.status == Intercom.Status.ON) Color.White else OrangeDark)
        }
        if (!here) {
            Text("Hablen por voz mientras ruedan, como un intercomunicador. Funciona con el intercom Bluetooth del casco o audífonos y sigue sonando con la pantalla apagada.",
                color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (st.active) Lbl("Estás en la llamada de ${st.rodadaName}. Si entras aquí, sales de esa.", OrangeDark)
            Btn(if (st.active) "Cambiar a esta llamada" else "Entrar a la llamada", Modifier.fillMaxWidth(), Icons.Filled.Call) { join() }
            return@Card2
        }
        // Quién está en la llamada y quién habla.
        val inCall = listOf(0) + st.peers.sorted()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            inCall.forEach { id ->
                val name = if (id == 0) "Tú" else names[id] ?: "Rider"
                val talking = id in st.speaking
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (talking) Green else Outline))
                    Spacer(Modifier.width(8.dp))
                    Text(name, Modifier.weight(1f), fontWeight = if (talking) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        talking -> Lbl("hablando", Green)
                        id == 0 && !st.transmitting -> Icon(Icons.Filled.MicOff, "Micrófono apagado", Modifier.size(18.dp), tint = Muted)
                    }
                }
            }
            if (st.peers.isEmpty() && st.status == Intercom.Status.ON) Lbl("Aún no entra nadie más. Diles que toquen \"Entrar a la llamada\".")
        }
        // Modo: micrófono abierto o pulsar para hablar.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionTile(!st.pushToTalk, { ic.setPushToTalk(false) }, Modifier.weight(1f)) {
                Text("Micrófono abierto", fontWeight = FontWeight.Bold, fontSize = 14.sp); Lbl("Hablas cuando quieras")
            }
            OptionTile(st.pushToTalk, { ic.setPushToTalk(true) }, Modifier.weight(1f)) {
                Text("Pulsar para hablar", fontWeight = FontWeight.Bold, fontSize = 14.sp); Lbl("Como un walkie-talkie")
            }
        }
        if (st.pushToTalk) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TalkButton(vm, 120) }
        else Btn(if (st.muted) "Activar micrófono" else "Silenciar", Modifier.fillMaxWidth(), if (st.muted) Icons.Filled.Mic else Icons.Filled.MicOff,
            kind = if (st.muted) BK.Primary else BK.Ghost) { ic.setMuted(!st.muted) }
        Btn("Salir de la llamada", Modifier.fillMaxWidth(), Icons.Filled.CallEnd, kind = BK.Danger) { ic.leave() }
        Lbl("La voz pasa por los servidores de Agora y no se graba.", Muted)
    }
}

/** Botón redondo de pulsar para hablar: habla mientras lo sostienes. */
@Composable
private fun TalkButton(vm: AppViewModel, size: Int) {
    val ic = vm.container.intercom
    val st by ic.state.collectAsState()
    val on = st.pressing
    val pulse by rememberInfiniteTransition(label = "ptt").animateFloat(1f, 1.08f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "p")
    Box(
        Modifier.size(size.dp).scale(if (on) pulse else 1f).clip(CircleShape).background(if (on) Green else Orange)
            .pointerInput(Unit) { detectTapGestures(onPress = { ic.press(true); try { tryAwaitRelease() } finally { ic.press(false) } }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Mic, null, Modifier.size((size / 3).dp), tint = Color.White)
            if (size >= 100) Text(if (on) "Hablando" else "Mantén\npara hablar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
        }
    }
}

/** Franja fija bajo el encabezado mientras hay una llamada abierta: quién habla, micrófono y salir. */
@Composable
fun IntercomBar(vm: AppViewModel, onOpen: (String) -> Unit) {
    val ic = vm.container.intercom
    val st by ic.state.collectAsState()
    val rid = st.rodadaId ?: return
    Row(
        Modifier.fillMaxWidth().background(CyanDeep).clickable(onClickLabel = "Abrir la rodada") { onOpen(rid) }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.HeadsetMic, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Intercom · ${st.rodadaName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val talking = (st.speaking - 0).size
            Text(when {
                st.status != Intercom.Status.ON -> if (st.status == Intercom.Status.RECONNECTING) "Reconectando…" else "Conectando…"
                talking > 0 -> "Alguien está hablando"
                else -> "${st.peers.size + 1} en la llamada"
            }, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
        if (st.pushToTalk) TalkButton(vm, 40)
        else IconButton({ ic.setMuted(!st.muted) }) {
            Icon(if (st.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (st.muted) "Activar micrófono" else "Silenciar", tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(36.dp).clip(CircleShape).background(Red).clickable(onClickLabel = "Salir de la llamada") { ic.leave() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CallEnd, null, Modifier.size(18.dp), tint = Color.White)
        }
    }
}
