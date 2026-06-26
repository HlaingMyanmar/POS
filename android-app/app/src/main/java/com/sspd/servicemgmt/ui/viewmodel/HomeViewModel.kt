package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.BookingDTO
import com.sspd.servicemgmt.api.DashboardStats
import com.sspd.servicemgmt.utils.PreferenceManager
import com.sspd.servicemgmt.websocket.StompClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val gson  = GsonBuilder().create()

    private val _uiState = MutableStateFlow(HomeUiState(
        username    = prefs.username,
        displayName = prefs.displayName.ifEmpty { prefs.username }
    ))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var alertStomp: StompClient? = null
    private var alertReconnectJob: Job? = null

    init {
        loadStats()
        connectAlertWs()
        // Refresh dashboard stats whenever sales, jobs, or bookings change
        onDataEvent("Sale", "Service Job", "Booking", debounceMs = 800L) { loadStats() }
    }

    // ── Dashboard stats ───────────────────────────────────────────────────────

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                val res = ApiClient.service.getStats(ApiClient.bearer(prefs.authToken))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(stats = res.body()?.data ?: DashboardStats(), loading = false) }
                } else {
                    _uiState.update { it.copy(loading = false) }
                }
            }.onFailure { _uiState.update { it.copy(loading = false) } }
        }
    }

    // ── Booking alert WebSocket ───────────────────────────────────────────────

    private fun connectAlertWs() {
        alertStomp?.disconnect()
        alertStomp = StompClient(
            client         = ApiClient.wsClient(),
            url            = ApiClient.wsNativeUrl,
            token          = prefs.authToken,
            onConnected    = { alertStomp?.subscribe("/topic/booking-alerts") },
            onMessage      = { dest, body ->
                if (dest == "/topic/booking-alerts") parseAndMergeAlerts(body)
            },
            onDisconnected = { scheduleAlertReconnect() },
        )
        alertStomp?.connect()
    }

    private fun scheduleAlertReconnect() {
        alertReconnectJob?.cancel()
        alertReconnectJob = viewModelScope.launch {
            delay(5_000)
            connectAlertWs()
        }
    }

    private fun parseAndMergeAlerts(json: String) {
        runCatching {
            val type    = object : TypeToken<List<BookingDTO>>() {}.type
            val arrived = gson.fromJson<List<BookingDTO>>(json, type) ?: return
            _uiState.update { state ->
                // Merge: keep existing dismissed alerts gone, add new ones without duplicates
                val existingIds = state.bookingAlerts.mapTo(mutableSetOf()) { it.id }
                val newAlerts = arrived.filter { it.id !in existingIds }
                state.copy(bookingAlerts = state.bookingAlerts + newAlerts)
            }
        }
    }

    fun dismissAlert(bookingId: Int) {
        _uiState.update { it.copy(bookingAlerts = it.bookingAlerts.filter { a -> a.id != bookingId }) }
    }

    fun dismissAllAlerts() {
        _uiState.update { it.copy(bookingAlerts = emptyList()) }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun logout() {
        prefs.clear()
        _uiState.update { it.copy(isLoggedOut = true) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        alertReconnectJob?.cancel()
        alertStomp?.disconnect()
    }

    // ── State ─────────────────────────────────────────────────────────────────

    data class HomeUiState(
        val stats:         DashboardStats  = DashboardStats(),
        val loading:       Boolean         = true,
        val username:      String          = "",
        val displayName:   String          = "",
        val isLoggedOut:   Boolean         = false,
        val bookingAlerts: List<BookingDTO> = emptyList(),
    )
}
