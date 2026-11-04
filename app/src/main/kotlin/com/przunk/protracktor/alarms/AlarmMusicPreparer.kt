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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Explicit setup only. At wake-up neither the native engine nor the music database is used. */
internal object AlarmMusicPreparer {
    private val preparation = Mutex()
    private val progress = MutableStateFlow<String?>(null)
    val preparing = progress.asStateFlow()
    @Volatile private var job: Job? = null
    fun cancel() { job?.cancel() }
    suspend fun add(context: Context, ref: TrackRef, subsong: Int?, load: suspend () -> AlarmMusicInput): AlarmSong {
        check(preparation.tryLock()) { "Alarm music is already being prepared" }
        job = currentCoroutineContext()[Job]
        progress.value = ref.title
        try { return withContext(Dispatchers.IO) { withTimeout(180_000) {
            val repository = AlarmRepository.from(context)
            check(repository.readFailure == null) { "Alarm data cannot be opened" }
            val catalogue = Catalogue.known.firstOrNull { it.pathFrom(ref.id) != null }
            check(catalogue?.id != "amp" || com.przunk.protracktor.net.Amp.access.enabled)
            val input = load()
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
                var refreshed: AlarmSong? = null
                repository.change { book ->
                    val existing = book.songs.firstOrNull { it.sourceKey == key }
                    if (existing == null) book else {
                        refreshed = metadata.applyTo(existing)
                        book.copy(songs = book.songs.map { if (it.id == existing.id) checkNotNull(refreshed) else it })
                    }
                }
                refreshed?.let { return@withTimeout it }
                val id = UUID.randomUUID().toString()
                val root = repository.files.directory
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
                    check(AlarmRepository.sourceAllowed(song)) { "This catalogue is unavailable" }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    repository.add(song, output)
                } finally { output.delete() }
            }
        } } } finally {
            job = null; progress.value = null; preparation.unlock()
        }
    }
}
