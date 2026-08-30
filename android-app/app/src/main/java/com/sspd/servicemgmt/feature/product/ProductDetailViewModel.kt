package com.sspd.servicemgmt.feature.product

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.ProductDTO
import com.sspd.servicemgmt.core.network.ProductSerialDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.navigation.optionalId
import com.sspd.servicemgmt.core.realtime.onDataEvent

class ProductDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val productId: Int = savedStateHandle.optionalId("productId")
    private val incomingSerial: String? = savedStateHandle["serialNumber"]

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Product", "Stock", "Sale") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val productDeferred = async { ApiClient.service.getProduct(token, productId) }
                val serialsDeferred = async { ApiClient.service.getProductSerials(token, productId) }

                val productRes = productDeferred.await()
                val serialsRes = serialsDeferred.await()

                val product = if (productRes.isSuccessful) productRes.body()?.data else null
                val error = when {
                    product != null -> null
                    productRes.code() == 401 -> "Login သက်တမ်းကုန်နေပါသည်။ ပြန်လည် Login ဝင်ပါ။"
                    productRes.code() == 403 -> "ကုန်ပစ္စည်းအချက်အလက် ကြည့်ရှုခွင့်မရှိပါ။"
                    productRes.code() == 404 -> "ကုန်ပစ္စည်း မတွေ့ပါ။"
                    else -> productRes.body()?.message ?: "ကုန်ပစ္စည်းအချက်အလက် ရယူ၍မရပါ (HTTP ${productRes.code()})"
                }
                _uiState.update {
                    it.copy(
                        product = product,
                        serials = if (serialsRes.isSuccessful) serialsRes.body()?.data ?: emptyList() else emptyList(),
                        loading = false,
                        scannedSerial = incomingSerial,
                        error = error
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "ချိတ်ဆက်မှု ချို့ယွင်းနေသည်: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun uploadSerialPhoto(serialId: Int, photoBase64: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingSerialId = serialId) }
            try {
                val token   = ApiClient.bearer(prefs.authToken)
                val current = _uiState.value.serials.find { it.id == serialId } ?: return@launch
                val updated = current.copy(photoBase64 = photoBase64)
                val res = ApiClient.service.updateProductSerial(token, serialId, updated)
                if (res.isSuccessful) {
                    _uiState.update { state ->
                        state.copy(
                            serials = state.serials.map {
                                if (it.id == serialId) it.copy(photoBase64 = photoBase64) else it
                            },
                            uploadSuccess   = serialId,
                            uploadingSerialId = null
                        )
                    }
                } else {
                    val msg = when (res.code()) {
                        403  -> "ခွင့်မပြုပါ (Permission မရှိ)"
                        413  -> "ပုံ အရွယ်အစားကြီးလွန်းသည်"
                        else -> "ပုံ upload မအောင်မြင်ပါ (${res.code()})"
                    }
                    _uiState.update { it.copy(uploadError = msg, uploadingSerialId = null) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(uploadError = "ချိတ်ဆက်မှု ချို့ယွင်းနေသည်", uploadingSerialId = null) }
            }
        }
    }

    fun uploadProductPhoto(photoBase64: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingProductPhoto = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.updateProductPhoto(token, productId, mapOf("photoBase64" to photoBase64))
                if (res.isSuccessful) {
                    _uiState.update { state ->
                        state.copy(
                            product = state.product?.copy(photoBase64 = photoBase64),
                            uploadSuccess = -1,
                            uploadingProductPhoto = false
                        )
                    }
                } else {
                    val msg = when (res.code()) {
                        403  -> "ခွင့်မပြုပါ (Permission မရှိ)"
                        413  -> "ပုံ အရွယ်အစားကြီးလွန်းသည်"
                        else -> "ပုံ upload မအောင်မြင်ပါ (${res.code()})"
                    }
                    _uiState.update { it.copy(uploadError = msg, uploadingProductPhoto = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(uploadError = "ချိတ်ဆက်မှု ချို့ယွင်းနေသည်", uploadingProductPhoto = false) }
            }
        }
    }

    fun clearUploadSuccess() = _uiState.update { it.copy(uploadSuccess = null) }
    fun clearUploadError()   = _uiState.update { it.copy(uploadError = null) }
    fun showScanner()        = _uiState.update { it.copy(showScanner = true) }
    fun dismissScanner()     = _uiState.update { it.copy(showScanner = false) }
    fun clearHighlight()     = _uiState.update { it.copy(scannedSerial = null) }
    fun clearScanError()     = _uiState.update { it.copy(scanError = null) }

    fun addSerial(serialNumber: String) {
        val p = _uiState.value.product ?: return
        val sn = serialNumber.trim().uppercase()
        if (sn.isBlank()) {
            _uiState.update { it.copy(uploadError = "Serial number ဖြည့်ပါ") }
            return
        }
        if (_uiState.value.serials.any { it.serialNumber.equals(sn, ignoreCase = true) }) {
            _uiState.update { it.copy(uploadError = "\"$sn\" ထပ်နေသည်") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(addingSerial = true) }
            try {
                val res = ApiClient.service.createProductSerial(
                    ApiClient.bearer(prefs.authToken),
                    ProductSerialDTO(serialNumber = sn, status = "AVAILABLE", productId = p.id)
                )
                val created = res.body()?.data
                if (res.isSuccessful && created != null) {
                    _uiState.update { it.copy(serials = it.serials + created, addingSerial = false, uploadSuccess = created.id) }
                } else {
                    _uiState.update { it.copy(addingSerial = false, uploadError = res.body()?.message ?: "Serial မသိမ်းနိုင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(addingSerial = false, uploadError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun deleteSerial(serial: ProductSerialDTO) {
        if (!serial.status.equals("AVAILABLE", ignoreCase = true)) {
            _uiState.update { it.copy(uploadError = "Available serial ကိုသာ ဖျက်နိုင်ပါသည်") }
            return
        }
        val id = serial.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingSerialId = id) }
            try {
                val res = ApiClient.service.deleteProductSerial(ApiClient.bearer(prefs.authToken), id)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(serials = it.serials.filterNot { s -> s.id == id }, deletingSerialId = null) }
                } else {
                    _uiState.update { it.copy(deletingSerialId = null, uploadError = res.body()?.message ?: "Serial ဖျက်မရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(deletingSerialId = null, uploadError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun onScanResult(serialNumber: String) {
        _uiState.update { it.copy(showScanner = false) }
        val found = _uiState.value.serials.any { it.serialNumber == serialNumber }
        if (found) {
            _uiState.update { it.copy(scannedSerial = serialNumber) }
        } else {
            _uiState.update { it.copy(scanError = "\"$serialNumber\" ဤပစ္စည်းတွင် မတွေ့ပါ") }
        }
    }

    data class ProductDetailUiState(
        val product: ProductDTO? = null,
        val serials: List<ProductSerialDTO> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val showScanner: Boolean = false,
        val scannedSerial: String? = null,
        val scanError: String? = null,
        val uploadingSerialId: Int? = null,
        val uploadingProductPhoto: Boolean = false,
        val addingSerial: Boolean = false,
        val deletingSerialId: Int? = null,
        val uploadSuccess: Int? = null,   // serialId, or -1 for product photo
        val uploadError: String? = null
    )
}
