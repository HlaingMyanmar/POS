package com.sspd.servicemgmt.feature.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.CustomerCreditTermDTO
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreditOperationsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)
    private val _uiState = MutableStateFlow(CreditOperationsUiState())
    val uiState: StateFlow<CreditOperationsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val customersD = async { ApiClient.service.getCustomers(token) }
                val termsD = async { ApiClient.service.getCreditTerms(token) }
                val salesD = async { ApiClient.service.getSales(token, size = 1000) }
                val customers = customersD.await().body()?.data ?: emptyList()
                val terms = termsD.await().body()?.data ?: emptyList()
                val sales = salesD.await().body()?.data?.content ?: emptyList()
                val selected = _uiState.value.selectedCustomerId.takeIf { id -> customers.any { it.id == id } }
                    ?: customers.firstOrNull()?.id
                _uiState.update {
                    it.copy(customers = customers, terms = terms, sales = sales, selectedCustomerId = selected, loading = false)
                }
                selected?.let { selectCustomer(it) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "Credit data မဖတ်နိုင်ပါ") }
            }
        }
    }

    fun setSearch(v: String) = _uiState.update { it.copy(search = v) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun selectCustomer(id: Int) = _uiState.update { s ->
        val customer = s.customers.find { it.id == id }
        val term = s.terms.find { it.customerId == id }
        s.copy(
            selectedCustomerId = id,
            formCreditHold = customer?.creditHold == true,
            formCreditHoldReason = customer?.creditHoldReason.orEmpty(),
            formBlacklisted = customer?.blacklisted == true,
            formBlacklistReason = customer?.blacklistReason.orEmpty(),
            formCreditAllowed = term?.creditAllowed == true,
            formCreditLimit = ((term?.creditLimit ?: 0.0).toLong()).toString(),
            formCreditDays = (term?.creditDays ?: 0).toString(),
            error = null
        )
    }

    fun setFormCreditHold(v: Boolean) = _uiState.update { it.copy(formCreditHold = v) }
    fun setFormCreditHoldReason(v: String) = _uiState.update { it.copy(formCreditHoldReason = v) }
    fun setFormBlacklisted(v: Boolean) = _uiState.update { it.copy(formBlacklisted = v) }
    fun setFormBlacklistReason(v: String) = _uiState.update { it.copy(formBlacklistReason = v) }
    fun setFormCreditAllowed(v: Boolean) = _uiState.update { it.copy(formCreditAllowed = v) }
    fun setFormCreditLimit(v: String) = _uiState.update { it.copy(formCreditLimit = v.filter(Char::isDigit)) }
    fun setFormCreditDays(v: String) = _uiState.update { it.copy(formCreditDays = v.filter(Char::isDigit)) }

    fun saveControls() {
        val s = _uiState.value
        val customer = s.customers.find { it.id == s.selectedCustomerId } ?: return
        if (s.formCreditHold && s.formCreditHoldReason.isBlank()) { _uiState.update { it.copy(error = "Credit Hold အကြောင်းရင်း ဖြည့်ပါ") }; return }
        if (s.formBlacklisted && s.formBlacklistReason.isBlank()) { _uiState.update { it.copy(error = "Blacklist အကြောင်းရင်း ဖြည့်ပါ") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(savingControls = true, error = null) }
            try {
                val body = customer.copy(
                    creditHold = s.formCreditHold,
                    creditHoldReason = if (s.formCreditHold) s.formCreditHoldReason.trim() else "",
                    blacklisted = s.formBlacklisted,
                    blacklistReason = if (s.formBlacklisted) s.formBlacklistReason.trim() else ""
                )
                val res = ApiClient.service.updateCustomer(ApiClient.bearer(prefs.authToken), customer.id!!, body)
                if (res.isSuccessful) load()
                else _uiState.update { it.copy(error = res.body()?.message ?: "Control မသိမ်းနိုင်ပါ (${res.code()})") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Control မသိမ်းနိုင်ပါ") }
            }
            _uiState.update { it.copy(savingControls = false) }
        }
    }

    fun saveTerms() {
        val s = _uiState.value
        val customerId = s.selectedCustomerId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(savingTerms = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val existing = s.terms.find { it.customerId == customerId }
                val body = CustomerCreditTermDTO(
                    id = existing?.id,
                    customerId = customerId,
                    creditAllowed = s.formCreditAllowed,
                    creditLimit = if (s.formCreditAllowed) s.formCreditLimit.toDoubleOrNull() ?: 0.0 else 0.0,
                    creditDays = if (s.formCreditAllowed) s.formCreditDays.toIntOrNull() ?: 0 else 0
                )
                val res = if (existing?.id != null) ApiClient.service.updateCreditTerm(token, body)
                else ApiClient.service.createCreditTerm(token, body)
                if (res.isSuccessful) load()
                else _uiState.update { it.copy(error = res.body()?.message ?: "Credit terms မသိမ်းနိုင်ပါ (${res.code()})") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Credit terms မသိမ်းနိုင်ပါ") }
            }
            _uiState.update { it.copy(savingTerms = false) }
        }
    }

    data class CreditOperationsUiState(
        val loading: Boolean = true,
        val savingControls: Boolean = false,
        val savingTerms: Boolean = false,
        val error: String? = null,
        val customers: List<CustomerDTO> = emptyList(),
        val terms: List<CustomerCreditTermDTO> = emptyList(),
        val sales: List<SaleDTO> = emptyList(),
        val selectedCustomerId: Int? = null,
        val search: String = "",
        val formCreditHold: Boolean = false,
        val formCreditHoldReason: String = "",
        val formBlacklisted: Boolean = false,
        val formBlacklistReason: String = "",
        val formCreditAllowed: Boolean = false,
        val formCreditLimit: String = "0",
        val formCreditDays: String = "0"
    )
}
