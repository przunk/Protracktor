// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.przunk.protracktor.alarms.api.MusicAlarms
import kotlinx.coroutines.CancellationException
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
    private var closed = false
    private var lastStartId = 0
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
        if (closed) return START_NOT_STICKY
        lastStartId = startId
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
                if (closed) return@launch
                check(control.readFailure == null) { "Alarm data is unavailable" }
                coordinator = control
                if (observer == null) observer = scope.launch {
                    control.repository.state.collect { book -> update(book.ringing) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                android.util.Log.w("Protracktor", "Alarm could not ring", failure)
            } finally {
                pendingCommands--
                if (!closed && pendingCommands == 0) {
                    val active = coordinator?.repository?.book?.ringing
                    update(active)
                }
            }
        }
        return START_STICKY
    }
    private fun update(active: RingingAlarm?) {
        if (closed) return
        try { updateResources(active) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            android.util.Log.w("Protracktor", "Alarm resources are unavailable", failure)
            silence()
            active?.let { session -> scope.launch(Dispatchers.IO) {
                try { coordinator?.failed(session.token) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { android.util.Log.w("Protracktor", "Alarm resource failure could not be saved", failure) }
            } }
            finishStart()
        }
    }
    private fun updateResources(active: RingingAlarm?) {
        if (active == null) {
            if (pendingCommands > 0) return
            silence(); audioKey = null
            deadline?.cancel(); deadline = null
            finishStart(); return
        }
        val now = AlarmPlatform.clock(this)
        if (active.expired(now.elapsedMillis, now.bootCount) || !AlarmReadiness.notifications(this)) {
            silence()
            scope.launch(Dispatchers.IO) {
                try { coordinator?.reconcile() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { android.util.Log.w("Protracktor", "Alarm expiry could not be saved", failure) }
            }
            finishStart(); return
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
        MusicAlarms.interruptExistingPlayback()
        wake?.let { if (it.isHeld) it.release() }
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:music-alarm").apply {
                acquire((active.deadlineElapsed - SystemClock.elapsedRealtime()).coerceIn(1, AlarmLimits.RINGING_LIMIT_MS) + 2000)
            }
        deadline = scope.launch {
            delay((active.deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            // The hard audible limit does not depend on a successful storage write.
            if (!owns(active.token)) return@launch
            silence()
            try { withContext(Dispatchers.IO) { coordinator?.reconcile() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("Protracktor", "Alarm timeout could not be saved", failure) }
            finally {
                if (owns(active.token) && coordinator?.repository?.book?.ringing?.token in listOf(null, active.token)) finishStart()
            }
        }
        sound = AlarmSound(this, status = { if (owns(active.token, active.backup)) audio.value = it }) {
            if (owns(active.token, active.backup)) scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (active.backup) coordinator?.failed(active.token) else coordinator?.backup(active.token)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    android.util.Log.w("Protracktor", "Alarm audio failure could not be saved", failure)
                    if (owns(active.token, active.backup)) { silence(); finishStart() }
                }
            }
        }.also { it.start(coordinator?.repository?.files?.audioFile(active.song.id), active.backup) }
    }
    private fun owns(token: String) = !closed && audioKey?.first == token
    private fun owns(token: String, backup: Boolean) = !closed && audioKey == token to backup
    private fun finishStart() {
        if (closed) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(AlarmPlatform.STARTUP_NOTIFICATION); manager.cancel(AlarmPlatform.NOTIFICATION)
        stopSelfResult(lastStartId)
    }
    private fun silence() {
        sound?.close(); sound = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
    }
    override fun onDestroy() {
        if (!closed) {
            closed = true
            running = false
            scope.cancel()
            deadline?.cancel(); deadline = null
            observer?.cancel(); observer = null
            silence(); audioKey = null
            audio.value = AlarmAudioStatus.STOPPED
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.cancel(AlarmPlatform.STARTUP_NOTIFICATION); manager.cancel(AlarmPlatform.NOTIFICATION)
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
