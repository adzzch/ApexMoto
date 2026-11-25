@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

// S.O.S. de emergencia de verdad (accidente, heridos, robo, peligro): lleva al marcador del teléfono con el número
// ya escrito (la persona solo toca llamar) y ayuda a decir dónde está. No pasa por los talleres ni por Firestore:
// los contactos de emergencia se guardan solo en este teléfono. Lo de pinchazos y fallas es "Asistencia técnica".

/** Una línea de emergencia de Colombia. */
private data class EmergencyLine(val number: String, val title: String, val detail: String, val icon: ImageVector)

private val LINES = listOf(
    EmergencyLine("123", "Policía", "Robo, riña, peligro. Línea única nacional", Icons.Filled.LocalPolice),
    EmergencyLine("123", "Ambulancia", "Heridos o alguien inconsciente. Pide la ambulancia en el 123", Icons.Filled.MedicalServices),
    EmergencyLine("#767", "Accidente en carretera", "Policía de Tránsito y Carreteras", Icons.Filled.CarCrash),
    EmergencyLine("119", "Bomberos", "Incendio, atrapados, derrames", Icons.Filled.LocalFireDepartment),
    EmergencyLine("132", "Cruz Roja", "Atención y traslado de heridos", Icons.Filled.LocalHospital),
)

/** Contacto de emergencia guardado en el teléfono. */
data class EmergencyContact(val name: String, val phone: String)

internal object EmergencyContacts {
    const val MAX = 3
    fun read(json: String): List<EmergencyContact> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> EmergencyContact(o.optString("n"), o.optString("p")) } }.filter { it.phone.isNotBlank() }
    }.getOrDefault(emptyList())
    fun write(list: List<EmergencyContact>): String =
        JSONArray().apply { list.forEach { put(JSONObject().put("n", it.name).put("p", it.phone)) } }.toString()
    fun clean(phone: String) = phone.filter { it.isDigit() || it == '+' || it == '#' || it == '*' }.take(20)
}

/** Abre el marcador con el número escrito. No llama solo: así no se marca al 123 por un toque accidental. */
fun dial(ctx: Context, number: String) {
    val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) { }
}

private fun mapsLink(lat: Double, lng: Double) = "https://maps.google.com/?q=%.6f,%.6f".format(java.util.Locale.US, lat, lng)

private fun helpText(lat: Double, lng: Double, near: String?) = buildString {
    append("Tengo una emergencia en la moto. ")
    if (lat != 0.0) { append("Estoy aquí"); near?.let { append(" (cerca de $it)") }; append(": ").append(mapsLink(lat, lng)) }
    else append("Llámame apenas puedas.")
}

/** Botón rojo "SOS" del encabezado. */
@Composable
fun SosHeaderButton(onClick: () -> Unit) {
    Row(
        Modifier.height(TouchMin).clip(RoundedCornerShape(TouchMin / 2)).background(Red)
            .clickable(onClickLabel = "Abrir emergencia", role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Call, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.width(5.dp))
        Text("SOS", color = Color.White, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

/** Hoja de emergencia: líneas de emergencia, contactos, ubicación y aviso a la rodada. */
@Composable
fun EmergencySheet(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = vm.container.prefs
    var contacts by remember { mutableStateOf(EmergencyContacts.read(prefs.emergencyContacts)) }
    fun save(l: List<EmergencyContact>) { contacts = l.take(EmergencyContacts.MAX); prefs.emergencyContacts = EmergencyContacts.write(contacts) }
    // Si llenó el contacto de emergencia en su perfil de conductor (rentas) y aquí no tiene ninguno, se ofrece ese.
    val driver by vm.riderProfile.collectAsState()
    val suggested = driver?.takeIf { d -> d.emergencyPhone.isNotBlank() && contacts.none { EmergencyContacts.clean(it.phone) == EmergencyContacts.clean(d.emergencyPhone) } }

    var lat by remember { mutableStateOf(0.0) }
    var lng by remember { mutableStateOf(0.0) }
    val getLoc = rememberLocationRequest(vm) { a, b -> lat = a; lng = b }
    LaunchedEffect(Unit) {
        if (vm.locationConsented && vm.container.location.hasPermission())
            runCatching { vm.container.location.last() }.getOrNull()?.let { lat = it.latitude; lng = it.longitude }
    }
    val catalog = rememberCatalog(vm)
    val near = remember(catalog, lat, lng) { if (lat == 0.0) null else catalog?.nearestTown(lat, lng)?.name }

    var adding by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val phone = EmergencyContacts.clean(c.getString(1).orEmpty())
                    if (phone.isNotBlank()) save(contacts + EmergencyContact(c.getString(0).orEmpty().take(40).ifBlank { phone }, phone))
                }
            }
        }.onFailure { vm.toast("No pude leer ese contacto. Escríbelo a mano.") }
    }

    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Bg) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Sos, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Emergencia", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Lbl("Toca a quién llamar: se abre tu teléfono con el número listo.")
                }
            }
            // Llamada principal: 123 atiende policía, ambulancias y bomberos en todo el país.
            Box(
                Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(16.dp)).background(Red).clickable { dial(ctx, "123") }.padding(16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Call, null, Modifier.size(30.dp), tint = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Llamar al 123", color = Color.White, fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("Línea única de emergencias de Colombia", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            }
            LINES.forEach { l -> CallRow(l.icon, l.title, l.detail, l.number) { dial(ctx, l.number) } }

            H("Mis contactos de emergencia", Icons.Filled.ContactPhone)
            if (contacts.isEmpty() && suggested == null) Lbl("Agrega a alguien que deba enterarse si te pasa algo. Se guarda solo en este teléfono.")
            contacts.forEachIndexed { i, c ->
                Card2(index = 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.Bold); Lbl(c.phone) }
                        IconButton({ save(contacts.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Delete, "Quitar ${c.name}", tint = Muted) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Llamar", Modifier.weight(1f), Icons.Filled.Call, kind = BK.Sos, small = true) { dial(ctx, c.phone) }
                        Btn("Mensaje", Modifier.weight(1f), Icons.Filled.Sms, kind = BK.Ghost, small = true) {
                            val i2 = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.phone))).putExtra("sms_body", helpText(lat, lng, near))
                            try { ctx.startActivity(i2) } catch (_: ActivityNotFoundException) { vm.toast("No encontré una app de mensajes.") }
                        }
                    }
                }
            }
            if (suggested != null && contacts.size < EmergencyContacts.MAX) Card2(tone = CyanSoft) {
                Lbl("En tu perfil de conductor tienes a ${suggested.emergencyName.ifBlank { "un contacto" }} · ${suggested.emergencyPhone}")
                Btn("Agregarlo aquí", Modifier.fillMaxWidth(), Icons.Filled.Add, kind = BK.Ghost, small = true) {
                    save(contacts + EmergencyContact(suggested.emergencyName.ifBlank { "Contacto" }.take(40), EmergencyContacts.clean(suggested.emergencyPhone)))
                }
            }
            if (contacts.size < EmergencyContacts.MAX) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("De mis contactos", Modifier.weight(1f), Icons.Filled.Contacts, kind = BK.Ghost, small = true) {
                    try { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                    catch (_: ActivityNotFoundException) { adding = true }
                }
                Btn("Escribirlo", Modifier.weight(1f), Icons.Filled.Edit, kind = BK.Ghost, small = true) { adding = true }
            }
            if (adding) AddContactCard(onCancel = { adding = false }) { c -> save(contacts + c); adding = false }

            H("Dónde estás", Icons.Filled.MyLocation)
            Card2 {
                if (lat == 0.0) {
                    Lbl("Para decirle al 123 o a tu contacto dónde estás.")
                    Btn("Ver mi ubicación", Modifier.fillMaxWidth(), Icons.Filled.MyLocation, kind = BK.Ghost) { getLoc() }
                } else {
                    near?.let { Text("Cerca de $it", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    Text("%.5f, %.5f".format(java.util.Locale.US, lat, lng), fontFamily = JetBrainsMono, fontSize = 15.sp)
                    Lbl("Dicta estas coordenadas si no sabes la dirección. Tu ubicación no se envía a nadie a menos que la compartas.")
                    Btn("Compartir mi ubicación", Modifier.fillMaxWidth(), Icons.Filled.Share, kind = BK.Ghost) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, helpText(lat, lng, near))
                        ctx.startActivity(Intent.createChooser(send, "Enviar mi ubicación").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }

            // En una rodada con el mapa del grupo encendido: el punto de la persona se pone rojo para todos.
            if (vm.sharingRodada != null) Card2(tone = if (vm.emergencyOn) RedSoft else Color.White) {
                if (!vm.emergencyOn) {
                    Lbl("Tu rodada te ve en el mapa. Si los avisas, tu punto se pone rojo y les sale una alerta.")
                    Btn("Avisar a mi rodada", Modifier.fillMaxWidth(), Icons.Filled.Campaign, kind = BK.Sos) { vm.emergencyOn = true; vm.toast("Tu rodada verá la alerta en unos segundos") }
                } else {
                    IconLine(Icons.Filled.Campaign, "Tu rodada ya ve la alerta.", Red)
                    Btn("Ya estoy bien, quitar la alerta", Modifier.fillMaxWidth(), Icons.Filled.CheckCircle, kind = BK.Ghost) { vm.emergencyOn = false }
                }
            }

            Card2(tone = Surf3) {
                IconLine(Icons.Filled.HealthAndSafety, "Si hay heridos: no les quites el casco ni los muevas, señaliza la vía y espera a la ambulancia.", Ink, bold = false)
            }
            Lbl("¿Un pinchazo, batería o sin gasolina? Eso es Asistencia técnica, en la pestaña Asistencia.", Muted)
        }
    }
}

@Composable
private fun CallRow(icon: ImageVector, title: String, detail: String, number: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Surf3, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Llamar a $title", onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(RedSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Red) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Lbl(detail) }
        Text(number, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Red)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Call, null, Modifier.size(20.dp), tint = Red)
    }
}

@Composable
private fun AddContactCard(onCancel: () -> Unit, onSave: (EmergencyContact) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val digits = EmergencyContacts.clean(phone)
    Card2 {
        Field("Nombre", name, { name = it.take(40) }, hint = "Ej: Mamá", cap = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
        Field("Celular", phone, { phone = it.take(20) }, hint = "300 123 4567", kb = androidx.compose.ui.text.input.KeyboardType.Phone)
        Missing(buildList { if (name.isBlank()) add("el nombre"); if (digits.count { it.isDigit() } < 7) add("el número") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Cancelar", Modifier.weight(0.7f), kind = BK.Ghost, small = true) { onCancel() }
            Btn("Guardar", Modifier.weight(1f), Icons.Filled.Check, small = true, enabled = name.isNotBlank() && digits.count { it.isDigit() } >= 7) {
                onSave(EmergencyContact(name.trim(), digits))
            }
        }
    }
}
