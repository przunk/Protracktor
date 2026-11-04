// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.alarms

import org.json.JSONArray
import org.json.JSONObject

class AlarmDataVersionException(val version: Int) : IllegalStateException("Unsupported alarm data version $version")

/** Strict, additive alarm storage; a newer/corrupt file is an error, never an empty replacement. */
object AlarmCodec {
    fun encode(value: AlarmBook): String {
        val book = value.frozen()
        return JSONObject().apply {
            put("version", AlarmLimits.STORE_VERSION)
            put("revision", book.revision)
            put("songs", JSONArray(book.songs.map(::songJson)))
            put("alarms", JSONArray(book.alarms.map { a -> JSONObject().apply {
                put("id", a.id); put("hour", a.hour); put("minute", a.minute); put("label", a.label)
                put("weekdays", JSONArray(a.weekdays.sorted())); put("songIds", JSONArray(a.songIds))
                put("enabled", a.enabled); put("nextAtMillis", a.nextAtMillis)
                put("occurrenceDate", a.occurrenceDate ?: JSONObject.NULL)
                put("consumedDate", a.consumedDate ?: JSONObject.NULL)
            } }))
            put("ringing", book.ringing?.let { r -> JSONObject().apply {
                put("token", r.token); put("alarmIds", JSONArray(r.alarmIds.sorted()))
                put("song", songJson(r.song))
                put("quiz", r.quiz?.let { q -> JSONObject().apply {
                    put("category", q.category.name)
                    put("choices", JSONArray(q.choices.map { JSONObject().put("id", it.id).put("value", it.value) }))
                    put("correctId", q.correctId)
                } } ?: JSONObject.NULL)
                put("startedElapsed", r.startedElapsed)
                put("deadlineElapsed", r.deadlineElapsed); put("bootCount", r.bootCount)
                put("preview", r.preview); put("backup", r.backup)
            } } ?: JSONObject.NULL)
            put("outcome", book.lastOutcome?.let { o -> JSONObject().apply {
                put("alarmIds", JSONArray(o.alarmIds.sorted())); put("atMillis", o.atMillis); put("kind", o.kind.name)
            } } ?: JSONObject.NULL)
        }.toString()
    }

    fun decode(text: String): AlarmBook {
        val root = JSONObject(text)
        val version = root.int("version")
        if (version != AlarmLimits.STORE_VERSION) throw AlarmDataVersionException(version)
        val songs = root.array("songs", AlarmLimits.MAX_SONGS).map { song(it as JSONObject) }
        val alarms = root.array("alarms", AlarmLimits.MAX_ALARMS).map { value ->
            val a = value as JSONObject
            MusicAlarm(a.string("id"), a.int("hour"), a.int("minute"), a.string("label"),
                a.array("weekdays", 7).map { number(it).also { day -> require(day in 1L..7L) }.toInt() }.toSet(),
                a.strings("songIds", AlarmLimits.MAX_SONGS), a.boolean("enabled"), a.long("nextAtMillis"),
                a.nullableString("occurrenceDate"), a.nullableString("consumedDate"))
        }
        val ringing = root.nullableObject("ringing")?.let { r ->
            val quiz = r.nullableObject("quiz")?.let { q ->
                val choices = q.array("choices", 3).map { c -> (c as JSONObject).let { AlarmChoice(it.string("id"), it.string("value")) } }
                AlarmQuiz(AlarmQuestion.valueOf(q.string("category")), choices, q.string("correctId"))
            }
            // An active cohort may retain IDs of schedules deleted while it was ringing.
            RingingAlarm(r.string("token"), r.strings("alarmIds", 2048).toSet(),
                song(r.getJSONObject("song")), quiz,
                r.long("startedElapsed"), r.long("deadlineElapsed"), r.int("bootCount"), r.boolean("preview"), r.boolean("backup"))
        }
        val outcome = root.nullableObject("outcome")?.let {
            val ids = it.strings("alarmIds", 2048).toSet()
            require(ids.isNotEmpty() && ids.all(AlarmLimits::validId))
            AlarmOutcome(ids, it.long("atMillis").also { time -> require(time >= 0) }, AlarmOutcomeKind.valueOf(it.string("kind")))
        }
        return AlarmBook(root.long("revision"), songs, alarms, ringing, outcome).frozen()
    }

    private fun songJson(s: AlarmSong) = JSONObject().apply {
        put("id", s.id); put("sourceKey", s.sourceKey); put("title", s.title); put("author", s.author)
        put("platformId", s.platformId ?: JSONObject.NULL); put("catalogueId", s.catalogueId ?: JSONObject.NULL)
        put("durationMillis", s.durationMillis); put("year", s.year)
        put("authorAlternatives", JSONArray(s.authorAlternatives)); put("yearAlternatives", JSONArray(s.yearAlternatives))
    }

    private fun song(s: JSONObject) = AlarmSong(s.string("id"), s.string("sourceKey"), s.string("title"), s.string("author"),
        s.nullableString("platformId"), s.nullableString("catalogueId"), s.long("durationMillis"),
        if (s.has("year")) s.string("year") else "",
        if (s.has("authorAlternatives")) s.array("authorAlternatives", 16).map { it as String } else emptyList(),
        if (s.has("yearAlternatives")) s.array("yearAlternatives", 16).map { it as String } else emptyList())

    private fun number(value: Any): Long {
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
    private fun JSONObject.long(key: String) = number(get(key))
    private fun JSONObject.int(key: String) = long(key).also { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }.toInt()
    private fun JSONObject.string(key: String) = (get(key) as String).also { require(it.length <= 1024) }
    private fun JSONObject.boolean(key: String) = get(key) as Boolean
    private fun JSONObject.nullableString(key: String) = if (get(key) == JSONObject.NULL) null else string(key)
    private fun JSONObject.nullableObject(key: String) = if (get(key) == JSONObject.NULL) null else getJSONObject(key)
    private fun JSONObject.array(key: String, limit: Int): List<Any> {
        val value = getJSONArray(key)
        require(value.length() <= limit)
        return (0 until value.length()).map(value::get)
    }
    private fun JSONObject.strings(key: String, limit: Int) = array(key, limit).map { (it as String).also { id -> require(AlarmLimits.validId(id)) } }
}
