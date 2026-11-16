@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.apexmoto.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.apexmoto.app.core.*
import co.apexmoto.app.data.*
import co.apexmoto.app.ui.theme.*

// ───────────────── Comunidad / rodadas ─────────────────
@Composable
fun CommunityScreen(vm: AppViewModel, nav: NavController, u: AppUser) {
    val reqIn by vm.friendReqIn.collectAsState()
    Screen {
        Segmented(listOf(Triple("rodadas", "Rodadas", Icons.Filled.Groups),
            Triple("amigos", if (reqIn.isEmpty()) "Amigos" else "Amigos · ${reqIn.size}", Icons.Filled.People)), vm.communityTab) { vm.communityTab = it }
        if (vm.communityTab == "amigos") FriendsPane(vm, u) else RodadasPane(vm, nav, u)
    }
}

// ───────────────── Rentar motos ─────────────────
fun bikeTint(b: Bike) = when (b.category) { "Deportiva" -> OrangeSoft; "Turismo" -> CyanTint; "Urbana" -> Surf3; else -> GreenTint }

@Composable
fun RentScreen(vm: AppViewModel, nav: NavController) {
    Screen { BikesList(vm, nav) }
}

@Composable
private fun ColumnScope.BikesList(vm: AppViewModel, nav: NavController) {
    val bikes by vm.bikes.collectAsState()
    // Ciudad donde se recoge: empieza en la ciudad de la persona (la de Inicio) y se cambia con las ciudades del catálogo.
    // Cambiarla aquí no cambia la de Inicio: se puede rentar en la ciudad a la que se va a viajar.
    val cp = rememberCatalogAndPlace(vm)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }   // null = la de Inicio; "all" = todas
    val city = picked ?: cp?.second?.town?.name ?: cp?.second?.label?.substringBefore(",")
    var pickCity by remember { mutableStateOf(false) }
    val askLocation = rememberLocationRequest(vm) { la, ln -> cp?.first?.nearestTown(la, ln)?.let { picked = it.name } }
    fun sameCity(b: Bike) = city == null || city == "all" || norm(b.city.trim()) == norm(city)
    var cat by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("rec") }
    val days = maxOf(1, daysBetween(vm.rentFrom, vm.rentTo) + 1)
    Card2 {
        Lbl("¿Dónde la recoges?")
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(Surf0).clickable(enabled = cp != null) { pickCity = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, null, tint = OrangeDark); Spacer(Modifier.width(10.dp))
            Text(when (city) { null -> "Buscando tu ciudad…"; "all" -> "Todas las ciudades"; else -> city }, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Cambiar", color = CyanDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Recoges", vm.rentFrom, today(), Modifier.weight(1f)) { vm.rentFrom = it; if (vm.rentTo < it) vm.rentTo = addDays(it, 1) }
            DateField("Devuelves", vm.rentTo, vm.rentFrom, Modifier.weight(1f)) { vm.rentTo = it }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Todas", cat == "all", accent = Cyan) { cat = "all" }
        bikes.map { it.category }.distinct().sorted().forEach { c -> Chip(c, cat == c, accent = Cyan) { cat = c } }
    }
    // Una moto sin reseñas no cuenta como "5,0": va después de las calificadas.
    fun score(b: Bike) = if (b.reviews > 0) b.rating else 0.0
    val list = bikes.filter { it.available && sameCity(it) && (cat == "all" || it.category == cat) }
        .let { l -> when (sort) { "pa" -> l.sortedBy { it.pricePerDay }; "pd" -> l.sortedByDescending { it.pricePerDay }; "rt" -> l.sortedByDescending { score(it) }; else -> l.sortedByDescending { score(it) * 10 + it.reviews / 100.0 } } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lbl("${list.size} moto${if (list.size == 1) "" else "s"} · $days día${if (days == 1) "" else "s"}", modifier = Modifier.weight(0.9f))
        Drop("", listOf("rec" to "Recomendadas", "pa" to "Menor precio", "pd" to "Mayor precio", "rt" to "Mejor calificadas"), sort, { sort = it }, Modifier.weight(1.1f))
    }
    if (list.isEmpty()) Empty(if (city != null && city != "all") "No hay motos en $city con esos filtros." else "No hay motos con esos filtros.", Icons.Filled.TwoWheeler)
    // Otras ciudades con motos disponibles, para no quedarse en una lista vacía.
    val others = bikes.filter { it.available && (cat == "all" || it.category == cat) && !sameCity(it) }.groupingBy { it.city.trim() }.eachCount()
    if (city != "all" && others.isNotEmpty()) {
        Lbl(if (list.isEmpty()) "Hay motos en:" else "También hay motos en:")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            others.entries.sortedByDescending { it.value }.forEach { (c, n) -> Chip("$c · $n", false, Icons.Filled.LocationCity, accent = Cyan) { picked = c } }
            Chip("Todas", false, accent = Cyan) { picked = "all" }
        }
    }
    cp?.first?.let { c ->
        if (pickCity) CityPicker(c, onDismiss = { pickCity = false }, onGps = { pickCity = false; askLocation() }, title = "¿En qué ciudad la recoges?") { t ->
            pickCity = false; picked = t.name
        }
    }
    list.forEachIndexed { i, b ->
        val busy = overlaps(b.booked, vm.rentFrom, vm.rentTo)
        Card2(Modifier.alpha(if (busy) 0.6f else 1f), index = i, padding = 0.dp, onClick = { nav.navigate("bike/${b.id}") }) {
            MediaHeader(bikeTint(b), Icons.Filled.TwoWheeler, 150.dp) {
                if (b.instant) Box(Modifier.align(Alignment.TopStart).padding(Gap.card)) { Pill("Instantánea", Color.White, OrangeDark, Icons.Filled.Bolt) }
                Row(Modifier.align(Alignment.BottomEnd).padding(Gap.card).glow(Orange, RoundedCornerShape(9.dp), elevation = 6.dp).clip(RoundedCornerShape(9.dp)).background(Orange).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Num(cop(b.pricePerDay), Color.White, 16); Text(" /día", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.padding(start = Gap.card, end = Gap.card, bottom = Gap.card), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.reviews > 0) Stars(b.rating) else Pill("Nueva")
                }
                Lbl("${b.city}${if (b.reviews > 0) " · ${b.reviews} reseña${if (b.reviews == 1) "" else "s"}" else ""}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${b.cc} cc"); Pill(b.category); Pill("Licencia ${licenseFor(b.cc)}")
                    if (b.minYears > 0) Pill("${b.minYears}+ años manejando")
                    b.specs.take(1).forEach { Pill(it) }
                }
                if (busy) Pill("No disponible en tus fechas", RedSoft, Red)
                else Lbl("$days día${if (days == 1) "" else "s"} · ${cop(days * b.pricePerDay)}", OrangeDark)
            }
        }
    }
}

@Composable
fun BikeDetailScreen(vm: AppViewModel, nav: NavController, u: AppUser, id: String) {
    val bikes by vm.bikes.collectAsState()
    val profile by vm.riderProfile.collectAsState()
    val myReqs by vm.reqFrom.collectAsState()
    val b = bikes.firstOrNull { it.id == id }
    var extras by remember { mutableStateOf(setOf<String>()) }
    var delivery by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf(false) }
    var agree by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf(false) }
    if (editProfile) RiderProfileSheet(vm, u, profile) { editProfile = false }
    if (b == null) { Screen { Empty("Cargando moto…") }; return }
    val docsOk = bikeDocsOk(b.soatUntil, b.rtmUntil, vm.rentTo)
    val cat = licenseFor(b.cc)
    val p = profile
    // Requisitos contra el perfil de conductor. Las reglas del servidor comprueban lo mismo.
    val catOk = p != null && licenseCovers(p.licenseCat, b.cc)
    val licenseValid = p != null && p.licenseUntil >= vm.rentTo
    val yearsOk = p != null && yearsRiding(p.since) >= b.minYears
    val profileOk = catOk && licenseValid && yearsOk
    // Reserva inmediata solo con historial: al menos una renta terminada en ApexMoto.
    val history = myReqs.firstOrNull { it.type == "rent" && it.status == "fin" }
    val instant = b.instant && history != null
    val days = maxOf(1, daysBetween(vm.rentFrom, vm.rentTo) + 1)
    val ex = C.EXTRAS.filter { it.key in extras }
    val base = b.pricePerDay * days
    val total = base + ex.sumOf { it.price } * days + if (delivery) C.DELIVERY_FEE else 0L
    val busy = overlaps(b.booked, vm.rentFrom, vm.rentTo)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MediaHeader(bikeTint(b), Icons.Filled.TwoWheeler, 190.dp) {
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (b.instant) Pill("Reserva instantánea", Color.White, OrangeDark, Icons.Filled.Bolt)
                    Text(b.name, fontFamily = Chivo, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { RatingLine(b.rating, b.reviews); Spacer(Modifier.width(10.dp)); Lbl(b.city) }
                }
            }
            Column(Modifier.padding(horizontal = Gap.screen, vertical = Gap.between), verticalArrangement = Arrangement.spacedBy(Gap.between)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${b.cc} cc"); Pill(b.category); b.specs.forEach { Pill(it) }
                }
                Card2 {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField("Recoges", vm.rentFrom, today(), Modifier.weight(1f)) { vm.rentFrom = it; if (vm.rentTo < it) vm.rentTo = addDays(it, 1) }
                        DateField("Devuelves", vm.rentTo, vm.rentFrom, Modifier.weight(1f)) { vm.rentTo = it }
                    }
                    if (busy) Pill("La moto no está disponible en esas fechas", RedSoft, Red)
                }
                Card2(border = if (docsOk && (p == null || profileOk)) null else RedSoft) {
                    H("Requisitos", Icons.Filled.VerifiedUser)
                    if (docsOk) IconLine(Icons.Filled.CheckCircle, "SOAT hasta ${shortDate(b.soatUntil)} · Revisión técnico-mecánica hasta ${shortDate(b.rtmUntil)}", Green, bold = false)
                    else IconLine(Icons.Filled.ErrorOutline, "El dueño no tiene SOAT o revisión técnico-mecánica vigentes para esas fechas. No se puede rentar.", Red, bold = false)
                    Sub("Son fechas declaradas por el dueño. Pídele los documentos al recibir la moto.")
                    if (p == null) {
                        IconLine(Icons.Filled.Badge, "Pide licencia $cat${if (b.minYears > 0) " y ${b.minYears}+ años manejando" else ""}.", CyanDeep, bold = false)
                        Sub("Para pedirla completa tu perfil de conductor: lo ve solo el dueño, dentro de tu solicitud.")
                        Btn("Completar mi perfil de conductor", Modifier.fillMaxWidth(), Icons.Filled.Badge, kind = BK.Ghost) { editProfile = true }
                    } else {
                        ReqLine(catOk, if (catOk) "Licencia $cat: tienes ${p.licenseCat}" else "Pide licencia $cat y la tuya es ${p.licenseCat}")
                        ReqLine(licenseValid, if (licenseValid) "Tu licencia está vigente hasta ${yearDate(p.licenseUntil)}" else "Tu licencia vence antes de devolver la moto (${yearDate(p.licenseUntil)})")
                        if (b.minYears > 0) ReqLine(yearsOk, "Pide ${b.minYears}+ años manejando: tienes ${yearsLabel(p.since)}")
                        TextButton({ editProfile = true }, contentPadding = EdgeLink) { Text("Editar mi perfil de conductor", color = CyanDeep) }
                        // Declaración del rider. No se pide foto ni número de la licencia: el dueño la verifica al entregar.
                        CheckRow(license, "Declaro que mi licencia de conducción categoría ${p.licenseCat} está vigente y que la presentaré al recibir la moto.") { license = it }
                    }
                }
                Card2 {
                    H("Acuerdo de daños", Icons.Filled.Handshake)
                    IconLine(Icons.Filled.Savings, if (b.deposit > 0) "Depósito de ${cop(b.deposit)}: se devuelve al entregar la moto, menos los daños del acta." else "Sin depósito.", Ink, bold = false)
                    IconLine(Icons.Filled.Build, if (b.damageCap > 0) "Si hay daños nuevos pagas como máximo ${cop(b.damageCap)}." else "Sin tope: si hay daños nuevos respondes por su valor, según la ley.", Ink, bold = false)
                    Sub("El desgaste normal no es un daño. Los daños se anotan en el acta de devolución, que tú confirmas. Es un acuerdo entre tú y el dueño, no un seguro.")
                    CheckRow(agree, "Acepto el acuerdo de daños de esta moto.") { agree = it }
                }
                Card2 {
                    H("Extras por día", Icons.Filled.AddCircle)
                    C.EXTRAS.chunked(2).forEach { pair ->
                        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { e ->
                                val on = e.key in extras
                                OptionTile(on, { extras = if (on) extras - e.key else extras + e.key }, Modifier.weight(1f).fillMaxHeight()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(e.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline, null, Modifier.size(20.dp), tint = if (on) CyanDeep else Outline)
                                    }
                                    Num("+${cop(e.price)}", Muted, 13)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (b.delivery) SwitchRow("Entrega a domicilio", "+${cop(C.DELIVERY_FEE)} una vez", delivery) { delivery = it }
                }
                Card2(tone = Surf1) {
                    Row { Text("$days día${if (days == 1) "" else "s"} × ${cop(b.pricePerDay)}", Modifier.weight(1f), color = Muted); Num(cop(base), Ink, 14) }
                    if (ex.isNotEmpty()) Row { Text("Extras", Modifier.weight(1f), color = Muted); Num(cop(ex.sumOf { it.price } * days), Ink, 14) }
                    if (delivery) Row { Text("Entrega", Modifier.weight(1f), color = Muted); Num(cop(C.DELIVERY_FEE), Ink, 14) }
                    if (b.deposit > 0) Row { Text("Depósito (se devuelve, no va en el total)", Modifier.weight(1f), color = Muted); Num(cop(b.deposit), Muted, 14) }
                    Sub("Le pagas directamente al dueño. Puedes retractarte sin costo dentro de los 5 días hábiles siguientes, antes de recibir la moto.")
                    if (b.instant && !instant) Sub("La reserva inmediata es para riders con al menos una renta terminada en ApexMoto. Esta vez el dueño aprueba tu solicitud.")
                }
                Card2 {
                    H("Reseñas", Icons.Filled.Star)
                    RatingLine(b.rating, b.reviews)
                    ReviewsList(vm, "bike", b.id)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        BottomBar {
            val faltan = buildList {
                if (p == null) add("tu perfil de conductor")
                else if (!profileOk) add(when { !catOk -> "licencia de la categoría"; !licenseValid -> "licencia vigente en esas fechas"; else -> "los años de experiencia" })
                if (!docsOk) add("SOAT y revisión vigentes de la moto")
                if (!license) add("declarar tu licencia")
                if (!agree) add("aceptar el acuerdo")
            }
            Column(Modifier.weight(1f)) {
                Lbl("Total"); Num(cop(animatedMoney(total)), OrangeDark, 22)
                if (faltan.isNotEmpty()) Text("Falta: ${joinEs(faltan)}.", color = Muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2)
            }
            Btn(if (instant) "Reservar" else "Solicitar", icon = Icons.Filled.Check, enabled = !busy && docsOk && p != null && profileOk && license && agree) {
                val pr = p ?: return@Btn
                val req = ServiceRequest(type = "rent", fromId = u.id, fromName = u.name, toId = b.ownerId, itemId = b.id, itemName = b.name, days = days,
                    amount = base, total = total, dateFrom = vm.rentFrom, dateTo = vm.rentTo, extras = ex.map { it.name }, delivery = delivery,
                    status = if (instant) "ok" else "p", licenseDeclared = true, licenseCat = pr.licenseCat,
                    licenseUntil = pr.licenseUntil, riderSince = pr.since, riderMaxCc = pr.maxCc, emergencyName = pr.emergencyName, emergencyPhone = pr.emergencyPhone,
                    deposit = b.deposit, damageCap = b.damageCap, damageAccepted = true, prevReq = if (instant) history?.id.orEmpty() else "")
                // La solicitud y el apartado de fechas van en un solo lote (o quedan las dos cosas o ninguna).
                vm.act(if (instant) "¡Moto reservada!" else "Solicitud enviada al dueño") { vm.repo.createRequest(req, if (instant) b else null) }
                nav.popBackStack()
            }
        }
    }
}

/** Requisito cumplido (verde) o no (rojo). */
@Composable
private fun ReqLine(ok: Boolean, text: String) =
    IconLine(if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, text, if (ok) Green else Red, bold = false)

@Composable
private fun CheckRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = Orange, uncheckedColor = Outline, checkmarkColor = Color.White))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

