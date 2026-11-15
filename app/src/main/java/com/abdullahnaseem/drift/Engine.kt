package com.abdullahnaseem.drift

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The whole background story: an inexact alarm wakes [Engine.Receiver], we set whatever is due,
 * book the next alarm, and the process is free to die. Nothing stays resident.
 */
object Engine {
    private const val TAG = "Drift"
    private const val WINDOW_MS = 10 * 60_000L // the system grants inexact alarms at least 10 min

    /** Apply anything due now (or everything, if [force]) and book the next wake-up. */
    @Synchronized
    fun run(ctx: Context, force: Boolean = false) {
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        val state = Store.load(ctx)
        val home = state.activeFor(Surface.HOME, now)
        val lock = state.activeFor(Surface.LOCK, now)

        fun due(s: Surface, c: Collection?): Boolean {
            c ?: return false
            val shown = state.shown[s] ?: return true
            return force || shown.collectionId != c.id || c.photos.none { it == shown.photo } ||
                nowMs >= shown.at + c.intervalMin * 60_000L - 60_000L
        }

        val shown = state.shown.toMutableMap()
        val bags = state.bags.toMutableMap()

        fun apply(c: Collection, surfaces: List<Surface>) {
            val flags = surfaces.fold(0) { f, s -> f or if (s == Surface.HOME) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK }
            val current = surfaces.firstNotNullOfOrNull { shown[it]?.photo }
            var bag = bags[c.id].orEmpty().filter { it in c.photos }
            repeat(c.photos.size) {
                if (bag.isEmpty()) bag = c.photos.shuffled().let { if (it.size > 1 && it[0] == current) it.drop(1) + it[0] else it }
                val photo = bag.first(); bag = bag.drop(1)
                if (setWallpaper(ctx, Uri.parse(photo), flags)) {
                    surfaces.forEach { shown[it] = Shown(c.id, photo, nowMs) }
                    bags[c.id] = bag
                    return
                }
            }
            Log.w(TAG, "No readable photo in collection ${c.id}")
        }

        val dueHome = due(Surface.HOME, home)
        val dueLock = due(Surface.LOCK, lock)
        if (home != null && home.id == lock?.id && (dueHome || dueLock)) {
            apply(home, listOf(Surface.HOME, Surface.LOCK))
        } else {
            if (dueHome) apply(home!!, listOf(Surface.HOME))
            if (dueLock) apply(lock!!, listOf(Surface.LOCK))
        }

        // Merge into the latest state: the UI may have saved while we were busy.
        val saved = Store.update(ctx) { it.copy(shown = shown, bags = bags.filterKeys { id -> it.collections.any { c -> c.id == id } }) }
        schedule(ctx, saved, now)
    }

    private fun setWallpaper(ctx: Context, uri: Uri, flags: Int): Boolean = try {
        ctx.contentResolver.openInputStream(uri)!!.use {
            WallpaperManager.getInstance(ctx).setStream(it, null, true, flags)
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Can't use $uri", e)
        false
    }

    private fun schedule(ctx: Context, s: State, now: LocalDateTime) {
        val zone = ZoneId.systemDefault()
        val times = buildList {
            for (surface in Surface.entries) {
                val c = s.activeFor(surface, now) ?: continue
                if (c.photos.size > 1) s.shown[surface]?.let { add(it.at + c.intervalMin * 60_000L) }
            }
            s.collections.filter { it.enabled && it.photos.isNotEmpty() }.forEach { c ->
                c.schedule?.let { add(it.nextEdge(now).atZone(zone).toInstant().toEpochMilli()) }
            }
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, Receiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = times.minOrNull()
        if (next == null) am.cancel(pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MS, pi)
        Log.i(TAG, "Next check: ${next?.let { java.time.Instant.ofEpochMilli(it) } ?: "none"}")
    }

    /** Alarm, boot, clock change and app update all mean the same thing: catch up and re-book. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val pending = goAsync()
            Thread {
                try { run(ctx.applicationContext) } finally { pending.finish() }
            }.start()
        }
    }
}
