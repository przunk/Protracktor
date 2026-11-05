// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.przunk.protracktor.R
import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.ui.LabelledAction
import com.przunk.protracktor.ui.PlayerIcons
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun AlarmManagement(book: AlarmBook, armedAt: Long?, dataError: Boolean,
    exact: Boolean, notificationAccess: Boolean, fullscreen: Boolean, muted: Boolean,
    preparing: AlarmPreparation? = null, onCancelPreparation: (String) -> Unit = {}, onOpenRinging: () -> Unit = {},
    usable: (AlarmSong) -> Boolean, onBack: () -> Unit, onSave: suspend (MusicAlarm) -> AlarmSaveResult,
    onDelete: suspend (String) -> Boolean, onRemoveMusic: (String) -> Unit, onTest: (String) -> Unit,
    onAccess: (AlarmAccess) -> Unit, onRestore: suspend (MusicAlarm) -> AlarmSaveResult = onSave,
) {
    val effectiveArmedAt = armedAt.takeIf { exact && notificationAccess && fullscreen }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MusicAlarm?>(null) }
    var remove by remember { mutableStateOf<AlarmSong?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedLabel = stringResource(R.string.alarm_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val failureLabel = stringResource(R.string.alarm_failure)
    val unarmedLabel = stringResource(R.string.alarm_saved_not_armed)
    val report: suspend (AlarmSaveResult) -> Unit = { result ->
        when (result) {
            AlarmSaveResult.Failed -> snackbar.showSnackbar(failureLabel)
            is AlarmSaveResult.Saved -> if (result.schedule is AlarmScheduleResult.Blocked || result.schedule is AlarmScheduleResult.Failed)
                snackbar.showSnackbar(unarmedLabel)
        }
    }
    val editor = editing
    if (editor != null) {
        AlarmEditor(editor, book.songs, usable, { editing = null }) { alarm ->
            val result = onSave(alarm)
            if (result is AlarmSaveResult.Saved) {
                editing = null
                scope.launch { report(result) }
            }
            result
        }
        return
    }
    val now by produceState(Instant.now(), book) {
        while (true) {
            value = Instant.now()
            kotlinx.coroutines.delay(AlarmTimeLabels.refreshDelayMillis(value, ZoneId.systemDefault()))
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing, snackbarHost = { SnackbarHost(snackbar) }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
                AlarmHeader(stringResource(R.string.alarm_title), onBack,
                    help = alarmHelp(book.songs.size, book.alarms.size) +
                        listOfNotNull(effectiveArmedAt?.let { stringResource(R.string.alarm_next, upcoming(it, now)) },
                            book.lastOutcome?.let { outcomeText(it) }).joinToString("\n\n", prefix = "\n\n"),
                    action = { LabelledAction(PlayerIcons.Add, stringResource(R.string.alarm_new), {
                        val now = java.time.LocalTime.now().plusMinutes(1)
                        editing = MusicAlarm(UUID.randomUUID().toString(), now.hour, now.minute,
                            songIds = book.songs.filter(usable).map { it.id }, enabled = book.songs.any(usable))
                    }, Modifier.width(76.dp), enabled = !dataError && book.alarms.size < AlarmLimits.MAX_ALARMS) })
                TabRow(tab) {
                    listOf(R.string.alarm_title to PlayerIcons.Alarm, R.string.alarm_music_tab to PlayerIcons.AudioFile).forEachIndexed { i, (label, icon) ->
                        Tab(tab == i, { tab = i }, text = { Text(stringResource(label)) }, icon = { Icon(icon, null) })
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (dataError) item { AlarmNotice(PlayerIcons.Info, stringResource(R.string.alarm_data_error), error = true) }
                    if (!notificationAccess) item { AccessCard(R.string.alarm_notifications_needed, R.string.alarm_notifications_action) { onAccess(AlarmAccess.NOTIFICATIONS) } }
                    if (!exact) item { AccessCard(R.string.alarm_exact_needed, R.string.alarm_exact_action) { onAccess(AlarmAccess.EXACT) } }
                    if (!fullscreen) item { AccessCard(R.string.alarm_fullscreen_needed, R.string.alarm_fullscreen_action) { onAccess(AlarmAccess.FULL_SCREEN) } }
                    if (muted) item { AccessCard(R.string.alarm_volume_muted, R.string.alarm_volume_action) { onAccess(AlarmAccess.VOLUME) } }
                    preparing?.let { preparation -> item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailLine(PlayerIcons.AudioFile, stringResource(R.string.alarm_preparing, preparation.title))
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                LabelledAction(PlayerIcons.Close, stringResource(R.string.alarm_cancel), { onCancelPreparation(preparation.id) })
                            }
                        }
                    } }
                    book.ringing?.let { item { AccessCard(R.string.alarm_ringing, R.string.alarm_open_challenge, onOpenRinging) } }
                    if (tab == 0) {
                        if (book.alarms.isEmpty()) item { AlarmNotice(PlayerIcons.Alarm, stringResource(if (book.songs.isEmpty()) R.string.alarm_intro else R.string.alarm_empty)) }
                        items(book.alarms.sortedWith(compareBy({ it.hour }, { it.minute })), key = { it.id }) { alarm ->
                            val ready = alarm.songIds.any { id -> book.songs.any { it.id == id && usable(it) } }
                            val status = when {
                                !alarm.enabled -> stringResource(R.string.alarm_disabled)
                                !ready -> stringResource(R.string.alarm_unavailable)
                                effectiveArmedAt == null || alarm.nextAtMillis == 0L -> stringResource(R.string.alarm_not_armed)
                                else -> stringResource(R.string.alarm_armed, upcoming(alarm.nextAtMillis, now))
                            }
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Row(Modifier.weight(1f).clickable { editing = alarm }, verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(PlayerIcons.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                                            Text(alarmTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                                        }
                                        Switch(alarm.enabled, { enabled -> scope.launch { report(onSave(alarm.copy(enabled = enabled))) } }, enabled = !dataError && (alarm.enabled || ready),
                                            modifier = Modifier.semanticsLabel(stringResource(R.string.alarm_enable)))
                                    }
                                    if (alarm.label.isNotBlank()) Row(verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Icon(PlayerIcons.Label, null, Modifier.padding(top = 3.dp).size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                        Text("${stringResource(R.string.alarm_label)} · ${alarm.label}", Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    DetailLine(PlayerIcons.Calendar, weekdays(alarm.weekdays), stringResource(R.string.alarm_repeat))
                                    DetailLine(PlayerIcons.AudioFile, stringResource(R.string.alarm_music_selected, alarm.songIds.size))
                                    DetailLine(if (alarm.enabled && ready && effectiveArmedAt != null) PlayerIcons.Check else PlayerIcons.Info,
                                        status, stringResource(R.string.alarm_status), emphasis = true)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LabelledAction(PlayerIcons.Rename, stringResource(R.string.alarm_edit), { editing = alarm }, Modifier.weight(1f), enabled = !dataError)
                                        LabelledAction(PlayerIcons.Play, stringResource(R.string.alarm_test), { onTest(alarm.id) }, Modifier.weight(1f), enabled = ready && notificationAccess && book.ringing == null && !dataError)
                                        LabelledAction(PlayerIcons.Remove, stringResource(R.string.alarm_delete), { scope.launch {
                                            if (onDelete(alarm.id)) {
                                                snackbar.currentSnackbarData?.dismiss()
                                                if (snackbar.showSnackbar(deletedLabel, undoLabel, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed)
                                                    report(onRestore(alarm))
                                            }
                                        } }, Modifier.weight(1f), enabled = !dataError)
                                    }
                                }
                            }
                        }
                    } else {
                        if (book.songs.isEmpty()) item { AlarmNotice(PlayerIcons.AudioFile, stringResource(R.string.alarm_empty_music)) }
                        items(book.songs, key = { it.id }) { song ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DetailLine(PlayerIcons.AudioFile, song.title, stringResource(R.string.alarm_tune), emphasis = true)
                                    SongDetails(song)
                                    if (song.author.isNotBlank() && !AlarmQuizFactory.authorKnown(song.author))
                                        AlarmNotice(PlayerIcons.Info, stringResource(R.string.alarm_refresh_metadata))
                                    DetailLine(if (usable(song)) PlayerIcons.Downloaded else PlayerIcons.Info,
                                        stringResource(if (usable(song)) R.string.alarm_offline_excerpt else R.string.alarm_unavailable, song.durationMillis / 1000))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LabelledAction(PlayerIcons.Play, stringResource(R.string.alarm_test), { onTest(song.id) }, Modifier.weight(1f), enabled = usable(song) && notificationAccess && book.ringing == null && !dataError)
                                        LabelledAction(PlayerIcons.Remove, stringResource(R.string.alarm_delete), { remove = song }, Modifier.weight(1f), enabled = !dataError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    remove?.let { song -> AlertDialog(onDismissRequest = { remove = null }, title = { Text(song.title) },
        text = { Text(stringResource(R.string.alarm_remove_music)) },
        confirmButton = { TextButton({ onRemoveMusic(song.id); remove = null }) { Text(stringResource(R.string.alarm_delete)) } },
        dismissButton = { TextButton({ remove = null }) { Text(stringResource(R.string.alarm_cancel)) } }) }
}

@Composable
private fun AlarmHeader(title: String, onBack: () -> Unit, help: String, action: @Composable () -> Unit) {
    var info by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LabelledAction(PlayerIcons.Back, stringResource(R.string.alarm_back), onBack, Modifier.width(60.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LabelledAction(PlayerIcons.Info, stringResource(R.string.alarm_info), { info = true }, Modifier.width(52.dp))
            action()
        }
    }
    if (info) AlertDialog(onDismissRequest = { info = false },
        icon = { Icon(PlayerIcons.Info, null) }, title = { Text(stringResource(R.string.action_info)) },
        text = { Text(help, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton({ info = false }) { Text(stringResource(R.string.action_close)) } })
}

@Composable internal fun AlarmEditor(alarm: MusicAlarm, songs: List<AlarmSong>, usable: (AlarmSong) -> Boolean,
    onCancel: () -> Unit, onSave: suspend (MusicAlarm) -> AlarmSaveResult,
) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    var hour by remember(alarm.id) { mutableIntStateOf(alarm.hour) }
    var minute by remember(alarm.id) { mutableIntStateOf(alarm.minute) }
    var label by remember(alarm.id) { mutableStateOf(alarm.label) }
    var days by remember(alarm.id) { mutableStateOf(alarm.weekdays) }
    var selected by remember(alarm.id) { mutableStateOf(alarm.songIds.toSet()) }
    val dayColumns = if (LocalDensity.current.fontScale > 1.2f) 3 else 4
    val scope = rememberCoroutineScope()
    var saving by remember(alarm.id) { mutableStateOf(false) }
    var saveFailed by remember(alarm.id) { mutableStateOf(false) }
    val canSave = !saving && selected.any { id -> songs.any { it.id == id && usable(it) } }
    BackHandler(onBack = onCancel)
    Surface(Modifier.fillMaxSize()) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                AlarmHeader(stringResource(R.string.alarm_edit), onCancel, help = alarmHelp(songs.size), action = {
                    LabelledAction(PlayerIcons.Save, stringResource(R.string.alarm_save), {
                        if (!saving) scope.launch {
                            saving = true; saveFailed = false
                            try {
                                saveFailed = onSave(alarm.copy(hour = hour, minute = minute, label = label, weekdays = days,
                                    songIds = selected.toList(), enabled = true)) == AlarmSaveResult.Failed
                            } finally { saving = false }
                        }
                    }, Modifier.width(76.dp), enabled = canSave)
                })
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (saveFailed) AlarmNotice(PlayerIcons.Info, stringResource(R.string.alarm_failure), error = true)
                    Card(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, DateFormat.is24HourFormat(context)).show() },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(PlayerIcons.Alarm, null, Modifier.size(36.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.alarm_time), style = MaterialTheme.typography.labelLarge)
                                Text(alarmTime(hour, minute), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.alarm_change_time), style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(PlayerIcons.ChevronRight, null)
                        }
                    }
                    OutlinedTextField(label, { label = it.take(160) }, label = { Text(stringResource(R.string.alarm_name)) },
                        placeholder = { Text(stringResource(R.string.alarm_name_hint)) },
                        leadingIcon = { Icon(PlayerIcons.Label, null) }, singleLine = true, shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth())
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading(PlayerIcons.Calendar, stringResource(R.string.alarm_repeat))
                            for (row in (1..7).toList().chunked(dayColumns)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { day ->
                                    val selectedDay = day in days
                                    OutlinedButton(onClick = { days = if (selectedDay) days - day else days + day },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                            this.selected = selectedDay
                                            contentDescription = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)
                                        }, shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp),
                                        border = BorderStroke(1.dp, if (selectedDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selectedDay) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurface)) {
                                        Icon(if (selectedDay) PlayerIcons.Check else PlayerIcons.Answer, null, Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale),
                                            style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                                    }
                                }
                                repeat(dayColumns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    SectionHeading(PlayerIcons.AudioFile, stringResource(R.string.alarm_choose_music))
                    if (songs.isEmpty()) AlarmNotice(PlayerIcons.Info, stringResource(R.string.alarm_empty_music))
                    else {
                        DetailLine(PlayerIcons.Check, stringResource(R.string.alarm_music_selected, selected.size))
                        if (songs.none(usable)) AlarmNotice(PlayerIcons.Info, stringResource(R.string.alarm_no_quiz))
                        songs.forEach { song ->
                            val checked = song.id in selected
                            val ready = usable(song)
                            Surface(shape = RoundedCornerShape(20.dp),
                                color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().toggleable(checked, enabled = ready, role = Role.Checkbox) {
                                    selected = if (it) selected + song.id else selected - song.id
                                }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(PlayerIcons.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        val details = listOfNotNull(song.author.takeIf(AlarmQuizFactory::authorKnown), song.platformId?.let(Platforms::byId)?.name,
                                            song.year.takeIf(String::isNotBlank))
                                        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                        if (!ready) Text(stringResource(R.string.alarm_unavailable), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Checkbox(checked, null, enabled = ready)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun alarmHelp(songs: Int, alarms: Int? = null): String = listOfNotNull(
    stringResource(R.string.alarm_intro),
    alarms?.let { stringResource(R.string.alarm_limit, songs, it) },
    stringResource(R.string.alarm_quiz_hint),
    if (alarms == null) stringResource(R.string.alarm_save_enables) else null,
    stringResource(R.string.alarm_disclaimer),
).joinToString("\n\n")

@Composable private fun outcomeText(outcome: AlarmOutcome): String {
    val label = when (outcome.kind) {
        AlarmOutcomeKind.ANSWERED -> R.string.alarm_outcome_answered
        AlarmOutcomeKind.EMERGENCY_STOP -> R.string.alarm_outcome_emergency_stop
        AlarmOutcomeKind.TIMED_OUT -> R.string.alarm_outcome_timed_out
        AlarmOutcomeKind.MISSED -> R.string.alarm_outcome_missed
        AlarmOutcomeKind.INTERRUPTED -> R.string.alarm_outcome_interrupted
        AlarmOutcomeKind.FAILED -> R.string.alarm_outcome_failed
    }
    return stringResource(R.string.alarm_last_outcome, stringResource(label), date(outcome.atMillis))
}

@Composable private fun SectionHeading(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun SongDetails(song: AlarmSong) {
    if (AlarmQuizFactory.authorKnown(song.author)) DetailLine(PlayerIcons.Person, song.author, stringResource(R.string.field_artist))
    song.platformId?.let(Platforms::byId)?.name?.let { DetailLine(PlayerIcons.Joystick, it, stringResource(R.string.alarm_platform)) }
    if (song.year.isNotBlank()) DetailLine(PlayerIcons.Calendar, song.year, stringResource(R.string.field_year))
}
@Composable private fun DetailLine(icon: ImageVector, value: String, label: String? = null, emphasis: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (label == null) value else "$label · $value", style = if (emphasis) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
            color = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun AlarmNotice(icon: ImageVector, body: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun AccessCard(body: Int, action: Int, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(PlayerIcons.Shield, stringResource(R.string.alarm_access))
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            LabelledAction(PlayerIcons.Settings, stringResource(action), onClick)
        }
    }
}
@Composable private fun alarmTime(hour: Int, minute: Int): String {
    val context = LocalContext.current
    return AlarmTimeLabels.time(java.time.LocalTime.of(hour, minute), context.resources.configuration.locales[0],
        DateFormat.is24HourFormat(context))
}
@Composable private fun upcoming(millis: Long, now: Instant): String {
    val context = LocalContext.current
    return AlarmTimeLabels.upcoming(millis, now, ZoneId.systemDefault(), context.resources.configuration.locales[0],
        DateFormat.is24HourFormat(context), stringResource(R.string.alarm_today), stringResource(R.string.alarm_tomorrow))
}
@Composable private fun date(millis: Long): String = DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
    .withLocale(LocalContext.current.resources.configuration.locales[0])
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
@Composable private fun weekdays(days: Set<Int>): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return if (days.isEmpty()) stringResource(R.string.alarm_one_shot)
        else days.sorted().joinToString(" · ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
}

private fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }
