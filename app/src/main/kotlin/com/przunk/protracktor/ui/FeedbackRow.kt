// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.przunk.protracktor.R

/**
 * Where testers say what works and what does not: an email to me, or the testers' group.
 *
 * An email rather than a form so the reply is a conversation, and prefilled with the version and
 * the phone so a report like C122 arrives with what it needs. Nothing is sent by the app; the
 * email app shows all of it before the user decides to send.
 */
object Feedback {
    const val EMAIL = "przunk@gmail.com"
    const val GROUP = "https://groups.google.com/g/protracktor"

    fun email(context: Context, version: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject, version))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.feedback_body, version,
                Build.VERSION.RELEASE.orEmpty(), Build.VERSION.SDK_INT.toString(),
                listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ")))
        }

    fun group(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(GROUP))
}

/** Settings → About: feedback by email or in the group, each an icon with its name. */
@Composable
internal fun FeedbackRow(version: String) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_feedback)) },
        supportingContent = {
            Text(stringResource(R.string.settings_feedback_detail), style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = { Icon(PlayerIcons.Mail, contentDescription = null) },
        trailingContent = {
            Row {
                LabelledAction(
                    icon = PlayerIcons.Mail,
                    label = stringResource(R.string.action_feedback_email),
                    slim = true,
                    onClick = {
                        context.open(Feedback.email(context, version),
                            context.getString(R.string.notice_no_email, Feedback.EMAIL))
                    },
                )
                LabelledAction(
                    icon = PlayerIcons.Forum,
                    label = stringResource(R.string.action_feedback_group),
                    slim = true,
                    onClick = {
                        context.open(Feedback.group(), context.getString(R.string.notice_no_browser, Feedback.GROUP))
                    },
                )
            }
        },
    )
}

/** A phone with nothing to handle the link says so, rather than the press doing nothing. */
private fun Context.open(intent: Intent, refusal: String) {
    try { startActivity(intent) } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, refusal, Toast.LENGTH_LONG).show()
    }
}
