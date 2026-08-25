package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.StaffDTO
import com.sspd.servicemgmt.api.SupplierCreditApplyRequest
import com.sspd.servicemgmt.api.SupplierCreditSummaryDTO
import com.sspd.servicemgmt.api.SupplierDTO
import com.sspd.servicemgmt.api.SupplierPayable
import com.sspd.servicemgmt.api.SupplierPaymentDTO
import com.sspd.servicemgmt.api.SupplierPaymentRequest
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SupplierPaymentViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadMasters() }

    fun loadMasters() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                coroutineScope {
                    val suppliers = async { ApiClient.service.getSuppliers(token) }
                    val methods = async { ApiClient.service.getActivePaymentMethods(token) }
                    val staff = async { ApiClient.service.getActiveStaff(token) }
                    val supplierList = suppliers.await().body()?.data ?: emptyList()
                    val methodList = methods.await().body()?.data ?: emptyList()
                    val staffList = staff.await().body()?.data ?: emptyList()
                    _uiState.update {
                        it.copy(
                            suppliers = supplierList,
                            paymentMethods = methodList,
                            staff = staffList,
                            selectedStaff = it.selectedStaff ?: staffList.firstOrNull(),
                            selectedPaymentMethod = it.selectedPaymentMethod ?: methodList.firstOrNull(),
                            loading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun selectSupplier(supplier: SupplierDTO?) {
        _uiState.update {
            it.copy(
                selectedSupplier = supplier,
                payables = emptyList(),
                payments = emptyList(),
                creditSummary = null,
                creditPurchaseId = null,
                creditAmount = "",
                creditReason = "",
                error = null,
                successMessage = null
            )
        }
        if (supplier != null) loadSupplierData(supplier.id)
    }

    fun selectPaymentMethod(method: PaymentMethodDTO?) =
        _uiState.update { it.copy(selectedPaymentMethod = method) }

    fun setAmount(v: String) = _uiState.update { it.copy(amount = v.filterMoney()) }
    fun setTransactionNo(v: String) = _uiState.update { it.copy(transactionNo = v) }
    fun setRemark(v: String) = _uiState.update { it.copy(remark = v) }
    fun setCreditPurchaseId(id: Int?) = _uiState.update { it.copy(creditPurchaseId = id) }
    fun setCreditAmount(v: String) = _uiState.update { it.copy(creditAmount = v.filterMoney()) }
    fun setCreditReason(v: String) = _uiState.update { it.copy(creditReason = v) }
    fun clearMessages() = _uiState.update { it.copy(error = null, successMessage = null) }

    fun loadSupplierData(supplierId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                coroutineScope {
                    val payablesD = async { ApiClient.service.getSupplierPayables(token, supplierId) }
                    val creditD = async { ApiClient.service.getSupplierCreditSummary(token, supplierId) }
                    val historyD = async { ApiClient.service.getSupplierPayments(token, supplierId) }
                    val payables = payablesD.await().body()?.data.orEmpty()
                    val credit = creditD.await().body()?.data
                    val history = historyD.await().body()?.data.orEmpty()
                    _uiState.update {
                        it.copy(
                            payables = payables,
                            creditSummary = credit,
                            payments = history,
                            creditPurchaseId = it.creditPurchaseId?.takeIf { id -> payables.any { p -> p.purchaseId == id } }
                                ?: payables.firstOrNull()?.purchaseId,
                            busy = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun pay() {
        if (!prefs.hasPermission("CAN_ACCESS_PAYMENT_TRANSACTION_CREATE"))
            return fail("Supplier payment ပြုလုပ်ရန် ခွင့်ပြုချက် မရှိပါ")
        val s = _uiState.value
        val supplier = s.selectedSupplier ?: return fail("Supplier ရွေးပါ")
        val staff = s.selectedStaff ?: return fail("Staff မရှိပါ")
        val method = s.selectedPaymentMethod ?: return fail("ငွေပေးချေနည်း ရွေးပါ")
        val amount = s.amount.toDoubleOrNull() ?: 0.0
        if (amount <= 0) return fail("ပမာဏ ထည့်ပါ")

        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, successMessage = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.createSupplierPayment(
                    token,
                    SupplierPaymentRequest(
                        supplierId = supplier.id,
                        staffId = staff.id,
                        paymentMethodId = method.id,
                        amount = amount,
                        transactionNo = s.transactionNo.trim().ifBlank { null },
                        remark = s.remark.trim().ifBlank { null },
                        allocations = null // FIFO by due date on server
                    )
                )
                if (res.isSuccessful) {
                    val payment = res.body()?.data
                    _uiState.update {
                        it.copy(
                            busy = false,
                            amount = "",
                            transactionNo = "",
                            remark = "",
                            successMessage = payment?.paymentNo?.let { no -> "Saved $no" } ?: "Payment saved"
                        )
                    }
                    loadSupplierData(supplier.id)
                } else {
                    _uiState.update {
                        it.copy(busy = false, error = res.body()?.message ?: "Payment မရပါ (${res.code()})")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun applyCredit() {
        if (!prefs.hasPermission("CAN_ACCESS_PAYMENT_TRANSACTION_CREATE"))
            return fail("Supplier credit အသုံးပြုရန် ခွင့်ပြုချက် မရှိပါ")
        val s = _uiState.value
        val supplier = s.selectedSupplier ?: return fail("Supplier ရွေးပါ")
        val staff = s.selectedStaff ?: return fail("Staff မရှိပါ")
        val purchaseId = s.creditPurchaseId ?: return fail("Payable ရွေးပါ")
        val amount = s.creditAmount.toDoubleOrNull() ?: 0.0
        val available = s.creditSummary?.availableCredit ?: 0.0
        if (available <= 0) return fail("Credit မရှိပါ")
        if (amount <= 0) return fail("Credit ပမာဏ ထည့်ပါ")
        if (amount > available) return fail("Available credit ထက် မကျော်ရပါ")

        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, successMessage = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.applySupplierCredit(
                    token,
                    SupplierCreditApplyRequest(
                        supplierId = supplier.id,
                        purchaseId = purchaseId,
                        staffId = staff.id,
                        amount = amount,
                        reason = s.creditReason.trim().ifBlank { null }
                    )
                )
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            creditAmount = "",
                            creditReason = "",
                            successMessage = res.body()?.data?.applicationNo?.let { no -> "Credit applied $no" }
                                ?: "Credit applied"
                        )
                    }
                    loadSupplierData(supplier.id)
                } else {
                    _uiState.update {
                        it.copy(busy = false, error = res.body()?.message ?: "Credit apply မရပါ (${res.code()})")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    data class UiState(
        val suppliers: List<SupplierDTO> = emptyList(),
        val paymentMethods: List<PaymentMethodDTO> = emptyList(),
        val staff: List<StaffDTO> = emptyList(),
        val selectedSupplier: SupplierDTO? = null,
        val selectedPaymentMethod: PaymentMethodDTO? = null,
        val selectedStaff: StaffDTO? = null,
        val payables: List<SupplierPayable> = emptyList(),
        val payments: List<SupplierPaymentDTO> = emptyList(),
        val creditSummary: SupplierCreditSummaryDTO? = null,
        val amount: String = "",
        val transactionNo: String = "",
        val remark: String = "",
        val creditPurchaseId: Int? = null,
        val creditAmount: String = "",
        val creditReason: String = "",
        val loading: Boolean = true,
        val busy: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null
    )
}

private fun String.filterMoney(): String = filter { it.isDigit() || it == '.' }.let {
    val dot = it.indexOf('.')
    if (dot < 0) it else it.take(dot + 1) + it.drop(dot + 1).replace(".", "")
}
