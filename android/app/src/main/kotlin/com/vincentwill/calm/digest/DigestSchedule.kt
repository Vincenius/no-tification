package com.vincentwill.calm.digest

import java.time.DayOfWeek
import java.time.LocalTime

enum class ScheduleMode { EVERY_DAY, WEEKDAYS_WEEKENDS, PER_DAY }

/** Which list of times a [DigestTime] belongs to. Each mode uses its own groups. */
enum class ScheduleGroup {
    ALL, WEEKDAYS, WEEKENDS, MON, TUE, WED, THU, FRI, SAT, SUN;

    companion object {
        fun groupsFor(mode: ScheduleMode): List<ScheduleGroup> = when (mode) {
            ScheduleMode.EVERY_DAY -> listOf(ALL)
            ScheduleMode.WEEKDAYS_WEEKENDS -> listOf(WEEKDAYS, WEEKENDS)
            ScheduleMode.PER_DAY -> listOf(MON, TUE, WED, THU, FRI, SAT, SUN)
        }

        fun groupFor(mode: ScheduleMode, day: DayOfWeek): ScheduleGroup = when (mode) {
            ScheduleMode.EVERY_DAY -> ALL
            ScheduleMode.WEEKDAYS_WEEKENDS ->
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) WEEKENDS else WEEKDAYS
            ScheduleMode.PER_DAY -> entries[MON.ordinal + day.ordinal]
        }
    }
}

data class DigestTime(
    val id: Long = 0,
    val group: ScheduleGroup,
    val minuteOfDay: Int,
    val enabled: Boolean = true,
) {
    val localTime: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
}

/** All stored times; only the groups of [mode] are active, the others are kept for switching back. */
data class DigestSchedule(val mode: ScheduleMode, val times: List<DigestTime>) {
    fun timesFor(day: DayOfWeek): List<LocalTime> {
        val group = ScheduleGroup.groupFor(mode, day)
        return times.filter { it.enabled && it.group == group }
            .map { it.minuteOfDay }.distinct().sorted()
            .map { LocalTime.of(it / 60, it % 60) }
    }

    val hasAnyActiveTime: Boolean
        get() = ScheduleGroup.groupsFor(mode).let { groups -> times.any { it.enabled && it.group in groups } }
}
