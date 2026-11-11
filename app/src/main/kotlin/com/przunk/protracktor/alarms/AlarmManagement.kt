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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
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
    /** Where *Add music* finds tunes; without it (a preview) there is no Add music. */
    sources: AlarmMusicSources? = null,
    /** Tunes picked in *Add music*, as [AlarmMusicQueue] prepares them. */
    picks: List<AlarmPick> = emptyList(),
    onAddMusic: (com.przunk.protracktor.player.TrackRef) -> Unit = {},
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
    val limitLabel = stringResource(R.string.alarm_limit_reached)
    // *Add music* (B62), over the editor or the list. What is added from the editor is ticked in it.
    var picking by remember { mutableStateOf<PickFrom?>(null) }
    var fromEditor by remember { mutableStateOf(emptySet<String>()) }
    val add: (com.przunk.protracktor.player.TrackRef) -> Unit = { ref ->
        if (picking == PickFrom.EDITOR) fromEditor = fromEditor + ref.id
        onAddMusic(ref)
    }
    val picker: @Composable () -> Unit = {
        if (picking != null && sources != null) AlarmMusicPicker(sources, picks, add, onBack = { picking = null })
    }
    val report: suspend (AlarmSaveResult) -> Unit = { result ->
        when (result) {
            AlarmSaveResult.Failed -> snackbar.showSnackbar(failureLabel)
            is AlarmSaveResult.Saved -> if (result.schedule is AlarmScheduleResult.Blocked || result.schedule is AlarmScheduleResult.Failed)
                snackbar.showSnackbar(unarmedLabel)
        }
    }
    val editor = editing
    if (editor != null) {
        val tick = picks.filter { it.ref.id in fromEditor }.mapNotNull { (it.state as? AlarmPickState.Added)?.songId }.toSet()
        Box(Modifier.fillMaxSize()) {
            AlarmEditor(editor, book.songs, usable, { editing = null; fromEditor = emptySet() }, onSave = { alarm ->
                val result = onSave(alarm)
                if (result is AlarmSaveResult.Saved) {
                    editing = null
                    fromEditor = emptySet()
                    scope.launch { report(result) }
                }
                result
            }, isNew = book.alarms.none { it.id == editor.id }, tick = tick,
                onAddMusic = sources?.let { { picking = PickFrom.EDITOR } })
            picker()
        }
        return
    }
    val now by produceState(Instant.now(), book) {
        while (true) {
            value = Instant.now()
            kotlinx.coroutines.delay(AlarmTimeLabels.refreshDelayMillis(value, ZoneId.systemDefault()))
        }
    }
    val newAlarm = {
        if (book.alarms.size >= AlarmLimits.MAX_ALARMS) scope.launch { snackbar.showSnackbar(limitLabel) }
        else {
            val now = java.time.LocalTime.now().plusMinutes(1)
            editing = MusicAlarm(UUID.randomUUID().toString(), now.hour, now.minute,
                songIds = book.songs.filter(usable).map { it.id }, enabled = book.songs.any(usable))
        }
    }
    Box(Modifier.fillMaxSize()) {
    Surface(Modifier.fillMaxSize()) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing, snackbarHost = { SnackbarHost(snackbar) },
            // **The list's main action, where the thumb is** (B62): New alarm on Alarms, Add music on
            // Music. Kept at the limit, where it says why instead of acting.
            floatingActionButton = {
                if (!dataError) when {
                    tab == 0 -> ExtendedFloatingActionButton(onClick = { newAlarm() },
                        icon = { Icon(PlayerIcons.Add, null) }, text = { Text(stringResource(R.string.alarm_new)) },
                        // Its label as its name, which the button does not take from its text by itself.
                        modifier = Modifier.testTag("alarm-new").semanticsLabel(stringResource(R.string.alarm_new)))
                    sources != null -> ExtendedFloatingActionButton(onClick = { picking = PickFrom.LIBRARY },
                        icon = { Icon(PlayerIcons.Add, null) }, text = { Text(stringResource(R.string.alarm_add_music)) },
                        // Its label as its name, which the button does not take from its text by itself.
                        modifier = Modifier.testTag("alarm-add-music").semanticsLabel(stringResource(R.string.alarm_add_music)))
                }
            }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
                AlarmHeader(stringResource(R.string.alarm_title), onBack,
                    help = alarmHelp(book.songs.size, book.alarms.size) +
                        listOfNotNull(effectiveArmedAt?.let { stringResource(R.string.alarm_next, upcoming(it, now)) },
                            book.lastOutcome?.let { outcomeText(it) }).joinToString("\n\n", prefix = "\n\n"),
                    action = {})
                TabRow(tab) {
                    listOf(R.string.alarm_title to PlayerIcons.Alarm, R.string.alarm_music_tab to PlayerIcons.AudioFile).forEachIndexed { i, (label, icon) ->
                        Tab(tab == i, { tab = i }, text = { Text(stringResource(label)) }, icon = { Icon(icon, null) })
                    }
                }
                // Room at the end for the floating button, so it never covers the last card's actions.
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        // **When it will wake me**, the question an alarm clock is opened for, first
                        // rather than in Info.
                        else item(key = "next") { NextAlarm(effectiveArmedAt, now) }
                        items(book.alarms.sortedWith(compareBy({ it.hour }, { it.minute })), key = { it.id }) { alarm ->
                            val ready = alarm.songIds.any { id -> book.songs.any { it.id == id && usable(it) } }
                            val status = when {
                                !alarm.enabled -> stringResource(R.string.alarm_disabled)
                                !ready -> stringResource(R.string.alarm_unavailable)
                                effectiveArmedAt == null || alarm.nextAtMillis == 0L -> stringResource(R.string.alarm_not_armed)
                                else -> stringResource(R.string.alarm_rings, upcoming(alarm.nextAtMillis, now))
                            }
                            // The whole card opens the editor; its switch and actions are their own.
                            Card(onClick = { editing = alarm }, enabled = !dataError,
                                modifier = Modifier.fillMaxWidth().testTag("alarm-card"), shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // An alarm that is off is drawn faded, not only switched off.
                                        Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.6f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(alarmTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                                            if (alarm.label.isNotBlank()) Text(alarm.label, style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold, maxLines = 2)
                                        }
                                        Switch(alarm.enabled, { enabled -> scope.launch { report(onSave(alarm.copy(enabled = enabled))) } }, enabled = !dataError && (alarm.enabled || ready),
                                            modifier = Modifier.semanticsLabel(stringResource(R.string.alarm_enable)))
                                    }
                                    Column(Modifier.alpha(if (alarm.enabled) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DetailLine(PlayerIcons.Calendar, days(alarm.weekdays))
                                        DetailLine(PlayerIcons.AudioFile, music(alarm, book.songs))
                                    }
                                    DetailLine(if (alarm.enabled && ready && effectiveArmedAt != null) PlayerIcons.Check else PlayerIcons.Info,
                                        status, emphasis = true)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LabelledAction(PlayerIcons.Play, stringResource(R.string.alarm_test), { onTest(alarm.id) }, Modifier.weight(1f), slim = true,
                                            enabled = ready && notificationAccess && book.ringing == null && !dataError)
                                        LabelledAction(PlayerIcons.Remove, stringResource(R.string.alarm_delete), { scope.launch {
                                            if (onDelete(alarm.id)) {
                                                snackbar.currentSnackbarData?.dismiss()
                                                if (snackbar.showSnackbar(deletedLabel, undoLabel, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed)
                                                    report(onRestore(alarm))
                                            }
                                        } }, Modifier.weight(1f), slim = true, enabled = !dataError)
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
                                    val users = AlarmSummaries.users(song, book.alarms).size
                                    DetailLine(PlayerIcons.Alarm, if (users == 0) stringResource(R.string.alarm_unused)
                                        else pluralStringResource(R.plurals.alarm_used_by, users, users))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LabelledAction(PlayerIcons.Play, stringResource(R.string.alarm_test), { onTest(song.id) }, Modifier.weight(1f), slim = true, enabled = usable(song) && notificationAccess && book.ringing == null && !dataError)
                                        LabelledAction(PlayerIcons.Remove, stringResource(R.string.alarm_delete), { remove = song }, Modifier.weight(1f), slim = true, enabled = !dataError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    picker()
    }
    remove?.let { song -> AlertDialog(onDismissRequest = { remove = null }, title = { Text(song.title) },
        // Which alarms lose it, by their times and names, before it goes.
        text = {
            val users = AlarmSummaries.users(song, book.alarms)
            val names = users.map { alarm -> listOf(alarmTime(alarm.hour, alarm.minute), alarm.label).filter(String::isNotBlank).joinToString(" ") }
            val lose = if (names.isEmpty()) null else stringResource(R.string.alarm_remove_music_used, names.joinToString(", "))
            Text(listOfNotNull(stringResource(R.string.alarm_remove_music), lose).joinToString("\n\n"))
        },
        confirmButton = { TextButton({ onRemoveMusic(song.id); remove = null }) { Text(stringResource(R.string.alarm_delete)) } },
        dismissButton = { TextButton({ remove = null }) { Text(stringResource(R.string.alarm_cancel)) } }) }
}

@Composable
internal fun AlarmHeader(title: String, onBack: () -> Unit, help: String, action: @Composable () -> Unit) {
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
    onCancel: () -> Unit,
    onSave: suspend (MusicAlarm) -> AlarmSaveResult,
    /** A new alarm says so in its title; an existing one is edited. */
    isNew: Boolean = false,
    /** Tunes just added through *Add music* from here, ticked once each as they arrive (B62). */
    tick: Set<String> = emptySet(),
    /** Opens *Add music*; null where there is none (a preview). */
    onAddMusic: (() -> Unit)? = null,
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
    val hasMusic = selected.any { id -> songs.any { it.id == id && usable(it) } }
    val canSave = !saving && hasMusic
    // Each added tune ticked once, when it is in the library: unticking it afterwards sticks.
    var ticked by remember(alarm.id) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(tick, songs) {
        val arrived = tick.filter { id -> id !in ticked && songs.any { it.id == id } }
        if (arrived.isNotEmpty()) { selected = selected + arrived; ticked = ticked + arrived }
    }
    // **Back with changes asks** rather than throwing them away (B62).
    val changed = hour != alarm.hour || minute != alarm.minute || label != alarm.label || days != alarm.weekdays ||
        selected != alarm.songIds.toSet()
    var discarding by remember(alarm.id) { mutableStateOf(false) }
    val leave = { if (changed) discarding = true else onCancel() }
    BackHandler(onBack = leave)
    if (discarding) AlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.alarm_discard_title)) },
        text = { Text(stringResource(R.string.alarm_discard_body)) },
        confirmButton = { TextButton({ discarding = false; onCancel() }) { Text(stringResource(R.string.alarm_discard)) } },
        dismissButton = { TextButton({ discarding = false }) { Text(stringResource(R.string.alarm_keep_editing)) } })
    val now by produceState(Instant.now()) { while (true) { kotlinx.coroutines.delay(30_000); value = Instant.now() } }
    Surface(Modifier.fillMaxSize()) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                AlarmHeader(stringResource(if (isNew) R.string.alarm_new else R.string.alarm_edit), leave, help = alarmHelp(songs.size), action = {
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
                    // Why Save cannot be pressed, beside it rather than nowhere.
                    if (!saving && !hasMusic) AlarmNotice(PlayerIcons.Info, stringResource(R.string.alarm_save_needs_music))
                    Card(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, DateFormat.is24HourFormat(context)).show() },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(PlayerIcons.Alarm, null, Modifier.size(36.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.alarm_time), style = MaterialTheme.typography.labelLarge)
                                Text(alarmTime(hour, minute), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium)
                                // When this would ring, as the time and the days change.
                                // With no music an alarm cannot be on, and does not ring.
                                val next = if (selected.isEmpty()) null else AlarmPlanner.next(alarm.copy(hour = hour, minute = minute,
                                    weekdays = days, songIds = selected.toList(), enabled = true, consumedDate = null), now, ZoneId.systemDefault())
                                Text(if (next == null) stringResource(R.string.alarm_rings_never)
                                    else stringResource(R.string.alarm_rings, upcoming(next.atMillis, now)),
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("alarm-editor-rings"))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionHeading(PlayerIcons.AudioFile, stringResource(R.string.alarm_music_tab)) }
                        onAddMusic?.let { LabelledAction(PlayerIcons.Add, stringResource(R.string.alarm_add_music), it, slim = true,
                            modifier = Modifier.testTag("alarm-editor-add-music")) }
                    }
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

/** Where *Add music* was opened from: the editor ticks what it adds, the Music tab only adds. */
internal enum class PickFrom { EDITOR, LIBRARY }

/** The next alarm, its day and time and how long until it, or that none will ring. */
@Composable private fun NextAlarm(at: Long?, now: Instant) {
    Card(Modifier.fillMaxWidth().testTag("alarm-next"), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(PlayerIcons.Alarm, null, Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.alarm_next_title), style = MaterialTheme.typography.labelLarge)
                if (at == null) Text(stringResource(R.string.alarm_next_none), style = MaterialTheme.typography.titleMedium)
                else {
                    Text(upcoming(at, now), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    waitText(at, now)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable private fun waitText(at: Long, now: Instant): String? = AlarmSummaries.until(at, now)?.let { wait ->
    if (wait.hours == 0L) stringResource(R.string.alarm_in_minutes, wait.minutes)
    else stringResource(R.string.alarm_in_hours, wait.hours, wait.minutes)
}

/** The days by name where they have one: Weekdays, Every day, Weekends, Once; else the days. */
@Composable private fun days(weekdays: Set<Int>): String = when (AlarmSummaries.days(weekdays)) {
    AlarmSummaries.Days.ONCE -> stringResource(R.string.alarm_days_once)
    AlarmSummaries.Days.WEEKDAYS -> stringResource(R.string.alarm_days_weekdays)
    AlarmSummaries.Days.WEEKENDS -> stringResource(R.string.alarm_days_weekends)
    AlarmSummaries.Days.EVERY_DAY -> stringResource(R.string.alarm_days_every)
    AlarmSummaries.Days.LISTED -> weekdays(weekdays)
}

/** The music by its first tune and how many more. */
@Composable private fun music(alarm: MusicAlarm, songs: List<AlarmSong>): String =
    when (val summary = AlarmSummaries.music(alarm, songs)) {
        null -> stringResource(R.string.alarm_music_none)
        else -> if (summary.more == 0) summary.first else stringResource(R.string.alarm_music_more, summary.first, summary.more)
    }
