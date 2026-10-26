package com.vincentwill.calm.digest

import java.time.ZonedDateTime

object NextDigestCalculator {
    fun next(schedule: DigestSchedule, after: ZonedDateTime): ZonedDateTime? =
        upcoming(schedule, after, 1).firstOrNull()

    /**
     * The next [count] digest moments strictly after [after], in [after]'s zone.
     *
     * DST: a time inside the spring-forward gap moves to after the gap (02:30 → 03:30). A time in the
     * fall-back overlap fires once, at its first occurrence.
     */
    fun upcoming(schedule: DigestSchedule, after: ZonedDateTime, count: Int): List<ZonedDateTime> {
        if (!schedule.hasAnyActiveTime || count <= 0) return emptyList()
        val result = mutableListOf<ZonedDateTime>()
        var date = after.toLocalDate()
        // A group can be empty on most days (e.g. only Mondays), so look far enough ahead.
        repeat(7 * count + 1) {
            for (time in schedule.timesFor(date.dayOfWeek)) {
                val candidate = ZonedDateTime.of(date, time, after.zone)
                if (candidate.isAfter(after) && candidate !in result) {
                    result += candidate
                    if (result.size == count) return result
                }
            }
            date = date.plusDays(1)
        }
        return result
    }
}

object IntervalGenerator {
    const val MIN_INTERVAL_MINUTES = 15

    /**
     * "Every [intervalMinutes] from [startMinute] to [endMinute]" (both inclusive, minutes of day).
     * An end before the start means the window crosses midnight.
     */
    fun generate(startMinute: Int, endMinute: Int, intervalMinutes: Int): List<Int> {
        require(startMinute in 0 until MINUTES_PER_DAY && endMinute in 0 until MINUTES_PER_DAY) { "time out of range" }
        require(intervalMinutes >= MIN_INTERVAL_MINUTES) { "interval must be at least $MIN_INTERVAL_MINUTES minutes" }
        val span = if (endMinute >= startMinute) endMinute - startMinute else endMinute + MINUTES_PER_DAY - startMinute
        return (0..span step intervalMinutes).map { (startMinute + it) % MINUTES_PER_DAY }.distinct().sorted()
    }
}

object ScheduleValidator {
    const val MIN_GAP_MINUTES = 15

    fun normalize(minutes: List<Int>): List<Int> =
        minutes.filter { it in 0 until MINUTES_PER_DAY }.distinct().sorted()

    /**
     * Neighbouring times closer than [MIN_GAP_MINUTES], including the wrap from the last time of the
     * day to the first time of the next. Used for warnings only; the times are still kept.
     */
    fun tooClose(minutes: List<Int>): List<Pair<Int, Int>> {
        val sorted = normalize(minutes)
        if (sorted.size < 2) return emptyList()
        val pairs = sorted.zipWithNext().filter { (a, b) -> b - a < MIN_GAP_MINUTES }.toMutableList()
        val wrapGap = sorted.first() + MINUTES_PER_DAY - sorted.last()
        if (wrapGap < MIN_GAP_MINUTES) pairs += sorted.last() to sorted.first()
        return pairs
    }
}

const val MINUTES_PER_DAY = 24 * 60
