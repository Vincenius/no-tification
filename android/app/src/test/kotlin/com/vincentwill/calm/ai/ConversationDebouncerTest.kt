package com.vincentwill.calm.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDebouncerTest {
    private fun TestScope.debouncer(flushed: MutableList<Pair<String, List<String>>>) =
        ConversationDebouncer<String>(backgroundScope, quietMs = 3_000, maxWaitMs = 10_000) { key, items -> flushed += key to items }

    @Test
    fun `rapid messages become one batch after 3 quiet seconds`() = runTest {
        val flushed = mutableListOf<Pair<String, List<String>>>()
        val d = debouncer(flushed)

        d.submit("chat", listOf("hi"))
        advanceTimeBy(1_000)
        d.submit("chat", listOf("are you there?"))
        advanceTimeBy(2_000)
        d.submit("chat", listOf("call me"))
        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(flushed.isEmpty())
        assertTrue(d.hasPending("chat"))
        assertEquals(3, d.pending().size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("chat" to listOf("hi", "are you there?", "call me")), flushed)
        assertFalse(d.hasPending("chat"))
    }

    @Test
    fun `conversations are batched separately`() = runTest {
        val flushed = mutableListOf<Pair<String, List<String>>>()
        val d = debouncer(flushed)

        d.submit("a", listOf("1"))
        advanceTimeBy(2_000)
        d.submit("b", listOf("2"))
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf("a" to listOf("1")), flushed)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("a" to listOf("1"), "b" to listOf("2")), flushed)
    }

    @Test
    fun `a never-quiet chat is flushed after 10 seconds anyway`() = runTest {
        val flushed = mutableListOf<Pair<String, List<String>>>()
        val d = debouncer(flushed)

        repeat(6) { i ->
            d.submit("busy", listOf("m$i"))
            advanceTimeBy(2_000)
        }
        // Submitted at 0, 2, 4, 6, 8, 10 s: the max wait hits at 10 s, right after the sixth message.
        runCurrent()
        assertEquals(1, flushed.size)
        assertEquals(6, flushed.single().second.size)

        // The quiet timer of the flushed batch must not fire a second, empty flush.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, flushed.size)
    }

    @Test
    fun `items stay pending while their flush is running`() = runTest {
        val release = CompletableDeferred<Unit>()
        val d = ConversationDebouncer<String>(backgroundScope, quietMs = 3_000, maxWaitMs = 10_000) { _, _ -> release.await() }

        d.submit("chat", listOf("hi"))
        advanceTimeBy(3_001)
        runCurrent()
        // The flush (the AI call) hasn't returned yet: a repost must still see "hi" as known.
        assertTrue(d.hasPending("chat"))
        assertEquals(listOf("hi"), d.pending())

        release.complete(Unit)
        runCurrent()
        assertFalse(d.hasPending("chat"))
        assertTrue(d.pending().isEmpty())
    }

    @Test
    fun `messages after a flush start a new batch`() = runTest {
        val flushed = mutableListOf<Pair<String, List<String>>>()
        val d = debouncer(flushed)

        d.submit("chat", listOf("first"))
        advanceTimeBy(3_001)
        runCurrent()
        d.submit("chat", listOf("second"))
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(listOf("chat" to listOf("first"), "chat" to listOf("second")), flushed)
    }
}
