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

class AlarmRingingActivity : ComponentActivity() {
    private val model by lazy { androidx.lifecycle.ViewModelProvider(this, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application))[AlarmRingingViewModel::class.java] }
    override fun attachBaseContext(newBase: Context) {
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { super.attachBaseContext(newBase); return }
        val unlocked = newBase.getSystemService(UserManager::class.java).isUserUnlocked
        super.attachBaseContext(if (unlocked) AppLocale.wrap(newBase) else newBase.createDeviceProtectedStorageContext())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { finish(); return }
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        val theme = if (unlocked) Appearance.theme(this) else AppTheme.SYSTEM
        enableAppEdgeToEdge(theme)
        setContent {
            ProtracktorTheme(theme = theme, dynamicColour = unlocked && Appearance.dynamicColour(this)) {
                val state by model.state.collectAsState()
                LaunchedEffect(state.active?.token) {
                    if (state.active != null && !AlarmService.running) {
                        try { startForegroundService(android.content.Intent(this@AlarmRingingActivity, AlarmService::class.java)) }
                        catch (failure: RuntimeException) { model.serviceFailed(failure) }
                    }
                }
                val audio by AlarmService.audioStatus.collectAsState()
                AlarmRingingScreen(state.active, state.waiting, state.failed,
                    onAnswer = model::answer, onStop = model::stop, onClose = ::finish, audioStatus = audio,
                    busy = state.busy, completion = state.completion, wrongChoice = state.wrongChoice,
                    commandFailed = state.commandFailed)
            }
        }
    }
}
