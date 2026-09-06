package com.millerchnz.gpsemulator.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.millerchnz.gpsemulator.mock.MockLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class MapUiState(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = MockLocationService.DEFAULT_ACCURACY,
    val isMocking: Boolean = false,
    val mockStartedAtElapsedRealtime: Long = 0L,
    val errorMessage: String? = null,
    val cameraGeneration: Int = 0,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        MapUiState(
            latitude = prefs.getLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(MockLocationService.DEFAULT_LAT))
                .let(java.lang.Double::longBitsToDouble),
            longitude = prefs.getLong(KEY_LNG, java.lang.Double.doubleToRawLongBits(MockLocationService.DEFAULT_LNG))
                .let(java.lang.Double::longBitsToDouble),
            accuracyMeters = prefs.getFloat(KEY_ACC, MockLocationService.DEFAULT_ACCURACY),
            isMocking = MockLocationService.isRunning,
            mockStartedAtElapsedRealtime = MockLocationService.startedAtElapsedRealtime,
        ),
    )
    val state: StateFlow<MapUiState> = _state

    init {
        if (!prefs.contains(KEY_LAT)) {
            seedFromLastKnown()
        }
    }

    fun dropPin(latitude: Double, longitude: Double, recenter: Boolean = false) {
        persist(latitude, longitude, _state.value.accuracyMeters)
        _state.update {
            it.copy(
                latitude = latitude,
                longitude = longitude,
                errorMessage = null,
                cameraGeneration = if (recenter) it.cameraGeneration + 1 else it.cameraGeneration,
            )
        }
        if (_state.value.isMocking) {
            MockLocationService.update(getApplication(), latitude, longitude, _state.value.accuracyMeters)
        }
    }

    fun setAccuracy(accuracyMeters: Float) {
        val clamped = accuracyMeters.coerceIn(1f, 100f)
        persist(_state.value.latitude, _state.value.longitude, clamped)
        _state.update { it.copy(accuracyMeters = clamped) }
        if (_state.value.isMocking) {
            val s = _state.value
            MockLocationService.update(getApplication(), s.latitude, s.longitude, clamped)
        }
    }

    fun toggleMock() {
        val s = _state.value
        if (s.isMocking) {
            MockLocationService.stop(getApplication())
            _state.update {
                it.copy(
                    isMocking = false,
                    mockStartedAtElapsedRealtime = 0L,
                    errorMessage = null,
                )
            }
        } else {
            runCatching {
                MockLocationService.start(getApplication(), s.latitude, s.longitude, s.accuracyMeters)
            }.onSuccess {
                _state.update {
                    it.copy(
                        isMocking = true,
                        mockStartedAtElapsedRealtime = MockLocationService.startedAtElapsedRealtime,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isMocking = false,
                        mockStartedAtElapsedRealtime = 0L,
                        errorMessage = error.message ?: "Could not start mock location",
                    )
                }
            }
        }
    }

    fun applyCoordinates(raw: String): Boolean {
        val parsed = parseCoordinates(raw)
        if (parsed == null) {
            _state.update { it.copy(errorMessage = "Could not parse coordinates") }
            return false
        }
        dropPin(parsed.first, parsed.second, recenter = true)
        return true
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun refreshRunning() {
        val running = MockLocationService.isRunning
        _state.update {
            it.copy(
                isMocking = running,
                mockStartedAtElapsedRealtime = if (running) {
                    MockLocationService.startedAtElapsedRealtime
                } else {
                    0L
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun seedFromLastKnown() {
        val app = getApplication<Application>()
        val fused = LocationServices.getFusedLocationProviderClient(app)
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null && !_state.value.isMocking) {
                    dropPin(loc.latitude, loc.longitude, recenter = true)
                }
            }
            .addOnFailureListener {
                val manager = app.getSystemService(LocationManager::class.java)
                val last = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (last != null && !_state.value.isMocking) {
                    dropPin(last.latitude, last.longitude, recenter = true)
                }
            }
    }

    private fun persist(latitude: Double, longitude: Double, accuracyMeters: Float) {
        prefs.edit()
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_LNG, java.lang.Double.doubleToRawLongBits(longitude))
            .putFloat(KEY_ACC, accuracyMeters)
            .apply()
    }

    companion object {
        private const val PREFS = "gps_emulator"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_ACC = "acc"

        fun parseCoordinates(raw: String): Pair<Double, Double>? {
            val cleaned = raw.trim().replace(';', ',')
            val parts = cleaned.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lng = parts[1].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            return lat to lng
        }

        fun formatElapsed(elapsedMs: Long): String {
            val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }
    }
}
