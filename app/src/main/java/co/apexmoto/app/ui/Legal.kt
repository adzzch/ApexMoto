@file:OptIn(ExperimentalMaterial3Api::class)

package co.apexmoto.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.apexmoto.app.core.BusinessDays
import co.apexmoto.app.core.Retract
import co.apexmoto.app.data.AppUser
import co.apexmoto.app.data.Role
import co.apexmoto.app.data.ServiceRequest
import co.apexmoto.app.legal.AcceptMethod
import co.apexmoto.app.legal.Legal
import co.apexmoto.app.legal.LegalDoc
import co.apexmoto.app.legal.Provider
import co.apexmoto.app.legal.RequestType
import co.apexmoto.app.ui.theme.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// ───────────────── Lectura de documentos ─────────────────

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) { append(part) } else append(part) }
}

/** Muestra los documentos legales, escritos en un Markdown mínimo: títulos (#, ##), listas (-) y **negrilla**. */
@Composable
fun MarkdownText(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("## ") -> Text(line.removePrefix("## "), Modifier.padding(top = 8.dp), fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 21.sp)
                line.startsWith("# ") -> Text(line.removePrefix("# "), fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 27.sp)
                line.startsWith("- ") -> Row {
                    Text("•", Modifier.width(16.dp), color = OrangeDark, fontWeight = FontWeight.Bold)
                    Text(inline(line.removePrefix("- ")), color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(inline(line), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Hoja con el texto completo de un documento. Funciona sin conexión: el texto viaja con la aplicación. */
@Composable
fun LegalDocSheet(doc: LegalDoc, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember(doc.id) { runCatching { Legal.text(ctx, doc) }.getOrDefault("No pudimos cargar este documento. Actualiza la aplicación e inténtalo de nuevo.") }
    Sheet(onDismiss) {
        Row { Pill("Versión ${doc.version}", CyanSoft, CyanDeep) }
        MarkdownText(text)
        Btn("Cerrar", Modifier.fillMaxWidth(), kind = BK.Ghost) { onDismiss() }
    }
}

/** Enlaces para consultar los documentos sin tener cuenta (pie de la pantalla de ingreso). */
@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf<LegalDoc?>(null) }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Legal.TERMS to "Términos", Legal.PRIVACY to "Privacidad", Legal.REFUNDS to "Reembolsos", Legal.TRACKING to "Rastreo").forEach { (id, label) ->
            Text(label, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Leer $label") { open = Legal.doc(id) }.padding(horizontal = 8.dp, vertical = 12.dp),
                color = Muted, fontSize = 12.sp, textDecoration = TextDecoration.Underline)
        }
    }
    open?.let { LegalDocSheet(it) { open = null } }
}

// ───────────────── Casillas de aceptación ─────────────────

/**
 * Una casilla por documento, nunca una sola para todo: la autorización de datos personales debe ser expresa y
 * separada de la aceptación de los términos (Ley 1581 de 2012, art. 9; Decreto 1377 de 2013, arts. 5 y 7).
 * Ninguna casilla viene marcada y cada una tiene al lado el enlace a su documento.
 */
@Composable
fun LegalChecks(docs: List<LegalDoc>, checked: Set<String>, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf<LegalDoc?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        docs.forEach { d ->
            val on = d.id in checked
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                        .toggleable(on, role = androidx.compose.ui.semantics.Role.Checkbox) { onChange(if (it) checked + d.id else checked - d.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(on, null, Modifier.padding(top = 1.dp), colors = CheckboxDefaults.colors(checkedColor = Orange, uncheckedColor = Outline, checkmarkColor = Color.White))
                    Spacer(Modifier.width(10.dp))
                    Text(d.accept, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Text("Leer: ${d.title}", Modifier.padding(start = 26.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Leer ${d.title}") { open = d }.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = CyanDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textDecoration = TextDecoration.Underline)
            }
        }
    }
    open?.let { LegalDocSheet(it) { open = null } }
}

/**
 * Bloquea la aplicación hasta aceptar los documentos pendientes: cuentas creadas antes de que existieran,
 * o una versión nueva. Quien no esté de acuerdo puede cerrar sesión o eliminar su cuenta.
 */
@Composable
fun ReconsentScreen(vm: AppViewModel, u: AppUser) {
    val docs = Legal.pending(u.role, u.legal)
    var checked by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandMark(52.dp)
        Text(if (u.legal.isEmpty()) "Antes de continuar" else "Actualizamos nuestros documentos", fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 32.sp)
        Text("Para seguir usando ApexMoto necesitamos que leas y aceptes lo siguiente. Guardamos la fecha y la versión que aceptas.", color = Muted, style = MaterialTheme.typography.bodyLarge)
        Card2 { LegalChecks(docs, checked) { checked = it } }
        Btn("Aceptar y continuar", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = checked.containsAll(docs.map { it.id }), loading = busy) {
            busy = true; vm.acceptDocs(docs, AcceptMethod.REACCEPT).invokeOnCompletion { busy = false }
        }
        TextButton({ vm.logout() }, Modifier.align(Alignment.CenterHorizontally)) { Text("Cerrar sesión", color = Muted) }
        TextButton({ confirmDelete = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("No acepto: eliminar mi cuenta", color = Red) }
    }
    if (confirmDelete) DeleteAccountDialog(vm) { confirmDelete = false }
}

// ───────────────── Ubicación ─────────────────

/**
 * Aviso previo al permiso de ubicación de Android: explica para qué se usa antes de que aparezca el cuadro del sistema.
 * Es la autorización expresa e informada (Ley 1581) y la "divulgación destacada" que pide Google Play.
 */
@Composable
fun LocationDisclosure(onAccept: () -> Unit, onDismiss: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
        icon = { Box(Modifier.size(56.dp).clip(CircleShape).background(CyanSoft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MyLocation, null, Modifier.size(28.dp), tint = CyanDeep) } },
        title = { Text("¿Nos dejas usar tu ubicación?", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("La usamos solo con la aplicación abierta, para:", color = Muted, style = MaterialTheme.typography.bodyMedium)
                IconLine(Icons.Filled.Navigation, "Guiarte giro a giro. No guardamos tu recorrido.", Ink, bold = false)
                IconLine(Icons.Filled.Sos, "Enviar tu punto al taller en un S.O.S. Se borra al terminar.", Ink, bold = false)
                IconLine(Icons.Filled.Map, "Mostrarte en el mapa de comercios.", Ink, bold = false)
                Text("No la usamos en segundo plano ni para publicidad. Si dices que no, la aplicación sigue funcionando sin guiado en vivo.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Leer el aviso completo", Modifier.clip(RoundedCornerShape(8.dp)).clickable { full = true }.padding(vertical = 8.dp),
                    color = CyanDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textDecoration = TextDecoration.Underline)
            }
        },
        confirmButton = { Btn("Aceptar y continuar", Modifier.fillMaxWidth()) { onAccept() } },
        dismissButton = { TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Ahora no", color = Muted) } },
    )
    if (full) LegalDocSheet(Legal.doc(Legal.LOCATION)) { full = false }
}

// ───────────────── Retracto ─────────────────

private val DayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "CO"))
fun longDate(d: LocalDate): String = d.format(DayFmt)

/** Una renta se puede cancelar desde la aplicación mientras no haya empezado. */
fun canCancel(r: ServiceRequest) = r.status == "p" || r.status == "ok"

/**
 * Cancelación de una renta. Si la persona está dentro de los 5 días hábiles y el servicio no ha empezado,
 * se registra como retracto (art. 47 Ley 1480 de 2011) y se le informa que la devolución es total.
 */
@Composable
fun CancelRequestDialog(vm: AppViewModel, r: ServiceRequest, onDismiss: () -> Unit) {
    val retract = Retract.applies(r.createdAt, r.dateFrom, started = !canCancel(r))
    var policy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
        title = { Text(if (retract) "Ejercer mi derecho de retracto" else "Cancelar solicitud", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.itemName, fontWeight = FontWeight.Bold)
                if (retract) {
                    Text("Estás dentro del plazo de 5 días hábiles (vence el ${longDate(Retract.deadline(r.createdAt))}) y el servicio no ha empezado.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text("Si ya pagaste, quien recibió el dinero debe devolvértelo completo, sin descuentos, en máximo 30 días calendario. Queda registrada la fecha de tu retracto.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("El plazo de retracto ya venció o el servicio empieza hoy. Aplican las condiciones de cancelación que te informó el aliado antes de reservar; si no te informó ninguna, la devolución es total.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Leer la política de cancelaciones", Modifier.clip(RoundedCornerShape(8.dp)).clickable { policy = true }.padding(vertical = 8.dp),
                    color = CyanDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textDecoration = TextDecoration.Underline)
            }
        },
        confirmButton = {
            Btn(if (retract) "Confirmar retracto" else "Cancelar solicitud", kind = BK.Danger) {
                onDismiss()
                vm.act(if (retract) "Retracto registrado. Avisamos al aliado." else "Solicitud cancelada") { vm.repo.cancelRequest(r, if (retract) "retracto" else "cancelacion") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Volver", color = Muted) } },
    )
    if (policy) LegalDocSheet(Legal.doc(Legal.REFUNDS)) { policy = false }
}

// ───────────────── Eliminar cuenta ─────────────────

@Composable
fun DeleteAccountDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
        title = { Text("¿Eliminar tu cuenta?", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se borran tu perfil, tus rutas guardadas y lo que publicaste (negocio o motos). No se puede deshacer.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text("Conservamos, por el tiempo que indica la Política de Privacidad, el registro de tus aceptaciones y el historial de servicios con otras personas, porque la ley nos exige poder demostrarlos y atender reclamos.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Btn("Eliminar mi cuenta", kind = BK.Danger) { onDismiss(); vm.deleteAccount() } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar", color = Muted) } },
    )
}

// ───────────────── Centro de legal y privacidad ─────────────────

private val StampFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "CO"))
private fun stamp(d: Date?): String = d?.let { StampFmt.format(it) } ?: "registrando…"

@Composable
private fun DocRow(title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Description, null, Modifier.size(20.dp), tint = CyanDeep); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Lbl(sub) }
        Icon(Icons.Filled.ChevronRight, null, tint = Outline)
    }
}

/**
 * Todo lo legal en un solo lugar del perfil: documentos, datos personales (consultar, corregir, descargar, suprimir),
 * solicitudes con su plazo de respuesta, registro de aceptaciones y datos del oferente.
 */
@Composable
fun LegalCenterScreen(vm: AppViewModel, u: AppUser) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<LegalDoc?>(null) }
    var export by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var filed by remember { mutableStateOf<Pair<String, String>?>(null) }   // (radicado, tipo)
    val acceptances by remember(u.id) { vm.repo.acceptances(u.id) }.collectAsState(initial = emptyList())
    val requests by remember(u.id) { vm.repo.privacyRequests(u.id) }.collectAsState(initial = emptyList())
    val partner = u.role != Role.RIDER

    Screen {
        // 1. Documentos
        H("Documentos", Icons.Filled.Gavel)
        Card2 {
            val docs = Legal.PUBLIC + listOfNotNull(Legal.doc(Legal.OWNER).takeIf { u.role == Role.OWNER }, Legal.doc(Legal.LICENSES))
            docs.forEach { d -> DocRow(d.title, "Versión ${d.version}") { open = d } }
        }

        // 2. Datos personales
        H("Tus datos", Icons.Filled.Person)
        Card2 {
            var name by remember(u.name) { mutableStateOf(u.name) }
            Field("Nombre que ven los demás", name, { name = it.take(60) })
            if (name.trim() != u.name) Btn("Guardar nombre", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = name.isNotBlank()) { vm.updateName(name) }
            if (u.email.isNotBlank()) Sub("Ingresas con ${u.email}. Para cambiarlo, envía una solicitud abajo.")
            Btn("Ver y descargar mis datos", Modifier.fillMaxWidth(), Icons.Filled.Download, kind = BK.Ghost, loading = exporting) {
                exporting = true
                vm.act { export = vm.repo.exportMyData(u.id) }.invokeOnCompletion { exporting = false }
            }
        }

        // 3. Identificación del oferente (art. 53 Ley 1480)
        if (partner) {
            H("Datos del oferente", Icons.Filled.Badge)
            ProviderCard(vm, u)
        }

        // 4. Solicitudes
        H("Enviar una solicitud", Icons.AutoMirrored.Filled.Send)
        Card2 {
            var type by rememberSaveable { mutableStateOf("consulta") }
            var detail by rememberSaveable { mutableStateOf("") }
            Sub("Consultas, correcciones, supresión de datos, quejas y reclamos. Es gratis.")
            Drop("¿Qué necesitas?", RequestType.ALL.map { it.first to it.second }, type, { type = it })
            Field("Cuéntanos el detalle", detail, { detail = it.take(2000) }, lines = 3, hint = "Qué pides y por qué")
            Lbl("Respondemos en máximo ${RequestType.days(type)} días hábiles.")
            Btn("Enviar solicitud", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Send, enabled = detail.trim().length >= 10) {
                val t = type; val d = detail
                vm.act { filed = vm.repo.createPrivacyRequest(u, t, d) to t; detail = "" }
            }
        }
        requests.sortedByDescending { it.createdAt ?: Date() }.forEach { r ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(RequestType.label(r.type), fontWeight = FontWeight.Bold); Lbl("Radicado ${r.id.take(8).uppercase()} · ${stamp(r.createdAt)}") }
                    if (r.status == "respondida") Pill("Respondida", GreenSoft, Green) else Pill(if (r.status == "en_tramite") "En trámite" else "Recibida", AmberSoft, Amber)
                }
                if (r.response.isNotBlank()) Text(r.response, style = MaterialTheme.typography.bodyMedium)
                else r.createdAt?.let { Sub("Plazo de respuesta: ${longDate(BusinessDays.addBusinessDays(BusinessDays.dateOf(it.time), RequestType.days(r.type)))}") }
            }
        }

        // 5. Aceptaciones
        H("Lo que has aceptado", Icons.Filled.FactCheck)
        if (acceptances.isEmpty()) Empty("Aún no hay registros.", Icons.Filled.FactCheck)
        else Card2 {
            acceptances.sortedByDescending { it.acceptedAt ?: Date() }.forEach { a ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("${Legal.DOCS.firstOrNull { it.id == a.docId }?.title ?: a.docId} · v${a.version}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Lbl(stamp(a.acceptedAt))
                }
            }
        }

        // 6. Analítica
        Card2(tone = Surf1) {
            IconLine(Icons.Filled.VisibilityOff, "Sin analítica ni publicidad", CyanDeep)
            Sub("Esta versión no usa analítica, publicidad ni reportes automáticos de fallos. Si algún día se activan, te pediremos permiso primero.")
        }

        // 7. Supresión
        Btn("Eliminar mi cuenta", Modifier.fillMaxWidth(), Icons.Filled.DeleteForever, kind = BK.Danger) { confirmDelete = true }
        Spacer(Modifier.height(8.dp))
    }

    open?.let { LegalDocSheet(it) { open = null } }
    if (confirmDelete) DeleteAccountDialog(vm) { confirmDelete = false }
    filed?.let { (id, type) ->
        AlertDialog(
            onDismissRequest = { filed = null }, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
            title = { Text("Solicitud recibida", fontFamily = Chivo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tu número de radicado es", color = Muted)
                    Text(id.take(8).uppercase(), fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Te respondemos a más tardar el ${longDate(BusinessDays.addBusinessDays(BusinessDays.today(), RequestType.days(type)))}. Verás la respuesta en esta misma pantalla.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { Btn("Entendido", Modifier.fillMaxWidth()) { filed = null } },
        )
    }
    export?.let { text ->
        Sheet({ export = null }) {
            H("Tus datos en ApexMoto", Icons.Filled.Download)
            Sub("Esto es todo lo que guardamos sobre ti. Puedes compartirlo o guardarlo.")
            Btn("Compartir o guardar", Modifier.fillMaxWidth(), Icons.Filled.Share) {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Mis datos en ApexMoto").putExtra(Intent.EXTRA_TEXT, text.take(200_000)), "Compartir mis datos"))
            }
            Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surf0).padding(12.dp), fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 15.sp, color = Ink)
        }
    }
}

/**
 * Identificación del aliado. El art. 53 de la Ley 1480 de 2011 obliga a la plataforma a tenerla y a entregarla
 * al consumidor que la pida para reclamar. No se muestra a otros usuarios.
 */
@Composable
private fun ProviderCard(vm: AppViewModel, u: AppUser) {
    var loaded by remember(u.id) { mutableStateOf<Provider?>(null) }
    var ready by remember(u.id) { mutableStateOf(false) }
    LaunchedEffect(u.id) { loaded = runCatching { vm.repo.provider(u.id) }.getOrNull(); ready = true }
    val p = loaded ?: Provider()
    var legalName by remember(ready) { mutableStateOf(p.legalName) }
    var idType by remember(ready) { mutableStateOf(p.idType) }
    var idNumber by remember(ready) { mutableStateOf(p.idNumber) }
    var address by remember(ready) { mutableStateOf(p.address) }
    var phone by remember(ready) { mutableStateOf(p.phone) }
    val draft = Provider(legalName = legalName, idType = idType, idNumber = idNumber, address = address, phone = phone)
    Card2(border = if (ready && loaded?.complete != true) AmberSoft else null) {
        Sub("La ley nos exige identificar a quien ofrece productos o servicios en la plataforma. Estos datos no son públicos: solo se entregan a un cliente tuyo que los pida para presentar un reclamo.")
        Field("Nombre completo o razón social", legalName, { legalName = it.take(120) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Drop("Documento", listOf("CC" to "Cédula", "CE" to "Cédula de extranjería", "NIT" to "NIT", "PAS" to "Pasaporte"), idType, { idType = it }, Modifier.weight(0.9f))
            Field("Número", idNumber, { idNumber = it.filter { c -> c.isLetterOrDigit() || c == '-' }.take(20) }, Modifier.weight(1.1f))
        }
        Field("Dirección de notificaciones", address, { address = it.take(160) })
        Field("Teléfono", phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20) }, kb = KeyboardType.Phone)
        Btn("Guardar datos del oferente", Modifier.fillMaxWidth(), Icons.Filled.Check, enabled = ready && draft.complete) {
            vm.act("Datos guardados") { vm.repo.saveProvider(u.id, draft); loaded = draft }
        }
    }
}
