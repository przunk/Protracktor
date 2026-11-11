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
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.Appearance
import com.przunk.protracktor.R
import com.przunk.protracktor.ui.theme.ProtracktorTheme
import com.przunk.protracktor.ui.theme.enableAppEdgeToEdge

class AlarmsActivity : ComponentActivity() {
    private val model by lazy { androidx.lifecycle.ViewModelProvider(this, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application))[AlarmManagementViewModel::class.java] }
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(if (com.przunk.protracktor.alarms.api.MusicAlarms.enabled) AppLocale.wrap(newBase) else newBase)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { finish(); return }
        enableAppEdgeToEdge(Appearance.theme(this))
        setContent {
            ProtracktorTheme(theme = Appearance.theme(this), dynamicColour = Appearance.dynamicColour(this)) {
                val state by model.state.collectAsState()
                LaunchedEffect(model) { model.messages.collect {
                    android.widget.Toast.makeText(this@AlarmsActivity, it, android.widget.Toast.LENGTH_LONG).show()
                } }
                val access = com.przunk.protracktor.permissions.rememberAccessRequestCoordinator { _, result ->
                    if (result == com.przunk.protracktor.permissions.AccessRequestResult.UNAVAILABLE)
                        android.widget.Toast.makeText(this, R.string.alarm_failure, android.widget.Toast.LENGTH_LONG).show()
                    model.refresh()
                }
                val current = state
                if (current == null) Surface(Modifier.fillMaxSize()) { Box(Modifier.safeDrawingPadding().padding(24.dp)) {
                    Text(stringResource(R.string.alarm_loading))
                } } else {
                    val book = current.book
                    val readiness = current.readiness
                    val preparing by AlarmMusicPreparer.preparing.collectAsState()
                    val picks by model.music.picks.collectAsState()
                    AlarmManagement(book, current.schedule.armedAt, current.dataError,
                        preparing = preparing, onCancelPreparation = model::cancelPreparation,
                        sources = model.sources, picks = picks, onAddMusic = model.music::add,
                        onOpenRinging = {
                            startForegroundService(Intent(this, AlarmService::class.java))
                            startActivity(Intent(this, AlarmRingingActivity::class.java))
                        },
                        exact = readiness?.exact == true, notificationAccess = readiness?.notifications == true,
                        fullscreen = readiness?.fullScreen == true, muted = readiness?.muted == true,
                        usable = { it.id in current.usableIds }, onBack = ::finish,
                        onSave = model::save, onDelete = model::delete, onRestore = model::restore,
                        onRemoveMusic = { model.removeMusic(it) },
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
    override fun onResume() { super.onResume(); if (com.przunk.protracktor.alarms.api.MusicAlarms.enabled) model.refresh() }
}
