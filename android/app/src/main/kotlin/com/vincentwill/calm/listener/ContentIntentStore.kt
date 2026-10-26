package com.vincentwill.calm.listener

import android.app.PendingIntent

/**
 * In-memory map of notification key → original contentIntent. PendingIntents can't be persisted,
 * so after process death callers fall back to launching the source app.
 */
object ContentIntentStore {
    private const val MAX_ENTRIES = 1000

    private val intents = object : LinkedHashMap<String, PendingIntent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>) =
            size > MAX_ENTRIES
    }

    fun put(key: String, intent: PendingIntent) = synchronized(intents) { intents[key] = intent }

    fun get(key: String): PendingIntent? = synchronized(intents) { intents[key] }
}
