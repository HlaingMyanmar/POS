package com.sspd.servicemgmt.feature.customer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.realtime.onDataEvent
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomerHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        loadCustomers()
        onDataEvent("Customer") { loadCustomers() }
    }

    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    fun loadCustomers() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val response = ApiClient.service.getCustomers(ApiClient.bearer(prefs.authToken))
                if (response.isSuccessful) {
                    _state.update { it.copy(customers = response.body()?.data.orEmpty(), loading = false) }
                } else {
                    _state.update { it.copy(loading = false, error = "Customer များရယူ၍မရပါ (${response.code()})") }
                }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "Server ချိတ်ဆက်၍မရပါ") }
            }
        }
    }

    fun selectCustomer(customer: CustomerDTO) {
        val customerId = customer.id ?: return
        _state.update {
            it.copy(
                selected = customer,
                jobs = emptyList(),
                sales = emptyList(),
                historyLoading = true,
                canViewSales = true,
                error = null,
                saleError = null,
            )
        }
        viewModelScope.launch {
            val auth = ApiClient.bearer(prefs.authToken)
            try {
                val response = ApiClient.service.getServiceJobsByCustomer(auth, customerId)
                if (response.isSuccessful) {
                    val jobs = response.body()?.data.orEmpty()
                        .sortedByDescending { it.receivedDate ?: it.modifiedAt.orEmpty() }
                    _state.update { it.copy(jobs = jobs) }
                } else {
                    _state.update { it.copy(error = "Service History ရယူ၍မရပါ (${response.code()})") }
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Service History ရယူ၍မရပါ") }
            }

            try {
                val response = ApiClient.service.getSalesByCustomer(auth, customerId)
                when {
                    response.isSuccessful -> {
                        val sales = response.body()?.data.orEmpty()
                            .filter { it.voided != true }
                            .sortedByDescending { it.saleDate.orEmpty() }
                        _state.update { it.copy(sales = sales, canViewSales = true, saleError = null) }
                    }
                    response.code() == 403 -> {
                        _state.update {
                            it.copy(
                                canViewSales = false,
                                saleError = null,
                                sales = emptyList(),
                            )
                        }
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                canViewSales = true,
                                saleError = "Sale History ရယူ၍မရပါ (${response.code()})",
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        canViewSales = true,
                        saleError = error.message ?: "Sale History ရယူ၍မရပါ",
                    )
                }
            }
            _state.update { it.copy(historyLoading = false) }
        }
    }

    fun clearSelection() = _state.update {
        it.copy(selected = null, jobs = emptyList(), sales = emptyList(), error = null, saleError = null)
    }

    data class State(
        val customers: List<CustomerDTO> = emptyList(),
        val search: String = "",
        val selected: CustomerDTO? = null,
        val jobs: List<ServiceJobDTO> = emptyList(),
        val sales: List<SaleDTO> = emptyList(),
        val canViewSales: Boolean = true,
        val loading: Boolean = true,
        val historyLoading: Boolean = false,
        val error: String? = null,
        val saleError: String? = null,
    ) {
        val filteredCustomers: List<CustomerDTO>
            get() {
                val query = search.trim()
                if (query.isEmpty()) return customers
                return customers.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.phone.orEmpty().contains(query) ||
                        it.address.orEmpty().contains(query, ignoreCase = true)
                }
            }
    }
}
