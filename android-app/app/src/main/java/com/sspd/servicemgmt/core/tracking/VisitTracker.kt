package com.sspd.servicemgmt.core.tracking

import android.content.Context
import com.google.gson.JsonSyntaxException
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.ApiResponse
import com.sspd.servicemgmt.core.network.CustomerLocationRequest
import com.sspd.servicemgmt.core.network.TechnicianVisitDTO
import com.sspd.servicemgmt.core.network.VisitReasonRequest
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import retrofit2.Response

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

    suspend fun startVisit(context: Context, jobId: Int, purpose: String) {
        withBusy {
            val fix = LocationClient(context).current()
            val prefs = PreferenceManager(context)
            val res = ApiClient.service.startTechnicianVisit(
                ApiClient.bearer(prefs.authToken),
                jobId,
                purpose,
                fix.toPing()
            )
            val visit = res.body()?.data
            if (!res.isSuccessful) {
                throw IllegalStateException(serverMessage(res, "Visit စတင်၍မရပါ"))
            }
            if (visit?.id == null) {
                throw IllegalStateException("Visit အချက်အလက် ပြန်မရပါ။ ပြန်ကြိုးစားပါ")
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
            if (!res.isSuccessful) {
                throw IllegalStateException(serverMessage(res, "ရောက်ကြောင်း မသိမ်းနိုင်ပါ"))
            }
            val visit = res.body()?.data ?: throw IllegalStateException("ရောက်ကြောင်း မသိမ်းနိုင်ပါ")
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

    suspend fun departCustomer(context: Context, outcome: String, note: String? = null) {
        val visitId = requiredVisitId(context)
        withBusy {
            val fix = LocationClient(context).current()
            val prefs = PreferenceManager(context)
            val res = ApiClient.service.departCustomerVisit(
                ApiClient.bearer(prefs.authToken), visitId, outcome, note, fix.toPing()
            )
            if (!res.isSuccessful) {
                throw IllegalStateException(serverMessage(res, "Customer ဆီမှ ပြန်ထွက်ချိန် မသိမ်းနိုင်ပါ"))
            }
            val visit = res.body()?.data
                ?: throw IllegalStateException("Customer ဆီမှ ပြန်ထွက်ချိန် မသိမ်းနိုင်ပါ")
            remember(prefs, visit)
            TechnicianLocationService.start(context)
            _message.value = "Customer ဆီမှ ပြန်ထွက်လာပြီး"
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
            if (!res.isSuccessful) throw IllegalStateException(serverMessage(res, "Visit ပိတ်မရပါ"))
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
            if (!res.isSuccessful) throw IllegalStateException(serverMessage(res, "Cancel မရပါ"))
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
            val fix = LocationClient(context).current()
            val res = ApiClient.service.addTechnicianVisitReason(
                ApiClient.bearer(prefs.authToken),
                visitId,
                VisitReasonRequest(code, note, fix.toPing())
            )
            if (!res.isSuccessful) {
                throw IllegalStateException(serverMessage(res, "အကြောင်းပြချက်နှင့် GPS ကို မသိမ်းနိုင်ပါ"))
            }
            res.body()?.data?.let { remember(prefs, it) }
            _message.value = "အကြောင်းပြချက်နှင့် လက်ရှိ GPS သိမ်းပြီး"
        }
    }

    suspend fun resumeJourney(context: Context) {
        val visitId = requiredVisitId(context)
        withBusy {
            val prefs = PreferenceManager(context)
            val fix = LocationClient(context).current()
            val res = ApiClient.service.resumeTechnicianJourney(
                ApiClient.bearer(prefs.authToken), visitId, fix.toPing()
            )
            if (!res.isSuccessful) {
                throw IllegalStateException(serverMessage(res, "ခရီးဆက်ကြောင်း မသိမ်းနိုင်ပါ"))
            }
            val visit = res.body()?.data
                ?: throw IllegalStateException("ခရီးဆက်ကြောင်း မသိမ်းနိုင်ပါ")
            remember(prefs, visit)
            TechnicianLocationService.start(context)
            _message.value = "ခရီးဆက်ပြီဟု မှတ်တမ်းတင်ပြီး"
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
            _message.value = friendlyError(error)
            throw error
        } finally {
            _busy.value = false
        }
    }

    private fun friendlyError(error: Exception): String {
        val msg = error.message.orEmpty()
        return when {
            error is JsonSyntaxException || msg.contains("BEGIN_ARRAY") || msg.contains("Expected a string") ->
                "ဆာဗာအဖြေကို ဖတ်မရပါ"
            msg.isNotBlank() -> msg
            else -> "လုပ်ဆောင်မှု မအောင်မြင်ပါ"
        }
    }

    private fun serverMessage(res: Response<ApiResponse<TechnicianVisitDTO>>, fallback: String): String {
        res.body()?.message?.takeIf { it.isNotBlank() }?.let { return localizeVisitError(it) }
        val raw = runCatching { res.errorBody()?.string() }.getOrNull().orEmpty()
        parseJsonMessage(raw)?.let { return localizeVisitError(it) }
        return when (res.code()) {
            401 -> "အကောင့် သက်တမ်းကုန်ပါပြီ။ ပြန်ဝင်ရောက်ပါ"
            409 -> "သင့်တွင် Active visit ရှိနေပါသည်။ အရင် Visit ကို ပိတ်ပါ"
            in 500..599 -> "ဆာဗာတွင် ပြဿနာရှိနေပါသည်"
            else -> fallback
        }
    }

    private fun parseJsonMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            listOf("message", "error", "detail").firstNotNullOfOrNull { key ->
                obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
            }
        }.getOrNull()
    }

    private fun localizeVisitError(message: String): String = when {
        message.contains("assigned technician", ignoreCase = true) ->
            "ဤ Job ကို သင့်အား assign မလုပ်ရသေးပါ"
        message.contains("not linked to staff", ignoreCase = true) ->
            "ဤအကောင့်ကို Staff နှင့် ချိတ်မထားပါ"
        message.contains("already has an active visit", ignoreCase = true) ->
            "သင့်တွင် Active visit ရှိနေပါသည်။ အရင် Visit ကို ပိတ်ပါ"
        message.contains("Invalid coordinates", ignoreCase = true) ->
            "GPS တည်နေရာ မမှန်ကန်ပါ"
        message.contains("Access Denied", ignoreCase = true) ||
            message.contains("Access is denied", ignoreCase = true) ->
            "သင့်မှာ ခွင့်ပြုချက်မရှိပါ"
        else -> message
    }
}
