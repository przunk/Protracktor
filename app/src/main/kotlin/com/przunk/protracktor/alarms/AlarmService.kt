// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.przunk.protracktor.player.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingCommands = 0
    private var coordinator: AlarmCoordinator? = null
    private var observer: Job? = null
    private var deadline: Job? = null
    private var sound: AlarmSound? = null
    private var announcedToken: String? = null
    private var audioKey: Pair<String, Boolean>? = null
    private var wake: PowerManager.WakeLock? = null
    companion object {
        private val audio = kotlinx.coroutines.flow.MutableStateFlow(AlarmAudioStatus.STOPPED)
        internal val audioStatus: kotlinx.coroutines.flow.StateFlow<AlarmAudioStatus> = audio
        @Volatile var running: Boolean = false
            private set
    }
    override fun onCreate() {
        super.onCreate()
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { stopSelf(); return }
        AlarmNotifications.channel(this)
        startForeground(AlarmPlatform.STARTUP_NOTIFICATION, AlarmNotifications.ringing(this, null))
        running = true
        audio.value = AlarmAudioStatus.PREPARING
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { stopSelf(); return START_NOT_STICKY }
        pendingCommands++
        scope.launch {
            try {
                val control = withContext(Dispatchers.IO) {
                    AlarmCoordinator.from(this@AlarmService).also {
                        when (intent?.action) {
                            AlarmPlatform.FIRE -> it.deliver(intent.getLongExtra("revision", -1), intent.getLongExtra("at", -1))
                            AlarmPlatform.TEST -> {
                                val id = intent.getStringExtra("id").orEmpty()
                                val alarm = it.repository.book.alarms.firstOrNull { a -> a.id == id }
                                val songs = alarm?.songIds?.toSet() ?: setOf(id)
                                it.preview(id, songs)
                            }
                            else -> it.reconcile()
                        }
                    }
                }
                check(control.readFailure == null) { "Alarm data is unavailable" }
                coordinator = control
                if (observer == null) observer = scope.launch {
                    control.repository.state.collect { book -> update(book.ringing) }
                }
            } catch (failure: Exception) {
                android.util.Log.w("Protracktor", "Alarm could not ring", failure)
            } finally {
                pendingCommands--
                if (pendingCommands == 0) {
                    val active = coordinator?.repository?.book?.ringing
                    update(active)
                }
            }
        }
        return START_STICKY
    }
    private fun update(active: RingingAlarm?) {
        try { updateResources(active) }
        catch (failure: Exception) {
            android.util.Log.w("Protracktor", "Alarm resources are unavailable", failure)
            silence()
            active?.let { session -> scope.launch(Dispatchers.IO) {
                runCatching { coordinator?.failed(session.token) }
            } }
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    private fun updateResources(active: RingingAlarm?) {
        if (active == null) {
            if (pendingCommands > 0) return
            sound?.close(); sound = null; audioKey = null
            deadline?.cancel(); wake?.let { if (it.isHeld) it.release() }; wake = null
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val now = AlarmPlatform.clock(this)
        if (active.expired(now.elapsedMillis, now.bootCount) || !AlarmReadiness.notifications(this)) {
            silence()
            scope.launch(Dispatchers.IO) { runCatching { coordinator?.reconcile() } }
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val manager = getSystemService(android.app.NotificationManager::class.java)
        val notification = AlarmNotifications.ringing(this, active, announce = announcedToken != active.token)
        if (announcedToken != active.token) {
            // FSI is evaluated for a newly posted notification, not the earlier loading update.
            if (announcedToken != null) stopForeground(STOP_FOREGROUND_REMOVE)
            startForeground(AlarmPlatform.NOTIFICATION, notification)
            manager.cancel(AlarmPlatform.STARTUP_NOTIFICATION)
        } else manager.notify(AlarmPlatform.NOTIFICATION, notification)
        announcedToken = active.token
        if (audioKey == active.token to active.backup) return
        sound?.close(); deadline?.cancel()
        audioKey = active.token to active.backup
        PlaybackController.pauseExistingForAlarm()
        wake?.let { if (it.isHeld) it.release() }
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:music-alarm").apply {
                acquire((active.deadlineElapsed - SystemClock.elapsedRealtime()).coerceIn(1, AlarmLimits.RINGING_LIMIT_MS) + 2000)
            }
        deadline = scope.launch {
            delay((active.deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            // The hard audible limit does not depend on a successful storage write.
            if (audioKey?.first == active.token) silence()
            try { withContext(Dispatchers.IO) { coordinator?.reconcile() } }
            catch (failure: Exception) { android.util.Log.w("Protracktor", "Alarm timeout could not be saved", failure) }
            finally {
                if (coordinator?.repository?.book?.ringing?.token in listOf(null, active.token)) {
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        sound = AlarmSound(this, status = { audio.value = it }) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (active.backup) coordinator?.failed(active.token) else coordinator?.backup(active.token)
                } catch (failure: Exception) {
                    android.util.Log.w("Protracktor", "Alarm audio failure could not be saved", failure)
                    withContext(Dispatchers.Main) { silence(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                }
            }
        }.also { it.start(coordinator?.repository?.files?.audioFile(active.song.id), active.backup) }
    }
    private fun silence() {
        sound?.close(); sound = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
    }
    override fun onDestroy() {
        running = false
        silence()
        audio.value = AlarmAudioStatus.STOPPED
        scope.cancel(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
