package com.vincentwill.calm.listener

import java.security.MessageDigest

/** One storable unit: a single chat message, or a whole non-messaging notification. */
data class IncomingItem(
    val hash: String,
    val sender: String?,
    val text: String,
    val timestamp: Long,
)

/**
 * Messaging apps repost one notification per conversation with the full list of unread messages
 * ("3 new messages"). We store each message once, identified by notification key + sender +
 * timestamp + text.
 */
object Deduplicator {
    fun items(n: CapturedNotification): List<IncomingItem> {
        if (n.messages.isEmpty()) {
            val text = n.text.orEmpty()
            return listOf(IncomingItem(hash(n.key, n.title, text), null, text, n.postTime))
        }
        // The user's own replies show up in the list too. Skip them, unless the app marks every
        // message as "self", which some do.
        val incoming = n.messages.filterNot { it.fromSelf }.ifEmpty { n.messages }
        return incoming
            .map { IncomingItem(hash(n.key, it.sender, it.timestamp.toString(), it.text), it.sender, it.text, it.timestamp) }
            .distinctBy { it.hash }
    }

    fun newItems(n: CapturedNotification, isKnown: (String) -> Boolean): List<IncomingItem> =
        items(n).filterNot { isKnown(it.hash) }

    private fun hash(vararg parts: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { digest.update((it ?: "\u0000").toByteArray()); digest.update(0x1f) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
