package com.abdullahnaseem.drift

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Target { HOME, LOCK, BOTH;
    fun covers(s: Surface) = this == BOTH || name == s.name
}

enum class Surface { HOME, LOCK }

/**
 * When a collection is active. [days] is a bitmask, bit 0 = Monday … bit 6 = Sunday.
 * Times are minutes of day. start == end means all day; end < start runs past midnight
 * and belongs to the day it starts on.
 */
data class Schedule(val days: Int = 0b0011111, val start: Int = 9 * 60, val end: Int = 17 * 60) {
    val allDay get() = start == end

    private fun hasDay(t: LocalDateTime) = days and (1 shl (t.dayOfWeek.value - 1)) != 0

    fun matches(t: LocalDateTime): Boolean {
        val m = t.hour * 60 + t.minute
        return when {
            allDay -> hasDay(t)
            start < end -> hasDay(t) && m >= start && m < end
            else -> (hasDay(t) && m >= start) || (hasDay(t.minusDays(1)) && m < end)
        }
    }

    /** Earliest moment after [now] at which [matches] may flip. */
    fun nextEdge(now: LocalDateTime): LocalDateTime {
        val midnight = now.truncatedTo(ChronoUnit.DAYS)
        val edges = if (allDay) listOf(0) else listOf(start, end)
        return (0..8L).asSequence()
            .flatMap { d -> edges.map { midnight.plusDays(d).plusMinutes(it.toLong()) } }
            .first { it.isAfter(now) }
    }
}

data class Collection(
    val id: Long,
    val name: String,
    val photos: List<String>,
    val target: Target = Target.BOTH,
    val intervalMin: Int = 60,
    val schedule: Schedule? = null,
    val enabled: Boolean = true,
)

/** What is currently on a surface, so we know when it's due to change. */
data class Shown(val collectionId: Long, val photo: String, val at: Long)

data class State(
    val collections: List<Collection> = emptyList(),
    val shown: Map<Surface, Shown> = emptyMap(),
    /** Per collection: photos not yet shown this round, so shuffles don't repeat early. */
    val bags: Map<Long, List<String>> = emptyMap(),
) {
    /** Scheduled collections win over always-on ones; ties go to list order. */
    fun activeFor(s: Surface, now: LocalDateTime): Collection? {
        val live = collections.filter { it.enabled && it.photos.isNotEmpty() && it.target.covers(s) }
        return live.firstOrNull { it.schedule?.matches(now) == true }
            ?: live.firstOrNull { it.schedule == null }
    }
}

object Store {
    private fun file(c: Context) = File(c.filesDir, "state.json")

    @Synchronized
    fun load(c: Context): State = runCatching { decode(JSONObject(file(c).readText())) }.getOrDefault(State())

    @Synchronized
    fun save(c: Context, s: State) {
        val tmp = File(c.filesDir, "state.json.tmp")
        tmp.writeText(encode(s).toString())
        tmp.renameTo(file(c))
    }

    @Synchronized
    fun update(c: Context, f: (State) -> State): State = f(load(c)).also { save(c, it) }

    fun encode(s: State) = JSONObject().apply {
        put("collections", JSONArray(s.collections.map { c ->
            JSONObject().apply {
                put("id", c.id); put("name", c.name); put("photos", JSONArray(c.photos))
                put("target", c.target.name); put("interval", c.intervalMin); put("enabled", c.enabled)
                c.schedule?.let { put("schedule", JSONObject().put("days", it.days).put("start", it.start).put("end", it.end)) }
            }
        }))
        put("shown", JSONObject().apply {
            s.shown.forEach { (k, v) -> put(k.name, JSONObject().put("c", v.collectionId).put("p", v.photo).put("at", v.at)) }
        })
        put("bags", JSONObject().apply { s.bags.forEach { (k, v) -> put(k.toString(), JSONArray(v)) } })
    }

    fun decode(o: JSONObject): State {
        fun JSONArray.strings() = List(length()) { getString(it) }
        val cs = o.optJSONArray("collections") ?: JSONArray()
        return State(
            collections = List(cs.length()) { i ->
                val c = cs.getJSONObject(i)
                Collection(
                    id = c.getLong("id"), name = c.getString("name"), photos = c.getJSONArray("photos").strings(),
                    target = Target.valueOf(c.getString("target")), intervalMin = c.getInt("interval"),
                    enabled = c.optBoolean("enabled", true),
                    schedule = c.optJSONObject("schedule")?.let { Schedule(it.getInt("days"), it.getInt("start"), it.getInt("end")) },
                )
            },
            shown = o.optJSONObject("shown")?.let { sh ->
                sh.keys().asSequence().associate { k ->
                    val v = sh.getJSONObject(k)
                    Surface.valueOf(k) to Shown(v.getLong("c"), v.getString("p"), v.getLong("at"))
                }
            } ?: emptyMap(),
            bags = o.optJSONObject("bags")?.let { b ->
                b.keys().asSequence().associate { it.toLong() to b.getJSONArray(it).strings() }
            } ?: emptyMap(),
        )
    }
}
