package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import com.sspd.servicemgmt.websocket.DataEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(ProductListUiState())
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            DataEventBus.events
                .filter { e ->
                    e.entity.contains("Product", ignoreCase = true) ||
                    e.entity.contains("Stock",   ignoreCase = true) ||
                    e.entity.contains("Sale",    ignoreCase = true)
                }
                .debounce(600)
                .collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val res = ApiClient.service.getProducts(ApiClient.bearer(prefs.authToken))
                if (res.isSuccessful) _uiState.update { it.copy(items = res.body()?.data ?: emptyList()) }
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }
    fun setFilter(filter: ProductFilter) = _uiState.update { it.copy(filter = filter) }

    fun showScanner()    = _uiState.update { it.copy(showScanner = true) }
    fun dismissScanner() = _uiState.update { it.copy(showScanner = false) }
    fun clearScanError() = _uiState.update { it.copy(scanError = null) }

    fun onScanResult(serial: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanLoading = true, scanError = null, showScanner = false) }
            try {
                val res = ApiClient.service.findProductBySerial(ApiClient.bearer(prefs.authToken), serial)
                val found = res.body()?.data
                if (res.isSuccessful && found != null && found.productId != null) {
                    // Navigate directly to ProductDetail with serial highlighted
                    _uiState.update { it.copy(
                        navigateToDetail = found.productId to serial,
                        scanLoading = false
                    ) }
                } else {
                    _uiState.update { it.copy(
                        scanLoading = false,
                        scanError = "Serial '$serial' နှင့် ကုန်ပစ္စည်း မတွေ့ပါ"
                    ) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(scanLoading = false, scanError = "ချိတ်ဆက်မှု ချို့ယွင်းနေသည်") }
            }
        }
    }

    fun onNavigated() = _uiState.update { it.copy(navigateToDetail = null) }

    data class ProductListUiState(
        val items:            List<ProductDTO>   = emptyList(),
        val loading:          Boolean            = true,
        val search:           String             = "",
        val filter:           ProductFilter      = ProductFilter.ALL,
        val showScanner:      Boolean            = false,
        val scanLoading:      Boolean            = false,
        val scanError:        String?            = null,
        val navigateToDetail: Pair<Int, String>? = null  // productId to serialNumber
    )

    enum class ProductFilter { ALL, LOW_STOCK, SERIAL, NO_COST, NO_SELLING_PRICE }
}
