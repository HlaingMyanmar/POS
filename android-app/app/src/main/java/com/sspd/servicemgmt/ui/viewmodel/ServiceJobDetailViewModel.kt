package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.PaymentTransactionDTO
import com.sspd.servicemgmt.api.ProductSerialDTO
import com.sspd.servicemgmt.api.ServiceJobDTO
import com.sspd.servicemgmt.api.ServiceJobPayDueRequest
import com.sspd.servicemgmt.api.SettleJobRequest
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServiceJobDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val jobId: Int = checkNotNull(savedStateHandle["jobId"])

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
                val pmD     = async { ApiClient.service.getActivePaymentMethods(token) }
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
                        paymentMethods    = pmD.await().body()?.data ?: emptyList(),
                        serialWarrantyMap = snMap,
                        loading           = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    // ── Status Update ─────────────────────────────────────────────────────────

    fun updateStatus(status: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.updateServiceJobStatus(token, jobId, status)
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(job = res.body()!!.data, actionLoading = false, actionSuccess = "အဆင့် ပြောင်းလဲပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
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
        payments:  List<PaymentTransactionDTO>? = null
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
                            job             = res.body()!!.data,
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

    fun payDue(amount: Double, methodId: Int, txnNo: String?, note: String?, payments: List<PaymentTransactionDTO>? = null) {
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
                        payments        = payments?.ifEmpty { null }
                    )
                )
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update {
                        it.copy(
                            job              = res.body()!!.data,
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
        val paymentMethods:    List<PaymentMethodDTO>        = emptyList(),
        val serialWarrantyMap: Map<String, ProductSerialDTO> = emptyMap(),
        val loading:           Boolean                      = true,
        val actionLoading:     Boolean                      = false,
        val showSettleDialog:  Boolean                      = false,
        val showPayDueDialog:  Boolean                      = false,
        val showDeleteDialog:  Boolean                      = false,
        val deleteLoading:     Boolean                      = false,
        val actionSuccess:     String?                      = null,
        val actionError:       String?                      = null
    )
}
