package com.sspd.servicemgmt.feature.service.job

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.CustomerCreditApplyRequest
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.PaymentTransactionDTO
import com.sspd.servicemgmt.core.network.ProductSerialDTO
import com.sspd.servicemgmt.core.network.ReworkRequestDTO
import com.sspd.servicemgmt.core.network.AssignmentActionRequest
import com.sspd.servicemgmt.core.network.NoteRequest
import com.sspd.servicemgmt.core.network.ReasonRequest
import com.sspd.servicemgmt.core.network.AssignmentDecisionRequest
import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.HandoverDTO
import com.sspd.servicemgmt.core.network.HandoverRequest
import com.sspd.servicemgmt.core.network.ServiceJobAttachmentDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.network.ServiceJobNotificationDTO
import com.sspd.servicemgmt.core.network.ServiceJobPayDueRequest
import com.sspd.servicemgmt.core.network.SettleJobRequest
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.network.httpFailureMessage
import com.sspd.servicemgmt.core.network.toUserNetworkMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.realtime.onDataEvent
import java.util.concurrent.atomic.AtomicBoolean

class ServiceJobDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val jobId: Int = checkNotNull(savedStateHandle["jobId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val loadInFlight = AtomicBoolean(false)
    private val actionInFlight = AtomicBoolean(false)

    init {
        load()
        onDataEvent("Service Job", debounceMs = 1200L) { load(background = true) }
    }

    fun load(background: Boolean = false) {
        if (!loadInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            val keepJob = _uiState.value.job != null
            _uiState.update {
                it.copy(
                    loading = !background && !keepJob,
                    loadError = null
                )
            }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val jobRes = ApiClient.service.getServiceJobById(token, jobId)
                val jobData = jobRes.body()?.data
                if (!jobRes.isSuccessful || jobData == null) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadError = httpFailureMessage(jobRes.code(), jobRes.body()?.message)
                        )
                    }
                    return@launch
                }
                val extras = loadExtras(token, jobData)
                _uiState.update {
                    it.copy(
                        job = jobData,
                        team = extras.team ?: it.team,
                        teamError = extras.teamError,
                        paymentMethods = extras.paymentMethods ?: it.paymentMethods,
                        staff = extras.staff ?: it.staff,
                        serialWarrantyMap = extras.serials,
                        serviceAllowDeliveryWithDue = extras.allowDue ?: it.serviceAllowDeliveryWithDue,
                        extrasWarning = extras.warning,
                        loading = false,
                        loadError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        loadError = if (it.job == null) e.toUserNetworkMessage() else null,
                        extrasWarning = if (it.job != null) e.toUserNetworkMessage() else it.extrasWarning
                    )
                }
            } finally {
                loadInFlight.set(false)
            }
        }
    }

    private data class Extras(
        val team: TeamSnapshotDTO? = null,
        val teamError: String? = null,
        val paymentMethods: List<PaymentMethodDTO>? = null,
        val staff: List<StaffDTO>? = null,
        val serials: Map<String, ProductSerialDTO> = emptyMap(),
        val allowDue: Boolean? = null,
        val warning: String? = null
    )

    private suspend fun loadExtras(token: String, itJob: ServiceJobDTO?): Extras = coroutineScope {
        val currentUi = _uiState.value
        val hasPm = currentUi.paymentMethods.isNotEmpty()
        val hasStaff = currentUi.staff.isNotEmpty()

        val teamAttempt = async { runCatching { ApiClient.service.getServiceJobTeam(token, jobId) } }
        val pmAttempt = if (hasPm) null else async { runCatching { ApiClient.service.getActivePaymentMethods(token) } }
        val staffAttempt = if (hasStaff) null else async { runCatching { ApiClient.service.getActiveStaff(token) } }
        val settingsAttempt = async { runCatching { ApiClient.service.getCompanySettings(token) } }

        val teamResult = teamAttempt.await()
        val teamRes = teamResult.getOrNull()
        val warnings = mutableListOf<String>()
        val teamError = when {
            teamResult.isFailure -> {
                warnings += "Assignment စာရင်း မရပါ"
                teamResult.exceptionOrNull()?.toUserNetworkMessage()
            }
            teamRes?.isSuccessful != true ->
                "Assignment မရပါ (HTTP ${teamRes?.code()}) — CAN_ACCESS_SERVICE_JOB_READ စစ်ပါ"
            else -> null
        }
        val pmResult = pmAttempt?.await()
        val pmData = if (hasPm) currentUi.paymentMethods else pmResult?.getOrNull()?.body()?.data
        if (!hasPm && (pmResult == null || pmResult.isFailure || pmResult.getOrNull()?.isSuccessful != true)) {
            warnings += "ငွေပေးချေနည်း စာရင်း မရပါ"
        }

        val staffResult = staffAttempt?.await()
        val staffData = if (hasStaff) currentUi.staff else staffResult?.getOrNull()?.body()?.data
        if (!hasStaff && (staffResult == null || staffResult.isFailure || staffResult.getOrNull()?.isSuccessful != true)) {
            warnings += "Staff စာရင်း မရပါ"
        }

        val settingsRes = settingsAttempt.await()
        if (settingsRes.isFailure || settingsRes.getOrNull()?.isSuccessful != true) {
            warnings += "Company settings မရပါ"
        }
        val allSerials = (itJob?.productParts ?: emptyList()).flatMap { it.serialNumbers ?: emptyList() }
        val snMap: Map<String, ProductSerialDTO> = if (allSerials.isNotEmpty()) {
            runCatching {
                ApiClient.service.getProductSerialsBySerials(token, allSerials)
                    .body()?.data?.associateBy { it.serialNumber } ?: emptyMap()
            }.getOrElse { emptyMap() }
        } else emptyMap()
        Extras(
            team = teamRes?.body()?.data,
            teamError = teamError,
            paymentMethods = pmData,
            staff = staffData,
            serials = snMap,
            allowDue = settingsRes.getOrNull()?.body()?.data?.serviceAllowDeliveryWithDue,
            warning = warnings.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        )
    }

    private fun beginAction(): Boolean = actionInFlight.compareAndSet(false, true)

    // ── Status Update ─────────────────────────────────────────────────────────

    fun updateStatus(status: String, holdReason: String? = null) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.updateServiceJobStatus(token, jobId, status, holdReason)
                if (res.isSuccessful && res.body()?.data != null) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = "အဆင့် ပြောင်းလဲပြီး", showHoldDialog = false) }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.toUserNetworkMessage()) }
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    fun acceptAssignment(assignmentId: Int) {
        runTeamAction("တာဝန်လက်ခံပြီးပါပြီ") {
            ApiClient.service.acceptServiceJobAssignment(it, jobId, assignmentId)
        }
    }

    fun rejectAssignment(assignmentId: Int, reason: String?) {
        runTeamAction("Assignment ငြင်းပယ်ပြီးပါပြီ") {
            ApiClient.service.rejectServiceJobAssignment(it, jobId, assignmentId, AssignmentDecisionRequest(reason))
        }
    }

    fun acceptHandover(handoverId: Int) {
        runHandoverAction("Hand Over လက်ခံပြီးပါပြီ") {
            ApiClient.service.acceptServiceJobHandover(it, jobId, handoverId)
        }
    }

    fun rejectHandover(handoverId: Int, reason: String?) {
        runHandoverAction("Hand Over ငြင်းပယ်ပြီးပါပြီ") {
            ApiClient.service.rejectServiceJobHandover(it, jobId, handoverId, AssignmentDecisionRequest(reason))
        }
    }

    fun requestHandover(
        fromAssignmentId: Int,
        toStaffId: Int,
        completedWork: String?,
        remainingWork: String,
        diagnosisNote: String?
    ) {
        runHandoverAction("Hand Over တောင်းဆိုပြီးပါပြီ") {
            ApiClient.service.requestServiceJobHandover(
                it, jobId,
                HandoverRequest(
                    fromAssignmentId = fromAssignmentId,
                    toStaffId = toStaffId,
                    completedWork = completedWork,
                    remainingWork = remainingWork,
                    diagnosisNote = diagnosisNote
                )
            )
        }
    }

    fun recordWork(
        assignmentId: Int,
        action: String,
        note: String? = null,
        completedWork: String? = null,
        serviceDetails: String? = null,
        partsDetails: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val success = when (action.uppercase()) {
            "COMPLETE" -> "လုပ်ငန်းပြီးစီးမှတ်တမ်း သိမ်းပြီး"
            "NOTE" -> "လုပ်ငန်းမှတ်တမ်း တင်ပြီး"
            else -> "မှတ်တမ်း သိမ်းပြီး"
        }
        runTeamAction(success, onSuccess) {
            ApiClient.service.recordServiceJobWork(
                it, jobId, assignmentId,
                AssignmentActionRequest(
                    action = action,
                    note = note,
                    completedWork = completedWork,
                    serviceDetails = serviceDetails,
                    partsDetails = partsDetails
                )
            )
        }
    }

    fun submitLeadFinalCheck(note: String) = runJobAction(
        if (_uiState.value.team?.supervisorApprovalRequired == false) "Lead Final Check ပြီး၍ Job COMPLETED ဖြစ်ပါပြီ"
        else "Lead Final Check တင်ပြီးပါပြီ"
    ) {
        ApiClient.service.submitLeadFinalCheck(it, jobId, NoteRequest(note.ifBlank { null }))
    }

    fun approveFinal() = runJobAction("Supervisor အတည်ပြုပြီး Job COMPLETED ဖြစ်ပါပြီ") {
        ApiClient.service.approveServiceJobFinal(it, jobId)
    }

    fun returnFinalCheck(reason: String) = runJobAction("Lead Technician ထံ ပြန်ပြင်ရန်ပို့ပြီးပါပြီ") {
        ApiClient.service.returnFinalCheck(it, jobId, ReasonRequest(reason))
    }

    private inline fun runJobAction(successMsg: String, crossinline call: suspend (String) -> retrofit2.Response<com.sspd.servicemgmt.core.network.ApiResponse<ServiceJobDTO>>) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = call(token)
                if (res.isSuccessful && res.body()?.data != null) {
                    load(background = true)
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = successMsg) }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.toUserNetworkMessage()) }
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    val canSupervise get() = prefs.hasPermission("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")
    val myStaffId get() = prefs.staffId

    private fun runHandoverAction(successMsg: String, call: suspend (String) -> retrofit2.Response<com.sspd.servicemgmt.core.network.ApiResponse<HandoverDTO>>) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = call(token)
                if (res.isSuccessful && res.body()?.data != null) {
                    load(background = true)
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = successMsg) }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.toUserNetworkMessage()) }
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    private fun runTeamAction(
        successMsg: String = "မှတ်တမ်း သိမ်းပြီး",
        onSuccess: () -> Unit = {},
        call: suspend (String) -> retrofit2.Response<com.sspd.servicemgmt.core.network.ApiResponse<AssignmentDTO>>
    ) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = call(token)
                if (res.isSuccessful && res.body()?.data != null) {
                    onSuccess()
                    load(background = true)
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = successMsg) }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.toUserNetworkMessage()) }
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    fun deliver() {
        val job = _uiState.value.job ?: return
        val due = job.dueAmount ?: 0.0
        val approved = !job.dueDeliveryApprovedAt.isNullOrBlank()
        if (due > 0.0 && !approved) {
            if (!_uiState.value.serviceAllowDeliveryWithDue) {
                _uiState.update { it.copy(actionError = "Company Settings မှာ အကြွေးကျန်ဖြင့် ပေးအပ်ခွင့် ပိတ်ထားသည်") }
                return
            }
            _uiState.update { it.copy(showDueDeliveryDialog = true, actionError = null) }
            return
        }
        deliverNow()
    }

    fun dismissDueDeliveryDialog() = _uiState.update { it.copy(showDueDeliveryDialog = false) }

    fun confirmDueDeliveryAndDeliver(reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val approveRes = ApiClient.service.approveServiceJobDueDelivery(token, jobId, ReasonRequest(reason.trim()))
                if (!approveRes.isSuccessful) {
                    _uiState.update { it.copy(actionLoading = false, actionError = approveRes.body()?.message ?: "Due delivery အတည်ပြုမရပါ") }
                    return@launch
                }
                approveRes.body()?.data?.let { approvedJob -> _uiState.update { state -> state.copy(job = approvedJob) } }
                deliverNow()
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    private fun deliverNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val res = ApiClient.service.deliverServiceJob(ApiClient.bearer(prefs.authToken), jobId)
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(job = res.body()?.data, actionLoading = false, showDueDeliveryDialog = false, actionSuccess = "ပစ္စည်းပြန်အပ်ပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "ပြန်အပ်မှု မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ── Settle ────────────────────────────────────────────────────────────────

    fun showSettleDialog() = _uiState.update { it.copy(showSettleDialog = true) }
    fun dismissSettleDialog() = _uiState.update { it.copy(showSettleDialog = false, actionError = null) }

    fun settle(
        finalCost: Double,
        discount:  Double,
        foc:       Boolean,
        paid:      Double,
        methodId:  Int?,
        txnNo:     String?,
        dueDate:   String?,
        payments:  List<PaymentTransactionDTO>? = null,
        discountAllocationMethod: String = "PRO_RATA"
    ) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.settleServiceJob(
                    token, jobId,
                    SettleJobRequest(
                        finalCost       = finalCost,
                        discountAmount  = discount,
                        discountAllocationMethod = discountAllocationMethod,
                        foc             = foc,
                        paidAmount      = paid,
                        paymentMethodId = payments?.firstOrNull()?.paymentMethodId ?: methodId,
                        transactionNo   = txnNo?.ifBlank { null },
                        dueDate         = dueDate,
                        payments        = payments?.ifEmpty { null }
                    )
                )
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update {
                        it.copy(
                            job             = res.body()?.data,
                            actionLoading   = false,
                            showSettleDialog = false,
                            actionSuccess   = "ငွေချေပြီး ✓"
                        )
                    }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.toUserNetworkMessage()) }
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    // ── Pay Due ───────────────────────────────────────────────────────────────

    fun showPayDueDialog() = _uiState.update { it.copy(showPayDueDialog = true) }
    fun dismissPayDueDialog() = _uiState.update { it.copy(showPayDueDialog = false, actionError = null) }

    fun payDue(
        amount: Double,
        methodId: Int,
        txnNo: String?,
        note: String?,
        payments: List<PaymentTransactionDTO>? = null,
        paymentDiscount: Double = 0.0,
        paymentDiscountApprovalNote: String? = null
    ) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.payServiceJobDue(
                    token, jobId,
                    ServiceJobPayDueRequest(
                        paidAmount      = amount,
                        paymentMethodId = payments?.firstOrNull()?.paymentMethodId ?: methodId,
                        transactionNo   = txnNo?.ifBlank { null },
                        note            = note?.ifBlank { null },
                        payments        = payments?.ifEmpty { null },
                        paymentDiscountAmount = paymentDiscount,
                        paymentDiscountApprovalNote = paymentDiscountApprovalNote?.ifBlank { null }
                    )
                )
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update {
                        it.copy(
                            job              = res.body()?.data,
                            actionLoading    = false,
                            showPayDueDialog = false,
                            actionSuccess    = "ကျန်ငွေ ဆပ်ပြီး ✓"
                        )
                    }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.toUserNetworkMessage()) }
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    fun clearActionSuccess() = _uiState.update { it.copy(actionSuccess = null) }
    fun clearActionError()   = _uiState.update { it.copy(actionError = null) }

    fun showHoldDialog() = _uiState.update { it.copy(showHoldDialog = true) }
    fun dismissHoldDialog() = _uiState.update { it.copy(showHoldDialog = false) }

    fun showReworkDialog() = _uiState.update { it.copy(showReworkDialog = true) }
    fun dismissReworkDialog() = _uiState.update { it.copy(showReworkDialog = false) }

    fun showVoidDialog() = _uiState.update { it.copy(showVoidDialog = true) }
    fun dismissVoidDialog() = _uiState.update { it.copy(showVoidDialog = false) }

    fun showCreditDialog() {
        viewModelScope.launch {
            val customerId = _uiState.value.job?.customerId
            val bal = if (customerId != null) creditBalance(customerId) else 0.0
            _uiState.update { it.copy(showCreditDialog = true, creditBalance = bal) }
        }
    }
    fun dismissCreditDialog() = _uiState.update { it.copy(showCreditDialog = false) }

    fun showNotifyDialog() = _uiState.update { it.copy(showNotifyDialog = true) }
    fun dismissNotifyDialog() = _uiState.update { it.copy(showNotifyDialog = false) }

    fun createRework(request: ReworkRequestDTO) {
        if (!beginAction()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.createRework(token, jobId, request)
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update {
                        it.copy(actionLoading = false, showReworkDialog = false, actionSuccess = "Rework Job ${res.body()?.data?.jobNo} ဖန်တီးပြီး")
                    }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "Rework မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.toUserNetworkMessage()) }
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    fun voidSettlement(reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.voidServiceJobSettlement(token, jobId, mapOf("reason" to reason))
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(job = res.body()?.data, actionLoading = false, showVoidDialog = false, actionSuccess = "Settlement ပြန်ဖျက်ပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "Void မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun applyCredit(amount: Double, staffId: Int, reason: String?) {
        val job = _uiState.value.job ?: return
        val customerId = job.customerId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.applyCustomerCredit(
                    token,
                    CustomerCreditApplyRequest(
                        customerId = customerId,
                        serviceJobId = jobId,
                        staffId = staffId,
                        amount = amount,
                        reason = reason
                    )
                )
                if (res.isSuccessful) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, showCreditDialog = false, actionSuccess = "Credit သုံးပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "Credit မသုံးနိုင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun approveEstimate() {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.approveServiceJobEstimate(token, jobId)
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(job = res.body()?.data, actionLoading = false, actionSuccess = "Estimate အတည်ပြုပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun holdEstimate(reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.holdServiceJobEstimate(token, jobId, ReasonRequest(reason.trim()))
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(job = res.body()?.data, actionLoading = false, actionSuccess = "Estimate Hold ထားပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun rejectEstimate(reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.rejectServiceJobEstimate(token, jobId, ReasonRequest(reason.trim()))
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(job = res.body()?.data, actionLoading = false, actionSuccess = "Estimate ငြင်းပယ်ပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun notifyCustomer(channel: String, note: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.notifyServiceJobCustomer(
                    token, jobId, ServiceJobNotificationDTO(channel = channel, note = note)
                )
                if (res.isSuccessful) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, showNotifyDialog = false, actionSuccess = "အကြောင်းကြားမှတ်တမ်း သိမ်းပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun addPhoto(dataUrl: String, type: String = "JOB_PHOTO") {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.addServiceJobAttachment(
                    token, jobId,
                    ServiceJobAttachmentDTO(
                        attachmentType = type,
                        fileName = "job-photo.jpg",
                        contentType = "image/jpeg",
                        dataUrl = dataUrl
                    )
                )
                if (res.isSuccessful) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = "ဓာတ်ပုံ သိမ်းပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "ပုံမသိမ်းနိုင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun deleteAttachment(attachmentId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val res = ApiClient.service.deleteServiceJobAttachment(
                    ApiClient.bearer(prefs.authToken), jobId, attachmentId
                )
                if (res.isSuccessful) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = "Attachment ဖျက်ပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "Attachment မဖျက်နိုင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    suspend fun creditBalance(customerId: Int): Double {
        return runCatching {
            ApiClient.service.getCustomerCreditSummary(ApiClient.bearer(prefs.authToken), customerId)
                .body()?.data?.availableCredit ?: 0.0
        }.getOrDefault(0.0)
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun showDeleteDialog()    = _uiState.update { it.copy(showDeleteDialog = true, actionError = null) }
    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false, actionError = null) }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(deleteLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.deleteServiceJob(token, jobId)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(deleteLoading = false, showDeleteDialog = false) }
                    onDeleted()
                } else {
                    _uiState.update { it.copy(deleteLoading = false, actionError = "ဖျက်မှု မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(deleteLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    data class UiState(
        val job:               ServiceJobDTO?               = null,
        val team:              TeamSnapshotDTO?             = null,
        val teamError:         String?                      = null,
        val paymentMethods:    List<PaymentMethodDTO>        = emptyList(),
        val staff:             List<StaffDTO>                = emptyList(),
        val serialWarrantyMap: Map<String, ProductSerialDTO> = emptyMap(),
        val loading:           Boolean                      = true,
        val actionLoading:     Boolean                      = false,
        val showSettleDialog:  Boolean                      = false,
        val showPayDueDialog:  Boolean                      = false,
        val showDeleteDialog:  Boolean                      = false,
        val showReworkDialog:  Boolean                      = false,
        val showVoidDialog:    Boolean                      = false,
        val showCreditDialog:  Boolean                      = false,
        val showNotifyDialog:  Boolean                      = false,
        val showHoldDialog:    Boolean                      = false,
        val showDueDeliveryDialog: Boolean                  = false,
        val serviceAllowDeliveryWithDue: Boolean            = false,
        val creditBalance:     Double                       = 0.0,
        val deleteLoading:     Boolean                      = false,
        val extrasWarning:     String?                      = null,
        val loadError:         String?                      = null,
        val actionSuccess:     String?                      = null,
        val actionError:       String?                      = null
    )
}
