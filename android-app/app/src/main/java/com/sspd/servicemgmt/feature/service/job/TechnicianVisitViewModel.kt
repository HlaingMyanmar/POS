package com.sspd.servicemgmt.feature.service.job

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.TechnicianVisitDTO
import com.sspd.servicemgmt.core.tracking.VisitTracker
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TechnicianVisitViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)

    val visit: StateFlow<TechnicianVisitDTO?> = VisitTracker.visit.stateIn(
        viewModelScope, SharingStarted.Eagerly, VisitTracker.visit.value
    )
    val busy: StateFlow<Boolean> = VisitTracker.busy.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val message: StateFlow<String?> = VisitTracker.message.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    val canStart = prefs.hasPermission("CAN_ACCESS_TECHNICIAN_VISIT_START")
    val canSaveCustomerLocation = prefs.hasPermission("CAN_ACCESS_CUSTOMER_LOCATION_UPDATE")

    fun clearMessage() = VisitTracker.clearMessage()

    fun start(jobId: Int, purpose: String) = launch { VisitTracker.startVisit(getApplication(), jobId, purpose) }
    fun arrive() = launch { VisitTracker.arrive(getApplication()) }
    fun departCustomer(outcome: String, note: String? = null) = launch { VisitTracker.departCustomer(getApplication(), outcome, note) }
    fun end() = launch { VisitTracker.end(getApplication()) }
    fun cancel(reason: String) = launch { VisitTracker.cancel(getApplication(), reason) }
    fun addReason(code: String, note: String?) = launch { VisitTracker.addReason(getApplication(), code, note) }
    fun resumeJourney() = launch { VisitTracker.resumeJourney(getApplication()) }
    fun resumeTracking() = launch { VisitTracker.resumeTracking(getApplication()) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
