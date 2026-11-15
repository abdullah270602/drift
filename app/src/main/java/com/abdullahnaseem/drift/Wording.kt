package com.abdullahnaseem.drift

import android.content.Context

// ── Wording, shared by the list and the editor.

val Intervals = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hr", 180 to "3 hr", 360 to "6 hr", 720 to "12 hr", 1440 to "Daily")

fun intervalLabel(min: Int) = Intervals.firstOrNull { it.first == min }?.second ?: "$min min"

fun intervalText(min: Int) = when (min) {
    1440 -> "daily"
    60 -> "hourly"
    else -> "every " + (Intervals.firstOrNull { it.first == min }?.second ?: "$min min")
}

fun targetText(t: Target) = when (t) { Target.HOME -> "Home"; Target.LOCK -> "Lock"; Target.BOTH -> "Home & lock" }

private val DayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun daysText(days: Int) = when (days) {
    0b1111111 -> "Every day"
    0b0011111 -> "Weekdays"
    0b1100000 -> "Weekends"
    0 -> "No days"
    else -> {
        val on = (0..6).filter { days and (1 shl it) != 0 }
        // One unbroken run, wrapping past Sunday, reads better as a range: "Sun–Thu".
        val start = on.firstOrNull { days and (1 shl ((it + 6) % 7)) == 0 }
        val run = start?.let { s -> (0 until on.size).map { (s + it) % 7 } }
        if (on.size >= 3 && run != null && run.all { it in on }) "${DayNames[run.first()]}–${DayNames[run.last()]}"
        else on.joinToString(", ") { DayNames[it] }
    }
}

fun timeText(ctx: Context, minute: Int): String {
    val h = minute / 60; val m = minute % 60
    return if (android.text.format.DateFormat.is24HourFormat(ctx)) "%d:%02d".format(h, m)
    else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

fun scheduleText(ctx: Context, s: Schedule?) = when {
    s == null -> "Always"
    s.allDay -> daysText(s.days)
    else -> "${daysText(s.days)} · ${timeText(ctx, s.start)}–${timeText(ctx, s.end)}"
}
