package com.sspd.servicemgmt.feature.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.AccountTransferRequest
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadMethods() }

    fun loadMethods() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getActivePaymentMethods(token)
                val list  = res.body()?.data ?: emptyList()
                _uiState.update { it.copy(methods = list, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "ချိတ်ဆက်မှု ချို့ယွင်း: ${e.message}") }
            }
        }
    }

    fun selectFrom(m: PaymentMethodDTO) = _uiState.update { it.copy(fromMethod = m, sameMethodError = false) }
    fun selectTo(m: PaymentMethodDTO)   = _uiState.update { it.copy(toMethod = m,   sameMethodError = false) }
    fun setAmount(v: String)            = _uiState.update { it.copy(amountStr = v) }
    fun setTxnNo(v: String)             = _uiState.update { it.copy(transactionNo = v) }
    fun setNote(v: String)              = _uiState.update { it.copy(note = v) }
    fun clearSuccess()                  = _uiState.update { it.copy(success = false) }
    fun clearError()                    = _uiState.update { it.copy(error = null, saveError = null) }

    fun transfer() {
        val s = _uiState.value
        val from   = s.fromMethod ?: run { _uiState.update { it.copy(saveError = "မှ (From) method ရွေးပါ") }; return }
        val to     = s.toMethod   ?: run { _uiState.update { it.copy(saveError = "သို့ (To) method ရွေးပါ") }; return }
        if (from.id == to.id) { _uiState.update { it.copy(sameMethodError = true, saveError = "From နှင့် To မတူသင့်ပါ") }; return }
        val amount = s.amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) { _uiState.update { it.copy(saveError = "ပမာဏ မှန်ကန်သော ဂဏာန်း ထည့်ပါ") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.transferPaymentMethod(
                    auth = token,
                    body = AccountTransferRequest(
                        fromPaymentMethodId = from.id,
                        toPaymentMethodId   = to.id,
                        amount              = amount,
                        transactionNo       = s.transactionNo.trim().ifEmpty { null },
                        description         = s.note.trim().ifEmpty { null }
                    )
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    _uiState.update {
                        it.copy(
                            saving        = false,
                            success       = true,
                            amountStr     = "",
                            transactionNo = "",
                            note          = "",
                            fromMethod    = null,
                            toMethod      = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    data class UiState(
        val methods:         List<PaymentMethodDTO> = emptyList(),
        val fromMethod:      PaymentMethodDTO?      = null,
        val toMethod:        PaymentMethodDTO?      = null,
        val amountStr:       String                 = "",
        val transactionNo:   String                 = "",
        val note:            String                 = "",
        val loading:         Boolean                = true,
        val saving:          Boolean                = false,
        val success:         Boolean                = false,
        val sameMethodError: Boolean                = false,
        val error:           String?                = null,
        val saveError:       String?                = null
    )
}
