// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.content.Context
import com.przunk.protracktor.R
import com.przunk.protracktor.net.CacheBudget

internal class AndroidMaintenanceText(private val context: Context) : LibraryMaintenance.Text {
    override fun deleting(): String = context.getString(R.string.browse_deleting_short)

    override fun message(notice: LibraryMaintenance.Notice): Message = Message(when (notice) {
        is LibraryMaintenance.Notice.Freed -> freedMessage(notice.bytes)
        LibraryMaintenance.Notice.IndexDeleted -> context.getString(R.string.notice_index_deleted)
        LibraryMaintenance.Notice.MetadataDeleted -> context.getString(R.string.notice_song_metadata_deleted)
        is LibraryMaintenance.Notice.DeleteFailed ->
            context.getString(R.string.notice_delete_failed, notice.name)
        LibraryMaintenance.Notice.MetadataDeleteFailed ->
            context.getString(R.string.notice_delete_failed, context.getString(R.string.song_metadata_title))
        LibraryMaintenance.Notice.ReplaysDeleteFailed ->
            context.getString(R.string.notice_delete_failed, context.getString(R.string.replay_routines_title))
    })

    /** The wording for what [CacheBudget.describeFreed] worked out. */
    private fun freedMessage(bytes: Long): String =
        when (val freed = CacheBudget.describeFreed(bytes)) {
            CacheBudget.Freed.NOTHING -> context.getString(R.string.notice_freed_nothing)
            CacheBudget.Freed.LESS_THAN_A_MEGABYTE -> context.getString(R.string.notice_freed_under_a_megabyte)
            is CacheBudget.Freed.Megabytes -> context.getString(R.string.notice_freed_megabytes, freed.count)
        }
}
