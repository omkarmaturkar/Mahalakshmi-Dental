@file:OptIn(ExperimentalMaterial3Api::class)

package com.mahalakshmi.dental

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

// ───────────── Colours ─────────────
object Brand {
    val Primary = Color(0xFF0E9F6E)
    val PrimaryDark = Color(0xFF087453)
    val Teal = Color(0xFF0E7C86)
    val Mint = Color(0xFFE5F6EE)
    val MintStrong = Color(0xFFC9EBDB)
    val Bg = Color(0xFFF4F7F6)
    val Ink = Color(0xFF13201B)
    val Sub = Color(0xFF66766F)
    val Faint = Color(0xFF9DAAA5)
    val Line = Color(0xFFE3EAE7)
    val Soft = Color(0xFFF0F4F2)
    val Red = Color(0xFFE5484D)
    val RedSoft = Color(0xFFFDECEC)
    val Amber = Color(0xFFC77700)
    val AmberSoft = Color(0xFFFFF3DC)
    val GradStart = Color(0xFF12A876)
    val GradEnd = Color(0xFF0B7A8A)
    val Glass = Color(0x2EFFFFFF)
}

val BrandGradient: Brush = Brush.linearGradient(listOf(Brand.GradStart, Brand.GradEnd))

// ───────────── Font & theme ─────────────
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

private fun TextStyle.poppins(): TextStyle = copy(fontFamily = Poppins)
private val baseType = Typography()
val AppTypography = Typography(
    displayLarge = baseType.displayLarge.poppins(),
    displayMedium = baseType.displayMedium.poppins(),
    displaySmall = baseType.displaySmall.poppins(),
    headlineLarge = baseType.headlineLarge.poppins(),
    headlineMedium = baseType.headlineMedium.poppins(),
    headlineSmall = baseType.headlineSmall.poppins(),
    titleLarge = baseType.titleLarge.poppins(),
    titleMedium = baseType.titleMedium.poppins(),
    titleSmall = baseType.titleSmall.poppins(),
    bodyLarge = baseType.bodyLarge.poppins(),
    bodyMedium = baseType.bodyMedium.poppins(),
    bodySmall = baseType.bodySmall.poppins(),
    labelLarge = baseType.labelLarge.poppins(),
    labelMedium = baseType.labelMedium.poppins(),
    labelSmall = baseType.labelSmall.poppins()
)

@Composable
fun ClinicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand.Primary, onPrimary = Color.White,
            primaryContainer = Brand.Mint, onPrimaryContainer = Brand.PrimaryDark,
            secondary = Brand.Teal, onSecondary = Color.White,
            secondaryContainer = Brand.Mint, onSecondaryContainer = Brand.PrimaryDark,
            background = Brand.Bg, onBackground = Brand.Ink,
            surface = Color.White, onSurface = Brand.Ink,
            surfaceVariant = Brand.Soft, onSurfaceVariant = Brand.Sub,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Brand.Soft,
            outline = Color(0xFFD5DEDA), outlineVariant = Brand.Line, error = Brand.Red
        ),
        typography = AppTypography,
        content = content
    )
}

@Composable
fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = dark
    }
}

// ───────────── Custom icons ─────────────
private fun vectorIcon(name: String, viewport: Float, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = viewport, viewportHeight = viewport
    )
    for (p in paths) builder.addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
    return builder.build()
}

object AppIcons {
    val Tooth: ImageVector by lazy {
        vectorIcon(
            "tooth", 32f,
            "M9,4C5.5,4 3.5,7 4.3,11C5,14.5 6.5,16 7,20.5C7.6,25 8.5,28 10.3,28C12,28 12.3,25 13,22.5C13.6,20.5 14.6,19.5 16,19.5C17.4,19.5 18.4,20.5 19,22.5C19.7,25 20,28 21.7,28C23.5,28 24.4,25 25,20.5C25.5,16 27,14.5 27.7,11C28.5,7 26.5,4 23,4C20,4 18.5,5.5 16,5.5C13.5,5.5 12,4 9,4Z"
        )
    }
    val Camera: ImageVector by lazy {
        vectorIcon(
            "camera", 24f,
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0",
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zm3,15c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
        )
    }
    val Gallery: ImageVector by lazy {
        vectorIcon(
            "gallery", 24f,
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"
        )
    }
    val Cloud: ImageVector by lazy {
        vectorIcon(
            "cloud", 24f,
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z"
        )
    }
    val Chat: ImageVector by lazy {
        vectorIcon(
            "chat", 24f,
            "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z"
        )
    }
}

// ───────────── Formatting ─────────────
private val inr = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 2 }
fun rs(x: Double): String = "₹" + inr.format(x)
fun numText(x: Double): String = if (x == 0.0) "" else if (x % 1.0 == 0.0) x.toLong().toString() else x.toString()
fun fmtDate(t: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(t))
fun fmtTime(t: Long): String = SimpleDateFormat("EEEE · hh:mm a", Locale.getDefault()).format(Date(t))
fun fmtDateTime(t: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(t))
fun epochDayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
fun fmtDayLong(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))

fun fmtDay(day: Long): String {
    val today = todayEpochDay()
    return when (day) {
        today -> "Today"
        today + 1 -> "Tomorrow"
        else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
}

fun relativeTime(t: Long): String {
    if (t <= 0L) return ""
    val days = todayEpochDay() - epochDayOf(t)
    return when {
        days <= 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7L -> "$days days ago"
        days < 30L -> "${days / 7} wk ago"
        days < 365L -> "${days / 30} mo ago"
        else -> "${days / 365} yr ago"
    }
}

fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun animatedNumber(target: Double): Double {
    val anim = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        done = false
        anim.animateTo(target.toFloat(), tween(1000, easing = FastOutSlowInEasing))
        done = true
    }
    return if (done) target else anim.value.roundToLong().toDouble()
}

// ───────────── Building blocks ─────────────
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .shadow(8.dp, shape, ambientColor = Color(0x220E9F6E), spotColor = Color(0x330E9F6E))
            .clip(shape)
            .background(Color.White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

private val avatarColors = listOf(
    Color(0xFFDDF4E8) to Color(0xFF0B7A55),
    Color(0xFFE0EEFF) to Color(0xFF2357C6),
    Color(0xFFEDE7FF) to Color(0xFF6A43C9),
    Color(0xFFFFE8DC) to Color(0xFFC2541B),
    Color(0xFFFFE3EA) to Color(0xFFC0315A),
    Color(0xFFFFF1CC) to Color(0xFFA16207),
    Color(0xFFD9F3F5) to Color(0xFF0E7C86)
)

@Composable
fun Avatar(name: String, size: Dp = 48.dp, ring: Boolean = false) {
    val colors = avatarColors[(name.trim().lowercase().hashCode() and 0x7fffffff) % avatarColors.size]
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
    Box(
        Modifier.size(size)
            .then(if (ring) Modifier.border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(colors.first),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = colors.second, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.34f).sp)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(50)).background(BrandGradient))
        Spacer(Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Brand.PrimaryDark)
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.height(56.dp)
            .then(
                if (enabled) Modifier.shadow(
                    10.dp, shape,
                    ambientColor = Brand.Primary.copy(alpha = 0.4f), spotColor = Brand.Primary.copy(alpha = 0.5f)
                ) else Modifier
            )
            .clip(shape)
            .background(if (enabled) BrandGradient else SolidColor(Color(0xFFC9D3CF)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
fun SoftButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    bg: Color = Brand.Mint,
    fg: Color = Brand.PrimaryDark,
    onClick: () -> Unit
) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    bg: Color = Color.White,
    tint: Color = Brand.Ink,
    size: Dp = 44.dp
) {
    Box(
        Modifier.size(size)
            .then(
                if (bg == Color.White) Modifier.shadow(
                    4.dp, CircleShape, ambientColor = Color(0x1A000000), spotColor = Color(0x22000000)
                ) else Modifier
            )
            .clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
fun GradientFab(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.height(58.dp)
            .shadow(14.dp, shape, ambientColor = Brand.Primary.copy(alpha = 0.5f), spotColor = Brand.Primary.copy(alpha = 0.6f))
            .clip(shape).background(BrandGradient)
            .clickable(onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedBg: Color = Brand.Primary,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(if (selected) selectedBg else Color.White, label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else Brand.Ink, label = "chipFg")
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(bg)
            .border(1.dp, if (selected) selectedBg else Brand.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Soft).padding(4.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) Color.White else Color.Transparent, label = "segment")
            Box(
                Modifier.weight(1f).height(44.dp)
                    .then(if (isSelected) Modifier.shadow(3.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option, fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Brand.PrimaryDark else Brand.Sub
                )
            }
        }
    }
}

@Composable
fun AppField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    type: KeyboardType = KeyboardType.Text,
    lines: Int = 1,
    caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    prefix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = if (icon != null) { { Icon(icon, null, tint = Brand.Sub) } } else null,
        prefix = if (prefix != null) { { Text(prefix, color = Brand.Ink) } } else null,
        singleLine = lines == 1,
        minLines = lines,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = type),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand.Primary, unfocusedBorderColor = Brand.Line,
            focusedLabelColor = Brand.Primary, cursorColor = Brand.Primary,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color(0xFFFAFCFB)
        ),
        modifier = modifier
    )
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .shadow(6.dp, shape, ambientColor = Color(0x1A0E9F6E), spotColor = Color(0x260E9F6E))
            .clip(shape).background(Color.White).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Brand.Faint)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Search name or mobile number", color = Brand.Faint, fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Poppins, fontSize = 15.sp, color = Brand.Ink),
                cursorBrush = SolidColor(Brand.Primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Brand.Soft).clickable { onChange("") },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Close, null, tint = Brand.Sub, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun AppTopBar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Brand.Bg).statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onBack)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 13.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
fun BottomActionBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(16.dp).background(Color.White)
            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun FormCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.PrimaryDark)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Brand.Sub)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun EmptyState(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Brand.Mint), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Tooth, null, tint = Brand.Primary, modifier = Modifier.size(54.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, fontSize = 14.sp, color = Brand.Sub, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Delete",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(24.dp)) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(if (danger) Brand.RedSoft else Brand.Mint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (danger) Icons.Filled.Delete else Icons.Filled.Refresh, null,
                    tint = if (danger) Brand.Red else Brand.Primary
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Brand.Ink)
            Spacer(Modifier.height(6.dp))
            Text(message, fontSize = 14.sp, color = Brand.Sub)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                SoftButton(
                    confirmText, null, Modifier.weight(1f),
                    bg = if (danger) Brand.Red else Brand.Primary, fg = Color.White, onClick = onConfirm
                )
            }
        }
    }
}

@Composable
fun BusyDialog(text: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(color = Brand.Primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Text(text, fontSize = 15.sp, color = Brand.Ink)
        }
    }
}
