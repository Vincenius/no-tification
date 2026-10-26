package com.vincentwill.calm.data

import com.vincentwill.calm.classify.MatchType
import com.vincentwill.calm.classify.Rule
import com.vincentwill.calm.classify.RuleAction
import com.vincentwill.calm.classify.RuleKind
import com.vincentwill.calm.digest.DigestSchedule
import com.vincentwill.calm.digest.DigestTime
import com.vincentwill.calm.digest.IntervalGenerator
import com.vincentwill.calm.digest.ScheduleGroup
import com.vincentwill.calm.digest.ScheduleMode
import com.vincentwill.calm.digest.ScheduleValidator
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RulesRepository(private val dao: RuleDao) {
    @Volatile
    private var cache: List<Rule>? = null

    suspend fun all(): List<Rule> = cache ?: dao.all().mapNotNull { it.toRule() }.also { cache = it }

    suspend fun add(rule: Rule): Long {
        val normalized = rule.copy(pattern = rule.pattern.trim())
        require(normalized.pattern.isNotEmpty()) { "pattern must not be empty" }
        all().firstOrNull { it.copy(id = 0) == normalized.copy(id = 0) }?.let { return it.id }
        val id = dao.insert(
            RuleEntity(
                kind = normalized.kind.name,
                matchType = normalized.matchType.name,
                pattern = normalized.pattern,
                packageName = normalized.packageName,
                action = normalized.action.name,
                createdAt = System.currentTimeMillis(),
            ),
        )
        cache = null
        return id
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        cache = null
    }

    private fun RuleEntity.toRule(): Rule? = runCatching {
        Rule(id, RuleKind.valueOf(kind), MatchType.valueOf(matchType), pattern, packageName, RuleAction.valueOf(action))
    }.getOrNull()
}

/** Owns the digest time list. Every mutation is followed by [onChanged] (rescheduling the alarm). */
class ScheduleRepository(
    private val dao: DigestTimeDao,
    private val settings: Settings,
    private val onChanged: suspend () -> Unit,
) {
    private val mutex = Mutex()

    suspend fun load(): DigestSchedule = DigestSchedule(
        settings.scheduleMode,
        dao.all().mapNotNull { e ->
            runCatching { DigestTime(e.id, ScheduleGroup.valueOf(e.scheduleGroup), e.minuteOfDay, e.enabled) }.getOrNull()
        },
    )

    suspend fun seedDefaultsIfNeeded() = mutex.withLock {
        if (settings.defaultsSeeded) return@withLock
        dao.insertAll(DEFAULT_TIMES.map { DigestTimeEntity(scheduleGroup = ScheduleGroup.ALL.name, minuteOfDay = it, enabled = true) })
        settings.defaultsSeeded = true
    }

    /** Duplicates in the same group are dropped silently. */
    suspend fun add(group: ScheduleGroup, minuteOfDay: Int) = change {
        dao.insert(DigestTimeEntity(scheduleGroup = group.name, minuteOfDay = minuteOfDay, enabled = true))
    }

    /** Moving a time onto one that already exists merges the two. */
    suspend fun update(id: Long, minuteOfDay: Int, enabled: Boolean) = change {
        val existing = dao.byId(id) ?: return@change
        val clash = dao.inGroup(existing.scheduleGroup).any { it.id != id && it.minuteOfDay == minuteOfDay }
        if (clash) dao.delete(id) else dao.update(existing.copy(minuteOfDay = minuteOfDay, enabled = enabled))
    }

    suspend fun delete(id: Long) = change { dao.delete(id) }

    /** Replaces the group's times with the generated ones. */
    suspend fun generate(group: ScheduleGroup, startMinute: Int, endMinute: Int, intervalMinutes: Int) {
        val minutes = IntervalGenerator.generate(startMinute, endMinute, intervalMinutes)
        change {
            dao.deleteGroup(group.name)
            dao.insertAll(minutes.map { DigestTimeEntity(scheduleGroup = group.name, minuteOfDay = it, enabled = true) })
        }
    }

    /**
     * Switches mode. Groups of the new mode that have never been filled start as a copy of the
     * times that apply today under the old mode, so switching never leaves you without digests.
     */
    suspend fun setMode(mode: ScheduleMode) = change {
        val old = load()
        if (old.mode == mode) return@change
        val today = LocalDate.now().dayOfWeek
        val seed = dao.inGroup(ScheduleGroup.groupFor(old.mode, today).name)
        for (group in ScheduleGroup.groupsFor(mode)) {
            if (dao.inGroup(group.name).isEmpty()) {
                dao.insertAll(seed.map { it.copy(id = 0, scheduleGroup = group.name) })
            }
        }
        settings.scheduleMode = mode
    }

    /** Pairs of active times closer than the minimum gap, per group. */
    suspend fun warnings(): Map<ScheduleGroup, List<Pair<Int, Int>>> {
        val schedule = load()
        return ScheduleGroup.groupsFor(schedule.mode).associateWith { group ->
            ScheduleValidator.tooClose(schedule.times.filter { it.group == group && it.enabled }.map { it.minuteOfDay })
        }.filterValues { it.isNotEmpty() }
    }

    private suspend fun change(block: suspend () -> Unit) {
        mutex.withLock { block() }
        onChanged()
    }

    companion object {
        val DEFAULT_TIMES = listOf(11 * 60, 17 * 60)
    }
}
