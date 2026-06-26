package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.AccountBalanceDTO
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ChartOfAccountDTO
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.StaffDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OpeningBalanceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token   = ApiClient.bearer(prefs.authToken)
                val balD    = async { ApiClient.service.getAccountBalances(token) }
                val coaD    = async { ApiClient.service.getChartOfAccounts(token) }
                val staffD  = async { ApiClient.service.getActiveStaff(token) }
                val pmD     = async { ApiClient.service.getActivePaymentMethods(token) }

                val balances = balD.await().body()?.data ?: emptyList()
                val accounts = coaD.await().body()?.data ?: emptyList()
                val staff    = staffD.await().body()?.data ?: emptyList()
                val pms      = pmD.await().body()?.data ?: emptyList()

                // Merge: for each COA account, find its balance (if any)
                val merged = accounts.map { coa ->
                    val bal = balances.find { it.accountId == coa.id }
                    AccountRow(
                        accountId   = coa.id ?: 0,
                        accountName = coa.accountName ?: "",
                        accountType = coa.accountType ?: "",
                        accountCode = coa.code ?: "",
                        opening     = bal?.openingBalance ?: 0.0,
                        current     = bal?.currentBalance ?: 0.0
                    )
                }

                _uiState.update {
                    it.copy(
                        accounts       = merged,
                        paymentMethods = pms,
                        staffList      = staff,
                        selectedStaff  = it.selectedStaff ?: staff.firstOrNull(),
                        selectedPm     = it.selectedPm    ?: pms.firstOrNull(),
                        loading        = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ── Edit dialog ───────────────────────────────────────────────────────────

    fun openEdit(row: AccountRow) {
        _uiState.update {
            it.copy(
                editRow       = row,
                editAmountStr = if (row.opening > 0) String.format("%.0f", row.opening) else "",
                saveError     = null
            )
        }
    }

    fun dismissEdit() = _uiState.update { it.copy(editRow = null, editAmountStr = "", saveError = null) }

    fun setEditAmount(v: String) = _uiState.update { it.copy(editAmountStr = v) }
    fun selectStaff(s: StaffDTO) = _uiState.update { it.copy(selectedStaff = s) }
    fun selectPm(pm: PaymentMethodDTO) = _uiState.update { it.copy(selectedPm = pm) }
    fun clearError() = _uiState.update { it.copy(error = null, saveError = null) }

    fun save() {
        val s      = _uiState.value
        val row    = s.editRow ?: return
        val amount = s.editAmountStr.toDoubleOrNull()
        if (amount == null || amount < 0) {
            _uiState.update { it.copy(saveError = "ပမာဏ မှန်ကန်သော ဂဏာန်း ထည့်ပါ") }; return
        }
        val staffId = s.selectedStaff?.id
        if (staffId == null) {
            _uiState.update { it.copy(saveError = "ဝန်ထမ်း ရွေးပါ") }; return
        }
        val pmId = s.selectedPm?.id
        if (pmId == null) {
            _uiState.update { it.copy(saveError = "ငွေပေးချေနည်း ရွေးပါ") }; return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.setOpeningBalance(
                    auth            = token,
                    accountId       = row.accountId,
                    amount          = amount,
                    staffId         = staffId,
                    paymentMethodId = pmId
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    _uiState.update { it.copy(saving = false, editRow = null, editAmountStr = "") }
                    load()
                } else {
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ── Capital calculation ───────────────────────────────────────────────────

    fun capitalSummary(accounts: List<AccountRow>): CapitalSummary {
        val assets      = accounts.filter { it.accountType == "ASSET" }.sumOf { it.current }
        val liabilities = accounts.filter { it.accountType == "LIABILITY" }.sumOf { it.current }
        val equity      = accounts.filter { it.accountType == "EQUITY" }.sumOf { it.current }
        return CapitalSummary(assets, liabilities, equity)
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class AccountRow(
        val accountId:   Int,
        val accountName: String,
        val accountType: String,
        val accountCode: String,
        val opening:     Double,
        val current:     Double
    )

    data class CapitalSummary(
        val totalAssets:      Double,
        val totalLiabilities: Double,
        val equity:           Double
    ) {
        val netCapital get() = totalAssets - totalLiabilities
    }

    data class UiState(
        val accounts:       List<AccountRow>        = emptyList(),
        val paymentMethods: List<PaymentMethodDTO>  = emptyList(),
        val staffList:      List<StaffDTO>          = emptyList(),
        val selectedStaff:  StaffDTO?               = null,
        val selectedPm:     PaymentMethodDTO?       = null,
        val loading:        Boolean                 = true,
        val saving:         Boolean                 = false,
        val error:          String?                 = null,
        val saveError:      String?                 = null,
        val editRow:        AccountRow?             = null,
        val editAmountStr:  String                  = ""
    )
}
