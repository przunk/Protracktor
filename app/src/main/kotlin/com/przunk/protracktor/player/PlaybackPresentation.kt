// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import com.przunk.protracktor.AppLocale
import com.przunk.protracktor.MainActivity
import com.przunk.protracktor.R

/** Production presentation shared by the service and host checks, without starting a decoder. */
internal object PlaybackPresentation {
    const val ACTION_PLAY_PAUSE = "com.przunk.protracktor.PLAY_PAUSE"
    const val ACTION_NEXT = "com.przunk.protracktor.NEXT"
    const val ACTION_PREVIOUS = "com.przunk.protracktor.PREVIOUS"
    const val ACTION_STOP = "com.przunk.protracktor.STOP"

    data class Content(
        val title: String?, val subtitle: String, val fileName: String, val id: String,
        val heart: PlaylistHeart?, val playing: Boolean, val loading: Boolean,
    )

    fun contentOf(context: Context, state: PlayerUiState): Content {
        val local = AppLocale.wrap(context)
        return Content(state.current?.title,
            NowPlayingText.subtitle(state, local.getString(R.string.dock_loading),
                local.getString(R.string.dock_idle_subtitle)),
            state.current?.fileName.orEmpty(), state.current?.id.orEmpty(),
            PlaylistHeart.of(state), state.playing, state.loadingTrack)
    }

    private fun bold(title: String): CharSequence = SpannableString(title).apply {
        if (isNotEmpty()) setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun metadata(context: Context, state: PlayerUiState): MediaMetadata {
        val content = contentOf(context, state)
        val title = bold(content.title.orEmpty())
        return MediaMetadata.Builder()
            .putText(MediaMetadata.METADATA_KEY_TITLE, title)
            .putText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, content.subtitle)
            // Some system players read ARTIST rather than DISPLAY_SUBTITLE. Both must show the
            // same dock summary; the application's original artist metadata stays unchanged.
            .putString(MediaMetadata.METADATA_KEY_ARTIST, content.subtitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, state.current?.subtitle.orEmpty())
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (state.durationSeconds * 1000).toLong())
            .apply { state.current?.let {
                putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,
                    TrackArtworkBitmaps.forTrack(it.fileName, it.title, it.id))
            } }
            .build()
    }

    fun playbackState(context: Context, state: PlayerUiState): PlaybackState {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        // These include Random/results transport, unlike the selected playlist's cursor.
        if (state.canGoNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (state.canGoPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (state.seekable) actions = actions or PlaybackState.ACTION_SEEK_TO
        return PlaybackState.Builder().setActions(actions)
            .apply { PlaylistHeartActions.customAction(context, PlaylistHeart.of(state))?.let { addCustomAction(it) } }
            .setState(if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (state.positionSeconds * 1000).toLong(), if (state.playing) 1f else 0f)
            .build()
    }

    fun notification(context: Context, channel: String, token: MediaSession.Token, content: Content): Notification {
        val local = AppLocale.wrap(context)
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        fun command(action: String) = PendingIntent.getService(context, action.hashCode(),
            Intent(context, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun transport(icon: Int, label: Int, action: String) = Notification.Action.Builder(
            Icon.createWithResource(context, icon), local.getString(label), command(action)).build()
        return Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_protracktor)
            .setContentTitle(bold(content.title ?: local.getString(R.string.dock_idle_title)))
            .setContentText(content.subtitle)
            .setLargeIcon(content.title?.let { TrackArtworkBitmaps.forTrack(content.fileName, it, content.id) })
            // MediaStyle derives its palette from the existing artwork; keep that behaviour.
            .setColorized(true)
            .setContentIntent(open).setOngoing(content.playing).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(Notification.MediaStyle().setMediaSession(token).setShowActionsInCompactView(0, 1, 2))
            .addAction(transport(android.R.drawable.ic_media_previous, R.string.a11y_previous, ACTION_PREVIOUS))
            .addAction(transport(if (content.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (content.playing) R.string.a11y_pause else R.string.a11y_play, ACTION_PLAY_PAUSE))
            .addAction(transport(android.R.drawable.ic_media_next, R.string.a11y_next, ACTION_NEXT))
            .apply { PlaylistHeartActions.notificationAction(context, content.heart)?.let { addAction(it) } }
            .addAction(transport(android.R.drawable.ic_menu_close_clear_cancel, R.string.notification_stop, ACTION_STOP))
            .build()
    }
}
