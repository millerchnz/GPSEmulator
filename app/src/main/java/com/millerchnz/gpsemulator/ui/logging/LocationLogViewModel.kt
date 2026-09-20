package com.millerchnz.gpsemulator.ui.logging

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millerchnz.gpsemulator.data.AppGraph
import com.millerchnz.gpsemulator.data.LocationFields
import com.millerchnz.gpsemulator.data.db.LocationLogEntity
import com.millerchnz.gpsemulator.data.export.LogExporter
import com.millerchnz.gpsemulator.data.settings.LocationProviderChoice
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

data class LocationLogFilter(
    val range: TimeRange = TimeRange.ALL,
    val provider: LocationProviderChoice? = null,
    val startMillis: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LocationLogViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppGraph.database.locationLogs()

    private val _filter = MutableStateFlow(LocationLogFilter())
    val filter: StateFlow<LocationLogFilter> = _filter

    private val _limit = MutableStateFlow(PAGE_SIZE)

    val rows: StateFlow<List<LocationLogEntity>> = combine(_filter, _limit) { filter, limit -> filter to limit }
        .flatMapLatest { (filter, limit) ->
            dao.observeFiltered(
                fromMillis = filter.startMillis,
                toMillis = Long.MAX_VALUE,
                provider = filter.provider?.storedName,
                limit = limit,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val matchingCount: StateFlow<Int> = _filter
        .flatMapLatest { filter ->
            dao.observeFilteredCount(
                fromMillis = filter.startMillis,
                toMillis = Long.MAX_VALUE,
                provider = filter.provider?.storedName,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setRange(range: TimeRange) {
        _filter.update { it.copy(range = range, startMillis = range.startMillis()) }
        _limit.value = PAGE_SIZE
    }

    fun setProvider(provider: LocationProviderChoice?) {
        _filter.update { it.copy(provider = provider) }
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
                provider = filter.provider?.storedName,
            )
            if (matches.isEmpty()) {
                onError("Nothing to export for this filter")
                return@launch
            }
            runCatching {
                LogExporter.export(
                    context = getApplication(),
                    baseName = "location-logs",
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
