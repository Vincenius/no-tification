package com.vincentwill.calm.pipeline

import com.vincentwill.calm.ai.AiClassifier
import com.vincentwill.calm.ai.AiRequest
import com.vincentwill.calm.ai.ConversationDebouncer
import com.vincentwill.calm.classify.ClassificationInput
import com.vincentwill.calm.classify.Decision
import com.vincentwill.calm.classify.RulesEngine
import com.vincentwill.calm.data.NotificationDao
import com.vincentwill.calm.data.NotificationEntity
import com.vincentwill.calm.data.RulesRepository
import com.vincentwill.calm.data.Settings
import com.vincentwill.calm.listener.CapturedNotification
import com.vincentwill.calm.listener.Deduplicator
import com.vincentwill.calm.listener.IncomingItem
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope

/** What the pipeline does to the outside world; implemented by the listener service. */
interface NotificationActions {
    fun cancel(key: String)
    fun repostUrgent(source: CapturedNotification, items: List<IncomingItem>)
}

/**
 * dedup → rules → (debounced) AI → store → act.
 *
 * Rule decisions are acted on immediately. Gray-zone messages wait ~3 s per conversation so that a
 * burst of messages becomes one AI call; meanwhile the original stays silent thanks to DND.
 */
class NotificationPipeline(
    private val dao: NotificationDao,
    private val rules: RulesRepository,
    private val settings: Settings,
    private val events: PipelineEvents,
    private val ai: AiClassifier,
    scope: CoroutineScope,
    private val isDialer: (String) -> Boolean,
) {
    private class Pending(val n: CapturedNotification, val item: IncomingItem, val actions: NotificationActions)

    /** Notification key → whether everything in its last decided update was held. */
    private val lastHeld = ConcurrentHashMap<String, Boolean>()

    private val debouncer = ConversationDebouncer<Pending>(scope) { key, batch -> decideGrayZone(key, batch) }

    suspend fun process(n: CapturedNotification, actions: NotificationActions) {
        if (!settings.enabled) return

        val candidates = Deduplicator.items(n)
        val known = dao.existingHashes(candidates.map { it.hash }).toSet() + debouncer.pending().map { it.item.hash }
        val fresh = candidates.filterNot { it.hash in known }

        if (fresh.isEmpty()) {
            // A repost without new content (e.g. read-state sync). Keep held conversations quiet.
            // If we don't know (process restarted), leave it alone: failing open.
            if (lastHeld[n.key] == true && !debouncer.hasPending(n.key)) actions.cancel(n.key)
            return
        }

        val allRules = rules.all()
        val byRules = fresh.map { item -> item to RulesEngine.classify(input(n, item), allRules) }
        val decided = byRules.mapNotNull { (item, d) -> d?.let { item to it } }.toMutableList()
        val gray = byRules.filter { it.second == null }.map { it.first }

        var waitingForAi = false
        if (gray.isNotEmpty()) {
            if (ai.isAvailable) {
                debouncer.submit(n.key, gray.map { Pending(n, it, actions) })
                waitingForAi = true
            } else {
                decided += gray.map { it to RulesEngine.fallback("No AI configured, letting it through") }
            }
        }
        if (decided.isEmpty()) return

        store(n, decided)
        val urgent = decided.filter { it.second.urgent }.map { it.first }
        when {
            urgent.isNotEmpty() -> {
                actions.repostUrgent(n, urgent)
                lastHeld[n.key] = false
            }
            // Don't cancel yet: the AI may still decide the rest of this notification is urgent.
            !waitingForAi -> {
                actions.cancel(n.key)
                lastHeld[n.key] = true
            }
        }
        events.changed()
    }

    /** Test box in the settings: the same decision path, without storing or acting. */
    suspend fun preview(input: ClassificationInput): Decision =
        RulesEngine.classify(input, rules.all())
            ?: if (ai.isAvailable) ai.classify(aiRequest(input.appLabel, input.conversationTitle, input.isGroup, listOf(input.sender to input.text)))
            else RulesEngine.fallback("No AI configured, letting it through")

    private suspend fun decideGrayZone(key: String, batch: List<Pending>) {
        // The latest update of the conversation carries the freshest contentIntent and title.
        val latest = batch.last()
        val n = latest.n
        val decision = ai.classify(aiRequest(n.appLabel, n.conversationTitle, n.isGroupConversation, batch.map { it.item.sender to it.item.text }))

        // The batch can span several updates of the notification; store each message with its own.
        for ((source, items) in batch.groupBy { it.n }) store(source, items.map { it.item to decision })

        if (decision.urgent) {
            latest.actions.repostUrgent(n, batch.map { it.item })
            lastHeld[key] = false
        } else {
            latest.actions.cancel(key)
            lastHeld[key] = true
        }
        events.changed()
    }

    private fun aiRequest(appLabel: String, conversation: String?, isGroup: Boolean, messages: List<Pair<String?, String>>) =
        AiRequest(appLabel, conversation, isGroup, messages, ZonedDateTime.now())

    private fun input(n: CapturedNotification, item: IncomingItem) = ClassificationInput(
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

    private suspend fun store(n: CapturedNotification, decided: List<Pair<IncomingItem, Decision>>) {
        val now = System.currentTimeMillis()
        dao.insertAll(decided.map { (item, d) -> entity(n, item, d, now) })
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
