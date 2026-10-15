// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.util.Log
import androidx.core.app.ServiceCompat
import com.przunk.protracktor.R
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.MainActivity
import com.przunk.protracktor.data.DatabaseAccess
import com.przunk.protracktor.data.DatabaseAccessState
import com.przunk.protracktor.ui.problemText
import com.przunk.protracktor.player.PlaybackPresentation.ACTION_NEXT
import com.przunk.protracktor.player.PlaybackPresentation.ACTION_PREVIOUS
import com.przunk.protracktor.player.PlaybackPresentation.ACTION_PLAY_PAUSE
import com.przunk.protracktor.player.PlaybackPresentation.ACTION_STOP
import com.przunk.protracktor.player.PlaybackPresentation.Content
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
        // Version-only probe: do not run a migration or construct playback behind a blocked UI.
        val access = DatabaseAccess.check(this)
        if (access != DatabaseAccessState.Ready) {
            createChannel()
            val local = AppLocale.wrap(this)
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,
                Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_protracktor)
                    .setContentTitle(local.getString(R.string.database_unavailable_title))
                    .setContentText(access.problemText(local))
                    .setStyle(Notification.BigTextStyle().bigText(access.problemText(local)))
                    .setContentIntent(open).setAutoCancel(true).build())
            stopSelf()
            return
        }
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
                    } else if (everHadTrack && !content.playing && !content.loading) {
                        // **Only when nothing is happening** (`docs/STATUS.md` C43). A state with no
                        // current track is not the end of playback: it happens while a queue is
                        // replaced and when a session hands the playlist back, and stopping there
                        // took the transport away from music that was still playing. Nothing brings
                        // it back either, because a track ending and the next one starting is the
                        // controller's own doing and never passes through the transport buttons,
                        // which are what ask for this service.
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
        val created = PlaybackMediaSession.create(this)
        created.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = controller.togglePlayPauseTo(play = true)
            override fun onPause() = controller.pause()
            override fun onSkipToNext() = controller.next()
            override fun onSkipToPrevious() = controller.previous()
            override fun onSeekTo(pos: Long) = controller.seekTo(pos / 1000.0)
            override fun onCustomAction(action: String, extras: Bundle?) {
                if (PlaylistHeartActions.isHeartAction(action)) {
                    controller.savePlayingToPlaylist(PlaylistHeartActions.decode(action))
                }
            }
            override fun onStop() {
                controller.pause()
                stopForegroundAndSelf()
            }
        })
        created.isActive = true
        session = created
    }

    private fun publishSession(state: PlayerUiState) {
        session.setMetadata(PlaybackPresentation.metadata(this, state))
        session.setPlaybackState(PlaybackPresentation.playbackState(this, state))
    }

    private fun contentOf(state: PlayerUiState) = PlaybackPresentation.contentOf(this, state)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::controller.isInitialized) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.next()
            ACTION_PREVIOUS -> controller.previous()
            PlaylistHeartActions.ACTION -> controller.savePlayingToPlaylist(
                PlaylistHeartActions.decode(intent.dataString))
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
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun showNotification(content: Content) {
        val notification = buildNotification(content)
        if (!started) {
            // **Android may refuse, and refusing must not end the app** (`docs/STATUS.md` C72).
            // From Android 12 a service may enter the foreground only while its app is on screen,
            // and the check is made here, when the service is being created -- not when it was
            // asked for. A request made while the app was visible reaches this line a moment later,
            // and if the app has left the screen in between, the refusal arrives as an exception
            // thrown out of `onCreate`, which took the whole process down on a phone. The music
            // is in the engine, not in this service, so it plays on; what is lost is the
            // notification until the next press in the app asks for this service again.
            try {
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
            } catch (refused: IllegalStateException) {
                // `ForegroundServiceStartNotAllowedException` is an IllegalStateException, and
                // naming the parent keeps this compiling for API 29, which lacks the class.
                Log.w("Protracktor", "playback service: not allowed into the foreground", refused)
                stopSelf()
                return
            }
            started = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf() {
        // **Said out loud, because C43 had no reproduction.** If the transport goes missing again
        // while music plays, this line in the log is the difference between "the service stopped"
        // and "the notification was never posted" -- and they are different faults.
        Log.i("Protracktor", "playback service: leaving the foreground and stopping")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        started = false
        stopSelf()
    }

    private fun buildNotification(content: Content): Notification =
        PlaybackPresentation.notification(this, CHANNEL_ID, session.sessionToken, content)

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
    }
}
