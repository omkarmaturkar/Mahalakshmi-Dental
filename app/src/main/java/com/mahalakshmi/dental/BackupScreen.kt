package com.mahalakshmi.dental

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            } catch (e: Exception) {
                toast("Please choose a different folder")
            }
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

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        AppTopBar("Backup & Restore", "Keep your clinic data safe", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val safe = folder != null && last > 0L
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(if (safe) BrandGradient else Brush.linearGradient(listOf(Color(0xFFF5A524), Color(0xFFE5672B))))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.Glass), contentAlignment = Alignment.Center) {
                    Icon(
                        if (safe) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                        tint = Color.White, modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (safe) "Your data is protected" else "Backup needs attention",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp
                    )
                    Text(
                        if (last == 0L) "No backup yet" else "Last backup: ${fmtDateTime(last)}",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp
                    )
                }
            }

            FormCard("Automatic backup", "Copies new patients and photos every time you close the app") {
                Text("Folder:  ${folder ?: "Not chosen yet"}", fontSize = 14.sp, color = Brand.Ink)
                GradientButton(
                    if (folder == null) "Choose backup folder" else "Change folder",
                    Modifier.fillMaxWidth(), AppIcons.Cloud
                ) { pickFolder.launch(null) }
                if (folder != null) {
                    SoftButton("Back up now", Icons.Filled.Refresh, Modifier.fillMaxWidth()) { backupNow() }
                }
            }

            FormCard("Copy outside the phone", "Once a week, save one file to Google Drive or a pen drive – safe even if the phone is lost") {
                SoftButton("Save backup file (.zip)", Icons.Filled.Share, Modifier.fillMaxWidth()) {
                    exportZip.launch(Backup.zipName())
                }
            }

            FormCard("Restore", "On a new phone or after reinstalling. This replaces all current data.") {
                SoftButton(
                    "Restore from backup folder", AppIcons.Cloud, Modifier.fillMaxWidth(),
                    bg = Brand.Soft, fg = Brand.Ink
                ) { restoreFolder.launch(null) }
                SoftButton(
                    "Restore from .zip file", Icons.Filled.Refresh, Modifier.fillMaxWidth(),
                    bg = Brand.Soft, fg = Brand.Ink
                ) { restoreZip.launch(arrayOf("*/*")) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    pendingRestore?.let { work ->
        ConfirmDialog(
            "Replace all data?",
            "Everything currently in the app will be replaced with the data from the backup.",
            confirmText = "Restore",
            danger = false,
            onConfirm = {
                pendingRestore = null
                doWork("Restoring…", work) { ok ->
                    if (ok) {
                        toast("Data restored ✓")
                        onRestored()
                    } else {
                        toast("This is not a valid clinic backup")
                    }
                }
            },
            onDismiss = { pendingRestore = null }
        )
    }
    busy?.let { BusyDialog(it) }
}
