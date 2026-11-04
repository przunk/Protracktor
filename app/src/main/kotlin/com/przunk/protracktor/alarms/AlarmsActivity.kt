// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.R
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import com.przunk.protracktor.ui.theme.enableAppEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmsActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(if (com.przunk.protracktor.alarms.api.MusicAlarms.enabled) AppLocale.wrap(newBase) else newBase)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { finish(); return }
        enableAppEdgeToEdge(Appearance.theme(this))
        setContent {
            ProtracktorTheme(theme = Appearance.theme(this), dynamicColour = Appearance.dynamicColour(this)) {
                var coordinator by remember { mutableStateOf<AlarmCoordinator?>(null) }
                LaunchedEffect(refresh) {
                    coordinator = withContext(Dispatchers.IO) {
                        AlarmNotifications.channel(this@AlarmsActivity)
                        AlarmCoordinator.from(this@AlarmsActivity).also { runCatching { it.reconcile() } }
                    }
                }
                val access = com.przunk.protracktor.permissions.rememberAccessRequestCoordinator { _, result ->
                    if (result == com.przunk.protracktor.permissions.AccessRequestResult.UNAVAILABLE)
                        android.widget.Toast.makeText(this, R.string.alarm_failure, android.widget.Toast.LENGTH_LONG).show()
                    refresh++
                }
                val control = coordinator
                if (control == null) Surface(Modifier.fillMaxSize()) { Box(Modifier.safeDrawingPadding().padding(24.dp)) {
                    Text(stringResource(R.string.alarm_loading))
                } } else {
                    val book by control.repository.state.collectAsState()
                    val schedule by control.scheduling.collectAsState()
                    val readiness = AlarmReadiness.inspect(this)
                    val preparing by AlarmMusicPreparer.preparing.collectAsState()
                    AlarmManagement(book, schedule.armedAt, control.readFailure != null,
                        preparing = preparing, onCancelPreparation = AlarmMusicPreparer::cancel,
                        onOpenRinging = {
                            startForegroundService(Intent(this, AlarmService::class.java))
                            startActivity(Intent(this, AlarmRingingActivity::class.java))
                        },
                        exact = readiness.exact, notificationAccess = readiness.notifications,
                        fullscreen = readiness.fullScreen, muted = readiness.muted,
                        usable = control::usable, onBack = ::finish,
                        onSave = { alarm -> command { control.save(alarm) } },
                        onDelete = { id ->
                            val result = withContext(Dispatchers.IO) { runCatching { control.deleteAlarm(id) } }
                            if (result.isFailure) android.widget.Toast.makeText(this, R.string.alarm_failure, android.widget.Toast.LENGTH_LONG).show()
                            result.isSuccess
                        }, onRestore = { alarm -> command { control.restoreAlarm(alarm) } },
                        onRemoveMusic = { id -> command { control.deleteSong(id) } },
                        onTest = { id ->
                            startForegroundService(Intent(this, AlarmService::class.java).setAction(AlarmPlatform.TEST).putExtra("id", id))
                            startActivity(Intent(this, AlarmRingingActivity::class.java))
                        }, onAccess = { remedy ->
                            when (remedy) {
                                AlarmAccess.EXACT -> access.request(com.przunk.protracktor.permissions.AppAccess.EXACT_ALARMS)
                                AlarmAccess.FULL_SCREEN -> access.request(com.przunk.protracktor.permissions.AppAccess.FULL_SCREEN_ALARMS)
                                AlarmAccess.NOTIFICATIONS -> {
                                    val notifications = com.przunk.protracktor.permissions.AppAccess.NOTIFICATIONS
                                    if (com.przunk.protracktor.permissions.SystemAccess.allowed(this, notifications))
                                        access.openSettings(notifications, AlarmReadiness.settings(this, remedy))
                                    else access.request(notifications)
                                }
                                AlarmAccess.VOLUME -> runCatching { startActivity(AlarmReadiness.settings(this, remedy)) }
                                    .onFailure { android.widget.Toast.makeText(this, R.string.alarm_failure, android.widget.Toast.LENGTH_LONG).show() }.let { }
                            }
                        })
                }
            }
        }
    }
    private fun command(block: () -> Unit) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            if (result.isFailure) android.widget.Toast.makeText(this@AlarmsActivity, R.string.alarm_failure, android.widget.Toast.LENGTH_LONG).show()
            refresh++
        }
    }
    override fun onResume() { super.onResume(); refresh++ }
}
