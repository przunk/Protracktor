// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

internal sealed interface AlarmSaveResult {
    data class Saved(val schedule: AlarmScheduleResult) : AlarmSaveResult
    data object Failed : AlarmSaveResult
}
internal enum class AlarmAnswerResult { CORRECT, INCORRECT, STALE, EXPIRED, FAILED }
internal enum class AlarmCompletion { ANSWERED, FINISHED }

/** Presentation deliberately cannot inspect which answer is correct. */
internal data class AlarmQuestionView(val category: AlarmQuestion, val choices: List<AlarmChoice>)
internal data class AlarmChallenge(val token: String, val question: AlarmQuestionView?, val preview: Boolean, val backup: Boolean)
internal fun RingingAlarm.presented() = AlarmChallenge(token, quiz?.let { AlarmQuestionView(it.category, it.choices) }, preview, backup)
