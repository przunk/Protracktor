// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

internal sealed interface DownloadNotice {
    data class IndexDownloadFailed(val name: String) : DownloadNotice
    data class ArchiveStoreFailed(val name: String) : DownloadNotice
    data class Indexed(val name: String, val listed: Int, val playable: Int) : DownloadNotice
    data object SongLengthsFailed : DownloadNotice
    data object SongLengthsEmpty : DownloadNotice
    data class SongLengthsDone(val count: Int) : DownloadNotice
    data object TrackMetadataFailed : DownloadNotice
    data object TrackMetadataEmpty : DownloadNotice
    data object SongDbLengthsFailed : DownloadNotice
    data class TrackMetadataDone(val count: Int) : DownloadNotice
    data object FavouritesFailed : DownloadNotice
    data object FavouritesEmpty : DownloadNotice
    data class FavouritesDone(val playable: Int, val listed: Int) : DownloadNotice
    data object SongMetadataDone : DownloadNotice
    data class AllDownloaded(val landed: Int, val failed: Int) : DownloadNotice
    data class DownloadsStopped(val landed: Int) : DownloadNotice
    data class ReplaysDownloaded(val sc68: Int?, val uade: Int?) : DownloadNotice
}

/** Formats a notice when the workflow creates it, including notices retained for a later step. */
internal interface DownloadText {
    enum class Label { SONG_METADATA, ALL_RUNNING, REPLAY_ROUTINES }
    fun label(label: Label): String
    fun message(notice: DownloadNotice): Message
}
