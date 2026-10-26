package com.vincentwill.calm.health

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vincentwill.calm.CalmGraph
import com.vincentwill.calm.MainActivity
import com.vincentwill.calm.R
import com.vincentwill.calm.data.Settings
import com.vincentwill.calm.listener.CalmListenerService
import com.vincentwill.calm.pipeline.PipelineEvents
import com.vincentwill.calm.silence.Channels
import com.vincentwill.calm.silence.DndController
import com.vincentwill.calm.silence.canPostNotifications
import com.vincentwill.calm.util.SafeLog
import kotlinx.coroutines.launch

/**
 * Watches the notification listener while Calm is on. If it is gone for longer than
 * [HealthPolicy.GRACE_MS], silencing is paused (fail open) and the user is warned, because with
 * Calm's DND mode active and no listener, nothing would come through at all.
 */
class HealthMonitor(
    private val context: Context,
    private val settings: Settings,
    private val dnd: DndController,
    private val channels: Channels,
    private val events: PipelineEvents,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val component = ComponentName(context, CalmListenerService::class.java)

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    @Synchronized
    fun check(): HealthAction {
        val now = System.currentTimeMillis()
        val connected = events.listenerConnected.value
        val access = hasAccess()
        if (connected && access) {
            settings.listenerDisconnectedSince = null
        } else if (settings.listenerDisconnectedSince.let { it == null || it < bootTime(now) }) {
            // A disconnect from before a reboot (the listener goes away at shutdown) doesn't count:
            // Android binds the listener again shortly after boot, so the grace period starts now.
            settings.listenerDisconnectedSince = now
        }
        settings.lastHealthCheckAt = now

        val action = HealthPolicy.decide(
            enabled = settings.enabled,
            hasAccess = access,
            connected = connected,
            suspended = settings.silencingSuspended,
            disconnectedSince = settings.listenerDisconnectedSince,
            now = now,
        )
        SafeLog.d { "health: connected=$connected access=$access → $action" }
        when (action) {
            HealthAction.NOTHING -> if (settings.enabled) schedule(INTERVAL_MS) else cancel()
            HealthAction.REBIND -> {
                rebind(force = settings.silencingSuspended)
                schedule(RECHECK_MS)
            }
            HealthAction.FAILSAFE -> {
                dnd.deactivate()
                settings.silencingSuspended = true
                postWarning(accessRevoked = !access)
                rebind(force = true)
                schedule(RECHECK_MS)
                events.changed()
            }
            HealthAction.RESTORE -> {
                if (dnd.activate()) {
                    settings.silencingSuspended = false
                    NotificationManagerCompat.from(context).cancel(WARNING_ID)
                }
                schedule(INTERVAL_MS)
                events.changed()
            }
        }
        return action
    }

    fun onListenerDisconnected() {
        if (settings.listenerDisconnectedSince == null) settings.listenerDisconnectedSince = System.currentTimeMillis()
        if (settings.enabled) schedule(RECHECK_MS)
    }

    /** Called when Calm is switched on or off. */
    fun reset() {
        settings.silencingSuspended = false
        NotificationManagerCompat.from(context).cancel(WARNING_ID)
        if (settings.enabled) check() else cancel()
    }

    /**
     * [NotificationListenerService.requestRebind] is the official way. Toggling the component's
     * enabled state is the well-known stronger nudge that makes Android drop and re-create the
     * binding; it's only used when the listener has been gone for a while.
     */
    fun rebind(force: Boolean) {
        try {
            if (force) {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
            NotificationListenerService.requestRebind(component)
        } catch (e: RuntimeException) {
            SafeLog.w({ "rebind failed" }, e)
        }
    }

    private fun postWarning(accessRevoked: Boolean) {
        if (!canPostNotifications(context)) return
        channels.ensure()
        val open = PendingIntent.getActivity(
            context,
            WARNING_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (accessRevoked) {
            "Notification access was turned off, so no-tification paused Do Not Disturb. Tap to fix."
        } else {
            "no-tification isn't receiving notifications right now, so it paused Do Not Disturb. Nothing will be held until it reconnects."
        }
        val notification = NotificationCompat.Builder(context, channels.statusId)
            .setSmallIcon(R.drawable.ic_stat_calm)
            .setContentTitle("no-tification paused silencing")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(WARNING_ID, notification)
    }

    private fun bootTime(now: Long) = now - SystemClock.elapsedRealtime()

    private fun schedule(delayMs: Long) {
        // Inexact is fine for a watchdog and needs no permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pendingIntent())
    }

    private fun cancel() = alarms.cancel(pendingIntent())

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, HealthCheckReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val INTERVAL_MS = 15 * 60_000L
        const val RECHECK_MS = 60_000L
        const val WARNING_ID = 2
    }
}

class HealthCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = CalmGraph.get(context)
        val pending = goAsync()
        graph.scope.launch {
            try {
                graph.health.check()
                graph.cleanupIfDue()
            } finally {
                pending.finish()
            }
        }
    }
}
