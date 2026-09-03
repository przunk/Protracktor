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
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
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
 * It also owns the `MediaSession`, which is what makes the lock screen, Bluetooth and the button on
 * a pair of headphones work. The platform session rather than Media3's: with `minSdk 29` the
 * framework API is available directly, and it costs no dependency and no adapter layer over a
 * player that is already ours. `docs/ARCHITECTURE.md` §12 records the change of plan.
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

    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        controller = PlaybackController.get(this)
        createChannel()
        createSession()

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

        // Position moves without the track changing, so the session needs its own subscription --
        // the notification's would rebuild the whole thing five times a second for nothing.
        scope.launch {
            controller.state.collect { publishSession(it) }
        }
    }

    private fun createSession() {
        // Deliberately not written with apply(). MediaSession has its own getController(), so inside
        // an apply block the name `controller` silently stops meaning ours and starts meaning the
        // session's MediaController -- which compiles far enough to be confusing and would have been
        // very hard to see. Plain assignment keeps the name meaning what it says.
        val created = MediaSession(this, "Protracktor")
        created.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = controller.togglePlayPauseTo(play = true)
            override fun onPause() = controller.pause()
            override fun onSkipToNext() = controller.next()
            override fun onSkipToPrevious() = controller.previous()
            override fun onSeekTo(pos: Long) = controller.seekTo(pos / 1000.0)
            override fun onStop() {
                controller.pause()
                stopForegroundAndSelf()
            }
        })
        created.isActive = true
        session = created
    }

    private fun publishSession(state: PlayerUiState) {
        val track = state.current
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.metadata["artist"].orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track?.subtitle.orEmpty())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, (state.durationSeconds * 1000).toLong())
                .build()
        )

        var actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP
        // `canGoNext`, not `queue.hasNext`. The queue is the playlist, and next does not always
        // walk the playlist: in Random it walks the picks, and in a search it walks the results.
        // Asking the queue meant the notification hid its skip buttons in exactly the mode where
        // the dock was showing them -- the owner found it in Random, and search had it too.
        //
        // Since Android 13 the system builds these buttons from the session's PlaybackState rather
        // than from the notification's own actions, so this line is what decides whether they
        // exist. The `Notification.Action`s below were always added and were never the problem.
        if (state.canGoNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (state.canGoPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        // Advertised only when the backend can honour it. sc68 emulates a 68000 and cannot seek;
        // a lock screen offering a scrubber that does nothing is the same lie as an app that does.
        if (state.seekable) actions = actions or PlaybackState.ACTION_SEEK_TO

        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    (state.positionSeconds * 1000).toLong(),
                    if (state.playing) 1f else 0f,
                )
                .build()
        )
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
        session.isActive = false
        session.release()
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

        // The platform builder rather than NotificationCompat, because MediaStyle has to be handed
        // the platform session token and minSdk 29 means there is nothing to be compatible with.
        // MediaStyle is what turns a notification with buttons into transport the lock screen and
        // Android Auto understand.
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(content.title ?: getString(R.string.dock_idle_title))
            .setContentText(content.subtitle.orEmpty())
            .setContentIntent(open)
            .setOngoing(content.playing)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    // Which buttons survive the collapsed notification: previous, play/pause, next.
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                    getString(R.string.a11y_previous),
                    command(ACTION_PREVIOUS),
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        this,
                        if (content.playing) android.R.drawable.ic_media_pause
                        else android.R.drawable.ic_media_play,
                    ),
                    getString(if (content.playing) R.string.a11y_pause else R.string.a11y_play),
                    command(ACTION_PLAY_PAUSE),
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_next),
                    getString(R.string.a11y_next),
                    command(ACTION_NEXT),
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.notification_stop),
                    command(ACTION_STOP),
                ).build()
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
