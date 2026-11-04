// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.os.Bundle
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.AppTheme
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import com.przunk.protracktor.ui.theme.enableAppEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmRingingActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val unlocked = newBase.getSystemService(UserManager::class.java).isUserUnlocked
        super.attachBaseContext(if (unlocked) AppLocale.wrap(newBase) else newBase.createDeviceProtectedStorageContext())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        val theme = if (unlocked) Appearance.theme(this) else AppTheme.SYSTEM
        enableAppEdgeToEdge(theme)
        setContent {
            ProtracktorTheme(theme = theme, dynamicColour = unlocked && Appearance.dynamicColour(this)) {
                val scope = rememberCoroutineScope()
                var coordinator by remember { mutableStateOf<AlarmCoordinator?>(null) }
                var waiting by remember { mutableStateOf(true) }
                var failed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    coordinator = withContext(Dispatchers.IO) {
                        AlarmCoordinator.from(this@AlarmRingingActivity).also { runCatching { it.reconcile() } }
                    }
                    failed = coordinator?.repository?.readFailure != null
                    if (coordinator?.repository?.book?.ringing != null && !AlarmService.running) {
                        runCatching { startForegroundService(android.content.Intent(this@AlarmRingingActivity, AlarmService::class.java)) }
                            .onFailure { failed = true }
                    }
                    delay(2500); waiting = false
                }
                val audio by AlarmService.audioStatus.collectAsState()
                val book = coordinator?.repository?.state?.collectAsState()?.value
                AlarmRingingScreen(book?.ringing, waiting, failed,
                    onAnswer = { token, choice ->
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { coordinator?.answer(token, choice) } }
                            if (result.isFailure) android.widget.Toast.makeText(this@AlarmRingingActivity,
                                com.przunk.protracktor.R.string.alarm_failure, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }, onStop = { token ->
                        // Deliberate UI escape only; swiping a notification never sends this command.
                        sendBroadcast(android.content.Intent(this, AlarmReceiver::class.java).setAction(AlarmPlatform.STOP).putExtra("token", token))
                    },
                    onClose = {
                        if (failed || coordinator?.repository?.book?.ringing == null) finish()
                    }, audioStatus = audio)
            }
        }
    }
}
