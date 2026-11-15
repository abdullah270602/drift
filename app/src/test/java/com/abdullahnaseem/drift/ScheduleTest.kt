package com.abdullahnaseem.drift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    // 2026-11-16 is a Monday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 16 + day, h, m)

    @Test fun daytimeRange() {
        val s = Schedule(days = 0b0011111, start = 9 * 60, end = 17 * 60)
        assertTrue(s.matches(at(0, 9)))
        assertFalse(s.matches(at(0, 17)))
        assertFalse(s.matches(at(5, 12))) // Saturday
        assertEquals(at(0, 17), s.nextEdge(at(0, 12)))
        assertEquals(at(1, 9), s.nextEdge(at(0, 18)))
    }

    @Test fun overnightBelongsToStartDay() {
        val s = Schedule(days = 0b0010000, start = 22 * 60, end = 6 * 60) // Friday night
        assertTrue(s.matches(at(4, 23)))
        assertTrue(s.matches(at(5, 3)))   // early Saturday still counts
        assertFalse(s.matches(at(5, 23)))
        assertFalse(s.matches(at(4, 3)))  // early Friday belongs to Thursday
    }

    @Test fun allDayAndPriority() {
        val weekend = Collection(2, "w", listOf("b"), schedule = Schedule(days = 0b1100000, start = 0, end = 0))
        val always = Collection(1, "a", listOf("a"))
        val lockOnly = Collection(3, "l", listOf("c"), target = Target.LOCK)
        val st = State(listOf(always, weekend, lockOnly))
        assertEquals(2L, st.activeFor(Surface.HOME, at(6, 12))?.id)
        assertEquals(1L, st.activeFor(Surface.HOME, at(2, 12))?.id)
        assertEquals(2L, st.activeFor(Surface.LOCK, at(6, 12))?.id)
        assertEquals(1L, st.activeFor(Surface.LOCK, at(2, 12))?.id) // list order among always-on
        assertEquals(at(7, 0), weekend.schedule!!.nextEdge(at(6, 12)))
    }
}

class WordingTest {
    @Test fun dayRanges() {
        assertEquals("Weekdays", daysText(0b0011111))
        assertEquals("Sun–Fri", daysText(0b1011111))
        assertEquals("Sat–Mon", daysText(0b1100001))
        assertEquals("Mon, Wed", daysText(0b0000101))
        assertEquals("Mon, Tue", daysText(0b0000011))
    }
}
