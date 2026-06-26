package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.*
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ChartOfAccountDTO
import com.sspd.servicemgmt.api.ExpenseDTO
import com.sspd.servicemgmt.api.IncomeDTO
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.StaffDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExpenseFormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    // "EXPENSE" or "INCOME" passed as nav arg
    val entryType: String = savedStateHandle.get<String>("type") ?: "EXPENSE"
    val isExpense get() = entryType == "EXPENSE"

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadMaster() }

    private fun loadMaster() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token   = ApiClient.bearer(prefs.authToken)
                val accD    = async { ApiClient.service.getChartOfAccounts(token) }
                val pmD     = async { ApiClient.service.getActivePaymentMethods(token) }
                val staffD  = async { ApiClient.service.getActiveStaff(token) }
                val allAcc  = accD.await().body()?.data ?: emptyList()

                // Filter accounts by type
                val filtered = allAcc.filter { acc ->
                    val t = acc.accountType?.uppercase() ?: ""
                    val c = acc.code ?: ""
                    if (isExpense) {
                        (t == "EXPENSE" || t == "ASSET") &&
                        c !in listOf("EXP-006","EXP-007","EXP-010","EXP-011","EXP-012","EXP-013")
                    } else {
                        t == "INCOME" &&
                        c !in listOf("INC-002","INC-006","INC-007","INC-008")
                    }
                }
                _uiState.update {
                    it.copy(
                        accounts       = filtered,
                        paymentMethods = pmD.await().body()?.data ?: emptyList(),
                        staffList      = staffD.await().body()?.data ?: emptyList(),
                        loading        = false
                    )
                }
            } catch (_: Exception) { _uiState.update { it.copy(loading = false) } }
        }
    }

    // ── Field setters ─────────────────────────────────────────────────────────
    fun setAccountQuery(q: String)      = _uiState.update { it.copy(accountQuery = q, selectedAccount = null) }
    fun selectAccount(a: ChartOfAccountDTO) = _uiState.update { it.copy(selectedAccount = a, accountQuery = a.accountName ?: "") }
    fun selectPm(pm: PaymentMethodDTO?) = _uiState.update { it.copy(selectedPm = pm) }
    fun selectStaff(s: StaffDTO?)       = _uiState.update { it.copy(selectedStaff = s) }
    fun setAmount(v: String)            = _uiState.update { it.copy(amountStr = v) }
    fun setEntryDate(v: String)         = _uiState.update { it.copy(entryDate = v) }
    fun setDescription(v: String)       = _uiState.update { it.copy(description = v) }
    fun clearError()                    = _uiState.update { it.copy(saveError = null) }

    // ── Save ──────────────────────────────────────────────────────────────────
    fun save(onSuccess: () -> Unit) {
        val s = _uiState.value
        val amount = s.amountStr.toLongOrNull()
        when {
            s.selectedAccount == null -> { _uiState.update { it.copy(saveError = "အကောင့် ရွေးပါ") }; return }
            s.selectedPm == null      -> { _uiState.update { it.copy(saveError = "ငွေပေးချေနည်း ရွေးပါ") }; return }
            s.selectedStaff == null   -> { _uiState.update { it.copy(saveError = "ဝန်ထမ်း ရွေးပါ") }; return }
            amount == null || amount <= 0 -> { _uiState.update { it.copy(saveError = "ပမာဏ မှန်ကန်စွာ ထည့်ပါ") }; return }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                if (isExpense) {
                    val res = ApiClient.service.createExpense(token, ExpenseDTO(
                        accountId       = s.selectedAccount!!.id,
                        paymentMethodId = s.selectedPm!!.id,
                        staffId         = s.selectedStaff!!.id,
                        amount          = amount!!,
                        expenseDate     = s.entryDate.ifBlank { null }?.let { if (it.length == 10) "${it}T00:00:00" else it },
                        description     = s.description.ifBlank { null }
                    ))
                    if (res.isSuccessful) { _uiState.update { it.copy(saving = false) }; onSuccess() }
                    else _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})") }
                } else {
                    val res = ApiClient.service.createIncome(token, IncomeDTO(
                        accountId       = s.selectedAccount!!.id,
                        paymentMethodId = s.selectedPm!!.id,
                        staffId         = s.selectedStaff!!.id,
                        amount          = amount!!,
                        incomeDate      = s.entryDate.ifBlank { null }?.let { if (it.length == 10) "${it}T00:00:00" else it },
                        description     = s.description.ifBlank { null }
                    ))
                    if (res.isSuccessful) { _uiState.update { it.copy(saving = false) }; onSuccess() }
                    else _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    data class UiState(
        val loading:        Boolean                  = true,
        val saving:         Boolean                  = false,
        val saveError:      String?                  = null,
        val accounts:       List<ChartOfAccountDTO>  = emptyList(),
        val paymentMethods: List<PaymentMethodDTO>   = emptyList(),
        val staffList:      List<StaffDTO>           = emptyList(),
        val accountQuery:   String                   = "",
        val selectedAccount: ChartOfAccountDTO?      = null,
        val selectedPm:     PaymentMethodDTO?        = null,
        val selectedStaff:  StaffDTO?                = null,
        val amountStr:      String                   = "",
        val entryDate:      String                   = today(),
        val description:    String                   = ""
    )
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
