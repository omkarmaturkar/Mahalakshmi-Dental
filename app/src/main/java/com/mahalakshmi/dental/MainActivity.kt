package com.mahalakshmi.dental

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private lateinit var db: ClinicDb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        db = ClinicDb(applicationContext)
        setContent { ClinicTheme { ClinicApp(db) } }
    }

    // Whenever the app goes to the background, new data is copied to the backup folder
    override fun onStop() {
        super.onStop()
        val ctx = applicationContext
        Thread { Backup.autoBackup(ctx, db) }.start()
    }
}

sealed class Screen {
    object Home : Screen()
    object BackupPage : Screen()
    data class PatientForm(val patientId: Long = 0) : Screen()
    data class PatientDetail(val patientId: Long) : Screen()
    data class VisitForm(val patientId: Long, val visitId: Long = 0) : Screen()
}

data class NavEntry(val depth: Int, val screen: Screen)

@Composable
fun ClinicApp(db: ClinicDb) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    val current = NavEntry(stack.size, stack.last())
    StatusBarIcons(dark = current.screen !is Screen.Home && current.screen !is Screen.PatientDetail)

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            if (targetState.depth >= initialState.depth) {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it / 6 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(320)) { -it / 6 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(220)))
            }
        },
        label = "navigation"
    ) { entry ->
        Box(Modifier.fillMaxSize().background(Brand.Bg)) {
            when (val s = entry.screen) {
                is Screen.Home -> HomeScreen(db, go)
                is Screen.BackupPage -> BackupScreen(db, back) { stack.clear(); stack.add(Screen.Home) }
                is Screen.PatientForm -> PatientFormScreen(db, s.patientId, back) { newId, isNew ->
                    stack.removeAt(stack.lastIndex)
                    if (isNew) {
                        stack.add(Screen.PatientDetail(newId))
                        stack.add(Screen.VisitForm(newId))
                    }
                }
                is Screen.PatientDetail -> PatientDetailScreen(db, s.patientId, go, back)
                is Screen.VisitForm -> VisitFormScreen(db, s.visitId, s.patientId, back)
            }
        }
    }
}
