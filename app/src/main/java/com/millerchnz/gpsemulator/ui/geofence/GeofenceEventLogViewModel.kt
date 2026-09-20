package com.millerchnz.gpsemulator.ui.geofence

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.db.GeofenceEntity
import com.millerchnz.gpsemulator.data.db.GeofenceEventEntity
import com.millerchnz.gpsemulator.data.db.GeofenceTransition
import com.millerchnz.gpsemulator.data.export.LogExporter
import com.millerchnz.gpsemulator.ui.common.TimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GeofenceEventFilter(
    val range: TimeRange = TimeRange.ALL,
    val transition: GeofenceTransition? = null,
    val fenceId: String? = null,
    val startMillis: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GeofenceEventLogViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppGraph.database.geofenceEvents()

    private val _filter = MutableStateFlow(GeofenceEventFilter())
    val filter: StateFlow<GeofenceEventFilter> = _filter

    private val _limit = MutableStateFlow(PAGE_SIZE)

    val fences: StateFlow<List<GeofenceEntity>> = AppGraph.database.geofences()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<GeofenceEventEntity>> = combine(_filter, _limit) { filter, limit -> filter to limit }
        .flatMapLatest { (filter, limit) ->
            dao.observeFiltered(
                fromMillis = filter.startMillis,
                toMillis = Long.MAX_VALUE,
                transition = filter.transition?.storedName,
                fenceId = filter.fenceId,
                limit = limit,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val matchingCount: StateFlow<Int> = _filter
        .flatMapLatest { filter ->
            dao.observeFilteredCount(
                fromMillis = filter.startMillis,
                toMillis = Long.MAX_VALUE,
                transition = filter.transition?.storedName,
                fenceId = filter.fenceId,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setRange(range: TimeRange) {
        _filter.update { it.copy(range = range, startMillis = range.startMillis()) }
        _limit.value = PAGE_SIZE
    }

    fun setTransition(transition: GeofenceTransition?) {
        _filter.update { it.copy(transition = transition) }
        _limit.value = PAGE_SIZE
    }

    fun setFence(fenceId: String?) {
        _filter.update { it.copy(fenceId = fenceId) }
        _limit.value = PAGE_SIZE
    }

    fun loadMore() {
        _limit.update { it + PAGE_SIZE }
    }

    fun clearAll() {
        viewModelScope.launch { dao.deleteAll() }
    }

    fun export(
        format: LogExporter.Format,
        onReady: (Uri) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val filter = _filter.value
            val matches = dao.forExport(
                fromMillis = filter.startMillis,
                toMillis = Long.MAX_VALUE,
                transition = filter.transition?.storedName,
                fenceId = filter.fenceId,
            )
            if (matches.isEmpty()) {
                onError("Nothing to export for this filter")
                return@launch
            }
            runCatching {
                LogExporter.export(
                    context = getApplication(),
                    baseName = "geofence-events",
                    rows = matches.map { LocationFields.of(it) },
                    format = format,
                )
            }.fold(onReady) { error -> onError(error.message ?: "Export failed") }
        }
    }

    companion object {
        const val PAGE_SIZE = 500
    }
}
