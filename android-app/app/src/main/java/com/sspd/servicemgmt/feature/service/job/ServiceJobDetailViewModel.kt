package com.sspd.servicemgmt.feature.service.job

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.AssignmentRequest
import com.sspd.servicemgmt.core.network.CustomerCreditApplyRequest
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.PaymentTransactionDTO
import com.sspd.servicemgmt.core.network.ProductSerialDTO
import com.sspd.servicemgmt.core.network.AssignmentActionRequest
import com.sspd.servicemgmt.core.network.NoteRequest
import com.sspd.servicemgmt.core.network.ReasonRequest
import com.sspd.servicemgmt.core.network.ReworkRequestDTO
import com.sspd.servicemgmt.core.network.ServiceJobAttachmentDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.network.ServiceJobNotificationDTO
import com.sspd.servicemgmt.core.network.ServiceJobPayDueRequest
import com.sspd.servicemgmt.core.network.SettleJobRequest
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.navigation.optionalId
import com.sspd.servicemgmt.core.realtime.onDataEvent

class ServiceJobDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val jobId: Int = savedStateHandle.optionalId("jobId")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Service Job") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token   = ApiClient.bearer(prefs.authToken)
                val jobD    = async { ApiClient.service.getServiceJobById(token, jobId) }
                val teamD   = async { ApiClient.service.getServiceJobTeam(token, jobId) }
                val pmD     = async { ApiClient.service.getActivePaymentMethods(token) }
                val staffD  = async { ApiClient.service.getActiveStaff(token) }
                val settingsD = async { ApiClient.service.getCompanySettings(token) }
                val jobData = jobD.await().body()?.data
                val allSerials = (jobData?.productParts ?: emptyList()).flatMap { it.serialNumbers ?: emptyList() }
                val snMap: Map<String, ProductSerialDTO> = if (allSerials.isNotEmpty()) {
                    runCatching {
                        ApiClient.service.getProductSerialsBySerials(token, allSerials)
                            .body()?.data?.associateBy { it.serialNumber } ?: emptyMap()
                    }.getOrElse { emptyMap() }
                } else emptyMap()
                _uiState.update {
                    it.copy(
                        job               = jobData,
                        team              = teamD.await().body()?.data,
                        paymentMethods    = pmD.await().body()?.data ?: emptyList(),
                        staff             = staffD.await().body()?.data ?: emptyList(),
                        serialWarrantyMap = snMap,
                        serviceAllowDeliveryWithDue = settingsD.await().body()?.data?.serviceAllowDeliveryWithDue == true,
                        loading           = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    // ── Status Update ─────────────────────────────────────────────────────────

    fun updateStatus(status: String, holdReason: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.updateServiceJobStatus(token, jobId, status, holdReason)
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(job = data, actionLoading = false, actionSuccess = "အဆင့် ပြောင်းလဲပြီး", showHoldDialog = false) }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
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
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(job = data, actionLoading = false, showDueDeliveryDialog = false, actionSuccess = "ပစ္စည်းပြန်အပ်ပြီး") }
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
        warehouseId: Int?,
        txnNo:     String?,
        dueDate:   String?,
        payments:  List<PaymentTransactionDTO>? = null,
        discountAllocationMethod: String = "PRO_RATA"
    ) {
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
                        warehouseId     = warehouseId,
                        transactionNo   = txnNo?.ifBlank { null },
                        dueDate         = dueDate,
                        payments        = payments?.ifEmpty { null }
                    )
                )
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update {
                        it.copy(
                            job             = data,
                            actionLoading   = false,
                            showSettleDialog = false,
                            actionSuccess   = "ငွေချေပြီး ✓"
                        )
                    }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
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
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update {
                        it.copy(
                            job              = data,
                            actionLoading    = false,
                            showPayDueDialog = false,
                            actionSuccess    = "ကျန်ငွေ ဆပ်ပြီး ✓"
                        )
                    }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
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
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.createRework(token, jobId, request)
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update {
                        it.copy(actionLoading = false, showReworkDialog = false, actionSuccess = "Rework Job ${data.jobNo} ဖန်တီးပြီး")
                    }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "Rework မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun voidSettlement(reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.voidServiceJobSettlement(token, jobId, mapOf("reason" to reason))
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(job = data, actionLoading = false, showVoidDialog = false, actionSuccess = "Settlement ပြန်ဖျက်ပြီး") }
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
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(job = data, actionLoading = false, actionSuccess = "Estimate အတည်ပြုပြီး") }
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
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(job = data, actionLoading = false, actionSuccess = "Estimate Hold ထားပြီး") }
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
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(job = data, actionLoading = false, actionSuccess = "Estimate ငြင်းပယ်ပြီး") }
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
                    ServiceJobAttachmentDTO(attachmentType = type, fileName = "job-photo.jpg", contentType = "image/jpeg", dataUrl = dataUrl)
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

    fun assignTechnician(staffId: Int, role: String, taskDescription: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.assignServiceJobTechnician(
                    token, jobId,
                    AssignmentRequest(staffId = staffId, role = role, taskDescription = taskDescription.ifBlank { null })
                )
                if (res.isSuccessful) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = "Technician သတ်မှတ်ပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun cancelAssignment(assignmentId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.cancelServiceJobAssignment(token, jobId, assignmentId)
                if (res.isSuccessful) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = "Assignment ဖယ်ပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun submitLeadFinalCheck(note: String) = runJobAction("Lead Final Check တင်ပြီးပါပြီ") {
        ApiClient.service.submitLeadFinalCheck(it, jobId, NoteRequest(note.ifBlank { null }))
    }

    fun approveFinal() = runJobAction("Supervisor အတည်ပြုပြီး Job COMPLETED ဖြစ်ပါပြီ") {
        ApiClient.service.approveServiceJobFinal(it, jobId)
    }

    fun returnFinalCheck(reason: String) = runJobAction("Lead Technician ထံ ပြန်ပြင်ရန်ပို့ပြီးပါပြီ") {
        ApiClient.service.returnFinalCheck(it, jobId, ReasonRequest(reason))
    }

    private inline fun runJobAction(successMsg: String, crossinline call: suspend (String) -> retrofit2.Response<com.sspd.servicemgmt.core.network.ApiResponse<ServiceJobDTO>>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = call(token)
                if (res.isSuccessful && res.body()?.data != null) {
                    load()
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = successMsg) }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    val canSupervise get() = prefs.hasPermission("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")

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
        val actionSuccess:     String?                      = null,
        val actionError:       String?                      = null
    )
}
