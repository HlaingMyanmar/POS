package com.sspd.servicemgmt.feature.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.SalesRankingDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import com.sspd.servicemgmt.core.realtime.onDataEvent

class SalesRankingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val month = Calendar.getInstance().let { c ->
        "%d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private val _uiState = MutableStateFlow(SalesRankingUiState())
    val uiState: StateFlow<SalesRankingUiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Sale") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val res = ApiClient.service.getSalesRanking(ApiClient.bearer(prefs.authToken), month)
                if (res.isSuccessful) _uiState.update { it.copy(items = res.body()?.data ?: emptyList()) }
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false) }
        }
    }

    data class SalesRankingUiState(
        val items:   List<SalesRankingDTO> = emptyList(),
        val loading: Boolean               = true
    )
}
