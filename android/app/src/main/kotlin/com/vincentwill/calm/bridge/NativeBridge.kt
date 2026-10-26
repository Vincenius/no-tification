package com.vincentwill.calm.bridge

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.vincentwill.calm.CalmGraph
import com.vincentwill.calm.classify.MatchType
import com.vincentwill.calm.classify.Rule
import com.vincentwill.calm.classify.RuleAction
import com.vincentwill.calm.classify.RuleKind
import com.vincentwill.calm.classify.RulesEngine
import com.vincentwill.calm.data.NotificationEntity
import com.vincentwill.calm.digest.NextDigestCalculator
import com.vincentwill.calm.digest.ScheduleGroup
import com.vincentwill.calm.digest.ScheduleMode
import com.vincentwill.calm.listener.CalmListenerService
import com.vincentwill.calm.listener.ContentIntentStore
import com.vincentwill.calm.silence.canPostNotifications
import com.vincentwill.calm.util.SafeLog
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Flutter ↔ native boundary. Flutter only renders; all state lives on the native side.
 *
 * Method channel: request/response calls. Event channel: maps tagged with "type" ("changed" or
 * "route"); Flutter re-fetches whatever it shows when it sees "changed".
 */
class NativeBridge(private val activity: Activity, messenger: BinaryMessenger) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val graph = CalmGraph.get(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventJob: Job? = null
    private var sink: EventChannel.EventSink? = null

    init {
        MethodChannel(messenger, METHOD_CHANNEL).setMethodCallHandler(this)
        EventChannel(messenger, EVENT_CHANNEL).setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            // Status & permissions
            "getStatus" -> respond(result) { status() }
            "setEnabled" -> respond(result) { graph.setEnabled(call.arg("enabled")) }
            "openNotificationAccessSettings" -> launch(result) { notificationAccessIntent() }
            "openDndAccessSettings" -> launch(result) { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
            "openExactAlarmSettings" -> launch(result) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}"))
                } else null
            }
            "openUrgentChannelSettings" -> launch(result) {
                graph.channels.ensure()
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, graph.channels.urgentId)
            }
            "openAppNotificationSettings" -> launch(result) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            }
            "requestPostNotifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
                result.success(null)
            }
            "takeLaunchRoute" -> result.success(launchRoute.also { launchRoute = null })

            // History & digest
            "getHistory" -> respond(result) { graph.db.notifications().history(call.argument<Int>("limit") ?: 300).map { it.toMap() } }
            "getDigest" -> respond(result) { graph.db.notifications().unseenHeld().map { it.toMap() } }
            "markSeen" -> respond(result) {
                graph.db.notifications().markSeen(call.arg<List<Number>>("ids").map { it.toLong() })
                graph.delivery.dismissSummary()
                graph.events.changed()
            }
            "deliverNow" -> respond(result) { graph.delivery.deliver().also { graph.events.changed() } }
            "openEntry" -> respond(result) { openEntry(call.arg<Number>("id").toLong()) }

            // Schedule
            "getSchedule" -> respond(result) { schedule() }
            "setScheduleMode" -> respond(result) { graph.schedule.setMode(ScheduleMode.valueOf(call.arg("mode"))) }
            "addDigestTime" -> respond(result) {
                graph.schedule.add(ScheduleGroup.valueOf(call.arg("group")), call.arg("minute"))
            }
            "updateDigestTime" -> respond(result) {
                graph.schedule.update(call.arg<Number>("id").toLong(), call.arg("minute"), call.arg("enabled"))
            }
            "deleteDigestTime" -> respond(result) { graph.schedule.delete(call.arg<Number>("id").toLong()) }
            "generateDigestTimes" -> respond(result) {
                graph.schedule.generate(
                    ScheduleGroup.valueOf(call.arg("group")), call.arg("start"), call.arg("end"), call.arg("interval"),
                )
            }

            // Rules
            "getRules" -> respond(result) { graph.rules.all().map { it.toMap() } }
            "addRule" -> respond(result) {
                graph.rules.add(
                    Rule(
                        kind = RuleKind.valueOf(call.arg("kind")),
                        matchType = MatchType.valueOf(call.arg("matchType")),
                        pattern = call.arg("pattern"),
                        packageName = call.argument<String>("packageName"),
                        action = RuleAction.valueOf(call.arg("action")),
                    ),
                ).also { graph.events.changed() }
            }
            "deleteRule" -> respond(result) {
                graph.rules.delete(call.arg<Number>("id").toLong())
                graph.events.changed()
            }
            "getAppsSeen" -> respond(result) {
                graph.db.notifications().appsSeen().map {
                    mapOf("packageName" to it.packageName, "appLabel" to it.appLabel, "count" to it.count)
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
        eventJob?.cancel()
        eventJob = scope.launch {
            graph.events.changes.collect { events.success(mapOf("type" to "changed")) }
        }
    }

    override fun onCancel(arguments: Any?) {
        eventJob?.cancel()
        eventJob = null
        sink = null
    }

    fun dispose() = scope.cancel()

    /** Set by MainActivity from the launching intent; Flutter takes it once on startup. */
    var launchRoute: String? = null

    fun pushRoute(route: String) {
        val events = sink
        if (events == null) launchRoute = route else events.success(mapOf("type" to "route", "data" to route))
    }

    // ---------------------------------------------------------------------------------------------

    private fun respond(result: MethodChannel.Result, block: suspend () -> Any?) {
        scope.launch {
            try {
                val value = withContext(Dispatchers.IO) { block() }
                result.success(if (value is Unit) null else value)
            } catch (e: Exception) {
                SafeLog.w({ "bridge call failed" }, e)
                result.error("native_error", e.message, null)
            }
        }
    }

    private fun launch(result: MethodChannel.Result, intent: () -> Intent?) {
        val i = intent()
        if (i == null) {
            result.success(false)
            return
        }
        try {
            activity.startActivity(i)
            result.success(true)
        } catch (e: Exception) {
            SafeLog.w({ "settings screen not available" }, e)
            result.success(false)
        }
    }

    private suspend fun status(): Map<String, Any?> {
        val nm = NotificationManagerCompat.from(activity)
        graph.channels.ensure()
        // The exact alarm permission may just have been granted: upgrade the pending alarm.
        if (!graph.settings.nextDigestExact && graph.scheduler.canScheduleExact()) graph.scheduler.reschedule()

        val dao = graph.db.notifications()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return mapOf(
            "notificationAccess" to NotificationManagerCompat.getEnabledListenerPackages(activity).contains(activity.packageName),
            "listenerConnected" to graph.events.listenerConnected.value,
            "dndAccess" to graph.dnd.hasAccess(),
            "urgentBypassesDnd" to graph.channels.urgentBypassesDnd(),
            "postNotifications" to canPostNotifications(activity),
            "notificationsEnabled" to nm.areNotificationsEnabled(),
            "exactAlarms" to graph.scheduler.canScheduleExact(),
            "enabled" to graph.settings.enabled,
            "nextDigestAt" to graph.settings.nextDigestAt,
            "heldCount" to dao.unseenHeld().size,
            "todayPassed" to dao.countSince(startOfDay, urgent = true),
            "todayHeld" to dao.countSince(startOfDay, urgent = false),
            "sdkInt" to Build.VERSION.SDK_INT,
        )
    }

    private suspend fun schedule(): Map<String, Any?> {
        val schedule = graph.schedule.load()
        val groups = ScheduleGroup.groupsFor(schedule.mode)
        return mapOf(
            "mode" to schedule.mode.name,
            "groups" to groups.map { it.name },
            "times" to schedule.times.filter { it.group in groups }.sortedBy { it.minuteOfDay }.map {
                mapOf("id" to it.id, "group" to it.group.name, "minute" to it.minuteOfDay, "enabled" to it.enabled)
            },
            "warnings" to graph.schedule.warnings().map { (group, pairs) ->
                mapOf("group" to group.name, "pairs" to pairs.map { listOf(it.first, it.second) })
            },
            "upcoming" to NextDigestCalculator.upcoming(schedule, ZonedDateTime.now(), 3).map { it.toInstant().toEpochMilli() },
            "exactAlarms" to graph.scheduler.canScheduleExact(),
        )
    }

    private fun notificationAccessIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(activity, CalmListenerService::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    /** Fires the original contentIntent; falls back to launching the app. Returns true on success. */
    private suspend fun openEntry(id: Long): Boolean {
        val entry = graph.db.notifications().byId(id) ?: return false
        return withContext(Dispatchers.Main) {
            val pendingIntent = ContentIntentStore.get(entry.notificationKey)
            if (pendingIntent != null) {
                try {
                    pendingIntent.send(activity, 0, null, null, null, null, sendOptions())
                    return@withContext true
                } catch (_: PendingIntent.CanceledException) {
                    SafeLog.d { "contentIntent canceled, launching app instead" }
                }
            }
            val launch = activity.packageManager.getLaunchIntentForPackage(entry.packageName) ?: return@withContext false
            activity.startActivity(launch)
            true
        }
    }

    /** Since Android 14 the sender has to opt in to lend its foreground state to the PendingIntent. */
    @Suppress("DEPRECATION")
    private fun sendOptions() = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            .toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        else -> null
    }

    private fun NotificationEntity.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "packageName" to packageName,
        "appLabel" to appLabel,
        "title" to title,
        "text" to text,
        "conversationTitle" to conversationTitle,
        "sender" to sender,
        "isGroup" to isGroup,
        "category" to category,
        "postTime" to postTime,
        "receivedAt" to receivedAt,
        "urgent" to urgent,
        "reason" to reason,
        "decisionSource" to decisionSource,
        "decisionStep" to decisionStep,
        "delivered" to delivered,
        "seen" to seen,
    )

    private fun Rule.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "kind" to kind.name,
        "matchType" to matchType.name,
        "pattern" to pattern,
        "packageName" to packageName,
        "action" to action.name,
        "description" to RulesEngine.describe(this),
    )

    private fun <T> MethodCall.arg(name: String): T = requireNotNull(argument<T>(name)) { "missing argument $name" }

    private companion object {
        const val METHOD_CHANNEL = "com.vincentwill.calm/native"
        const val EVENT_CHANNEL = "com.vincentwill.calm/events"
    }
}
