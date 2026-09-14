package com.sspd.servicemgmt.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.DashboardStats
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.tracking.VisitTracker
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.util.VibrationUtil
import com.sspd.servicemgmt.core.realtime.StompClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.realtime.onDataEvent

/**
 * ViewModel managing the state and business logic for the Home/Dashboard screen.
 *
 * Responsibilities include:
 * - Loading and refreshing dashboard statistics and assigned service jobs.
 * - Listening to real-time WebSocket booking alerts (`/topic/booking-alerts`).
 * - Triggering haptic vibration feedback on new job arrivals.
 * - Handling outdoor visit tracking resumptions and user logout.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val gson  = GsonBuilder().create()

    private val _uiState = MutableStateFlow(HomeUiState(
        username     = prefs.username,
        displayName  = prefs.displayName.ifEmpty { prefs.username },
        isTechnician = prefs.isTechnician(),
        canOutdoorVisit = prefs.hasPermission("CAN_ACCESS_TECHNICIAN_VISIT_START")
    ))

    /** StateFlow exposing the current UI state to the Home screen UI. */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var alertStomp: StompClient? = null
    private var alertReconnectJob: Job? = null
    private var knownJobIds: Set<Int>? = null

    init {
        loadStats()
        connectAlertWs()
        // Refresh dashboard stats whenever sales, jobs, or bookings change
        onDataEvent("Sale", "Service Job", "Booking", debounceMs = 800L) { loadStats() }
    }

    // ── Dashboard stats ───────────────────────────────────────────────────────

    /**
     * Fetches fresh dashboard statistics and assigned service jobs from the backend API.
     *
     * @param fromPull `true` if invoked via pull-to-refresh, updating [HomeUiState.refreshing];
     *                 `false` for standard background/initial loading.
     */
    fun loadStats(fromPull: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = fromPull, loading = !fromPull && it.loading) }
            runCatching {
                coroutineScope {
                    val statsD = async { ApiClient.service.getStats(ApiClient.bearer(prefs.authToken)) }
                    val jobsD = async {
                        ApiClient.service.getServiceJobs(
                            auth = ApiClient.bearer(prefs.authToken),
                            size = 100,
                            dateFrom = "",
                            dateTo = "",
                            staffId = if (prefs.shouldScopeToOwnStaff()) prefs.staffId else null
                        )
                    }
                    val statsRes = statsD.await()
                    val jobs = jobsD.await().body()?.data?.content.orEmpty()
                    val currentJobIds = jobs.mapNotNull { it.id }.toSet()
                    if (knownJobIds != null && (currentJobIds - knownJobIds!!).isNotEmpty()) {
                        VibrationUtil.vibrateNewJob(getApplication())
                    }
                    knownJobIds = currentJobIds

                    if (statsRes.isSuccessful) {
                        _uiState.update {
                            it.copy(
                                stats = statsRes.body()?.data ?: DashboardStats(),
                                jobs = jobs,
                                loading = false,
                                refreshing = false
                            )
                        }
                    } else {
                        _uiState.update { it.copy(jobs = jobs, loading = false, refreshing = false) }
                    }
                }
            }.onFailure { _uiState.update { it.copy(loading = false, refreshing = false) } }
        }
    }

    /** Triggers a pull-to-refresh reload of dashboard statistics. */
    fun pullToRefresh() = loadStats(fromPull = true)

    // ── Booking alert WebSocket ───────────────────────────────────────────────

    /** Connects to the real-time STOMP WebSocket server to receive booking alert notifications. */
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

    /** Schedules a WebSocket reconnection attempt after a 5-second delay. */
    private fun scheduleAlertReconnect() {
        alertReconnectJob?.cancel()
        alertReconnectJob = viewModelScope.launch {
            delay(5_000)
            connectAlertWs()
        }
    }

    /** Parses raw WebSocket JSON payload and merges incoming booking alerts without duplicating existing ones. */
    private fun parseAndMergeAlerts(json: String) {
        runCatching {
            val type    = object : TypeToken<List<BookingDTO>>() {}.type
            val arrived = gson.fromJson<List<BookingDTO>>(json, type) ?: return
            _uiState.update { state ->
                // Merge: keep existing dismissed alerts gone, add new ones without duplicates
                val existingIds = state.bookingAlerts.mapTo(mutableSetOf()) { it.id }
                val newAlerts = arrived.filter { it.id !in existingIds }
                if (newAlerts.isNotEmpty()) {
                    VibrationUtil.vibrateNewJob(getApplication())
                }
                state.copy(bookingAlerts = state.bookingAlerts + newAlerts)
            }
        }
    }

    /** Dismisses a specific booking alert by [bookingId]. */
    fun dismissAlert(bookingId: Int) {
        _uiState.update { it.copy(bookingAlerts = it.bookingAlerts.filter { a -> a.id != bookingId }) }
    }

    /** Clears all active booking alerts from the UI state. */
    fun dismissAllAlerts() {
        _uiState.update { it.copy(bookingAlerts = emptyList()) }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /** Resumes active outdoor visit location tracking via [VisitTracker]. */
    fun resumeVisitTracking() {
        viewModelScope.launch {
            runCatching { VisitTracker.resumeTracking(getApplication()) }
        }
    }

    /** Clears stored user preferences and session tokens, triggering the logout flow. */
    fun logout() {
        com.sspd.servicemgmt.core.network.TechnicianPushRegistration.unregister(getApplication())
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

    /**
     * UI state snapshot for the Home screen.
     *
     * @property stats Summary statistics (sales, low stock, pending jobs, AR balances).
     * @property loading `true` when loading initial dashboard data.
     * @property refreshing `true` when refreshing data via pull-to-refresh.
     * @property username Account username from preferences.
     * @property displayName Display name or fallback username.
     * @property isLoggedOut `true` when session has been logged out.
     * @property isTechnician `true` if current account has technician privileges.
     * @property canOutdoorVisit `true` if user possesses outdoor visit permissions.
     * @property bookingAlerts Active real-time booking alert items.
     * @property jobs List of active service jobs assigned to the technician.
     */
    data class HomeUiState(
        val stats:         DashboardStats  = DashboardStats(),
        val loading:       Boolean         = true,
        val refreshing:    Boolean         = false,
        val username:      String          = "",
        val displayName:   String          = "",
        val isLoggedOut:   Boolean         = false,
        val isTechnician:  Boolean         = false,
        val canOutdoorVisit: Boolean       = false,
        val bookingAlerts: List<BookingDTO> = emptyList(),
        val jobs: List<ServiceJobDTO> = emptyList(),
    )
}
