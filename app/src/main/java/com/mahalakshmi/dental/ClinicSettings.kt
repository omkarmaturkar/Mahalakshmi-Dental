package com.mahalakshmi.dental

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

const val DEFAULT_CLINIC_NAME = "Mahalakshmi Dental Clinic"
const val DEFAULT_FOOTER = "Thank you for visiting. Wishing you a healthy smile!"

/** Clinic and doctor details printed on prescriptions, bills and statements. */
data class ClinicInfo(
    val name: String = DEFAULT_CLINIC_NAME,
    val doctor: String = "",
    val qualification: String = "",
    val regNo: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val timings: String = "",
    val footer: String = DEFAULT_FOOTER,
    val paper: String = "A4"
)

// Stored inside the database, so the details are included in every backup
fun ClinicDb.clinicInfo(): ClinicInfo {
    val map = HashMap<String, String>()
    readableDatabase.rawQuery("SELECT key, value FROM settings", null).use { c ->
        while (c.moveToNext()) map[c.getString(0)] = c.getString(1) ?: ""
    }
    return ClinicInfo(
        name = map["clinic_name"]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLINIC_NAME,
        doctor = map["doctor"] ?: "",
        qualification = map["qualification"] ?: "",
        regNo = map["reg_no"] ?: "",
        phone = map["phone"] ?: "",
        email = map["email"] ?: "",
        address = map["address"] ?: "",
        timings = map["timings"] ?: "",
        footer = map["footer"] ?: DEFAULT_FOOTER,
        paper = if (map["paper"] == "A5") "A5" else "A4"
    )
}

fun ClinicDb.saveClinicInfo(info: ClinicInfo) {
    val values = listOf(
        "clinic_name" to info.name, "doctor" to info.doctor, "qualification" to info.qualification,
        "reg_no" to info.regNo, "phone" to info.phone, "email" to info.email, "address" to info.address,
        "timings" to info.timings, "footer" to info.footer, "paper" to info.paper
    )
    val sql = writableDatabase
    sql.beginTransaction()
    try {
        for ((key, value) in values) {
            val cv = ContentValues().apply {
                put("key", key)
                put("value", value)
            }
            sql.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
        sql.setTransactionSuccessful()
    } finally {
        sql.endTransaction()
    }
}

@Composable
fun ClinicSettingsScreen(db: ClinicDb, onBack: () -> Unit) {
    val notify = LocalNotify.current
    val saved = remember { db.clinicInfo() }
    var name by remember { mutableStateOf(saved.name) }
    var doctor by remember { mutableStateOf(saved.doctor) }
    var qualification by remember { mutableStateOf(saved.qualification) }
    var regNo by remember { mutableStateOf(saved.regNo) }
    var phone by remember { mutableStateOf(saved.phone) }
    var email by remember { mutableStateOf(saved.email) }
    var address by remember { mutableStateOf(saved.address) }
    var timings by remember { mutableStateOf(saved.timings) }
    var footer by remember { mutableStateOf(saved.footer) }
    var paper by remember { mutableStateOf(saved.paper) }
    var preview by remember { mutableStateOf<ClinicInfo?>(null) }

    fun current() = ClinicInfo(
        name = name.trim().ifBlank { DEFAULT_CLINIC_NAME }, doctor = doctor.trim(),
        qualification = qualification.trim(), regNo = regNo.trim(), phone = phone.trim(),
        email = email.trim(), address = address.trim(), timings = timings.trim(),
        footer = footer.trim(), paper = paper
    )

    fun save() {
        db.saveClinicInfo(current())
        DataBus.changed()
        notify("Clinic details saved ✓")
        onBack()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp

        val clinicCard: @Composable () -> Unit = {
            FormCard("Clinic", "Printed at the top of every prescription and bill") {
                AppField(name, { name = it }, "Clinic name *", icon = AppIcons.Tooth, caps = KeyboardCapitalization.Words)
                AppField(phone, { phone = it }, "Clinic phone", icon = Icons.Filled.Phone, type = KeyboardType.Phone)
                AppField(
                    email, { email = it }, "Email (optional)", icon = Icons.Filled.Email,
                    type = KeyboardType.Email, caps = KeyboardCapitalization.None
                )
                AppField(address, { address = it }, "Clinic address", icon = Icons.Filled.Place, lines = 2)
                AppField(timings, { timings = it }, "Timings, e.g. Mon–Sat 10 AM–2 PM, 5–9 PM", icon = Icons.Filled.DateRange)
            }
        }
        val doctorCard: @Composable () -> Unit = {
            FormCard("Doctor", "Printed with the signature on prescriptions") {
                AppField(
                    doctor, { doctor = it }, "Doctor name, e.g. Dr. Priya Patil",
                    icon = Icons.Filled.Person, caps = KeyboardCapitalization.Words
                )
                AppField(qualification, { qualification = it }, "Qualification, e.g. BDS, MDS", caps = KeyboardCapitalization.Characters)
                AppField(regNo, { regNo = it }, "Registration number", caps = KeyboardCapitalization.Characters)
            }
        }
        val printCard: @Composable () -> Unit = {
            FormCard("Printing", "Paper size and the message at the bottom of each page") {
                Segmented(listOf("A4", "A5"), paper) { paper = it }
                AppField(footer, { footer = it }, "Bottom message", lines = 2)
                SoftButton("Preview letterhead", DocIcons.Pdf, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    preview = current()
                }
            }
        }

        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar("Clinic details", "Used on prescriptions, bills and statements", onBack)
            if (twoColumn) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) { clinicCard() }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        doctorCard()
                        printCard()
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    clinicCard()
                    doctorCard()
                    printCard()
                }
            }
            BottomActionBar {
                if (twoColumn) {
                    Spacer(Modifier.weight(1f))
                    GradientButton("Save details", Modifier.width(340.dp), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                } else {
                    GradientButton("Save details", Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                }
            }
        }
    }

    preview?.let { info ->
        PdfActionsDialog(
            title = "Letterhead preview",
            subtitle = "A sample page with these details",
            phone = "",
            message = info.name,
            build = { ctx -> letterheadSamplePdf(ctx, info) },
            onDismiss = { preview = null }
        )
    }
}
