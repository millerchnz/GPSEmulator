package com.millerchnz.gpsemulator.ui.geofence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.data.db.GeofenceEntity
import com.millerchnz.gpsemulator.mock.MockLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class GeofenceEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppGraph.database.geofences()

    private val _draft = MutableStateFlow<GeofenceEntity?>(null)
    val draft: StateFlow<GeofenceEntity?> = _draft

    private val _isNew = MutableStateFlow(true)
    val isNew: StateFlow<Boolean> = _isNew

    private var loadedKey: String? = null

    /** [latitude] and [longitude] are set when the user created the fence by tapping the map. */
    fun load(id: String?, latitude: Double?, longitude: Double?) {
        val key = "$id|$latitude|$longitude"
        if (loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            val existing = id?.takeIf { it.isNotBlank() }?.let { dao.byId(it) }
            if (existing != null) {
                _isNew.value = false
                _draft.value = existing.copy(radiusMeters = GeofenceEntity.clampRadius(existing.radiusMeters))
                return@launch
            }
            _isNew.value = true
            val center = if (latitude != null && longitude != null) {
                latitude to longitude
            } else {
                defaultCenter()
            }
            _draft.value = GeofenceEntity(
                id = UUID.randomUUID().toString(),
                name = "",
                latitude = center.first,
                longitude = center.second,
                radiusMeters = GeofenceEntity.DEFAULT_RADIUS_METERS,
                createdAtMillis = System.currentTimeMillis(),
            )
        }
    }

    fun update(transform: (GeofenceEntity) -> GeofenceEntity) {
        _draft.update { current -> current?.let(transform) }
    }

    fun save(onDone: () -> Unit) {
        val fence = _draft.value ?: return
        viewModelScope.launch {
            dao.upsert(
                fence.copy(
                    name = fence.name.trim().ifBlank { "Unnamed fence" },
                    radiusMeters = GeofenceEntity.clampRadius(fence.radiusMeters),
                ),
            )
            AppGraph.geofences.sync()
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val fence = _draft.value ?: return
        viewModelScope.launch {
            dao.delete(fence.id)
            AppGraph.geofences.sync()
            onDone()
        }
    }

    /** Opens a new fence over the last logged fix, falling back to the app's default coordinates. */
    private suspend fun defaultCenter(): Pair<Double, Double> {
        val latest = AppGraph.database.locationLogs().latest()
        val latitude = latest?.sample?.latitude
        val longitude = latest?.sample?.longitude
        return if (latitude != null && longitude != null) {
            latitude to longitude
        } else {
            MockLocationService.DEFAULT_LAT to MockLocationService.DEFAULT_LNG
        }
    }
}
