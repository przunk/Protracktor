// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.alarms

import android.content.Context
import android.media.MediaMetadataRetriever
import com.przunk.protracktor.alarms.api.AlarmMusicInput
import com.przunk.protracktor.engine.NativeEngine
import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.player.PcmExport
import com.przunk.protracktor.player.Platforms
import com.przunk.protracktor.player.TrackRef
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Explicit setup only. At wake-up neither the native engine nor the music database is used. */
internal object AlarmMusicPreparer {
    private val lock = Any()
    private val progress = MutableStateFlow<AlarmPreparation?>(null)
    val preparing = progress.asStateFlow()
    private var active: Pair<String, Job>? = null
    fun cancel(id: String): AlarmPreparationCancellation = synchronized(lock) {
        val current = active ?: return@synchronized AlarmPreparationCancellation.FINISHED
        if (current.first != id) return@synchronized AlarmPreparationCancellation.CHANGED
        if (current.second.isCompleted) return@synchronized AlarmPreparationCancellation.FINISHED
        current.second.cancel()
        AlarmPreparationCancellation.CANCELLED
    }
    suspend fun add(context: Context, ref: TrackRef, subsong: Int?, load: suspend () -> AlarmMusicInput): AlarmSong = coroutineScope {
        val id = UUID.randomUUID().toString()
        val owned = async(start = CoroutineStart.LAZY) { prepare(context, ref, subsong, load) }
        synchronized(lock) {
            if (active != null) { owned.cancel(); error("Alarm music is already being prepared") }
            active = id to owned
            progress.value = AlarmPreparation(id, ref.title)
        }
        try { owned.start(); owned.await() }
        finally { synchronized(lock) { if (active?.first == id) { active = null; progress.value = null } } }
    }
    private suspend fun prepare(context: Context, ref: TrackRef, subsong: Int?, load: suspend () -> AlarmMusicInput): AlarmSong {
        return withContext(Dispatchers.IO) { withTimeout(180_000) {
            check(com.przunk.protracktor.alarms.api.MusicAlarms.enabled) { "Music alarms are unavailable" }
            val coordinator = AlarmCoordinator.from(context)
            check(coordinator.readFailure == null) { "Alarm data cannot be opened" }
            val catalogue = Catalogue.known.firstOrNull { it.pathFrom(ref.id) != null }
            check(AlarmSourcePolicy.current.allows(catalogue?.id)) { "This catalogue is unavailable" }
            val input = load()
            currentCoroutineContext().ensureActive()
            val (decoder, reason) = NativeEngine.openRendering(input.bytes, ref.fileNameOrTitle, input.companions)
            checkNotNull(decoder) { reason }
            decoder.use { rendering ->
                if (subsong != null && subsong != rendering.currentSubsong()) check(rendering.selectSubsong(subsong)) { "This tune is unavailable" }
                val tune = rendering.currentSubsong()
                val key = MessageDigest.getInstance("SHA-256").digest((ref.id + "\u0000" + tune).toByteArray())
                    .joinToString("") { "%02x".format(it) }
                // Rendering applies selection synchronously; audible Track metadata can still
                // describe the previous tune while its audio-thread command is pending.
                val metadata = AlarmMetadata.describe(ref, input.format, rendering.describe(), input.metadata, input.credits)
                coordinator.refreshMusic(key, metadata)?.let { return@withTimeout it }
                val id = UUID.randomUUID().toString()
                val root = coordinator.repository.files.directory
                check(root.isDirectory || root.mkdirs())
                val output = File(root, "$id.preparing")
                try {
                    PcmExport.write(output, 44_100, 44_100L * AlarmLimits.EXCERPT_SECONDS,
                        mono = true, trimFrames = 44_100L * AlarmLimits.SILENCE_SECONDS) { rendering.render(44_100, it) }
                    val duration = MediaMetadataRetriever().use { reader ->
                        reader.setDataSource(output.absolutePath)
                        require(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
                        reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    } ?: error("Prepared audio cannot be read")
                    val platform = Platforms.forCatalogueRow(catalogue?.id.orEmpty(), input.format, ref.fileNameOrTitle)
                    val song = metadata.applyTo(AlarmSong(id, key, metadata.title,
                        metadata.author, platform?.id, catalogue?.id, duration))
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    coordinator.commitMusic(song, output)
                } finally { output.delete() }
            }
        } }
    }
}

internal data class AlarmPreparation(val id: String, val title: String)
internal enum class AlarmPreparationCancellation { CANCELLED, CHANGED, FINISHED }
