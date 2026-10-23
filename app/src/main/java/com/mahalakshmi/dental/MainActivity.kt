@file:OptIn(ExperimentalMaterial3Api::class)

package com.mahalakshmi.dental

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Colours & helpers ----------
val Green = Color(0xFF1E8E3E)
val LightGreen = Color(0xFFE8F5E9)
val LightRed = Color(0xFFFFEBEE)
val Red = Color(0xFFD32F2F)
val Border = Color(0xFFE3E3E3)

private val inr = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 2 }
fun rs(x: Double): String = "₹" + inr.format(x)
fun numText(x: Double): String = if (x == 0.0) "" else if (x % 1.0 == 0.0) x.toLong().toString() else x.toString()
fun fmtDate(t: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(t))

val reasonChips = listOf(
    "Checkup", "Toothache", "Cleaning", "Filling", "Root Canal",
    "Extraction", "Braces", "Cap / Crown", "Denture", "Implant"
)

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    private lateinit var db: ClinicDb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        db = ClinicDb(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green, onPrimary = Color.White,
                    background = Color.White, surface = Color.White,
                    surfaceContainerLow = Color.White, surfaceContainer = Color.White,
                    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF4F4F4)
                )
            ) { ClinicApp(db) }
        }
    }

    // Whenever the app goes to the background, new data is copied to the backup folder
    override fun onStop() {
        super.onStop()
        val ctx = applicationContext
        Thread { Backup.autoBackup(ctx, db) }.start()
    }
}

// ---------- Navigation ----------
sealed class Screen {
    object Home : Screen()
    object BackupPage : Screen()
    data class PatientForm(val patientId: Long = 0) : Screen()
    data class PatientDetail(val patientId: Long) : Screen()
    data class VisitForm(val patientId: Long, val visitId: Long = 0) : Screen()
}

@Composable
fun ClinicApp(db: ClinicDb) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    val screen = stack.last()
    key(stack.size, screen) {
        when (screen) {
            is Screen.Home -> HomeScreen(db, go)
            is Screen.BackupPage -> BackupScreen(db, back) { stack.clear(); stack.add(Screen.Home) }
            is Screen.PatientForm -> PatientFormScreen(db, screen.patientId, back) { newId, isNew ->
                stack.removeAt(stack.lastIndex)
                if (isNew) {
                    stack.add(Screen.PatientDetail(newId))
                    stack.add(Screen.VisitForm(newId))
                }
            }
            is Screen.PatientDetail -> PatientDetailScreen(db, screen.patientId, go, back)
            is Screen.VisitForm -> VisitFormScreen(db, screen.visitId, screen.patientId, back)
        }
    }
}

// ---------- Home ----------
@Composable
fun HomeScreen(db: ClinicDb, go: (Screen) -> Unit) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val patients = remember(search) { db.getPatients(search) }
    val totalDue = patients.sumOf { it.remaining.coerceAtLeast(0.0) }
    val backupOn = remember { Backup.autoFolderName(context) != null }
    val lastBackup = remember { Backup.lastBackup(context) }
    val warn = !backupOn || (lastBackup > 0 && System.currentTimeMillis() - lastBackup > 3L * 24 * 60 * 60 * 1000)

    Scaffold(
        topBar = { GreenTopBar("Mahalakshmi Dental Clinic") },
        floatingActionButton = { GreenFab("+  New Patient") { go(Screen.PatientForm()) } },
        containerColor = Color.White
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("🔍  Search by name or mobile") },
                singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            )
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (warn) LightRed else LightGreen)
                    .clickable { go(Screen.BackupPage) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        !backupOn -> "⚠  Backup is not set up – tap here"
                        lastBackup == 0L -> "☁  Backup is on"
                        else -> "☁  Last backup: ${fmtDate(lastBackup)}"
                    },
                    color = if (warn) Red else Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = Color.Gray, fontSize = 20.sp)
            }
            Text(
                "${patients.size} patients  •  Pending: ${rs(totalDue)}",
                color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (patients.isEmpty()) item {
                    Text(
                        if (search.isBlank()) "No patients yet. Tap \"+ New Patient\" to add one." else "No patient found.",
                        color = Color.Gray, modifier = Modifier.padding(top = 32.dp)
                    )
                }
                items(patients, key = { it.id }) { p -> PatientRow(p) { go(Screen.PatientDetail(p.id)) } }
            }
        }
    }
}

@Composable
fun PatientRow(p: Patient, onClick: () -> Unit) {
    Card(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(LightGreen, CircleShape), contentAlignment = Alignment.Center) {
                Text(p.name.take(1).uppercase(), color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                val sub = listOf(p.phone, if (p.age.isNotBlank()) "${p.age} yrs" else "")
                    .filter { it.isNotBlank() }.joinToString("  •  ")
                if (sub.isNotBlank()) Text(sub, color = Color.Gray, fontSize = 13.sp)
            }
            when {
                p.remaining > 0 -> Text("Due ${rs(p.remaining)}", color = Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                p.totalFees > 0 -> Text("Paid ✓", color = Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                else -> {}
            }
        }
    }
}

// ---------- Patient form ----------
@Composable
fun PatientFormScreen(db: ClinicDb, patientId: Long, onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit) {
    val existing = remember { if (patientId != 0L) db.getPatient(patientId) else null }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var age by remember { mutableStateOf(existing?.age ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "") }

    Scaffold(
        topBar = { GreenTopBar(if (existing == null) "New Patient" else "Edit Patient", onBack) },
        containerColor = Color.White
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Field("Patient name *", name, { name = it }, caps = KeyboardCapitalization.Words)
            Field("Mobile number", phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, KeyboardType.Phone)
            Field("Age", age, { age = it.filter { c -> c.isDigit() } }, KeyboardType.Number)
            SectionTitle("Gender")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Male", "Female", "Other").forEach { g ->
                    FilterChip(
                        selected = gender == g, onClick = { gender = if (gender == g) "" else g },
                        label = { Text(g) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LightGreen, selectedLabelColor = Green)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            GreenButton(if (existing == null) "Save & Add First Visit" else "Save Changes", enabled = name.isNotBlank()) {
                val id = db.savePatient(
                    Patient(
                        id = patientId, name = name.trim(), phone = phone.trim(), age = age.trim(), gender = gender,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
                onSaved(id, existing == null)
            }
        }
    }
}

// ---------- Patient detail ----------
@Composable
fun PatientDetailScreen(db: ClinicDb, patientId: Long, go: (Screen) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val patient = remember { db.getPatient(patientId) }
    val visits = remember { db.getVisits(patientId) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    if (patient == null) return

    Scaffold(
        topBar = {
            GreenTopBar(patient.name, onBack) {
                TextButton(onClick = { go(Screen.PatientForm(patientId)) }) {
                    Text("Edit", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        floatingActionButton = { GreenFab("+  Add Visit") { go(Screen.VisitForm(patientId)) } },
        containerColor = Color.White
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val info = listOf(patient.phone, if (patient.age.isNotBlank()) "${patient.age} yrs" else "", patient.gender)
                    .filter { it.isNotBlank() }.joinToString("  •  ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.ifBlank { "No details" }, color = Color.DarkGray, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (patient.phone.isNotBlank()) {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}")))
                        }) { Text("📞 Call", color = Green) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryBox("Total fees", rs(patient.totalFees), Color.Black, Modifier.weight(1f))
                    SummaryBox("Paid", rs(patient.totalPaid), Green, Modifier.weight(1f))
                    SummaryBox("Remaining", rs(patient.remaining), if (patient.remaining > 0) Red else Green, Modifier.weight(1f))
                }
            }
            item { SectionTitle("Visits (${visits.size})") }
            if (visits.isEmpty()) item { Text("No visits yet. Tap \"+ Add Visit\".", color = Color.Gray) }
            items(visits, key = { it.id }) { v ->
                VisitCard(v, onClick = { go(Screen.VisitForm(patientId, v.id)) }, onPhoto = { viewPhoto = it })
            }
            item {
                TextButton(onClick = { askDelete = true }) { Text("Delete this patient", color = Red) }
            }
        }
    }
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
    if (askDelete) ConfirmDialog(
        "Delete ${patient.name}?", "All visits and photos of this patient will be permanently deleted.",
        onConfirm = { db.deletePatient(patientId); askDelete = false; onBack() },
        onDismiss = { askDelete = false }
    )
}

@Composable
fun VisitCard(v: Visit, onClick: () -> Unit, onPhoto: (String) -> Unit) {
    Card(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtDate(v.date), color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                when {
                    v.remaining > 0 -> Text("Due ${rs(v.remaining)}", color = Red, fontWeight = FontWeight.Bold)
                    v.fees > 0 -> Text("Paid ✓", color = Green, fontWeight = FontWeight.Bold)
                    else -> {}
                }
            }
            if (v.reason.isNotBlank()) LabelValue("Reason", v.reason)
            if (v.problem.isNotBlank()) LabelValue("Problem", v.problem)
            Text("Fees ${rs(v.fees)}   •   Paid ${rs(v.paid)}", fontSize = 14.sp, color = Color.DarkGray)
            if (v.photos.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    v.photos.forEach { path ->
                        key(path) {
                            PhotoImage(path, Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)).clickable { onPhoto(path) })
                        }
                    }
                }
            }
        }
    }
}

// ---------- Visit form ----------
@Composable
fun VisitFormScreen(db: ClinicDb, visitId: Long, patientId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember { if (visitId != 0L) db.getVisit(visitId) else null }
    val patientName = remember { db.getPatient(patientId)?.name ?: "" }

    var reason by remember { mutableStateOf(existing?.reason ?: "") }
    var problem by remember { mutableStateOf(existing?.problem ?: "") }
    var fees by remember { mutableStateOf(numText(existing?.fees ?: 0.0)) }
    var paid by remember { mutableStateOf(numText(existing?.paid ?: 0.0)) }
    val photos = remember { mutableStateListOf<String>().apply { addAll(existing?.photos ?: emptyList()) } }
    val newPhotos = remember { mutableListOf<String>() }
    val removedPhotos = remember { mutableListOf<String>() }
    var saved by remember { mutableStateOf(false) }
    var savingPhoto by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }

    // If you go back without saving, photos taken on this screen are removed
    DisposableEffect(Unit) { onDispose { if (!saved) newPhotos.forEach { File(it).delete() } } }

    fun addPhotos(sources: List<() -> InputStream?>) {
        scope.launch {
            savingPhoto = true
            for (open in sources) {
                val path = withContext(Dispatchers.IO) { importPhoto(context, open) }
                if (path != null) { photos.add(path); newPhotos.add(path) }
            }
            savingPhoto = false
            cameraTempFile(context).delete()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = cameraTempFile(context)
        if (ok && temp.length() > 0) addPhotos(listOf({ temp.inputStream() })) else temp.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) addPhotos(uris.map { uri -> { context.contentResolver.openInputStream(uri) } })
    }

    fun openCamera() {
        try {
            val temp = cameraTempFile(context)
            temp.delete()
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", temp))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open camera", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { GreenTopBar((if (existing == null) "New Visit – " else "Edit Visit – ") + patientName, onBack) },
        containerColor = Color.White
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (existing != null) Text("Visit on ${fmtDate(existing.date)}", color = Color.Gray)

            SectionTitle("Reason for visit")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reasonChips.forEach { chip ->
                    val parts = reason.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    FilterChip(
                        selected = chip in parts,
                        onClick = { reason = (if (chip in parts) parts - chip else parts + chip).joinToString(", ") },
                        label = { Text(chip) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LightGreen, selectedLabelColor = Green)
                    )
                }
            }
            Field("Reason (tap above or type)", reason, { reason = it })

            SectionTitle("Problem / Findings")
            Field("Describe the problem", problem, { problem = it }, lines = 3)

            SectionTitle("Fees")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Total fees ₹", fees, { fees = it.filter { c -> c.isDigit() || c == '.' } },
                    KeyboardType.Decimal, modifier = Modifier.weight(1f))
                Field("Paid ₹", paid, { paid = it.filter { c -> c.isDigit() || c == '.' } },
                    KeyboardType.Decimal, modifier = Modifier.weight(1f))
            }
            val f = fees.toDoubleOrNull() ?: 0.0
            val p = paid.toDoubleOrNull() ?: 0.0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Remaining: ${rs(f - p)}", color = if (f - p > 0) Red else Green,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { paid = fees }) { Text("Mark fully paid", color = Green) }
            }

            SectionTitle("Photos (${photos.size})")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineGreenButton("📷  Camera", Modifier.weight(1f)) { openCamera() }
                OutlineGreenButton("🖼  Gallery", Modifier.weight(1f)) {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            if (photos.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { path ->
                        key(path) {
                            Box {
                                PhotoImage(path, Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)).clickable { viewPhoto = path })
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                                        .background(Color(0xAA000000), CircleShape)
                                        .clickable { photos.remove(path); removedPhotos.add(path) },
                                    contentAlignment = Alignment.Center
                                ) { Text("✕", color = Color.White, fontSize = 14.sp) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            GreenButton("Save Visit") {
                db.saveVisit(
                    Visit(
                        id = visitId, patientId = patientId, date = existing?.date ?: System.currentTimeMillis(),
                        reason = reason.trim(), problem = problem.trim(), fees = f, paid = p, photos = photos.toList()
                    )
                )
                removedPhotos.forEach { File(it).delete() }
                saved = true
                onBack()
            }
            if (existing != null) {
                TextButton(onClick = { askDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Delete this visit", color = Red)
                }
            }
        }
    }
    viewPhoto?.let { PhotoViewer(it) { viewPhoto = null } }
    if (savingPhoto) BusyDialog("Saving photo…")
    if (askDelete && existing != null) ConfirmDialog(
        "Delete this visit?", "This visit and its photos will be permanently deleted.",
        onConfirm = { db.deleteVisit(existing); askDelete = false; onBack() },
        onDismiss = { askDelete = false }
    )
}

// ---------- Backup screen ----------
@Composable
fun BackupScreen(db: ClinicDb, onBack: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf(Backup.autoFolderName(context)) }
    var last by remember { mutableLongStateOf(Backup.lastBackup(context)) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<(() -> Boolean)?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun doWork(label: String, work: () -> Boolean, done: (Boolean) -> Unit) {
        busy = label
        scope.launch {
            val ok = withContext(Dispatchers.IO) { try { work() } catch (e: Throwable) { false } }
            busy = null
            last = Backup.lastBackup(context)
            done(ok)
        }
    }

    fun backupNow() = doWork("Backing up…", { Backup.autoBackup(context, db, force = true) }) { ok ->
        toast(
            if (ok) "Backup saved ✓"
            else if (db.patientCount() == 0) "Nothing to back up yet"
            else "Backup failed – please choose the folder again"
        )
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                Backup.setAutoFolder(context, uri)
                folder = Backup.autoFolderName(context)
                backupNow()
            } catch (e: Exception) { toast("Please choose a different folder") }
        }
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) doWork("Creating backup file…", { Backup.exportZip(context, db, uri) }) { ok ->
            toast(if (ok) "Backup file saved ✓" else "Could not save the backup file")
        }
    }
    val restoreFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) pendingRestore = { Backup.restoreFromFolder(context, db, uri) }
    }
    val restoreZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = { Backup.restoreZip(context, db, uri) }
    }

    Scaffold(topBar = { GreenTopBar("Backup & Restore", onBack) }, containerColor = Color.White) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("1. Automatic backup")
            Hint("Choose a folder once. After that, every time you close the app, new patients and photos are copied there automatically.")
            InfoRow("Folder", folder ?: "Not chosen yet")
            InfoRow("Last backup", if (last == 0L) "Never" else fmtDate(last))
            GreenButton(if (folder == null) "Choose backup folder" else "Change folder") { pickFolder.launch(null) }
            if (folder != null) OutlineGreenButton("Back up now") { backupNow() }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Border)
            SectionTitle("2. Save a copy outside the phone")
            Hint("The folder above is on this phone. Once a week, save one backup file to Google Drive or a pen drive, so your data is safe even if the phone is lost.")
            OutlineGreenButton("Save backup file (.zip)") { exportZip.launch(Backup.zipName()) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Border)
            SectionTitle("3. Restore")
            Hint("Use this on a new phone or after reinstalling. It REPLACES all data currently in the app.")
            OutlineGreenButton("Restore from backup folder") { restoreFolder.launch(null) }
            OutlineGreenButton("Restore from .zip file") { restoreZip.launch(arrayOf("*/*")) }
        }
    }

    pendingRestore?.let { work ->
        ConfirmDialog(
            "Replace all data?", "Everything currently in the app will be replaced with the data from the backup.",
            confirmText = "Restore",
            onConfirm = {
                pendingRestore = null
                doWork("Restoring…", work) { ok ->
                    if (ok) { toast("Data restored ✓"); onRestored() } else toast("This is not a valid clinic backup")
                }
            },
            onDismiss = { pendingRestore = null }
        )
    }
    busy?.let { BusyDialog(it) }
}

// ---------- Photos ----------
const val MAX_PHOTO = 3000   // longest side in pixels: sharp, but saves storage

fun cameraTempFile(context: Context): File =
    File(File(context.cacheDir, "camera").apply { mkdirs() }, "camera.jpg")

fun newPhotoFile(context: Context): File =
    File(photoDir(context), "IMG_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")

/** Saves a sharp, upright JPEG copy of the photo inside the app. */
fun importPhoto(context: Context, open: () -> InputStream?): String? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO) sample *= 2
        var bmp = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = try {
            open()?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        val scale = MAX_PHOTO.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        if (rotation != 0f) m.postRotate(rotation)
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val file = newPhotoFile(context)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return file.absolutePath
    } catch (e: Throwable) {
        return null
    }
}

fun loadBitmap(path: String, maxSize: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: Throwable) { null }

@Composable
fun PhotoImage(path: String, modifier: Modifier, maxSize: Int = 400, crop: Boolean = true) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { loadBitmap(path, maxSize)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) Image(
        b, contentDescription = null, modifier = modifier,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit
    )
    else Box(modifier.background(Color(0x22888888)))
}

@Composable
fun PhotoViewer(path: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
        ) {
            PhotoImage(
                path,
                Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                maxSize = 3000, crop = false
            )
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 12.dp)) {
                Text("✕  Close", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

// ---------- Small reusable pieces ----------
@Composable
fun GreenTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Text("←", fontSize = 26.sp, color = Color.White) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Green, titleContentColor = Color.White, actionIconContentColor = Color.White
        )
    )
}

@Composable
fun GreenFab(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, containerColor = Green, contentColor = Color.White) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun GreenButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun OutlineGreenButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Green)
    ) { Text(text, color = Green, fontWeight = FontWeight.Bold) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

@Composable
fun Hint(text: String) {
    Text(text, color = Color.DarkGray, fontSize = 14.sp)
}

@Composable
fun InfoRow(label: String, value: String) {
    Row {
        Text("$label: ", color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp)
    }
}

@Composable
fun SummaryBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(LightGreen, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Green, focusedLabelColor = Green, cursorColor = Green,
    unfocusedBorderColor = Color(0xFFCCCCCC)
)

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    type: KeyboardType = KeyboardType.Text, lines: Int = 1,
    caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = lines == 1, minLines = lines,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = type),
        shape = RoundedCornerShape(12.dp), colors = fieldColors(), modifier = modifier
    )
}

@Composable
fun ConfirmDialog(
    title: String, message: String, confirmText: String = "Delete",
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = Red, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Green) } },
        containerColor = Color.White
    )
}

@Composable
fun BusyDialog(text: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Row(
            Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(color = Green, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Text(text, fontSize = 16.sp)
        }
    }
}
