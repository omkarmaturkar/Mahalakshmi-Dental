@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

val medicalOptions = listOf(
    "Diabetes", "Blood Pressure", "Heart Problem", "Allergy",
    "Asthma", "Thyroid", "Pregnant", "Blood Thinners"
)

// ───────────── New / edit patient ─────────────
@Composable
fun PatientFormScreen(db: ClinicDb, patientId: Long, onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit) {
    val notify = LocalNotify.current
    val existing = remember { if (patientId != 0L) db.getPatient(patientId) else null }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var age by remember { mutableStateOf(existing?.age ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val medical = remember { mutableStateListOf<String>().apply { addAll(existing?.alerts ?: emptyList()) } }

    fun save() {
        val id = db.savePatient(
            Patient(
                id = patientId, name = name.trim(), phone = phone.trim(), age = age.trim(), gender = gender,
                medical = medical.joinToString(", "), notes = notes.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        DataBus.changed()
        notify(if (existing == null) "Patient added ✓" else "Changes saved ✓")
        onSaved(id, existing == null)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp

        val preview: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name.ifBlank { "?" }, 76.dp, ring = true)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name.ifBlank { "Patient name" }, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                            color = if (name.isBlank()) Brand.Faint else Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val sub = listOf(gender, if (age.isNotBlank()) "$age yrs" else "", phone)
                            .filter { it.isNotBlank() }.joinToString("  ·  ")
                        Text(sub.ifBlank { "New patient" }, fontSize = 13.sp, color = Brand.Sub, maxLines = 1)
                    }
                }
            }
        }
        val basic: @Composable () -> Unit = {
            FormCard("Basic details") {
                AppField(name, { name = it }, "Full name *", icon = Icons.Filled.Person, caps = KeyboardCapitalization.Words)
                AppField(
                    phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Mobile number",
                    icon = Icons.Filled.Phone, type = KeyboardType.Phone
                )
                AppField(age, { age = it.filter { c -> c.isDigit() }.take(3) }, "Age (years)", type = KeyboardType.Number)
                Text("Gender", fontSize = 13.sp, color = Brand.Sub)
                Segmented(listOf("Male", "Female", "Other"), gender) { gender = if (gender == it) "" else it }
            }
        }
        val medicalCard: @Composable () -> Unit = {
            FormCard("Medical alerts", "Important health conditions – shown in red on the profile") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    medicalOptions.forEach { m ->
                        ChoiceChip(m, m in medical, selectedBg = Brand.Red) {
                            if (m in medical) medical.remove(m) else medical.add(m)
                        }
                    }
                }
            }
        }
        val notesCard: @Composable () -> Unit = {
            FormCard("Notes") {
                AppField(notes, { notes = it }, "Address, referred by, other notes", lines = 3)
            }
        }

        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar(
                if (existing == null) "New Patient" else "Edit Patient",
                if (existing == null) "Add the patient's details" else existing.name,
                onBack
            )
            if (twoColumn) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        preview()
                        basic()
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        medicalCard()
                        notesCard()
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    preview()
                    basic()
                    medicalCard()
                    notesCard()
                }
            }
            BottomActionBar {
                val label = if (existing == null) "Save & Add Visit" else "Save Changes"
                if (twoColumn) {
                    Spacer(Modifier.weight(1f))
                    GradientButton(label, Modifier.width(340.dp), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                } else {
                    GradientButton(label, Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                }
            }
        }
    }
}

// ───────────── Patient profile ─────────────
@Composable
fun PatientDetailScreen(db: ClinicDb, patientId: Long, go: (Screen) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val version = DataBus.version
    val patient = remember(version) { db.getPatient(patientId) }
    val visits = remember(version) { db.getVisits(patientId) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var payVisit by remember { mutableStateOf<Visit?>(null) }
    var payAll by remember { mutableStateOf(false) }
    if (patient == null) return

    val today = todayEpochDay()
    val nextVisit = visits.firstOrNull()?.nextVisit?.takeIf { it >= today }
    val onCall: () -> Unit = { callNumber(context, patient.phone) }
    val onChat: () -> Unit = { openWhatsApp(context, patient.phone, reminderMessage(patient, nextVisit)) }
    val onNewVisit: () -> Unit = { go(Screen.VisitForm(patientId)) }
    val onOpenVisit: (Visit) -> Unit = { v -> go(Screen.VisitForm(patientId, v.id)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Patient profile", patient.name, onBack) {
                CircleIconButton(Icons.Filled.Edit, { go(Screen.PatientForm(patientId)) }, tint = Brand.GreenDeep)
                Spacer(Modifier.width(10.dp))
                CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red)
            }
            if (twoColumn) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        Modifier.width(370.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                            .padding(top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileCard(patient, visits.size, nextVisit, onCall, onChat)
                        if (patient.alerts.isNotEmpty()) AlertCard(patient.alerts)
                        if (patient.notes.isNotBlank()) NotesCard(patient.notes)
                        PaymentCard(patient) { payAll = true }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("Treatment history", Modifier.weight(1f))
                            GradientButton("New Visit", Modifier.width(190.dp), Icons.Filled.Add, height = 50.dp, onClick = onNewVisit)
                        }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                            if (visits.isEmpty()) item(key = "none") {
                                EmptyState("No visits yet", "Tap \"New Visit\" to record the first treatment.")
                            }
                            itemsIndexed(visits, key = { _, v -> v.id }) { i, v ->
                                TimelineVisit(
                                    v, isLast = i == visits.lastIndex,
                                    onOpen = { onOpenVisit(v) },
                                    onPhoto = { index -> viewer = v.photos to index },
                                    onCollect = { payVisit = v }
                                )
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp)
                    ) {
                        item(key = "profile") {
                            Box(Modifier.padding(bottom = 16.dp)) { ProfileCard(patient, visits.size, nextVisit, onCall, onChat) }
                        }
                        if (patient.alerts.isNotEmpty()) item(key = "alerts") {
                            Box(Modifier.padding(bottom = 16.dp)) { AlertCard(patient.alerts) }
                        }
                        if (patient.notes.isNotBlank()) item(key = "notes") {
                            Box(Modifier.padding(bottom = 16.dp)) { NotesCard(patient.notes) }
                        }
                        item(key = "pay") {
                            Box(Modifier.padding(bottom = 24.dp)) { PaymentCard(patient) { payAll = true } }
                        }
                        item(key = "history") {
                            SectionHeader("Treatment history", Modifier.padding(bottom = 14.dp))
                        }
                        if (visits.isEmpty()) item(key = "none") {
                            EmptyState("No visits yet", "Tap \"New Visit\" to record the first treatment.")
                        }
                        itemsIndexed(visits, key = { _, v -> v.id }) { i, v ->
                            TimelineVisit(
                                v, isLast = i == visits.lastIndex,
                                onOpen = { onOpenVisit(v) },
                                onPhoto = { index -> viewer = v.photos to index },
                                onCollect = { payVisit = v }
                            )
                        }
                    }
                    GradientFab(
                        "New Visit", Icons.Filled.Add,
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
                        onClick = onNewVisit
                    )
                }
            }
        }
    }

    viewer?.let { (list, index) -> PhotoViewer(list, index) { viewer = null } }
    val target = payVisit
    if (payAll || target != null) {
        PaymentDialog(
            due = target?.remaining ?: patient.remaining,
            subtitle = if (target != null) "for visit on ${fmtDate(target.date)}" else "oldest dues first",
            onDismiss = {
                payAll = false
                payVisit = null
            }
        ) { amount, mode ->
            db.recordPayment(patientId, amount, mode, target?.id ?: 0L)
            payAll = false
            payVisit = null
            DataBus.changed()
            notify("Payment of ${rs(amount)} received ✓")
        }
    }
    if (askDelete) ConfirmDialog(
        "Delete ${patient.name}?",
        "All visits, photos and payments of this patient will be permanently deleted.",
        onConfirm = {
            askDelete = false
            db.deletePatient(patientId)
            DataBus.changed()
            notify("Patient deleted")
            onBack()
        },
        onDismiss = { askDelete = false }
    )
}

@Composable
private fun ProfileCard(p: Patient, visitCount: Int, nextVisit: Long?, onCall: () -> Unit, onChat: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(96.dp).background(CharcoalGradient).drawBehind {
                    val c = Offset(size.width * 0.9f, 0f)
                    val r = size.width * 0.6f
                    drawCircle(
                        Brush.radialGradient(listOf(Brand.Green.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r),
                        radius = r, center = c
                    )
                }
            )
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 46.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(102.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Avatar(p.name, 92.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Brand.Ink, textAlign = TextAlign.Center)
                val since = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(p.createdAt))
                val info = listOf(
                    p.gender,
                    if (p.age.isNotBlank()) "${p.age} yrs" else "",
                    if (visitCount == 1) "1 visit" else "$visitCount visits",
                    "Since $since"
                ).filter { it.isNotBlank() }.joinToString("  ·  ")
                Text(info, fontSize = 13.sp, color = Brand.Sub, textAlign = TextAlign.Center)
                if (p.phone.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(p.phone, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Brand.Ink)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton("Call", Icons.Filled.Call, Modifier.weight(1f), height = 48.dp, onClick = onCall)
                        SoftButton("WhatsApp", AppIcons.Chat, Modifier.weight(1f), height = 48.dp, onClick = onChat)
                    }
                }
                if (nextVisit != null) {
                    Spacer(Modifier.height(14.dp))
                    Pill("Next visit · ${fmtDay(nextVisit)}", Brand.GoldSoft, Brand.GoldDeep, Icons.Filled.DateRange)
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alerts: List<String>) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brand.RedSoft)
            .border(1.dp, Brand.Red.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Medical alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Red)
            Text(alerts.joinToString("  ·  "), fontSize = 14.sp, color = Brand.Ink)
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Text("Notes", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.GreenText)
        Spacer(Modifier.height(6.dp))
        Text(notes, fontSize = 14.sp, color = Brand.Ink)
    }
}

@Composable
private fun PaymentCard(p: Patient, onCollect: () -> Unit) {
    val target = if (p.totalFees > 0) (p.totalPaid / p.totalFees).toFloat().coerceIn(0f, 1f) else 0f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Payments", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.GreenText, modifier = Modifier.weight(1f))
            if (p.totalFees > 0) Pill("${(target * 100).roundToInt()}% paid", Brand.GreenSoft, Brand.GreenText)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            MoneyColumn("Total", rs(p.totalFees), Brand.Ink, Modifier.weight(1f))
            MoneyColumn("Paid", rs(p.totalPaid), Brand.GreenDeep, Modifier.weight(1f))
            MoneyColumn(
                "Due", rs(p.remaining.coerceAtLeast(0.0)),
                if (p.remaining > 0) Brand.Red else Brand.GreenDeep, Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Brand.Soft)) {
            Box(Modifier.fillMaxWidth(progress.value).fillMaxHeight().clip(RoundedCornerShape(50)).background(BrandGradient))
        }
        if (p.remaining > 0) {
            Spacer(Modifier.height(18.dp))
            GradientButton("Collect payment", Modifier.fillMaxWidth(), Icons.Filled.Add, onClick = onCollect)
        }
    }
}

@Composable
private fun MoneyColumn(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Brand.Sub)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TimelineVisit(
    v: Visit,
    isLast: Boolean,
    onOpen: () -> Unit,
    onPhoto: (Int) -> Unit,
    onCollect: () -> Unit
) {
    val today = todayEpochDay()
    Row(
        Modifier.fillMaxWidth().drawBehind {
            val x = 11.dp.toPx()
            val cy = 28.dp.toPx()
            if (!isLast) drawLine(Brand.GreenLine, Offset(x, cy), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, cy))
            drawCircle(Brand.Green, radius = 8.dp.toPx(), center = Offset(x, cy), style = Stroke(width = 3.dp.toPx()))
        }
    ) {
        Spacer(Modifier.width(34.dp))
        AppCard(Modifier.weight(1f).padding(bottom = 16.dp), onClick = onOpen, padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fmtDate(v.date), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink)
                    Text(fmtTime(v.date), fontSize = 12.sp, color = Brand.Faint)
                }
                when {
                    v.remaining > 0 -> Pill("Due ${rs(v.remaining)}", Brand.RedSoft, Brand.Red)
                    v.fees > 0 -> Pill("Paid", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
                    else -> {}
                }
            }
            if (v.treatments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    v.treatments.forEach { Pill(it, Brand.GreenSoft, Brand.GreenText) }
                }
            }
            if (v.teethList.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Tooth, null, tint = Brand.GreenDeep, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Teeth: " + v.teethList.joinToString(", "),
                        fontSize = 13.sp, color = Brand.GreenDeep, fontWeight = FontWeight.Medium
                    )
                }
            }
            if (v.problem.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(v.problem, fontSize = 14.sp, color = Brand.Ink)
            }
            if (v.photos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    v.photos.forEachIndexed { i, path ->
                        key(path) {
                            PhotoImage(path, Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).clickable { onPhoto(i) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Brand.Line)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fees ${rs(v.fees)}", fontSize = 13.sp, color = Brand.Sub)
                    Text(
                        "Paid ${rs(v.paid)}" + if (v.payMode.isNotBlank() && v.paid > 0) " · ${v.payMode}" else "",
                        fontSize = 13.sp, color = Brand.GreenDeep, fontWeight = FontWeight.Medium
                    )
                }
                if (v.remaining > 0) {
                    SoftButton("Collect", null, height = 40.dp, onClick = onCollect)
                }
            }
            if (v.nextVisit >= today) {
                Spacer(Modifier.height(12.dp))
                Pill("Next visit · ${fmtDay(v.nextVisit)}", Brand.GoldSoft, Brand.GoldDeep, Icons.Filled.DateRange)
            }
        }
    }
}

// ───────────── Collect payment ─────────────
@Composable
fun PaymentDialog(due: Double, subtitle: String, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf(numText(due)) }
    var mode by remember { mutableStateOf("Cash") }
    val value = amount.toDoubleOrNull() ?: 0.0
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color.White).padding(26.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Text("₹", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Collect payment", fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = Brand.Ink)
            Text("Pending ${rs(due)} · $subtitle", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(20.dp))
            AppField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount received",
                type = KeyboardType.Decimal, prefix = "₹ "
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Card").forEach { m -> ChoiceChip(m, mode == m) { mode = m } }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton(
                    "Save", Modifier.weight(1f), Icons.Filled.Check,
                    enabled = value > 0 && value <= due + 0.01, height = 52.dp
                ) { onSave(value, mode) }
            }
        }
    }
}

// ───────────── Call & WhatsApp ─────────────
fun callNumber(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open the dialer", Toast.LENGTH_SHORT).show()
    }
}

fun openWhatsApp(context: Context, phone: String, message: String) {
    val digits = phone.filter { it.isDigit() }
    val number = when {
        digits.length == 10 -> "91$digits"
        digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
        else -> digits
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(message))))
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp is not available", Toast.LENGTH_SHORT).show()
    }
}

fun reminderMessage(p: Patient, nextVisit: Long?): String {
    val first = p.name.trim().substringBefore(" ")
    return when {
        p.remaining > 0 ->
            "Hello $first, this is a gentle reminder from Mahalakshmi Dental Clinic. " +
                "An amount of ${rs(p.remaining)} is pending for your dental treatment. Thank you! 🙏"
        nextVisit != null ->
            "Hello $first, this is a reminder from Mahalakshmi Dental Clinic for your next dental visit on " +
                "${fmtDayLong(nextVisit)}. See you soon! 😊"
        else -> "Hello $first, greetings from Mahalakshmi Dental Clinic! 😊"
    }
}
