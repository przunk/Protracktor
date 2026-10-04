// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import com.przunk.protracktor.R

internal class AndroidPlaylistText(private val context: Context) {
    fun message(notice: PlaylistTransfer.Notice): Message = Message(when (notice) {
        PlaylistTransfer.Notice.EmptyPlaylist -> context.getString(R.string.notice_playlist_empty)
        PlaylistTransfer.Notice.ExportFailed -> context.getString(R.string.notice_playlist_failed)
        PlaylistTransfer.Notice.UnreadableFile -> context.getString(R.string.notice_file_unreadable)
        PlaylistTransfer.Notice.NoTracks -> context.getString(R.string.notice_file_no_tracks)
        is PlaylistTransfer.Notice.Imported -> if (notice.found == notice.offered) {
            context.resources.getQuantityString(
                R.plurals.notice_imported, notice.found, notice.found, notice.name,
            )
        } else {
            // Said, not swallowed. A playlist that silently arrived shorter than
            // the file it came from is worse than one that explains itself.
            context.getString(R.string.notice_imported_partial, notice.found, notice.offered, notice.name)
        }
    })
}
