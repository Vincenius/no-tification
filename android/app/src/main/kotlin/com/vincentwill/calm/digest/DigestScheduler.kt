package com.vincentwill.calm.digest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vincentwill.calm.data.ScheduleRepository
import com.vincentwill.calm.data.Settings
import com.vincentwill.calm.util.SafeLog
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Keeps exactly one alarm scheduled: the next digest. When it fires, [DigestAlarmReceiver] delivers
 * the digest and calls [reschedule] for the one after.
 */
class DigestScheduler(
    private val context: Context,
    private val settings: Settings,
    private val schedule: () -> ScheduleRepository,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * Cancels the current alarm and schedules the next one strictly after [after]. Passing the time
     * the alarm was meant for prevents firing twice when an inexact alarm arrives a bit early.
     */
    suspend fun reschedule(after: Instant = Instant.now()): Long? {
        val pending = pendingIntent()
        alarms.cancel(pending)

        val next = NextDigestCalculator.next(schedule().load(), ZonedDateTime.ofInstant(after, ZoneId.systemDefault()))
        if (next == null) {
            settings.nextDigestAt = null
            SafeLog.d { "no digest scheduled" }
            return null
        }
        val at = next.toInstant().toEpochMilli()
        val exact = canScheduleExact() && try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            true
        } catch (_: SecurityException) {
            false
        }
        if (!exact) alarms.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, pending)

        settings.nextDigestAt = at
        settings.nextDigestExact = exact
        SafeLog.d { "next digest at $next (exact=$exact)" }
        return at
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, DigestAlarmReceiver::class.java).setAction(DigestAlarmReceiver.ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val INEXACT_WINDOW_MS = 10 * 60 * 1000L
    }
}
