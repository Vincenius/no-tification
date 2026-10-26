package com.vincentwill.calm.digest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vincentwill.calm.CalmGraph
import com.vincentwill.calm.util.SafeLog
import java.time.Instant
import kotlinx.coroutines.launch

/** The digest alarm fired: deliver, then schedule the following digest. */
class DigestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = CalmGraph.get(context)
        val pending = goAsync()
        graph.scope.launch {
            try {
                // Was this alarm meant for later (an inexact alarm firing early)? Then don't refire it.
                val scheduledFor = graph.settings.nextDigestAt?.let(Instant::ofEpochMilli)
                val now = Instant.now()
                val released = graph.delivery.deliver()
                SafeLog.d { "digest delivered, $released new items" }
                graph.scheduler.reschedule(after = maxOf(now, scheduledFor ?: now))
                graph.events.changed()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.vincentwill.calm.DIGEST_ALARM"
    }
}

/** Alarms don't survive reboots and must follow clock/timezone changes, so reschedule on all of them. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = CalmGraph.get(context)
        val pending = goAsync()
        graph.scope.launch {
            try {
                SafeLog.d { "system event ${intent.action}, rescheduling" }
                graph.scheduler.reschedule()
                if (intent.action == Intent.ACTION_BOOT_COMPLETED && graph.settings.enabled) graph.dnd.activate()
            } finally {
                pending.finish()
            }
        }
    }
}
