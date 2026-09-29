// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.przunk.protracktor.MainActivity
import com.przunk.protracktor.R

/**
 * A folder scan's progress outside the app, with Stop (the owner, 2026-09-29).
 *
 * **An ordinary notification, not a foreground service.** He asked for a foreground one; that is a
 * second foreground-service type, `dataSync`, with its own Play Console declaration and video, days
 * before the production application. This shows the same bar and the same Stop. What it does not
 * do is keep the process alive, and it does not have to: a scan saves what it has found as it goes
 * (A67), so one the system stops in the background is paused, not lost.
 */
object ScanNotification {
    private const val CHANNEL_ID = "scanning"
    private const val ID = 2

    fun show(context: Context, folder: String, done: Int, total: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_scanning),
                // Low, as playback's: progress is to glance at, not to be interrupted by.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
        val stop = PendingIntent.getBroadcast(
            context, 0, Intent(context, ScanStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.scan_notification_title, folder))
            .setContentText(
                if (total > 0) context.getString(R.string.scan_progress, done, total)
                else context.getString(R.string.scan_listing)
            )
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    context.getString(R.string.action_stop_scan),
                    stop,
                ).build()
            )
            .build()
        // Without the permission there is simply no notification; the scan and its bar go on.
        runCatching { manager.notify(ID, notification) }
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}

/** The notification's Stop: the same as the one beside the bar. */
class ScanStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlaybackController.get(context).stopScan()
    }
}
