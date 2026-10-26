package com.vincentwill.calm.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Collects rapid-fire messages per conversation and flushes them as one batch once the
 * conversation has been quiet for [quietMs], or [maxWaitMs] after the first message at the latest
 * (so a busy chat still gets decided).
 */
class ConversationDebouncer<T>(
    private val scope: CoroutineScope,
    private val quietMs: Long = 3_000,
    private val maxWaitMs: Long = 10_000,
    private val flush: suspend (key: String, items: List<T>) -> Unit,
) {
    private class Batch<T> {
        val items = mutableListOf<T>()
        var quietJob: Job? = null
        var maxJob: Job? = null
    }

    private val lock = Any()
    private val batches = HashMap<String, Batch<T>>()

    fun submit(key: String, items: List<T>) {
        if (items.isEmpty()) return
        synchronized(lock) {
            val batch = batches.getOrPut(key) {
                Batch<T>().also { b -> b.maxJob = scope.launch { delay(maxWaitMs); flushBatch(key, b) } }
            }
            batch.items += items
            batch.quietJob?.cancel()
            batch.quietJob = scope.launch { delay(quietMs); flushBatch(key, batch) }
        }
    }

    /** Everything waiting for a decision, e.g. for deduplication. */
    fun pending(): List<T> = synchronized(lock) { batches.values.flatMap { it.items } }

    fun hasPending(key: String): Boolean = synchronized(lock) { key in batches }

    private suspend fun flushBatch(key: String, batch: Batch<T>) {
        val self = currentCoroutineContext().job
        val items = synchronized(lock) {
            // The other timer may have won the race already.
            if (batches[key] !== batch) return
            batches.remove(key)
            listOfNotNull(batch.quietJob, batch.maxJob).filter { it !== self }.forEach { it.cancel() }
            batch.items.toList()
        }
        flush(key, items)
    }
}
