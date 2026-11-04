// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.ui.LabelledAction
import com.przunk.protracktor.ui.PlayerIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AlarmRingingScreen(active: RingingAlarm?, waiting: Boolean, failed: Boolean,
    onAnswer: (String, String) -> Unit, onStop: (String) -> Unit, onClose: () -> Unit,
    audioStatus: AlarmAudioStatus = AlarmAudioStatus.PLAYING,
) {
    val colours = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var wrong by remember(active?.token) { mutableStateOf<String?>(null) }
    var confirmStop by remember(active?.token) { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }
    LaunchedEffect(active?.token) { if (active != null) solved = false }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); now = System.currentTimeMillis() } }
    var hadActive by remember { mutableStateOf(active != null) }
    LaunchedEffect(active?.token) { if (active != null) hadActive = true }
    val stopped = active == null && (hadActive || !waiting)
    val currentClose by rememberUpdatedState(onClose)
    LaunchedEffect(stopped, failed, active?.token) {
        if (stopped && hadActive && !failed) { delay(1000); currentClose() }
    }
    val scroll = key(stopped) { rememberScrollState() }
    Surface(Modifier.fillMaxSize(), color = colours.surface) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colours.primaryContainer.copy(alpha = .55f), colours.surface)))
            .safeDrawingPadding().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(now)),
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(if (stopped && solved) R.string.alarm_correct else if (stopped) R.string.alarm_finished else R.string.alarm_ringing),
                    modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                if (stopped && hadActive) Icon(PlayerIcons.Check, null, Modifier.size(96.dp), tint = colours.primary)
                else AlarmClockArt(ringing = active != null, nowMillis = now)
            }
            when {
                failed -> {
                    Text(stringResource(R.string.alarm_data_error), color = colours.error)
                    LabelledAction(PlayerIcons.Back, stringResource(R.string.alarm_back), onClose)
                }
                stopped -> if (!hadActive) LabelledAction(PlayerIcons.Back, stringResource(R.string.alarm_back), onClose)
                active == null -> Text(stringResource(R.string.alarm_loading))
                active.backup -> {
                    Text(stringResource(R.string.alarm_backup), textAlign = TextAlign.Center)
                    if (audioStatus == AlarmAudioStatus.WAITING_FOCUS) Text(stringResource(R.string.alarm_waiting_focus), textAlign = TextAlign.Center)
                    LabelledAction(PlayerIcons.Stop, stringResource(R.string.alarm_stop_anyway), { onStop(active.token) })
                }
                else -> {
                    val quiz = checkNotNull(active.quiz)
                    val question = when (quiz.category) {
                        AlarmQuestion.PLATFORM -> R.string.alarm_question_platform
                        AlarmQuestion.AUTHOR -> R.string.alarm_question_author
                        AlarmQuestion.TITLE -> R.string.alarm_question_title
                        AlarmQuestion.YEAR -> R.string.alarm_question_year
                    }
                    Text(stringResource(question), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    val soundLabel = when (audioStatus) {
                        AlarmAudioStatus.WAITING_FOCUS -> R.string.alarm_waiting_focus
                        AlarmAudioStatus.PREPARING, AlarmAudioStatus.STOPPED -> R.string.alarm_loading
                        AlarmAudioStatus.PLAYING -> R.string.alarm_listen
                    }
                    val feedback = wrong != null && audioStatus == AlarmAudioStatus.PLAYING
                    Text(stringResource(if (feedback) R.string.alarm_wrong else soundLabel),
                        color = if (feedback) colours.error else colours.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    quiz.choices.forEach { choice ->
                        val rejected = wrong == choice.id
                        OutlinedButton(onClick = {
                            if (!quiz.accepts(choice.id)) {
                                wrong = choice.id
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (quiz.accepts(choice.id)) solved = true
                            onAnswer(active.token, choice.id)
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (rejected) colours.error else colours.onSurface),
                            contentPadding = PaddingValues(16.dp)) {
                            Icon(if (rejected) PlayerIcons.Close else PlayerIcons.Answer, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(choice.value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    EmergencyStop(onHold = { onStop(active.token) }, onAccessibleClick = { confirmStop = true })
                }
            }
            if (active?.preview == true) Text(stringResource(R.string.alarm_preview), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
    if (confirmStop && active != null) AlertDialog(onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(R.string.alarm_stop_anyway)) }, text = { Text(stringResource(R.string.alarm_stop_confirm)) },
        confirmButton = { TextButton({ onStop(active.token); confirmStop = false }) { Text(stringResource(R.string.alarm_stop_anyway)) } },
        dismissButton = { TextButton({ confirmStop = false }) { Text(stringResource(R.string.alarm_cancel)) } })
}

@Composable private fun EmergencyStop(onHold: () -> Unit, onAccessibleClick: () -> Unit) {
    val hold by rememberUpdatedState(onHold)
    val accessible by rememberUpdatedState(onAccessibleClick)
    val label = stringResource(R.string.alarm_hold_stop)
    var pressed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 3000 else 100), label = "emergency-hold")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; onClick(label) { accessible(); true } }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        down.consume()
                        pressed = true
                        val timer = launch { delay(3000); hold() }
                        try {
                            do {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val finger = event.changes.firstOrNull { it.id == down.id }
                                finger?.consume()
                            } while (finger?.pressed == true)
                        } finally { timer.cancel(); pressed = false }
                    }
                }
            }) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(PlayerIcons.Stop, null); Text(label)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (pressed) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                trackColor = androidx.compose.ui.graphics.Color.Transparent)
        }
    }
}

@Composable internal fun AlarmClockArt(ringing: Boolean, nowMillis: Long = System.currentTimeMillis()) {
    val context = LocalContext.current
    val motion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val (turn, glow) = if (ringing && motion) {
        val transition = rememberInfiniteTransition(label = "alarm-clock")
        val rock by transition.animateFloat(-5f, 5f, infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bell-rock")
        val pulse by transition.animateFloat(.15f, .45f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "clock-glow")
        rock to pulse
    } else 0f to .18f
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val localTime = java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    Canvas(Modifier.size(176.dp).rotate(if (ringing && motion) turn else 0f)
        .clearAndSetSemantics { }) {
        val c = center
        val r = size.minDimension * .32f
        drawCircle(accent.copy(alpha = if (ringing && motion) glow else .18f), r * 1.5f)
        drawCircle(accent.copy(alpha = .25f), r * 1.2f, style = Stroke(2.dp.toPx()))
        drawCircle(ink, r, style = Stroke(5.dp.toPx()))
        for (i in 0..11) {
            val a = i * Math.PI / 6
            val outer = androidx.compose.ui.geometry.Offset(c.x + kotlin.math.sin(a).toFloat()*r*.84f, c.y - kotlin.math.cos(a).toFloat()*r*.84f)
            val inner = androidx.compose.ui.geometry.Offset(c.x + kotlin.math.sin(a).toFloat()*r*.72f, c.y - kotlin.math.cos(a).toFloat()*r*.72f)
            drawLine(ink, inner, outer, 2.dp.toPx())
        }
        fun hand(angle: Double, length: Float) = androidx.compose.ui.geometry.Offset(c.x + kotlin.math.sin(angle).toFloat()*length, c.y - kotlin.math.cos(angle).toFloat()*length)
        drawLine(ink, c, hand((localTime.hour % 12 + localTime.minute / 60.0) * Math.PI / 6, r*.5f), 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(accent, c, hand(localTime.minute * Math.PI / 30, r*.72f), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(accent, 5.dp.toPx())
        drawLine(ink, androidx.compose.ui.geometry.Offset(c.x-r*.65f,c.y+r*.75f), androidx.compose.ui.geometry.Offset(c.x-r*.85f,c.y+r*1.1f), 5.dp.toPx())
        drawLine(ink, androidx.compose.ui.geometry.Offset(c.x+r*.65f,c.y+r*.75f), androidx.compose.ui.geometry.Offset(c.x+r*.85f,c.y+r*1.1f), 5.dp.toPx())
        drawArc(accent, 190f, 160f, false, androidx.compose.ui.geometry.Offset(c.x-r*1.07f,c.y-r*1.15f), androidx.compose.ui.geometry.Size(r*.7f,r*.65f), style = Stroke(7.dp.toPx()))
        drawArc(accent, 190f, 160f, false, androidx.compose.ui.geometry.Offset(c.x+r*.37f,c.y-r*1.15f), androidx.compose.ui.geometry.Size(r*.7f,r*.65f), style = Stroke(7.dp.toPx()))
    }
}
