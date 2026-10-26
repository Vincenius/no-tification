package com.vincentwill.calm.listener

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Milestone 1 in-memory log of intercepted notifications, shared by the listener service and the
 * Flutter bridge (both live in the app process). Replaced by Room in milestone 2.
 */
object LiveLog {
    private const val MAX_ITEMS = 500

    private val items = ArrayDeque<CapturedNotification>()
    private val _events = MutableSharedFlow<CapturedNotification>(extraBufferCapacity = 64)
    private val _listenerConnected = MutableStateFlow(false)

    val events: SharedFlow<CapturedNotification> = _events.asSharedFlow()
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    @Volatile
    var ignoredCount: Int = 0
        private set

    fun add(notification: CapturedNotification) {
        synchronized(items) {
            items.addFirst(notification)
            while (items.size > MAX_ITEMS) items.removeLast()
        }
        _events.tryEmit(notification)
    }

    fun countIgnored() {
        ignoredCount++
    }

    /** Newest first. */
    fun snapshot(): List<CapturedNotification> = synchronized(items) { items.toList() }

    fun clear() {
        synchronized(items) { items.clear() }
        ignoredCount = 0
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }
}
