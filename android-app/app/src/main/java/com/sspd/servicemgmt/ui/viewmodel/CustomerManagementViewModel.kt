package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.CustomerCreditTermDTO
import com.sspd.servicemgmt.api.CustomerDTO
import com.sspd.servicemgmt.api.SaleDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomerManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)
    private val _uiState = MutableStateFlow(CustomerManagementUiState())
    val uiState: StateFlow<CustomerManagementUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val customersD = async { ApiClient.service.getCustomers(token) }
                val termsD = async { ApiClient.service.getCreditTerms(token) }
                val salesD = async { ApiClient.service.getSales(token, size = 1000) }

                _uiState.update {
                    it.copy(
                        customers = customersD.await().body()?.data ?: emptyList(),
                        terms = termsD.await().body()?.data ?: emptyList(),
                        sales = salesD.await().body()?.data?.content ?: emptyList(),
                        loading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "ဖောက်သည်စာရင်း မဖတ်နိုင်ပါ") }
            }
        }
    }

    fun setSearch(v: String) = _uiState.update { it.copy(search = v) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun openCreate() = _uiState.update {
        it.copy(
            editingCustomer = null,
            showEditor = true,
            formName = "",
            formPhone = "",
            formAddress = "",
            formCreditHold = false,
            formCreditHoldReason = "",
            formBlacklisted = false,
            formBlacklistReason = "",
            formCreditAllowed = false,
            formCreditLimit = "0",
            formCreditDays = "0",
            error = null
        )
    }

    fun openEdit(customer: CustomerDTO) = _uiState.update { s ->
        val term = s.terms.find { it.customerId == customer.id }
        s.copy(
            editingCustomer = customer,
            showEditor = true,
            formName = customer.name,
            formPhone = customer.phone.orEmpty(),
            formAddress = customer.address.orEmpty(),
            formCreditHold = customer.creditHold,
            formCreditHoldReason = customer.creditHoldReason.orEmpty(),
            formBlacklisted = customer.blacklisted,
            formBlacklistReason = customer.blacklistReason.orEmpty(),
            formCreditAllowed = term?.creditAllowed == true,
            formCreditLimit = ((term?.creditLimit ?: 0.0).toLong()).toString(),
            formCreditDays = (term?.creditDays ?: 0).toString(),
            error = null
        )
    }

    fun closeEditor() = _uiState.update { it.copy(showEditor = false, editingCustomer = null) }
    fun setFormName(v: String) = _uiState.update { it.copy(formName = v) }
    fun setFormPhone(v: String) = _uiState.update { it.copy(formPhone = v) }
    fun setFormAddress(v: String) = _uiState.update { it.copy(formAddress = v) }
    fun setFormCreditHold(v: Boolean) = _uiState.update { it.copy(formCreditHold = v) }
    fun setFormCreditHoldReason(v: String) = _uiState.update { it.copy(formCreditHoldReason = v) }
    fun setFormBlacklisted(v: Boolean) = _uiState.update { it.copy(formBlacklisted = v) }
    fun setFormBlacklistReason(v: String) = _uiState.update { it.copy(formBlacklistReason = v) }
    fun setFormCreditAllowed(v: Boolean) = _uiState.update { it.copy(formCreditAllowed = v) }
    fun setFormCreditLimit(v: String) = _uiState.update { it.copy(formCreditLimit = v.filter(Char::isDigit)) }
    fun setFormCreditDays(v: String) = _uiState.update { it.copy(formCreditDays = v.filter(Char::isDigit)) }

    fun save() {
        val s = _uiState.value
        if (s.formName.isBlank()) { _uiState.update { it.copy(error = "ဖောက်သည်အမည် ဖြည့်ပါ") }; return }
        if (s.formPhone.isBlank()) { _uiState.update { it.copy(error = "ဖုန်းနံပါတ် ဖြည့်ပါ") }; return }
        if (s.formAddress.isBlank()) { _uiState.update { it.copy(error = "လိပ်စာ ဖြည့်ပါ") }; return }
        if (s.formCreditHold && s.formCreditHoldReason.isBlank()) { _uiState.update { it.copy(error = "Credit Hold အကြောင်းရင်း ဖြည့်ပါ") }; return }
        if (s.formBlacklisted && s.formBlacklistReason.isBlank()) { _uiState.update { it.copy(error = "Blacklist အကြောင်းရင်း ဖြည့်ပါ") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val body = CustomerDTO(
                    id = s.editingCustomer?.id,
                    name = s.formName.trim(),
                    phone = s.formPhone.trim(),
                    address = s.formAddress.trim(),
                    creditHold = s.formCreditHold,
                    creditHoldReason = if (s.formCreditHold) s.formCreditHoldReason.trim() else "",
                    blacklisted = s.formBlacklisted,
                    blacklistReason = if (s.formBlacklisted) s.formBlacklistReason.trim() else ""
                )
                val res = if (s.editingCustomer?.id != null) {
                    ApiClient.service.updateCustomer(token, s.editingCustomer.id, body)
                } else {
                    ApiClient.service.createCustomer(token, body)
                }
                val saved = res.body()?.data
                if (!res.isSuccessful || saved?.id == null) {
                    _uiState.update { it.copy(saving = false, error = res.body()?.message ?: "ဖောက်သည် မသိမ်းနိုင်ပါ (${res.code()})") }
                    return@launch
                }

                saveTerm(token, saved.id)
                _uiState.update { it.copy(saving = false, showEditor = false, editingCustomer = null) }
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, error = e.message ?: "ဖောက်သည် မသိမ်းနိုင်ပါ") }
            }
        }
    }

    private suspend fun saveTerm(token: String, customerId: Int) {
        val s = _uiState.value
        val existing = s.terms.find { it.customerId == customerId }
        val body = CustomerCreditTermDTO(
            id = existing?.id,
            customerId = customerId,
            creditAllowed = s.formCreditAllowed,
            creditLimit = if (s.formCreditAllowed) s.formCreditLimit.toDoubleOrNull() ?: 0.0 else 0.0,
            creditDays = if (s.formCreditAllowed) s.formCreditDays.toIntOrNull() ?: 0 else 0
        )
        if (existing?.id != null) ApiClient.service.updateCreditTerm(token, body)
        else ApiClient.service.createCreditTerm(token, body)
    }

    fun delete(customer: CustomerDTO) {
        val id = customer.id ?: return
        if (_uiState.value.sales.any { it.customerId == id }) {
            _uiState.update { it.copy(error = "ရောင်းချမှု ရှိပြီးသား ဖောက်သည်ကို ဖျက်၍မရပါ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = id, error = null) }
            try {
                val res = ApiClient.service.deleteCustomer(ApiClient.bearer(prefs.authToken), id)
                if (res.isSuccessful) load()
                else _uiState.update { it.copy(error = res.body()?.message ?: "ဖောက်သည် မဖျက်နိုင်ပါ (${res.code()})") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "ဖောက်သည် မဖျက်နိုင်ပါ") }
            }
            _uiState.update { it.copy(deletingId = null) }
        }
    }

    data class CustomerManagementUiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val deletingId: Int? = null,
        val error: String? = null,
        val customers: List<CustomerDTO> = emptyList(),
        val terms: List<CustomerCreditTermDTO> = emptyList(),
        val sales: List<SaleDTO> = emptyList(),
        val search: String = "",
        val showEditor: Boolean = false,
        val editingCustomer: CustomerDTO? = null,
        val formName: String = "",
        val formPhone: String = "",
        val formAddress: String = "",
        val formCreditHold: Boolean = false,
        val formCreditHoldReason: String = "",
        val formBlacklisted: Boolean = false,
        val formBlacklistReason: String = "",
        val formCreditAllowed: Boolean = false,
        val formCreditLimit: String = "0",
        val formCreditDays: String = "0"
    )
}
