// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.PlaybackState
import android.net.Uri
import com.przunk.protracktor.R
import com.przunk.protracktor.AppLocale

/** Both surfaces carry immutable track/playlist identity, never retargeted extras. */
internal object PlaylistHeartActions {
    const val ACTION = "com.przunk.protracktor.SAVE_PLAYING_TO_PLAYLIST"
    private const val SCHEME = "protracktor-playlist-heart"

    fun encode(request: PlaylistHeart.Request): String = Uri.Builder()
        .scheme(SCHEME).authority("save").appendPath(request.playlistId.toString())
        .appendPath(request.trackId).build().toString()

    fun decode(encoded: String?): PlaylistHeart.Request? {
        if (encoded == null) return null
        val uri = Uri.parse(encoded)
        if (uri.scheme != SCHEME || uri.authority != "save" || uri.pathSegments.size != 2) return null
        val playlist = uri.pathSegments[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val track = uri.pathSegments[1].takeIf { it.isNotBlank() } ?: return null
        return PlaylistHeart.Request(track, playlist)
    }

    fun isHeartAction(action: String?) = action?.startsWith("$SCHEME:") == true

    private fun icon(heart: PlaylistHeart) =
        if (heart.member) R.drawable.ic_playlist_heart_filled else R.drawable.ic_playlist_heart_outline

    private fun label(context: Context, heart: PlaylistHeart): String = AppLocale.wrap(context).getString(when {
        heart.saving -> R.string.notification_playlist_saving
        heart.member -> R.string.notification_playlist_member
        else -> R.string.notification_playlist_add
    }, heart.playlistName)

    fun feedback(context: Context, outcome: PlaylistHeartSave.Outcome, heart: PlaylistHeart?): String =
        AppLocale.wrap(context).getString(when (outcome) {
            PlaylistHeartSave.Outcome.ADDED_AND_SAVED -> R.string.notification_playlist_added_saved
            PlaylistHeartSave.Outcome.ALREADY_AND_SAVED -> R.string.notification_playlist_already_saved
            PlaylistHeartSave.Outcome.STALE -> R.string.notification_playlist_stale
            PlaylistHeartSave.Outcome.FAILED -> R.string.notification_playlist_failed
            PlaylistHeartSave.Outcome.SAVING -> R.string.notification_playlist_saving_notice
        }, heart?.playlistName.orEmpty())

    fun customAction(context: Context, heart: PlaylistHeart?): PlaybackState.CustomAction? {
        if (heart == null || heart.saving) return null
        // Some controllers drop extras; identity in the action still binds the press.
        return PlaybackState.CustomAction.Builder(encode(heart.request), label(context, heart), icon(heart)).build()
    }

    fun notificationAction(context: Context, heart: PlaylistHeart?): Notification.Action? {
        if (heart == null) return null
        val command = if (heart.saving) null else PendingIntent.getService(
            context, ACTION.hashCode(),
            Intent(context, PlaybackService::class.java).setAction(ACTION)
                .setData(Uri.parse(encode(heart.request))),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Intent.data distinguishes old and new buttons; UPDATE_CURRENT would retarget stale ones.
        return Notification.Action.Builder(Icon.createWithResource(context, icon(heart)),
            label(context, heart), command).build()
    }
}
