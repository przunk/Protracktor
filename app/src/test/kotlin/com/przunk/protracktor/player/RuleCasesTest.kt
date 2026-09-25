// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue rules, driven from the file the page's checks read too.
 *
 * **`docs/rules/queue-cases.json` is the reference, and neither implementation is**
 * (`docs/PLAN_WEB_LIBRARY.md` S1). The rules live twice because a browser cannot run Kotlin; what
 * this stops is them being decided twice. C23, C30 and C31 were each one screen doing what the
 * other did not, none of them caught by a test, all three inside a week.
 *
 * A rule changed here and not in `web/src/rules.js` fails **there**, and the other way round. That
 * is the whole mechanism and it is why the cases are data rather than code.
 *
 * Shuffle is deliberately not in the file: the two sides shuffle with different generators, and
 * agreeing on a permutation would mean sharing an implementation, which is the thing that cannot be
 * done. `PlayQueueTest` covers it on this side.
 */
class RuleCasesTest {

    /**
     * The shared file, found by walking up from wherever the tests happen to run.
     *
     * Gradle's working directory for a unit test is the module, not the repository, and a test that
     * silently finds nothing would pass by testing nothing — which this project has been caught by
     * once already, on a suite that reported "BUILD SUCCESSFUL with zero tests".
     */
    private val cases: Map<String, List<Map<String, String>>> by lazy {
        var here: File? = File(".").absoluteFile
        while (here != null && !File(here, RULES).isFile) here = here.parentFile
        val file = here?.let { File(it, RULES) }
        assertTrue("$RULES not found from ${File(".").absolutePath}", file != null && file.isFile)
        parse(file!!.readText())
    }

    /**
     * `[group]`, a header row, then rows. Comments start with `#`, `-` means null.
     *
     * Five lines, because the alternative was `org.json` — which a unit test has only a stub of,
     * and which would have failed by returning nothing rather than by saying so.
     */
    private fun parse(text: String): Map<String, List<Map<String, String>>> {
        val groups = mutableMapOf<String, MutableList<Map<String, String>>>()
        var group: String? = null
        var header: List<String> = emptyList()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                group = line.trim('[', ']')
                header = emptyList()
                groups[group] = mutableListOf()
                continue
            }
            val cells = raw.split('\t')
            if (header.isEmpty()) { header = cells.map { it.trim() }; continue }
            groups[group ?: continue]?.add(header.zip(cells.map { it.trim() }).toMap())
        }
        return groups
    }

    private fun Map<String, String>.int(key: String) = this.getValue(key).toInt()
    private fun Map<String, String>.intOrNull(key: String) = this.getValue(key).toIntOrNull()
    private fun Map<String, String>.bool(key: String) = this.getValue(key) == "yes"
    private fun Map<String, String>.why() = this.getValue("why")

    private fun each(group: String, run: (Map<String, String>) -> Unit) {
        val list = cases[group].orEmpty()
        // A group that is empty is a group nobody is checking, which is the failure this file is
        // about — an agreement that quietly stopped being one.
        assertTrue("no cases for '$group'", list.isNotEmpty())
        list.forEach(run)
    }

    private fun tracks(count: Int) =
        (0 until count).map { TrackRef(id = "t$it", title = "Track $it") }

    private fun repeatOf(name: String?) = when (name) {
        // The file says "all" because that is what the page's mode is called; the app calls the
        // same thing PLAYLIST. Two names for one rule is exactly what this file exists to catch, so
        // the translation lives here, once, rather than in either implementation.
        "all" -> RepeatMode.PLAYLIST
        "one" -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }

    /** A queue sitting on [at], built the way the app builds one: by playing into it. */
    private fun queueAt(count: Int, at: Int, repeat: RepeatMode): PlayQueue {
        var queue = PlayQueue(tracks = tracks(count), repeat = RepeatMode.OFF)
        repeat(at + 1) { queue = queue.next() }
        return queue.copy(repeat = repeat)
    }

    @Test
    fun `what History records agrees with the shared cases`() = each("historyRecords") { case ->
        assertEquals(
            case.why(),
            case.bool("expect"),
            HistoryRecording.records(case.bool("walkingResults"), case.bool("fromHistory")),
        )
    }

    @Test
    fun `a length is shown as the shared cases say`() = each("lengthTotal") { case ->
        assertEquals(case.why(), case.getValue("expect"), com.przunk.protracktor.ui.formatTotal(case.getValue("seconds").toDouble()))
    }

    @Test
    fun `when a dock line scrolls agrees with the shared cases`() = each("lineScrolls") { case ->
        // The phone reads the animator scale; "animations off" is a scale of 0.
        val scale = if (case.bool("animationsOn")) 1f else 0f
        assertEquals(case.why(), case.bool("expect"), com.przunk.protracktor.ui.DockMarquee.scrolls(scale, case.bool("isStatus")))
    }

    @Test
    fun `how far the seek bar runs agrees with the shared cases`() = each("barLength") { case ->
        val bar = BarLength.of(
            case.getValue("duration").toDouble(),
            case.getValue("endsAt").takeIf { it != "-" }?.toDouble(),
            case.getValue("fallback").toDouble(),
        )
        assertEquals(case.why(), case.getValue("seconds").toDouble(), bar.seconds, 0.001)
        assertEquals(case.why(), case.bool("approximate"), bar.approximate)
    }

    @Test
    fun `the number at the bar's end and its room agree with the shared cases`() = each("barLabel") { case ->
        val label = com.przunk.protracktor.ui.barTotalText(case.getValue("seconds").toDouble(), case.bool("approximate"))
        assertEquals(case.why(), case.getValue("label"), label)
        assertEquals(case.why(), case.bool("fits"), com.przunk.protracktor.ui.fitsBarLabel(label))
    }

    @Test
    fun `what a search matches agrees with the shared cases`() = each("searchMatch") { case ->
        // The page decides the same thing in `rules.js`. Both sides split the query and look for
        // every word; the row that expects `no` for a run-on query is there on purpose.
        val author = case.getValue("author").takeIf { it != "-" } ?: ""
        assertEquals(
            case.why(),
            case.bool("expect"),
            com.przunk.protracktor.data.SearchTerms.matchesAny(
                case.getValue("query"), case.getValue("title"), author,
            ),
        )
    }

    @Test
    fun `HVSC's time tokens agree with the shared cases`() = each("songLengthTime") { case ->
        // The page reads the same tokens in `web/src/songlengths.js`, and a SID's whole length
        // comes from getting them right -- there is nothing in the file to fall back on.
        val expected = case.getValue("expect").takeIf { it != "-" }?.toDouble()
        assertEquals(
            case.why(),
            expected,
            com.przunk.protracktor.data.SongLengths.parseTime(case.getValue("token")),
        )
    }

    @Test
    fun `next agrees with the shared cases`() = each("next") { case ->
        val queue = queueAt(case.int("tracks"), case.int("at"), repeatOf(case["repeat"]))
        // `onTrackEnded` is the rule; `next()` is the button, and it deliberately ignores
        // repeat-one because a listener pressing next means next.
        assertEquals(case.why(), case.intOrNull("expect"), queue.onTrackEnded()?.currentIndex)
    }

    @Test
    fun `previous agrees with the shared cases`() = each("previous") { case ->
        val queue = queueAt(case.int("tracks"), case.int("at"), repeatOf(case["repeat"]))
        val moved = if (queue.hasPrevious) queue.previous().currentIndex else null
        assertEquals(case.why(), case.intOrNull("expect"), moved)
    }

    @Test
    fun `the subsong rule agrees with the shared cases`() = each("subsong") { case ->
        val answer = SubsongAdvance.after(
            playAll = case.bool("playAll"),
            subsong = case.int("subsong"),
            subsongCount = case.int("count"),
            repeatOne = case.bool("repeatOne"),
        )
        assertEquals(case.why(), case.intOrNull("expect"),
                     (answer as? SubsongAdvance.Next.Subsong)?.index)
    }

    @Test
    fun `play-from-the-end agrees with the shared cases`() = each("playFromEnd") { case ->
        assertEquals(
            case.why(),
            case.bool("expect"),
            PlayFromEnd.shouldRestart(
                engineSaysFinished = case.bool("engineFinished"),
                positionSeconds = case.int("position").toDouble(),
                durationSeconds = case.int("duration").toDouble(),
            ),
        )
    }

    /**
     * Modland's address rule, from the same file the page checks itself against.
     *
     * **Two runtimes, two encoders, and they disagreed.** Java's `URLEncoder` escapes everything
     * outside `A-Za-z0-9.-*_`; `encodeURIComponent` keeps seven more characters. Both URLs fetch the
     * same file — which is why it would have gone unnoticed until something compared them as
     * strings, and a queue decided the phone's copy of a track and its own were two tracks.
     */
    @Test
    fun `the Modland address agrees with the shared cases`() = each("modlandUrl") { case ->
        val path = listOf(case.getValue("format"), case.getValue("author"), case.getValue("title"))
            .joinToString("/")
        assertEquals(
            case.why(),
            "https://modland.com/pub/modules/" + case.getValue("expect"),
            com.przunk.protracktor.net.Modland.urlFor(path),
        )
    }

    /** ASMA's file address, the same way: the zip entry's path, escaped by the rule Modland uses. */
    @Test
    fun `the ASMA address agrees with the shared cases`() = each("asmaUrl") { case ->
        assertEquals(
            case.why(),
            "https://asma.atari.org/" + case.getValue("expect"),
            com.przunk.protracktor.net.Asma.fileUrlFor(case.getValue("path")),
        )
    }

    /** The format a refusal names (A51), from the file's address. */
    @Test
    fun `the format a refusal names agrees with the shared cases`() = each("refusalFormat") { case ->
        assertEquals(case.why(), case.getValue("expect").takeIf { it != "-" }, OpenFailure.modlandFormatOf(case.getValue("url")))
    }

    @Test
    fun `how long a tune shared as audio runs agrees with the shared cases`() = each("shareAudioPlan") { case ->
        val plan = AudioExport.plan(case.getValue("known").toDouble(), case.int("limit"))
        assertEquals(case.why(), case.getValue("seconds").toDouble(), plan.seconds, 0.0)
        assertEquals(case.why(), case.bool("fade"), plan.fade)
    }

    @Test
    fun `the fade of a tune shared as audio agrees with the shared cases`() = each("shareAudioFade") { case ->
        val gain = AudioExport.gainAt(case.getValue("frame").toLong(), case.getValue("total").toLong(), case.getValue("fadeFrames").toLong())
        assertEquals(case.why(), case.getValue("gain").toFloat(), gain, 0.001f)
    }

    @Test
    fun `the stored limit for sharing as audio agrees with the shared cases`() = each("shareAudioLimit") { case ->
        assertEquals(case.why(), case.int("expect"), AudioExport.limitFromStored(case.int("stored")))
    }

    @Test
    fun `the name of a tune shared as audio agrees with the shared cases`() = each("shareAudioName") { case ->
        val blank = { key: String -> case.getValue(key).takeIf { it != "-" }.orEmpty() }
        assertEquals(case.why(), case.getValue("expect"), AudioExport.fileName(blank("title"), blank("author")))
    }

    @Test
    fun `shuffle from a chosen tune agrees with the shared cases`() = each("shuffleFromTap") { case ->
        val count = case.int("tracks")
        val repeat = if (case.getValue("repeat") == "all") RepeatMode.PLAYLIST else RepeatMode.OFF
        // Something played before the tap, so "back stops at the tapped tune" has something to refuse.
        val before = PlayQueue(tracks = (0 until count).map { TrackRef(id = "t$it", title = "t$it") })
            .withShuffle(true).withRepeat(repeat).next().next()
        var q = before.startAt(case.int("tapped"))
        assertEquals(case.why() + ": back from the tapped tune", false, q.hasPrevious)
        if (q.hasNext) assertEquals(case.why() + ": back to the tapped tune", "t${case.int("tapped")}", q.next().previous().current!!.id)
        val played = mutableListOf(q.current!!.id)
        while (q.hasNext && played.size < count * 3) { q = q.next(); played += q.current!!.id }
        val stops = played.size < count * 3
        assertEquals(case.why(), "t${case.int("tapped")}", played.first())
        assertEquals(case.why(), case.intOrNull("played"), if (stops) played.size else null)
        assertEquals(case.why(), case.int("distinct"), played.take(count).toSet().size)
    }

    private companion object {
        const val RULES = "docs/rules/queue-cases.tsv"
    }
}
