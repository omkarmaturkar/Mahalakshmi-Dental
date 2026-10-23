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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
    val existing = remember { if (patientId != 0L) db.getPatient(patientId) else null }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var age by remember { mutableStateOf(existing?.age ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val medical = remember { mutableStateListOf<String>().apply { addAll(existing?.alerts ?: emptyList()) } }

    Column(Modifier.fillMaxSize().background(Brand.Bg).imePadding()) {
        AppTopBar(
            if (existing == null) "New Patient" else "Edit Patient",
            if (existing == null) "Add the patient's details" else existing.name,
            onBack
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name.ifBlank { "?" }, 68.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        name.ifBlank { "Patient name" }, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
                        color = if (name.isBlank()) Brand.Faint else Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val sub = listOf(gender, if (age.isNotBlank()) "$age yrs" else "").filter { it.isNotBlank() }.joinToString(" · ")
                    Text(sub.ifBlank { "New patient" }, fontSize = 13.sp, color = Brand.Sub)
                }
            }
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
            FormCard("Medical alerts", "Important health conditions, shown in red on the profile") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    medicalOptions.forEach { m ->
                        ChoiceChip(m, m in medical, selectedBg = Brand.Red) {
                            if (m in medical) medical.remove(m) else medical.add(m)
                        }
                    }
                }
            }
            FormCard("Notes") {
                AppField(notes, { notes = it }, "Address, referred by, other notes", lines = 3)
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomActionBar {
            GradientButton(
                if (existing == null) "Save & Add Visit" else "Save Changes",
                Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank()
            ) {
                val id = db.savePatient(
                    Patient(
                        id = patientId, name = name.trim(), phone = phone.trim(), age = age.trim(), gender = gender,
                        medical = medical.joinToString(", "), notes = notes.trim(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
                onSaved(id, existing == null)
            }
        }
    }
}

// ───────────── Patient profile ─────────────
@Composable
fun PatientDetailScreen(db: ClinicDb, patientId: Long, go: (Screen) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val patient = remember(refresh) { db.getPatient(patientId) }
    val visits = remember(refresh) { db.getVisits(patientId) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var payVisit by remember { mutableStateOf<Visit?>(null) }
    var payAll by remember { mutableStateOf(false) }
    if (patient == null) return

    val today = todayEpochDay()
    val nextVisit = visits.firstOrNull()?.nextVisit?.takeIf { it >= today }

    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "header") {
                ProfileHeader(
                    patient, visits.size, nextVisit, onBack,
                    onEdit = { go(Screen.PatientForm(patientId)) },
                    onDelete = { askDelete = true },
                    onCall = { callNumber(context, patient.phone) },
                    onChat = { openWhatsApp(context, patient.phone, reminderMessage(patient, nextVisit)) }
                )
            }
            if (patient.alerts.isNotEmpty()) item(key = "alerts") { AlertCard(patient.alerts) }
            if (patient.notes.isNotBlank()) item(key = "notes") { NotesCard(patient.notes) }
            item(key = "pay") { PaymentSummary(patient) { payAll = true } }
            item(key = "historyTitle") {
                SectionHeader("Treatment history", Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp))
            }
            if (visits.isEmpty()) item(key = "noVisits") {
                EmptyState("No visits yet", "Tap \"New Visit\" to record the first treatment.")
            }
            itemsIndexed(visits, key = { _, v -> v.id }) { i, v ->
                TimelineVisit(
                    v, isLast = i == visits.lastIndex,
                    onOpen = { go(Screen.VisitForm(patientId, v.id)) },
                    onPhoto = { index -> viewer = v.photos to index },
                    onCollect = { payVisit = v }
                )
            }
        }
        GradientFab(
            "New Visit", Icons.Filled.Add,
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
        ) { go(Screen.VisitForm(patientId)) }
    }

    viewer?.let { (list, index) -> PhotoViewer(list, index) { viewer = null } }
    val target = payVisit
    if (payAll || target != null) {
        PaymentDialog(
            due = target?.remaining ?: patient.remaining,
            subtitle = if (target != null) "for visit on ${fmtDate(target.date)}" else "oldest dues first",
            onDismiss = { payAll = false; payVisit = null }
        ) { amount, mode ->
            db.recordPayment(patientId, amount, mode, target?.id ?: 0L)
            payAll = false
            payVisit = null
            refresh++
            Toast.makeText(context, "Payment of ${rs(amount)} recorded ✓", Toast.LENGTH_SHORT).show()
        }
    }
    if (askDelete) ConfirmDialog(
        "Delete ${patient.name}?",
        "All visits, photos and payments of this patient will be permanently deleted.",
        onConfirm = { db.deletePatient(patientId); askDelete = false; onBack() },
        onDismiss = { askDelete = false }
    )
}

@Composable
private fun ProfileHeader(
    p: Patient,
    visitCount: Int,
    nextVisit: Long?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onChat: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(BrandGradient)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = 140.dp.toPx(), center = Offset(size.width, 0f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = 90.dp.toPx(), center = Offset(0f, size.height))
            }
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, bg = Brand.Glass, tint = Color.White)
            Spacer(Modifier.weight(1f))
            Box {
                CircleIconButton(Icons.Filled.MoreVert, { menu = true }, bg = Brand.Glass, tint = Color.White)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit details") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Brand.Ink) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete patient", color = Brand.Red) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Brand.Red) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
        Avatar(p.name, 96.dp, ring = true)
        Spacer(Modifier.height(12.dp))
        Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 23.sp, textAlign = TextAlign.Center)
        val since = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(p.createdAt))
        val info = listOf(
            p.gender,
            if (p.age.isNotBlank()) "${p.age} yrs" else "",
            if (visitCount == 1) "1 visit" else "$visitCount visits",
            "Since $since"
        ).filter { it.isNotBlank() }.joinToString("  ·  ")
        Text(info, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
        if (p.phone.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderAction("Call", Icons.Filled.Call, onCall)
                HeaderAction("WhatsApp", AppIcons.Chat, onChat)
            }
        }
        if (nextVisit != null) {
            Spacer(Modifier.height(14.dp))
            Pill("Next visit: ${fmtDay(nextVisit)}", Color.White, Brand.PrimaryDark, Icons.Filled.DateRange)
        }
    }
}

@Composable
private fun HeaderAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Brand.Glass)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun AlertCard(alerts: List<String>) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(Brand.RedSoft)
            .border(1.dp, Brand.Red.copy(alpha = 0.25f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Medical alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Red)
            Text(alerts.joinToString("  ·  "), fontSize = 14.sp, color = Brand.Ink)
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    AppCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth(), padding = 16.dp) {
        Text("Notes", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.PrimaryDark)
        Spacer(Modifier.height(4.dp))
        Text(notes, fontSize = 14.sp, color = Brand.Ink)
    }
}

@Composable
private fun PaymentSummary(p: Patient, onCollect: () -> Unit) {
    val target = if (p.totalFees > 0) (p.totalPaid / p.totalFees).toFloat().coerceIn(0f, 1f) else 0f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
    AppCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Payments", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.PrimaryDark, modifier = Modifier.weight(1f))
            if (p.totalFees > 0) Text("${(target * 100).roundToInt()}% paid", fontSize = 13.sp, color = Brand.Sub)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            MoneyColumn("Total", rs(p.totalFees), Brand.Ink, Modifier.weight(1f))
            MoneyColumn("Paid", rs(p.totalPaid), Brand.Primary, Modifier.weight(1f))
            MoneyColumn(
                "Due", rs(p.remaining.coerceAtLeast(0.0)),
                if (p.remaining > 0) Brand.Red else Brand.Primary, Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Brand.Soft)) {
            Box(Modifier.fillMaxWidth(progress.value).fillMaxHeight().clip(RoundedCornerShape(50)).background(BrandGradient))
        }
        if (p.remaining > 0) {
            Spacer(Modifier.height(16.dp))
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
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().drawBehind {
            val x = 11.dp.toPx()
            val cy = 24.dp.toPx()
            if (!isLast) drawLine(Brand.MintStrong, Offset(x, cy), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, cy))
            drawCircle(Brand.Primary, radius = 7.dp.toPx(), center = Offset(x, cy), style = Stroke(width = 3.dp.toPx()))
        }
    ) {
        Spacer(Modifier.width(32.dp))
        AppCard(Modifier.weight(1f).padding(bottom = 14.dp), onClick = onOpen, padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fmtDate(v.date), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink)
                    Text(fmtTime(v.date), fontSize = 12.sp, color = Brand.Faint)
                }
                when {
                    v.remaining > 0 -> Pill("Due ${rs(v.remaining)}", Brand.RedSoft, Brand.Red)
                    v.fees > 0 -> Pill("Paid", Brand.Mint, Brand.PrimaryDark, Icons.Filled.Check)
                    else -> {}
                }
            }
            if (v.treatments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    v.treatments.forEach { Pill(it, Brand.Mint, Brand.PrimaryDark) }
                }
            }
            if (v.teethList.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Tooth, null, tint = Brand.Teal, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Teeth: " + v.teethList.joinToString(", "), fontSize = 13.sp, color = Brand.Teal, fontWeight = FontWeight.Medium)
                }
            }
            if (v.problem.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(v.problem, fontSize = 14.sp, color = Brand.Ink)
            }
            if (v.photos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    v.photos.forEachIndexed { i, path ->
                        key(path) {
                            PhotoImage(path, Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).clickable { onPhoto(i) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Brand.Line)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fees ${rs(v.fees)}", fontSize = 13.sp, color = Brand.Sub)
                    Text(
                        "Paid ${rs(v.paid)}" + if (v.payMode.isNotBlank() && v.paid > 0) " · ${v.payMode}" else "",
                        fontSize = 13.sp, color = Brand.Primary, fontWeight = FontWeight.Medium
                    )
                }
                if (v.remaining > 0) {
                    Text(
                        "Collect", color = Brand.PrimaryDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Mint)
                            .clickable(onClick = onCollect).padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            if (v.nextVisit >= today) {
                Spacer(Modifier.height(10.dp))
                Pill("Next visit: ${fmtDay(v.nextVisit)}", Brand.AmberSoft, Brand.Amber, Icons.Filled.DateRange)
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
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(24.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Brand.Mint), contentAlignment = Alignment.Center) {
                Text("₹", color = Brand.Primary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Collect payment", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Text("Pending ${rs(due)} · $subtitle", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(18.dp))
            AppField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount received",
                type = KeyboardType.Decimal, prefix = "₹ "
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Card").forEach { m -> ChoiceChip(m, mode == m) { mode = m } }
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), enabled = value > 0 && value <= due + 0.01) { onSave(value, mode) }
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
