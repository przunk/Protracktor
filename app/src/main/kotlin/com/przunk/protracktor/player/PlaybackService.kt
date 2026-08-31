/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.przunk.protracktor.MainActivity
import com.przunk.protracktor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps playback alive when the app is not on screen.
 *
 * Android stops caring about a process with no visible components; a foreground service is the only
 * way to say "this one is doing something the user asked for". The notification is not decoration —
 * it is the price of the service and the user's way to stop it.
 *
 * No `MediaSession` yet, so lock-screen and Bluetooth transport do not work. That is the next step
 * and it is written down in `docs/BACKLOG.md` rather than implied by this comment.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: PlaybackController
    private var started = false

    /**
     * Whether a track has ever been loaded since this service started.
     *
     * The service is started *before* playback begins -- it has to be, because a foreground service
     * can only be started while the app is in the foreground, and waiting until the user leaves is
     * waiting until it is no longer allowed. So at start-up there is legitimately nothing playing,
     * and stopping on that would kill the service a moment before it was needed. Only an empty state
     * that follows a non-empty one means playback is actually over.
     */
    private var everHadTrack = false

    override fun onCreate() {
        super.onCreate()
        controller = PlaybackController.get(this)
        createChannel()

        // Immediately, whatever the state. Android gives a started service about five seconds to
        // call startForeground and kills it otherwise, and the track is still being read off disk.
        showNotification(contentOf(controller.state.value))

        scope.launch {
            controller.state
                .map { contentOf(it) }
                .distinctUntilChanged()
                .collect { content ->
                    if (content.title != null) {
                        everHadTrack = true
                        showNotification(content)
                    } else if (everHadTrack) {
                        stopForegroundAndSelf()
                    }
                }
        }
    }

    private fun contentOf(state: PlayerUiState) =
        NotificationContent(state.current?.title, state.current?.subtitle, state.playing)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.next()
            ACTION_PREVIOUS -> controller.previous()
            ACTION_STOP -> {
                controller.pause()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
        }

        showNotification(contentOf(controller.state.value))
        // NOT sticky: a restart by the system would bring back a service with nothing playing and
        // no way to know what should be.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class NotificationContent(
        val title: String?,
        val subtitle: String?,
        val playing: Boolean,
    )

    private fun showNotification(content: NotificationContent) {
        val notification = buildNotification(content)
        if (!started) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
            started = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        started = false
        stopSelf()
    }

    private fun buildNotification(content: NotificationContent): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(content.title ?: getString(R.string.dock_idle_title))
            .setContentText(content.subtitle.orEmpty())
            .setContentIntent(open)
            .setOngoing(content.playing)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.a11y_previous),
                command(ACTION_PREVIOUS),
            )
            .addAction(
                if (content.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(if (content.playing) R.string.a11y_pause else R.string.a11y_play),
                command(ACTION_PLAY_PAUSE),
            )
            .addAction(
                android.R.drawable.ic_media_next,
                getString(R.string.a11y_next),
                command(ACTION_NEXT),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_stop),
                command(ACTION_STOP),
            )
            .build()
    }

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_playback),
            // Low: a player that pings and vibrates every track change is a player people turn off.
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY_PAUSE = "com.przunk.protracktor.PLAY_PAUSE"
        const val ACTION_NEXT = "com.przunk.protracktor.NEXT"
        const val ACTION_PREVIOUS = "com.przunk.protracktor.PREVIOUS"
        const val ACTION_STOP = "com.przunk.protracktor.STOP"
    }
}
