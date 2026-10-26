package com.vincentwill.calm.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextDigestCalculatorTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, berlin)
    private fun t(h: Int, m: Int = 0, group: ScheduleGroup = ScheduleGroup.ALL, enabled: Boolean = true) =
        DigestTime(group = group, minuteOfDay = h * 60 + m, enabled = enabled)

    private fun everyDay(vararg times: DigestTime) = DigestSchedule(ScheduleMode.EVERY_DAY, times.toList())

    // 2026-10-26 is a Monday.
    private val mondayMorning = at(2026, 10, 26, 9, 0)

    @Test
    fun `picks the next time later today`() {
        val s = everyDay(t(11), t(17))
        assertEquals(at(2026, 10, 26, 11, 0), NextDigestCalculator.next(s, mondayMorning))
        assertEquals(at(2026, 10, 26, 17, 0), NextDigestCalculator.next(s, at(2026, 10, 26, 12, 0)))
    }

    @Test
    fun `unsorted input with many times`() {
        val s = everyDay(t(20), t(8), t(14, 30), t(10), t(18))
        assertEquals(at(2026, 10, 26, 10, 0), NextDigestCalculator.next(s, mondayMorning))
    }

    @Test
    fun `a digest exactly now is not the next one`() {
        val s = everyDay(t(11), t(17))
        assertEquals(at(2026, 10, 26, 17, 0), NextDigestCalculator.next(s, at(2026, 10, 26, 11, 0)))
    }

    @Test
    fun `after the last time of the day it rolls over to tomorrow`() {
        val s = everyDay(t(11), t(17))
        assertEquals(at(2026, 10, 27, 11, 0), NextDigestCalculator.next(s, at(2026, 10, 26, 18, 0)))
    }

    @Test
    fun `crossing midnight`() {
        val s = everyDay(t(0, 10), t(23, 55))
        assertEquals(at(2026, 10, 26, 23, 55), NextDigestCalculator.next(s, at(2026, 10, 26, 23, 50)))
        assertEquals(at(2026, 10, 27, 0, 10), NextDigestCalculator.next(s, at(2026, 10, 26, 23, 56)))
    }

    @Test
    fun `empty schedule has no next digest`() {
        assertNull(NextDigestCalculator.next(everyDay(), mondayMorning))
    }

    @Test
    fun `disabled times are skipped and all disabled means none`() {
        assertEquals(
            at(2026, 10, 26, 17, 0),
            NextDigestCalculator.next(everyDay(t(11, enabled = false), t(17)), mondayMorning),
        )
        assertNull(NextDigestCalculator.next(everyDay(t(11, enabled = false)), mondayMorning))
    }

    @Test
    fun `weekdays and weekends use separate lists`() {
        val s = DigestSchedule(
            ScheduleMode.WEEKDAYS_WEEKENDS,
            listOf(
                t(9, group = ScheduleGroup.WEEKDAYS),
                t(13, group = ScheduleGroup.WEEKDAYS),
                t(17, group = ScheduleGroup.WEEKDAYS),
                t(10, group = ScheduleGroup.WEEKENDS),
            ),
        )
        // Friday evening → Saturday 10:00.
        assertEquals(at(2026, 10, 31, 10, 0), NextDigestCalculator.next(s, at(2026, 10, 30, 18, 0)))
        // Sunday after 10:00 → Monday 09:00.
        assertEquals(at(2026, 11, 2, 9, 0), NextDigestCalculator.next(s, at(2026, 11, 1, 11, 0)))
    }

    @Test
    fun `per-day schedule with only one day skips ahead a week`() {
        val s = DigestSchedule(ScheduleMode.PER_DAY, listOf(t(8, group = ScheduleGroup.MON)))
        assertEquals(at(2026, 11, 2, 8, 0), NextDigestCalculator.next(s, mondayMorning))
    }

    @Test
    fun `times of inactive groups are ignored`() {
        // Stored per-day times must not leak into every-day mode.
        val s = DigestSchedule(ScheduleMode.EVERY_DAY, listOf(t(10, group = ScheduleGroup.MON), t(15)))
        assertEquals(at(2026, 10, 26, 15, 0), NextDigestCalculator.next(s, mondayMorning))
        assertNull(NextDigestCalculator.next(DigestSchedule(ScheduleMode.EVERY_DAY, listOf(t(10, group = ScheduleGroup.MON))), mondayMorning))
    }

    @Test
    fun `upcoming lists the next three across days`() {
        val s = everyDay(t(9), t(14), t(17))
        assertEquals(
            listOf(at(2026, 10, 26, 14, 0), at(2026, 10, 26, 17, 0), at(2026, 10, 27, 9, 0)),
            NextDigestCalculator.upcoming(s, at(2026, 10, 26, 12, 0), 3),
        )
    }

    @Test
    fun `DST spring forward - time inside the gap moves past the gap`() {
        // 2026-03-29: clocks jump from 02:00 to 03:00 in Berlin.
        val s = everyDay(t(2, 30))
        val next = NextDigestCalculator.next(s, at(2026, 3, 29, 1, 0))!!
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
        assertEquals(29, next.dayOfMonth)
    }

    @Test
    fun `DST spring forward - regular times keep their wall clock time`() {
        val s = everyDay(t(11))
        val next = NextDigestCalculator.next(s, at(2026, 3, 28, 12, 0))!!
        assertEquals(at(2026, 3, 29, 11, 0), next)
        assertEquals("+02:00", next.offset.id)
    }

    @Test
    fun `DST fall back - time in the overlap fires once`() {
        // 2026-10-25: clocks go back from 03:00 to 02:00 in Berlin, so 02:30 happens twice.
        val s = everyDay(t(2, 30))
        val first = NextDigestCalculator.next(s, at(2026, 10, 25, 1, 0))!!
        assertEquals("+02:00", first.offset.id)
        // Right after the first 02:30 the next digest is the following day, not the second 02:30.
        val second = NextDigestCalculator.next(s, first.plusMinutes(1))!!
        assertEquals(26, second.dayOfMonth)
        assertEquals(2, second.hour)
        assertEquals(30, second.minute)
    }

    @Test
    fun `DST fall back - day after keeps wall clock time`() {
        val next = NextDigestCalculator.next(everyDay(t(11)), at(2026, 10, 24, 12, 0))!!
        assertEquals(at(2026, 10, 25, 11, 0), next)
        assertEquals("+01:00", next.offset.id)
    }
}
