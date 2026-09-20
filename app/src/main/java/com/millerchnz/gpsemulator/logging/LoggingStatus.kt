package com.millerchnz.gpsemulator.logging

import com.millerchnz.gpsemulator.data.db.LocationSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Runtime state of the logging service, which the settings screen mirrors. */
object LoggingStatus {
    data class State(
        val running: Boolean = false,
        val sessionCount: Int = 0,
        val startedAtElapsedRealtime: Long = 0L,
        val lastFixAtMillis: Long? = null,
        val lastSample: LocationSample? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    internal fun onStarted(startedAtElapsedRealtime: Long) {
        _state.value = State(running = true, startedAtElapsedRealtime = startedAtElapsedRealtime)
    }

    internal fun onStopped() {
        _state.update { State(error = it.error) }
    }

    internal fun onFix(sample: LocationSample, atMillis: Long) {
        _state.update {
            it.copy(
                sessionCount = it.sessionCount + 1,
                lastFixAtMillis = atMillis,
                lastSample = sample,
                error = null,
            )
        }
    }

    internal fun onError(message: String?) {
        _state.update { it.copy(error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
