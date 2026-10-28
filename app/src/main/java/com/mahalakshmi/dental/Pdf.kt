package com.mahalakshmi.dental

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────── Colours used in PDFs ─────────────
val PdfGreen = 0xFF5A8F12.toInt()
val PdfInk = 0xFF1D2321.toInt()
val PdfSub = 0xFF69716D.toInt()
val PdfLine = 0xFFDCE3D6.toInt()
val PdfSoft = 0xFFF1F7E4.toInt()
val PdfRed = 0xFFD93A3F.toInt()

fun docNo(prefix: String, id: Long): String = prefix + id.toString().padStart(4, '0')

/** One cell in a table row. [width] is a share of the page width (0.5f = half). */
class PdfCell(
    val text: CharSequence,
    val width: Float,
    val size: Float = 10.5f,
    val color: Int = PdfInk,
    val weight: Int = 0,
    val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
)

/** Draws A4 / A5 pages with the clinic letterhead on top and a footer at the bottom. */
class PdfBuilder(private val context: Context, private val clinic: ClinicInfo) {
    private val small = clinic.paper == "A5"
    private val pageW = if (small) 420 else 595
    private val pageH = if (small) 595 else 842
    private val k = if (small) 0.86f else 1f
    private val margin = if (small) 28f else 40f
    val left = margin
    val right = pageW - margin
    val width = right - left
    private val bottom = pageH - (if (small) 50f else 62f)

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    private lateinit var canvas: Canvas
    private var y = 0f

    private val regular = font(R.font.poppins_regular, Typeface.DEFAULT)
    private val medium = font(R.font.poppins_medium, Typeface.DEFAULT)
    private val semibold = font(R.font.poppins_semibold, Typeface.DEFAULT_BOLD)
    private val logo: Bitmap? = loadLogo()

    init {
        newPage()
    }

    private fun font(id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: fallback } catch (e: Throwable) { fallback }

    private fun loadLogo(): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.clinic_logo, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 500) sample *= 2
        BitmapFactory.decodeResource(context.resources, R.drawable.clinic_logo, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }

    fun paint(size: Float, color: Int = PdfInk, weight: Int = 0) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * k
        this.color = color
        typeface = when (weight) {
            2 -> semibold
            1 -> medium
            else -> regular
        }
    }

    private fun layout(text: CharSequence, paint: TextPaint, w: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, w.toInt().coerceAtLeast(10))
            .setAlignment(align)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.1f)
            .build()

    private fun drawLayout(l: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(x, top)
        l.draw(canvas)
        canvas.restore()
    }

    private fun solid(color: Int) = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
    }

    fun ensure(h: Float) {
        if (y + h > bottom) newPage()
    }

    fun space(h: Float) {
        y += h
    }

    fun text(
        text: CharSequence, size: Float = 10.5f, color: Int = PdfInk, weight: Int = 0,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, after: Float = 4f
    ) {
        if (text.isBlank()) return
        val l = layout(text, paint(size, color, weight), width, align)
        ensure(l.height.toFloat())
        drawLayout(l, left, y)
        y += l.height + after
    }

    fun row(cells: List<PdfCell>, fill: Int? = null, pad: Float = 6f, after: Float = 0f, line: Boolean = false) {
        val layouts = cells.map { c -> layout(c.text, paint(c.size, c.color, c.weight), c.width * width - 10f, c.align) }
        val h = (layouts.maxOfOrNull { it.height } ?: 0) + pad * 2
        ensure(h)
        if (fill != null) canvas.drawRect(left, y, right, y + h, solid(fill))
        var x = left
        cells.forEachIndexed { i, c ->
            drawLayout(layouts[i], x + 5f, y + pad)
            x += c.width * width
        }
        y += h
        if (line) canvas.drawRect(left, y - 0.7f, right, y, solid(PdfLine))
        y += after
    }

    fun title(title: String, info: String) {
        val t = layout(title, paint(14f, PdfGreen, 2).apply { letterSpacing = 0.08f }, width * 0.55f)
        val i = layout(info, paint(9.5f, PdfSub), width * 0.45f, Layout.Alignment.ALIGN_OPPOSITE)
        val h = maxOf(t.height, i.height).toFloat()
        ensure(h)
        drawLayout(t, left, y)
        drawLayout(i, left + width * 0.55f, y)
        y += h + 12f
    }

    fun patientBox(p: Patient) {
        val details = listOf(
            listOf(p.gender, if (p.age.isNotBlank()) "${p.age} yrs" else "").filter { it.isNotBlank() }.joinToString(" / "),
            p.phone
        ).filter { it.isNotBlank() }.joinToString("   ·   ")
        val text = SpannableStringBuilder(p.name)
        if (details.isNotEmpty()) {
            val start = text.length
            text.append("\n").append(details)
            text.setSpan(RelativeSizeSpan(0.78f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(PdfSub), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        row(
            listOf(
                PdfCell(text, 0.66f, 12f, PdfInk, 2),
                PdfCell("Patient ID\n" + docNo("P-", p.id), 0.34f, 9f, PdfSub, 0, Layout.Alignment.ALIGN_OPPOSITE)
            ),
            fill = PdfSoft, pad = 8f, after = 12f
        )
    }

    fun total(label: String, value: String, color: Int = PdfInk, strong: Boolean = false) {
        row(
            listOf(
                PdfCell(
                    label, 0.7f, if (strong) 11.5f else 10.5f, if (strong) PdfInk else PdfSub,
                    if (strong) 2 else 0, Layout.Alignment.ALIGN_OPPOSITE
                ),
                PdfCell(value, 0.3f, if (strong) 12.5f else 10.5f, color, if (strong) 2 else 1, Layout.Alignment.ALIGN_OPPOSITE)
            ),
            pad = 3f
        )
    }

    fun signature() {
        val lines = listOf(
            clinic.doctor, clinic.qualification,
            if (clinic.regNo.isNotBlank()) "Reg. No. ${clinic.regNo}" else ""
        ).filter { it.isNotBlank() }
        val w = width * 0.42f
        val l = layout(
            lines.joinToString("\n").ifBlank { "Doctor's signature" },
            paint(9.5f, PdfInk, 1), w, Layout.Alignment.ALIGN_CENTER
        )
        ensure(44f + l.height)
        y += 36f
        canvas.drawRect(right - w, y, right, y + 0.8f, solid(PdfSub))
        y += 6f
        drawLayout(l, right - w, y)
        y += l.height + 6f
    }

    private fun letterhead() {
        canvas.drawRect(0f, 0f, pageW.toFloat(), 7f, solid(PdfGreen))
        val top = if (small) 22f else 30f
        val logoSize = if (small) 46f else 60f
        var textX = left
        var logoH = 0f
        logo?.let { bmp ->
            val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
            val w = if (ratio >= 1f) logoSize else logoSize * ratio
            val h = if (ratio >= 1f) logoSize / ratio else logoSize
            canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            textX = left + w + 12f
            logoH = h
        }
        val sideW = width * 0.36f
        val mainW = right - sideW - 12f - textX

        fun put(text: String, size: Float, color: Int, weight: Int, x: Float, w: Float, at: Float, align: Layout.Alignment): Float {
            if (text.isBlank()) return 0f
            val l = layout(text, paint(size, color, weight), w, align)
            drawLayout(l, x, at)
            return l.height + 2f
        }

        val start = Layout.Alignment.ALIGN_NORMAL
        val end = Layout.Alignment.ALIGN_OPPOSITE
        var leftY = top
        leftY += put(clinic.name, 17f, PdfInk, 2, textX, mainW, leftY, start)
        leftY += put(
            listOf(clinic.doctor, clinic.qualification).filter { it.isNotBlank() }.joinToString(", "),
            10.5f, PdfGreen, 1, textX, mainW, leftY, start
        )
        if (clinic.regNo.isNotBlank()) leftY += put("Reg. No. ${clinic.regNo}", 9f, PdfSub, 0, textX, mainW, leftY, start)

        var sideY = top + 2f
        listOf(
            if (clinic.phone.isNotBlank()) "Ph: ${clinic.phone}" else "",
            clinic.email, clinic.address, clinic.timings
        ).forEach { sideY += put(it, 8.8f, PdfSub, 0, right - sideW, sideW, sideY, end) }

        y = maxOf(top + logoH, leftY, sideY) + 8f
        canvas.drawRect(left, y, right, y + 1.6f, solid(PdfGreen))
        y += 16f
    }

    private fun footer() {
        val fy = pageH - (if (small) 36f else 44f)
        canvas.drawRect(left, fy - 6f, right, fy - 5.4f, solid(PdfLine))
        val pnW = 50f
        if (clinic.footer.isNotBlank()) drawLayout(layout(clinic.footer, paint(8.5f, PdfSub), width - pnW - 10f), left, fy)
        drawLayout(layout("Page $pageNo", paint(8.5f, PdfSub), pnW, Layout.Alignment.ALIGN_OPPOSITE), right - pnW, fy)
    }

    private fun newPage() {
        page?.let {
            footer()
            doc.finishPage(it)
        }
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        page = p
        canvas = p.canvas
        letterhead()
    }

    fun save(file: File): File {
        page?.let {
            footer()
            doc.finishPage(it)
        }
        page = null
        file.parentFile?.mkdirs()
        try {
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }
}

/** A new PDF file in the app's cache. PDFs older than a day are cleaned up. */
fun pdfFile(context: Context, kind: String, who: String): File {
    val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
    val now = System.currentTimeMillis()
    dir.listFiles()?.forEach { if (now - it.lastModified() > 24L * 60 * 60 * 1000) it.delete() }
    val safe = who.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "Patient" }.take(30)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
    return File(dir, "${kind}_${safe}_$stamp.pdf")
}

// ───────────── Receipt & statement ─────────────
data class PaymentEntry(val amount: Double, val date: Long, val mode: String)

fun ClinicDb.visitPayments(visitId: Long): List<PaymentEntry> {
    val list = mutableListOf<PaymentEntry>()
    readableDatabase.rawQuery(
        "SELECT amount, date, mode FROM payments WHERE visit_id = ? ORDER BY date", arrayOf(visitId.toString())
    ).use { c -> while (c.moveToNext()) list.add(PaymentEntry(c.getDouble(0), c.getLong(1), c.getString(2) ?: "")) }
    return list
}

fun receiptPdf(context: Context, db: ClinicDb, visitId: Long): File {
    val visit = db.getVisit(visitId) ?: error("Visit not found")
    val patient = db.getPatient(visit.patientId) ?: error("Patient not found")
    val pdf = PdfBuilder(context, db.clinicInfo())
    val end = Layout.Alignment.ALIGN_OPPOSITE
    pdf.title("PAYMENT RECEIPT", "Receipt No. ${docNo("R-", visit.id)}\nDate: ${fmtDate(visit.date)}")
    pdf.patientBox(patient)
    pdf.row(
        listOf(
            PdfCell("Treatment", 0.55f, 9.5f, PdfSub, 1),
            PdfCell("Teeth", 0.2f, 9.5f, PdfSub, 1),
            PdfCell("Amount", 0.25f, 9.5f, PdfSub, 1, end)
        ),
        fill = PdfSoft
    )
    pdf.row(
        listOf(
            PdfCell(visit.treatments.joinToString(", ").ifBlank { "Dental treatment" }, 0.55f),
            PdfCell(visit.teethList.joinToString(", ").ifBlank { "–" }, 0.2f),
            PdfCell(rs(visit.fees), 0.25f, align = end)
        ),
        pad = 8f, line = true
    )
    pdf.space(8f)
    pdf.total("Total fees", rs(visit.fees))
    pdf.total("Paid", rs(visit.paid), PdfGreen)
    pdf.total(
        "Balance due", rs(visit.remaining.coerceAtLeast(0.0)),
        if (visit.remaining > 0) PdfRed else PdfGreen, strong = true
    )
    val payments = db.visitPayments(visitId).filter { it.amount != 0.0 }
    if (payments.isNotEmpty()) {
        pdf.space(14f)
        pdf.text("Payments received", 11f, PdfGreen, 2)
        payments.forEach { p ->
            pdf.row(
                listOf(
                    PdfCell(fmtDate(p.date), 0.45f, 10f),
                    PdfCell(p.mode.ifBlank { "–" }, 0.3f, 10f, PdfSub),
                    PdfCell(if (p.amount < 0) "– " + rs(-p.amount) else rs(p.amount), 0.25f, 10f, align = end)
                ),
                pad = 5f, line = true
            )
        }
    }
    pdf.signature()
    return pdf.save(pdfFile(context, "Receipt", patient.name))
}

fun statementPdf(context: Context, db: ClinicDb, patientId: Long): File {
    val patient = db.getPatient(patientId) ?: error("Patient not found")
    val visits = db.getVisits(patientId).sortedBy { it.date }
    val pdf = PdfBuilder(context, db.clinicInfo())
    val end = Layout.Alignment.ALIGN_OPPOSITE
    pdf.title("ACCOUNT STATEMENT", "Date: ${fmtDate(System.currentTimeMillis())}")
    pdf.patientBox(patient)
    pdf.row(
        listOf(
            PdfCell("Date", 0.19f, 9.5f, PdfSub, 1),
            PdfCell("Treatment", 0.36f, 9.5f, PdfSub, 1),
            PdfCell("Fees", 0.15f, 9.5f, PdfSub, 1, end),
            PdfCell("Paid", 0.15f, 9.5f, PdfSub, 1, end),
            PdfCell("Due", 0.15f, 9.5f, PdfSub, 1, end)
        ),
        fill = PdfSoft
    )
    if (visits.isEmpty()) pdf.text("No visits yet.", 10f, PdfSub)
    visits.forEach { v ->
        val treatment = v.treatments.joinToString(", ").ifBlank { "Visit" } +
            if (v.teethList.isNotEmpty()) " (${v.teethList.joinToString(", ")})" else ""
        pdf.row(
            listOf(
                PdfCell(fmtDate(v.date), 0.19f, 9.5f),
                PdfCell(treatment, 0.36f, 9.5f),
                PdfCell(rs(v.fees), 0.15f, 9.5f, align = end),
                PdfCell(rs(v.paid), 0.15f, 9.5f, PdfGreen, align = end),
                PdfCell(rs(v.remaining.coerceAtLeast(0.0)), 0.15f, 9.5f, if (v.remaining > 0) PdfRed else PdfSub, align = end)
            ),
            pad = 6f, line = true
        )
    }
    pdf.space(8f)
    pdf.total("Total fees", rs(patient.totalFees))
    pdf.total("Total paid", rs(patient.totalPaid), PdfGreen)
    pdf.total(
        "Balance due", rs(patient.remaining.coerceAtLeast(0.0)),
        if (patient.remaining > 0) PdfRed else PdfGreen, strong = true
    )
    pdf.signature()
    return pdf.save(pdfFile(context, "Statement", patient.name))
}

fun letterheadSamplePdf(context: Context, clinic: ClinicInfo): File {
    val pdf = PdfBuilder(context, clinic)
    pdf.title("SAMPLE PAGE", "Date: ${fmtDate(System.currentTimeMillis())}")
    pdf.patientBox(Patient(id = 1, name = "Sample Patient", phone = "9876543210", age = "35", gender = "Female"))
    pdf.text("This is how the top and bottom of your prescriptions, receipts and statements will look.", 10.5f, PdfSub)
    pdf.signature()
    return pdf.save(pdfFile(context, "Letterhead", "Sample"))
}

// ───────────── Share, open, print ─────────────
private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun pdfUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun whatsAppNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.length == 10 -> "91$digits"
        digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
        else -> digits
    }
}

/** Opens WhatsApp (or WhatsApp Business) with the PDF attached, straight to the patient's chat when possible. */
fun sharePdfOnWhatsApp(context: Context, file: File, phone: String, message: String) {
    val uri = try {
        pdfUri(context, file)
    } catch (e: Exception) {
        toast(context, "Could not share the PDF")
        return
    }
    val number = whatsAppNumber(phone)
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            setPackage(pkg)
            putExtra(Intent.EXTRA_STREAM, uri)
            if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
            if (number.length >= 10) putExtra("jid", "$number@s.whatsapp.net")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(send)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next WhatsApp app
        }
    }
    toast(context, "WhatsApp is not installed")
    sharePdf(context, file, message)
}

fun sharePdf(context: Context, file: File, message: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri(context, file))
            if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share PDF"))
    } catch (e: Exception) {
        toast(context, "Could not share the PDF")
    }
}

fun openPdf(context: Context, file: File) {
    try {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri(context, file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        toast(context, "No PDF viewer found – choose an app")
        sharePdf(context, file, "")
    } catch (e: Exception) {
        toast(context, "Could not open the PDF")
    }
}
fun printPdf(context: Context, file: File) {
    try {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(file.nameWithoutExtension, PdfPrintAdapter(file), null)
    } catch (e: Exception) {
        toast(context, "Printing is not available on this device")
    }
}

private class PdfPrintAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.WriteResultCallback
    ) {
        try {
            file.inputStream().use { input ->
                FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

// ───────────── "PDF ready" dialog ─────────────
@Composable
fun PdfActionsDialog(
    title: String,
    subtitle: String,
    phone: String,
    message: String,
    build: (Context) -> File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val made = withContext(Dispatchers.IO) { runCatching { build(context) }.getOrNull() }
        if (made != null) file = made else failed = true
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color.White).padding(26.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Icon(DocIcons.Pdf, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Text(subtitle, fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(20.dp))
            val ready = file
            if (failed) {
                Text("Could not create the PDF. Please try again.", fontSize = 14.sp, color = Brand.Red)
            } else if (ready == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Brand.GreenDeep, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Preparing PDF…", fontSize = 15.sp, color = Brand.Ink)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton("Send on WhatsApp", Modifier.fillMaxWidth(), AppIcons.Chat) {
                        sharePdfOnWhatsApp(context, ready, phone, message)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SoftButton("Print", DocIcons.Print, Modifier.weight(1f)) { printPdf(context, ready) }
                        SoftButton("Open", DocIcons.Pdf, Modifier.weight(1f)) { openPdf(context, ready) }
                    }
                    SoftButton("Share or save", Icons.Filled.Share, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                        sharePdf(context, ready, message)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SoftButton("Close", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
        }
    }
}

// ───────────── "Prescriptions & bills" card on the patient profile ─────────────
@Composable
fun PatientDocumentsCard(db: ClinicDb, patient: Patient, visits: List<Visit>, go: (Screen) -> Unit) {
    val version = DataBus.version
    val rxVisits = remember(version, patient.id) { db.prescribedVisitIds(patient.id) }
    val clinicName = remember(version) { db.clinicInfo().name }
    var showAll by remember { mutableStateOf(false) }
    var receiptFor by remember { mutableStateOf<Visit?>(null) }
    var statement by remember { mutableStateOf(false) }
    val first = patient.name.trim().substringBefore(" ")

    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Text("Prescriptions & bills", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.GreenText)
        Text("Make a PDF, print it or send it on WhatsApp", fontSize = 12.sp, color = Brand.Sub)
        Spacer(Modifier.height(10.dp))
        if (visits.isEmpty()) {
            Text("Add a visit first.", fontSize = 13.sp, color = Brand.Sub)
        } else {
            (if (showAll) visits else visits.take(3)).forEachIndexed { i, v ->
                if (i > 0) HorizontalDivider(color = Brand.Line)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fmtDate(v.date), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
                        Text(
                            v.treatments.joinToString(", ").ifBlank { "Visit" }, fontSize = 12.sp, color = Brand.Sub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    val hasRx = v.id in rxVisits
                    SoftButton(
                        if (hasRx) "Rx ✓" else "Rx", DocIcons.Pill, height = 40.dp,
                        bg = if (hasRx) Brand.GreenDeep else Brand.GreenSoft,
                        fg = if (hasRx) Color.White else Brand.GreenText
                    ) { go(Screen.RxForm(patient.id, v.id)) }
                    Spacer(Modifier.width(8.dp))
                    SoftButton("Bill", DocIcons.Receipt, height = 40.dp, bg = Brand.Soft, fg = Brand.Ink) { receiptFor = v }
                }
            }
            if (visits.size > 3) {
                Text(
                    if (showAll) "Show less" else "Show all ${visits.size} visits",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.GreenDeep,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showAll = !showAll }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            SoftButton("Account statement (PDF)", DocIcons.Pdf, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                statement = true
            }
        }
    }

    receiptFor?.let { v ->
        PdfActionsDialog(
            title = "Payment receipt",
            subtitle = "Visit on ${fmtDate(v.date)}",
            phone = patient.phone,
            message = "Hello $first, here is your payment receipt from $clinicName. Thank you!",
            build = { ctx -> receiptPdf(ctx, db, v.id) },
            onDismiss = { receiptFor = null }
        )
    }
    if (statement) {
        PdfActionsDialog(
            title = "Account statement",
            subtitle = "All visits and payments of ${patient.name}",
            phone = patient.phone,
            message = "Hello $first, here is your account statement from $clinicName.",
            build = { ctx -> statementPdf(ctx, db, patient.id) },
            onDismiss = { statement = false }
        )
    }
}

// ───────────── Icons ─────────────
private fun docIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

object DocIcons {
    val Pdf: ImageVector by lazy {
        docIcon(
            "pdf",
            "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM11.5,9.5c0,0.83 -0.67,1.5 -1.5,1.5H9v2H7.5V7H10c0.83,0 1.5,0.67 1.5,1.5v1zM16.5,11.5c0,0.83 -0.67,1.5 -1.5,1.5h-2.5V7H15c0.83,0 1.5,0.67 1.5,1.5v3zM20.5,8.5H19v1h1.5V11H19v2h-1.5V7h3v1.5zM9,9.5h1v-1H9v1zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM14,11.5h1v-3h-1v3z"
        )
    }
    val Print: ImageVector by lazy {
        docIcon(
            "print",
            "M19,8H5c-1.66,0 -3,1.34 -3,3v6h4v4h12v-4h4v-6c0,-1.66 -1.34,-3 -3,-3zM16,19H8v-5h8v5zM19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM18,3H6v4h12V3z"
        )
    }
    val Receipt: ImageVector by lazy {
        docIcon(
            "receipt",
            "M18,17H6v-2h12v2zM18,13H6v-2h12v2zM18,9H6V7h12v2zM3,22l1.5,-1.5L6,22l1.5,-1.5L9,22l1.5,-1.5L12,22l1.5,-1.5L15,22l1.5,-1.5L18,22l1.5,-1.5L21,22V2l-1.5,1.5L18,2l-1.5,1.5L15,2l-1.5,1.5L12,2l-1.5,1.5L9,2 7.5,3.5 6,2 4.5,3.5 3,2v20z"
        )
    }
    val Pill: ImageVector by lazy {
        docIcon(
            "pill",
            "M6,3h12v2H6V3zM17,6H7C5.9,6 5,6.9 5,8v11c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V8C19,6.9 18.1,6 17,6zM15,15.5h-2v2h-2v-2H9v-2h2v-2h2v2h2V15.5z"
        )
    }
    val Settings: ImageVector get() = Icons.Filled.Settings
}
