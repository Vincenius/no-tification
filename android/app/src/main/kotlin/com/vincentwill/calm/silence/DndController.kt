package com.vincentwill.calm.silence

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.annotation.RequiresApi
import com.vincentwill.calm.MainActivity
import com.vincentwill.calm.R
import com.vincentwill.calm.data.Settings
import com.vincentwill.calm.util.SafeLog

/**
 * Keeps other apps from buzzing while Calm is on: only calls, repeat callers, alarms, media and
 * Calm's own channels may interrupt.
 *
 * - Android 15+: a dedicated "no-tification" DND mode (AutomaticZenRule). The user's own DND setup is left
 *   untouched, and turning Calm off just deactivates the mode.
 * - Android 14 and older: the global DND policy and filter, with the previous state saved and
 *   restored when Calm is turned off.
 */
class DndController(private val context: Context, private val settings: Settings) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    /** Returns false if DND access is missing. */
    fun activate(): Boolean {
        if (!hasAccess()) return false
        return try {
            if (Build.VERSION.SDK_INT >= 35) activateMode() else activateLegacy()
            true
        } catch (e: SecurityException) {
            SafeLog.w({ "DND activation failed" }, e)
            false
        }
    }

    fun deactivate() {
        if (!hasAccess()) return
        try {
            if (Build.VERSION.SDK_INT >= 35) deactivateMode() else deactivateLegacy()
        } catch (e: SecurityException) {
            SafeLog.w({ "DND deactivation failed" }, e)
        }
    }

    // Android 15+ ---------------------------------------------------------------------------------

    @RequiresApi(35)
    private fun activateMode() {
        val id = ensureRule()
        nm.setAutomaticZenRuleState(id, Condition(CONDITION_URI, "no-tification is on", Condition.STATE_TRUE, Condition.SOURCE_USER_ACTION))
    }

    @RequiresApi(35)
    private fun deactivateMode() {
        val id = settings.zenRuleId ?: return
        if (nm.getAutomaticZenRule(id) == null) return
        nm.setAutomaticZenRuleState(id, Condition(CONDITION_URI, "no-tification is off", Condition.STATE_FALSE, Condition.SOURCE_USER_ACTION))
    }

    @RequiresApi(35)
    private fun ensureRule(): String {
        settings.zenRuleId?.let { id ->
            nm.getAutomaticZenRule(id)?.let { existing ->
                // Keep the rule, but make sure it has the current name and policy.
                existing.name = MODE_NAME
                existing.zenPolicy = zenPolicy()
                nm.updateAutomaticZenRule(id, existing)
                return id
            }
        }
        val rule = AutomaticZenRule.Builder(MODE_NAME, CONDITION_URI)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setZenPolicy(zenPolicy())
            .setManualInvocationAllowed(true)
            .setTriggerDescription("Turned on in the no-tification app")
            .setIconResId(R.drawable.ic_stat_calm)
            .build()
        return nm.addAutomaticZenRule(rule).also { settings.zenRuleId = it }
    }

    @RequiresApi(35)
    private fun zenPolicy(): ZenPolicy = ZenPolicy.Builder()
        .disallowAllSounds()
        .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
        .allowRepeatCallers(true)
        .allowAlarms(true)
        .allowMedia(true)
        .allowSystem(true)
        .allowPriorityChannels(true)
        // Held notifications are cancelled right away. Until then, keep them from popping up.
        .showAllVisualEffects()
        .showPeeking(false)
        .showFullScreenIntent(false)
        .showLights(false)
        .build()

    // Android 14 and older ------------------------------------------------------------------------

    private fun activateLegacy() {
        if (settings.savedDndState == null) settings.savedDndState = encode(nm.currentInterruptionFilter, nm.notificationPolicy)
        var categories = Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
        var effects = 0
        if (Build.VERSION.SDK_INT >= 28) {
            categories = categories or Policy.PRIORITY_CATEGORY_ALARMS or Policy.PRIORITY_CATEGORY_MEDIA or
                Policy.PRIORITY_CATEGORY_SYSTEM
            effects = Policy.SUPPRESSED_EFFECT_PEEK or Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
                Policy.SUPPRESSED_EFFECT_LIGHTS
        }
        nm.notificationPolicy = policy(categories, Policy.PRIORITY_SENDERS_ANY, Policy.PRIORITY_SENDERS_ANY, effects)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun deactivateLegacy() {
        val saved = settings.savedDndState
        if (saved == null) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            return
        }
        val parts = saved.split('|').map { it.toInt() }
        nm.notificationPolicy = policy(parts[1], parts[2], parts[3], parts[4])
        nm.setInterruptionFilter(parts[0])
        settings.savedDndState = null
    }

    /** The constructor with suppressed visual effects only exists since Android 9 (API 28). */
    private fun policy(categories: Int, callSenders: Int, messageSenders: Int, effects: Int) =
        if (Build.VERSION.SDK_INT >= 28) Policy(categories, callSenders, messageSenders, effects)
        else Policy(categories, callSenders, messageSenders)

    private fun encode(filter: Int, policy: Policy) = listOf(
        filter, policy.priorityCategories, policy.priorityCallSenders, policy.priorityMessageSenders,
        policy.suppressedVisualEffects,
    ).joinToString("|")

    private companion object {
        const val MODE_NAME = "no-tification"
        val CONDITION_URI: Uri = Uri.parse("condition://com.vincentwill.calm/active")
    }
}
