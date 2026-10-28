package com.mahalakshmi.dental

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var db: ClinicDb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = ClinicDb(applicationContext)
        setContent {
            ClinicTheme {
                var showSplash by remember { mutableStateOf(true) }
                Crossfade(targetState = showSplash, animationSpec = tween(650), label = "splash") { splash ->
                    if (splash) SplashScreen { showSplash = false } else ClinicApp(db)
                }
            }
        }
    }

    // When the app goes to the background: backup to the tablet folder + daily Google Drive backup
    override fun onStop() {
        super.onStop()
        val ctx = applicationContext
        Thread {
            Backup.autoBackup(ctx, db)
            DriveBackup.autoBackup(ctx, db)
        }.start()
    }
}

sealed class Screen {
    object Home : Screen()
    object BackupPage : Screen()
    object ClinicSettings : Screen()
    data class PatientForm(val patientId: Long = 0) : Screen()
    data class PatientDetail(val patientId: Long) : Screen()
    data class VisitForm(val patientId: Long, val visitId: Long = 0) : Screen()
    data class RxForm(val patientId: Long, val visitId: Long) : Screen()
}

data class NavEntry(val depth: Int, val screen: Screen)

@Composable
fun ClinicApp(db: ClinicDb) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val open: (Screen?) -> Unit = { target ->
        stack.clear()
        stack.add(Screen.Home)
        if (target != null) stack.add(target)
    }
    BackHandler(enabled = stack.size > 1) { back() }
    StatusBarIcons(dark = true)

    var message by remember { mutableStateOf<String?>(null) }
    var shownText by remember { mutableStateOf("") }
    var messageId by remember { mutableIntStateOf(0) }
    LaunchedEffect(messageId) {
        if (messageId > 0) {
            delay(2600)
            message = null
        }
    }
    val notify: (String) -> Unit = { text ->
        shownText = text
        message = text
        messageId++
    }

    CompositionLocalProvider(LocalNotify provides notify) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
            val wide = maxWidth >= 700.dp
            val sidebarWidth = if (maxWidth >= 1100.dp) 380.dp else 330.dp
            val entry = NavEntry(stack.size, stack.last())
            if (wide) {
                val selectedId = stack.asReversed().firstNotNullOfOrNull { s ->
                    when (s) {
                        is Screen.PatientDetail -> s.patientId
                        is Screen.VisitForm -> s.patientId
                        is Screen.RxForm -> s.patientId
                        else -> null
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        db = db,
                        width = sidebarWidth,
                        dashboardActive = stack.size == 1,
                        backupActive = stack.last() is Screen.BackupPage,
                        settingsActive = stack.last() is Screen.ClinicSettings,
                        selectedId = selectedId,
                        onDashboard = { open(null) },
                        onBackup = { open(Screen.BackupPage) },
                        onSettings = { open(Screen.ClinicSettings) },
                        onNewPatient = { open(Screen.PatientForm()) },
                        onPatient = { id -> open(Screen.PatientDetail(id)) }
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Brand.Line))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        NavArea(entry) { screen -> ScreenHost(screen, db, true, stack, go, back, open) }
                    }
                }
            } else {
                NavArea(entry) { screen -> ScreenHost(screen, db, false, stack, go, back, open) }
            }
            AnimatedVisibility(
                visible = message != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
            ) { NotifyBanner(shownText) }
        }
    }
}

@Composable
private fun NavArea(entry: NavEntry, content: @Composable (Screen) -> Unit) {
    AnimatedContent(
        targetState = entry,
        transitionSpec = {
            if (targetState.depth >= initialState.depth) {
                (slideInHorizontally(tween(340)) { it / 4 } + fadeIn(tween(340))) togetherWith
                    (slideOutHorizontally(tween(340)) { -it / 8 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(340)) { -it / 8 } + fadeIn(tween(340))) togetherWith
                    (slideOutHorizontally(tween(340)) { it / 4 } + fadeOut(tween(220)))
            }
        },
        label = "navigation"
    ) { e ->
        Box(Modifier.fillMaxSize().background(Brand.Bg)) { content(e.screen) }
    }
}

@Composable
private fun ScreenHost(
    screen: Screen,
    db: ClinicDb,
    wide: Boolean,
    stack: MutableList<Screen>,
    go: (Screen) -> Unit,
    back: () -> Unit,
    open: (Screen?) -> Unit
) {
    when (screen) {
        is Screen.Home -> {
            if (wide) {
                DashboardWide(
                    db,
                    onBackup = { open(Screen.BackupPage) },
                    onPatient = { id -> open(Screen.PatientDetail(id)) },
                    onNewPatient = { open(Screen.PatientForm()) }
                )
            } else {
                HomeCompact(db, go)
            }
        }
        is Screen.BackupPage -> BackupScreen(db, back) { open(null) }
        is Screen.PatientForm -> PatientFormScreen(db, screen.patientId, back) { newId, isNew ->
            stack.removeAt(stack.lastIndex)
            if (isNew) {
                stack.add(Screen.PatientDetail(newId))
                stack.add(Screen.VisitForm(newId))
            }
        }
        is Screen.PatientDetail -> PatientDetailScreen(db, screen.patientId, go, back)
        is Screen.VisitForm -> VisitFormScreen(db, screen.visitId, screen.patientId, back)
        is Screen.ClinicSettings -> ClinicSettingsScreen(db, back)
        is Screen.RxForm -> PrescriptionScreen(db, screen.patientId, screen.visitId, back)
    }
}
