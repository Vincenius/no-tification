package com.vincentwill.calm.pipeline

import com.vincentwill.calm.classify.ClassificationInput
import com.vincentwill.calm.classify.Decision
import com.vincentwill.calm.classify.Rule
import com.vincentwill.calm.classify.RulesEngine
import com.vincentwill.calm.data.NotificationDao
import com.vincentwill.calm.data.NotificationEntity
import com.vincentwill.calm.data.RulesRepository
import com.vincentwill.calm.data.Settings
import com.vincentwill.calm.listener.CapturedNotification
import com.vincentwill.calm.listener.Deduplicator
import com.vincentwill.calm.listener.IncomingItem
import java.util.concurrent.ConcurrentHashMap

/** What the pipeline does to the outside world; implemented by the listener service. */
interface NotificationActions {
    fun cancel(key: String)
    fun repostUrgent(source: CapturedNotification, items: List<IncomingItem>)
}

/** dedup → classify → store → act. Runs sequentially, one notification at a time. */
class NotificationPipeline(
    private val dao: NotificationDao,
    private val rules: RulesRepository,
    private val settings: Settings,
    private val events: PipelineEvents,
    private val isDialer: (String) -> Boolean,
) {
    /** Notification key → whether everything in its last update was held. */
    private val lastHeld = ConcurrentHashMap<String, Boolean>()

    suspend fun process(n: CapturedNotification, actions: NotificationActions) {
        if (!settings.enabled) return

        val candidates = Deduplicator.items(n)
        val known = dao.existingHashes(candidates.map { it.hash }).toSet()
        val fresh = candidates.filterNot { it.hash in known }

        if (fresh.isEmpty()) {
            // A repost without new content (e.g. read-state sync). Keep held conversations quiet.
            // If we don't know (process restarted), leave it alone: failing open.
            if (lastHeld[n.key] == true) actions.cancel(n.key)
            return
        }

        val allRules = rules.all()
        val decided = fresh.map { item -> item to classify(n, item, allRules) }
        val urgent = decided.filter { it.second.urgent }.map { it.first }
        val now = System.currentTimeMillis()

        dao.insertAll(decided.map { (item, decision) -> entity(n, item, decision, now) })

        if (urgent.isEmpty()) {
            actions.cancel(n.key)
            lastHeld[n.key] = true
        } else {
            actions.repostUrgent(n, urgent)
            lastHeld[n.key] = false
        }
        events.changed()
    }

    private suspend fun classify(n: CapturedNotification, item: IncomingItem, allRules: List<Rule>): Decision {
        val input = ClassificationInput(
            packageName = n.packageName,
            appLabel = n.appLabel,
            title = n.title,
            text = item.text,
            conversationTitle = n.conversationTitle,
            sender = item.sender,
            isGroup = n.isGroupConversation,
            category = n.category,
            isDialer = isDialer(n.packageName),
        )
        // Milestone 3 adds the AI step for the gray zone here.
        return RulesEngine.classify(input, allRules) ?: RulesEngine.fallback()
    }

    private fun entity(n: CapturedNotification, item: IncomingItem, d: Decision, now: Long) = NotificationEntity(
        dedupHash = item.hash,
        notificationKey = n.key,
        packageName = n.packageName,
        appLabel = n.appLabel,
        title = n.title,
        text = item.text,
        conversationTitle = n.conversationTitle,
        sender = item.sender,
        isGroup = n.isGroupConversation,
        category = n.category,
        postTime = item.timestamp.takeIf { it > 0 } ?: n.postTime,
        receivedAt = now,
        urgent = d.urgent,
        reason = d.reason,
        decisionSource = d.source.name,
        decisionStep = d.step.name,
        delivered = d.urgent,
    )
}
