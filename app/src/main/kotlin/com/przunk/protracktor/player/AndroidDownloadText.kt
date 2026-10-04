// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import com.przunk.protracktor.R

internal class AndroidDownloadText(private val context: Context) : DownloadText {
    override fun label(label: DownloadText.Label): String = context.getString(when (label) {
        DownloadText.Label.SONG_METADATA -> R.string.song_metadata_title
        DownloadText.Label.ALL_RUNNING -> R.string.download_all_running
    })

    override fun message(notice: DownloadNotice): Message = Message(when (notice) {
        is DownloadNotice.IndexDownloadFailed ->
            context.getString(R.string.notice_index_download_failed, notice.name)
        is DownloadNotice.ArchiveStoreFailed ->
            context.getString(R.string.notice_archive_store_failed, notice.name)
        is DownloadNotice.Indexed -> if (notice.playable >= notice.listed) {
            context.resources.getQuantityString(
                R.plurals.notice_indexed_all, notice.listed, notice.listed, notice.name,
            )
        } else {
            context.resources.getQuantityString(
                R.plurals.notice_indexed_partly, notice.listed, notice.listed, notice.name, notice.playable,
            )
        }
        DownloadNotice.SongLengthsFailed -> context.getString(R.string.notice_song_lengths_failed)
        DownloadNotice.SongLengthsEmpty -> context.getString(R.string.notice_song_lengths_empty)
        is DownloadNotice.SongLengthsDone -> context.resources.getQuantityString(
            R.plurals.notice_song_lengths_done, notice.count, notice.count,
        )
        DownloadNotice.TrackMetadataFailed -> context.getString(R.string.notice_track_metadata_failed)
        DownloadNotice.TrackMetadataEmpty -> context.getString(R.string.notice_track_metadata_empty)
        DownloadNotice.SongDbLengthsFailed -> context.getString(R.string.notice_songdb_lengths_failed)
        is DownloadNotice.TrackMetadataDone -> context.resources.getQuantityString(
            R.plurals.notice_track_metadata_done, notice.count, notice.count,
        )
        DownloadNotice.FavouritesFailed -> context.getString(R.string.notice_favourites_failed)
        DownloadNotice.FavouritesEmpty -> context.getString(R.string.notice_favourites_empty)
        is DownloadNotice.FavouritesDone -> if (notice.playable > 0) {
            context.getString(R.string.notice_favourites_playable, notice.playable, notice.listed)
        } else {
            // The list arrived and reaches nothing. Said plainly, because the alternative
            // is a Favourites chip that stays disabled after a download that reported
            // success.
            context.getString(R.string.notice_favourites_need_index, notice.listed)
        }
        DownloadNotice.SongMetadataDone -> context.getString(R.string.notice_song_metadata_done)
        is DownloadNotice.AllDownloaded -> if (notice.failed == 0) {
            context.getString(R.string.notice_all_downloaded)
        } else {
            context.getString(R.string.notice_all_downloaded_partly, notice.landed, notice.failed)
        }
        is DownloadNotice.DownloadsStopped -> context.getString(R.string.notice_downloads_stopped, notice.landed)
    })
}
