// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import com.przunk.protracktor.net.Catalogue
import com.przunk.protracktor.net.UnExoticA
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Routes main and companion reads without owning a cache, a job or a decoder handle. */
internal class TrackFiles(
    private val access: Access,
    private val archiveWork: CoroutineDispatcher,
    private val onFetched: () -> Unit,
) {
    interface Access {
        suspend fun fetch(url: String): ByteArray?
        suspend fun readArchive(catalogueId: String, entry: String): ByteArray?
        fun readDocument(id: String): ByteArray?
        fun readDocumentCompanions(id: String): List<Pair<String, ByteArray>>
        fun decodeFileName(name: String): String
        fun extractLha(archive: ByteArray, member: String): ByteArray?
        fun reportMissingMember(archiveUrl: String, member: String)
    }

    /** Reads a track's bytes, from wherever it lives. */
    suspend fun read(ref: TrackRef): ByteArray? =
        if (com.przunk.protracktor.net.CatalogueRetention.blocksReference(ref.id)) {
            null
        } else if (UnExoticA.ENABLED && UnExoticA.pathFrom(ref.id) != null) {
            // The one catalogue whose unit of download is not the tune: every UnExoticA file lives
            // inside its game's `.lha`, so this fetches the archive -- cached like any other
            // download, so the rest of that soundtrack is free -- and unpacks the one member.
            // `docs/PLAN_UNEXOTICA.md` has the shape and the reason it is only three lines here.
            loadFromUnExoticA(UnExoticA.pathFrom(ref.id).orEmpty())?.also { onFetched() }
        } else if (archiveCatalogueOf(ref.id) != null) {
            // "<catalogue>://<entry>" -- read out of the archive that catalogue shipped as, which is
            // already on disk. No network, which is why an archive catalogue is worth its download.
            access.readArchive(ref.id.substringBefore("://"), ref.id.substringAfter("://"))
        } else if (ref.id.startsWith("http")) {
            access.fetch(ref.id)?.also { onFetched() }
        } else {
            withContext(Dispatchers.IO) { access.readDocument(ref.id) }
        }

    private fun archiveCatalogueOf(id: String): Catalogue? =
        Catalogue.all.firstOrNull { it.isArchive && id.startsWith("${it.id}://") }

    /**
     * The other files [ref]'s song needs, read from wherever [ref] itself came from.
     *
     * Empty for every single-file format, which is nearly all of them, and that costs one string
     * comparison. For TFMX it is `smpl.name` beside `mdat.name` (`Companions`), and the source
     * decides how "beside" is reached: the next URL in the same Modland directory, the next member
     * of the same UnExoticA archive, the next document in the same granted folder.
     *
     * **A companion that cannot be found is not an error here.** The song is still opened without
     * it, and UADE says it does not recognise it, which is the truth; failing before the decoder is
     * asked would hide which half was missing.
     */
    suspend fun companions(ref: TrackRef): List<Pair<String, ByteArray>> {
        if (Companions.namesFor(ref.fileNameOrTitle.substringAfterLast('/')).isEmpty() &&
            Companions.siblingPaths(ref.id).isEmpty()
        ) {
            return emptyList()
        }
        val unexotica = if (UnExoticA.ENABLED) UnExoticA.pathFrom(ref.id) else null
        return when {
            unexotica != null -> {
                val archiveUrl = UnExoticA.archiveUrlFor(unexotica) ?: return emptyList()
                val member = UnExoticA.split(unexotica)?.second ?: return emptyList()
                val archive = access.fetch(archiveUrl) ?: return emptyList()
                withContext(archiveWork) {
                    Companions.siblingPaths(member).mapNotNull { sibling ->
                        access.extractLha(archive, sibling)?.let { sibling.substringAfterLast('/') to it }
                    }
                }
            }
            ref.id.startsWith("http") ->
                Companions.siblingPaths(ref.id).mapNotNull { url ->
                    access.fetch(url)?.let { bytes ->
                        access.decodeFileName(url.substringAfterLast('/')) to bytes
                    }
                }
            ref.id.startsWith("content:") -> withContext(Dispatchers.IO) {
                access.readDocumentCompanions(ref.id)
            }
            else -> emptyList()
        }
    }

    /**
     * A tune out of an UnExoticA game archive.
     *
     * Two steps and both can fail for reasons worth telling apart in a log: the archive did not
     * arrive, or it arrived and does not hold what the index said it holds. The second means the
     * index and the archive have drifted -- songdb's snapshot is not ExoticA's live tree -- and it
     * is the failure worth watching for once real listening starts.
     */
    private suspend fun loadFromUnExoticA(path: String): ByteArray? {
        val archiveUrl = UnExoticA.archiveUrlFor(path) ?: return null
        val member = UnExoticA.split(path)?.second ?: return null
        val archive = access.fetch(archiveUrl) ?: return null
        return withContext(archiveWork) {
            access.extractLha(archive, member) ?: run {
                access.reportMissingMember(archiveUrl, member)
                null
            }
        }
    }
}
