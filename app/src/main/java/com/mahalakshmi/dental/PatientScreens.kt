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
                        Text(sub.ifBlank
