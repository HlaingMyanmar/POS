package com.sspd.servicemgmt.feature.customer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomerHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)
    private val canViewSales = prefs.hasPermission(SALE_READ_PERMISSION)
    private val _state = MutableStateFlow(State(canViewSales = canViewSales))
    val state: StateFlow<State> = _state.asStateFlow()

    init { loadCustomers() }

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
                error = null,
                saleError = null
            )
        }
        viewModelScope.launch {
            val auth = ApiClient.bearer(prefs.authToken)
            val query = customer.phone?.takeIf { it.isNotBlank() } ?: customer.name

            try {
                val response = ApiClient.service.getServiceJobs(
                    auth = auth,
                    size = 100,
                    search = query,
                    dateFrom = "",
                    dateTo = ""
                )
                if (response.isSuccessful) {
                    val jobs = response.body()?.data?.content.orEmpty()
                        .filter { it.customerId == customerId }
                        .sortedByDescending { it.receivedDate ?: it.modifiedAt.orEmpty() }
                    _state.update { it.copy(jobs = jobs) }
                } else {
                    _state.update { it.copy(error = "Service History ရယူ၍မရပါ (${response.code()})") }
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Service History ရယူ၍မရပါ") }
            }

            // Do not call the sales endpoint unless the authenticated session
            // explicitly contains the permission assigned by an administrator.
            if (canViewSales) {
                try {
                    val response = ApiClient.service.getSales(
                        auth = auth,
                        size = 100,
                        search = query,
                        dateFrom = "",
                        dateTo = ""
                    )
                    if (response.isSuccessful) {
                        val sales = response.body()?.data?.content.orEmpty()
                            .filter { it.customerId == customerId && it.voided != true }
                            .sortedByDescending { it.saleDate.orEmpty() }
                        _state.update { it.copy(sales = sales) }
                    } else {
                        _state.update { it.copy(saleError = "Sale History ရယူ၍မရပါ (${response.code()})") }
                    }
                } catch (error: Exception) {
                    _state.update { it.copy(saleError = error.message ?: "Sale History ရယူ၍မရပါ") }
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
        val canViewSales: Boolean = false,
        val loading: Boolean = true,
        val historyLoading: Boolean = false,
        val error: String? = null,
        val saleError: String? = null
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

    private companion object {
        const val SALE_READ_PERMISSION = "CAN_ACCESS_SALE_READ"
    }
}
