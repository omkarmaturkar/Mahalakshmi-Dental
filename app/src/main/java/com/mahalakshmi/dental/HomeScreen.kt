@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(db: ClinicDb, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val everyone = remember { db.getPatients("") }
    val stats = remember { db.stats() }
    val upcoming = remember { db.upcoming(7) }
    val backupOn = remember { Backup.autoFolderName(context) != null }
    val lastBackup = remember { Backup.lastBackup(context) }
    val backupWarn = !backupOn || (lastBackup > 0 && System.currentTimeMillis() - lastBackup > 3L * 24 * 60 * 60 * 1000)

    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }

    val today = remember { todayEpochDay() }
    val todayStart = remember { startOfToday() }
    val dueTotal = remember(everyone) { everyone.sumOf { it.remaining.coerceAtLeast(0.0) } }
    val dueCount = remember(everyone) { everyone.count { it.remaining > 0.0 } }
    val todayIds = remember(upcoming) { upcoming.filter { it.day == today }.map { it.patientId }.toSet() }
    val shown = remember(search, filter, everyone) {
        val q = search.trim().lowercase()
        val list = everyone.filter { p ->
            val matches = q.isEmpty() || p.name.lowercase().contains(q) || p.phone.contains(q)
            val inFilter = when (filter) {
                1 -> p.remaining > 0.0
                2 -> p.lastVisit >= todayStart || p.id in todayIds
                else -> true
            }
            matches && inFilter
        }
        if (filter == 1) list.sortedByDescending { it.remaining } else list
    }

    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "top") {
                HomeTop(
                    stats, dueTotal, dueCount, backupWarn,
                    onBackup = { go(Screen.BackupPage) },
                    onDues = { filter = 1 }
                )
            }
            if (!backupOn) item(key = "nudge") { BackupNudge { go(Screen.BackupPage) } }
            if (upcoming.isNotEmpty()) item(key = "upcoming") {
                Column {
                    SectionHeader("Upcoming visits", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(upcoming, key = { "${it.patientId}-${it.day}" }) { a ->
                            AppointmentCard(a, today) { go(Screen.PatientDetail(a.patientId)) }
                        }
                    }
                }
            }
            item(key = "search") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    SectionHeader("Patients")
                    Spacer(Modifier.height(12.dp))
                    SearchBox(search) { search = it }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("All  ${everyone.size}", filter == 0) { filter = 0 }
                        ChoiceChip("Pending dues  $dueCount", filter == 1, selectedBg = Brand.Red) { filter = 1 }
                        ChoiceChip("Today", filter == 2) { filter = 2 }
                    }
                }
            }
            if (shown.isEmpty()) item(key = "empty") {
                EmptyState(
                    if (everyone.isEmpty()) "Welcome, Doctor!" else "No patients found",
                    if (everyone.isEmpty()) "Tap \"New Patient\" to add your first patient." else "Try a different name, number or filter."
                )
            }
            items(shown, key = { it.id }) { p ->
                PatientCard(p, Modifier.animateItem().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    go(Screen.PatientDetail(p.id))
                }
            }
        }
        GradientFab(
            "New Patient", Icons.Filled.Add,
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
        ) { go(Screen.PatientForm()) }
    }
}

@Composable
private fun HomeTop(
    stats: Stats,
    dueTotal: Double,
    dueCount: Int,
    backupWarn: Boolean,
    onBackup: () -> Unit,
    onDues: () -> Unit
) {
    val todayAmount = animatedNumber(stats.todayCollection)
    val monthAmount = animatedNumber(stats.monthCollection)
    val dueAmount = animatedNumber(dueTotal)
    val dateText = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) }

    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                val r = 36.dp.toPx()
                val h = size.height - 58.dp.toPx()
                val brush = Brush.linearGradient(
                    listOf(Brand.GradStart, Brand.GradEnd), start = Offset.Zero, end = Offset(size.width, h)
                )
                drawRect(brush, size = Size(size.width, h - r))
                drawRoundRect(brush, topLeft = Offset(0f, h - 2 * r), size = Size(size.width, 2 * r), cornerRadius = CornerRadius(r, r))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = 130.dp.toPx(), center = Offset(size.width - 20.dp.toPx(), 30.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = 70.dp.toPx(), center = Offset(30.dp.toPx(), h - 90.dp.toPx()))
            }
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brand.Glass),
                contentAlignment = Alignment.Center
            ) { Icon(AppIcons.Tooth, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mahalakshmi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp)
                Text("Dental Clinic", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 16.sp)
            }
            Box {
                CircleIconButton(AppIcons.Cloud, onBackup, bg = Brand.Glass, tint = Color.White)
                if (backupWarn) {
                    Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Color(0xFFFFC53D)))
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("${greeting()}, Doctor 👋", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
        Text(dateText, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 23.sp)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brand.Glass)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Today's collection", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Text(rs(todayAmount), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${stats.todayVisits}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Text(
                    if (stats.todayVisits == 1) "visit today" else "visits today",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Pending dues", rs(dueAmount), if (dueCount == 1) "1 patient" else "$dueCount patients",
                Icons.Filled.Warning, Brand.Red, Brand.RedSoft, Modifier.weight(1f), onDues
            )
            StatTile(
                "This month", rs(monthAmount), "${stats.patientCount} total patients",
                Icons.Filled.DateRange, Brand.Primary, Brand.Mint, Modifier.weight(1f), null
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    accent: Color,
    soft: Color,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    AppCard(modifier, onClick = onClick, padding = 14.dp) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 12.sp, color = Brand.Sub)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun BackupNudge(onClick: () -> Unit) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(Brand.AmberSoft)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Cloud, null, tint = Brand.Amber, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Protect your patient data", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
            Text("Tap to turn on automatic backup", fontSize = 12.sp, color = Brand.Sub)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Amber)
    }
}

@Composable
private fun AppointmentCard(a: Appointment, today: Long, onClick: () -> Unit) {
    val isToday = a.day == today
    AppCard(Modifier.width(220.dp), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(a.name, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.reason.ifBlank { "Follow-up" }, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Pill(
            fmtDay(a.day),
            if (isToday) Brand.Primary else Brand.AmberSoft,
            if (isToday) Color.White else Brand.Amber,
            Icons.Filled.DateRange
        )
    }
}

@Composable
fun PatientCard(p: Patient, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 50.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (p.alerts.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(15.dp))
                    }
                }
                val line = listOf(p.phone, if (p.age.isNotBlank()) "${p.age} yrs" else "")
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
                if (line.isNotEmpty()) Text(line, fontSize = 13.sp, color = Brand.Sub, maxLines = 1)
                if (p.lastVisit > 0L) {
                    Text(
                        "${p.lastReason.substringBefore(",").ifBlank { "Visit" }} · ${relativeTime(p.lastVisit)}",
                        fontSize = 12.sp, color = Brand.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                p.remaining > 0 -> Column(horizontalAlignment = Alignment.End) {
                    Text(rs(p.remaining), color = Brand.Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("due", color = Brand.Red, fontSize = 11.sp)
                }
                p.totalFees > 0 -> Pill("Paid", Brand.Mint, Brand.PrimaryDark, Icons.Filled.Check)
                else -> {}
            }
        }
    }
}
