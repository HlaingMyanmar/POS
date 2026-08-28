package com.sspd.servicemgmt.core.tracking

import android.content.Context
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.CustomerLocationRequest
import com.sspd.servicemgmt.core.network.TechnicianVisitDTO
import com.sspd.servicemgmt.core.network.VisitReasonRequest
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VisitTracker {
    private val _visit = MutableStateFlow<TechnicianVisitDTO?>(null)
    val visit: StateFlow<TechnicianVisitDTO?> = _visit.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _pendingResume = MutableStateFlow(false)
    val pendingResume: StateFlow<Boolean> = _pendingResume.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun onServerVisit(visit: TechnicianVisitDTO?) {
        _visit.value = visit
    }

    fun stopServiceOnly(context: Context) {
        _pendingResume.value = PreferenceManager(context).activeVisitId > 0L
        TechnicianLocationService.stop(context)
    }

    suspend fun recover(context: Context) {
        val prefs = PreferenceManager(context)
        if (prefs.authToken.isBlank()) return
        val res = runCatching {
            ApiClient.service.getActiveTechnicianVisit(ApiClient.bearer(prefs.authToken))
        }.getOrNull()
        val active = res?.body()?.data
        if (active?.id != null) {
            prefs.saveActiveVisit(
                active.id,
                active.jobNo.orEmpty(),
                active.customerName.orEmpty(),
                active.status.orEmpty()
            )
            _visit.value = active
            _pendingResume.value = true
            _message.value = "Active visit ရှိနေပါသည်။ Tracking ပြန်စရန် နှိပ်ပါ။"
        } else {
            prefs.clearActiveVisit()
            _visit.value = null
            _pendingResume.value = false
            TechnicianLocationService.stop(context)
        }
    }

    suspend fun resumeTracking(context: Context) {
        if (_visit.value?.id == null) recover(context)
        if (_visit.value?.id == null) return
        _pendingResume.value = false
        TechnicianLocationService.start(context)
        _message.value = "Tracking ပြန်စပြီး"
    }

    suspend fun startVisit(context: Context, jobId: Int) {
        withBusy {
            val fix = LocationClient(context).current()
            val prefs = PreferenceManager(context)
            val res = ApiClient.service.startTechnicianVisit(
                ApiClient.bearer(prefs.authToken),
                jobId,
                fix.toPing()
            )
            val visit = res.body()?.data
            if (!res.isSuccessful || visit?.id == null) {
                throw IllegalStateException(res.body()?.message ?: "Visit စတင်မရပါ")
            }
            remember(prefs, visit)
            TechnicianLocationService.start(context)
            _message.value = "ထွက်ခွာပြီး"
        }
    }

    suspend fun arrive(context: Context) {
        val visitId = requiredVisitId(context)
        withBusy {
            val fix = LocationClient(context).current()
            val prefs = PreferenceManager(context)
            val res = ApiClient.service.arriveTechnicianVisit(
                ApiClient.bearer(prefs.authToken), visitId, fix.toPing()
            )
            val visit = res.body()?.data ?: throw IllegalStateException(res.body()?.message ?: "ရောက်ကြောင်း မသိမ်းနိုင်ပါ")
            remember(prefs, visit)
            TechnicianLocationService.start(context)
            if (visit.customerLatitude == null && visit.customerId != null && prefs.hasPermission("CAN_ACCESS_CUSTOMER_LOCATION_UPDATE")) {
                runCatching {
                    ApiClient.service.updateCustomerLocation(
                        ApiClient.bearer(prefs.authToken),
                        visit.customerId,
                        CustomerLocationRequest(fix.latitude, fix.longitude, fix.accuracy, "ARRIVAL")
                    )
                }
            }
            val distance = visit.distanceMeters
            _message.value = if (distance != null) "ရောက်ပြီး · ${distance.toInt()} m" else "ရောက်ပြီး"
        }
    }

    suspend fun end(context: Context) {
        val visitId = requiredVisitId(context)
        withBusy {
            val fix = LocationClient(context).current()
            val prefs = PreferenceManager(context)
            val pending = PendingPingStore(context).pending(visitId)
            if (pending.isNotEmpty()) {
                runCatching {
                    ApiClient.service.pingTechnicianVisitBatch(ApiClient.bearer(prefs.authToken), visitId, pending)
                }
            }
            val res = ApiClient.service.endTechnicianVisit(
                ApiClient.bearer(prefs.authToken), visitId, fix.toPing()
            )
            if (!res.isSuccessful) throw IllegalStateException(res.body()?.message ?: "Visit ပိတ်မရပါ")
            prefs.clearActiveVisit()
            _visit.value = null
            _pendingResume.value = false
            TechnicianLocationService.stop(context)
            _message.value = "ပြန်လာပြီး"
        }
    }

    suspend fun cancel(context: Context, reason: String) {
        val visitId = requiredVisitId(context)
        withBusy {
            val prefs = PreferenceManager(context)
            val res = ApiClient.service.cancelTechnicianVisit(
                ApiClient.bearer(prefs.authToken), visitId, mapOf("reason" to reason)
            )
            if (!res.isSuccessful) throw IllegalStateException(res.body()?.message ?: "Cancel မရပါ")
            prefs.clearActiveVisit()
            _visit.value = null
            _pendingResume.value = false
            TechnicianLocationService.stop(context)
            _message.value = "Visit ပယ်ဖျက်ပြီး"
        }
    }

    suspend fun addReason(context: Context, code: String, note: String?) {
        val visitId = requiredVisitId(context)
        withBusy {
            val prefs = PreferenceManager(context)
            val res = ApiClient.service.addTechnicianVisitReason(
                ApiClient.bearer(prefs.authToken), visitId, VisitReasonRequest(code, note)
            )
            res.body()?.data?.let { remember(prefs, it) }
        }
    }

    private fun remember(prefs: PreferenceManager, visit: TechnicianVisitDTO) {
        prefs.saveActiveVisit(
            visit.id ?: 0L,
            visit.jobNo.orEmpty(),
            visit.customerName.orEmpty(),
            visit.status.orEmpty()
        )
        _visit.value = visit
        _pendingResume.value = false
    }

    private fun requiredVisitId(context: Context): Long {
        val id = PreferenceManager(context).activeVisitId
        if (id <= 0L) throw IllegalStateException("Active visit မရှိပါ")
        return id
    }

    private suspend fun withBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        try {
            block()
        } catch (error: Exception) {
            _message.value = error.message ?: "လုပ်ဆောင်မှု မအောင်မြင်ပါ"
            throw error
        } finally {
            _busy.value = false
        }
    }
}
