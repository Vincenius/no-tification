package com.vincentwill.calm.pipeline

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signals for the UI. Payloads are fetched separately; these only say "something changed". */
class PipelineEvents {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _listenerConnected = MutableStateFlow(false)

    /** History, digest, schedule or status changed. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    fun changed() {
        _changes.tryEmit(Unit)
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
        changed()
    }
}
