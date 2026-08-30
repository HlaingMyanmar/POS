package com.sspd.servicemgmt.feature.customer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.CustomerCreditTermDTO
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.network.CustomerLocationRequest
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.tracking.LocationClient
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

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
            formLatitude = "",
            formLongitude = "",
            formLocationPair = "",
            capturingLocation = false,
            error = null
        )
    }

    fun openEdit(customer: CustomerDTO) = _uiState.update { s ->
        val term = s.terms.find { it.customerId == customer.id }
        val lat = formatCoord(customer.latitude)
        val lng = formatCoord(customer.longitude)
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
            formLatitude = lat,
            formLongitude = lng,
            formLocationPair = locationPair(lat, lng),
            capturingLocation = false,
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

    fun setFormLocationPair(v: String) {
        val parsed = parseLatLngPaste(v)
        if (parsed != null) {
            applyCoords(parsed.first, parsed.second)
        } else {
            _uiState.update { it.copy(formLocationPair = v) }
        }
    }

    fun setFormLatitude(v: String) {
        val parsed = parseLatLngPaste(v)
        if (parsed != null) {
            applyCoords(parsed.first, parsed.second)
            return
        }
        val cleaned = sanitizeCoord(v)
        _uiState.update { it.copy(formLatitude = cleaned, formLocationPair = locationPair(cleaned, it.formLongitude)) }
    }

    fun setFormLongitude(v: String) {
        val parsed = parseLatLngPaste(v)
        if (parsed != null) {
            applyCoords(parsed.first, parsed.second)
            return
        }
        val cleaned = sanitizeCoord(v)
        _uiState.update { it.copy(formLongitude = cleaned, formLocationPair = locationPair(it.formLatitude, cleaned)) }
    }

    fun clearLocation() = _uiState.update {
        it.copy(formLatitude = "", formLongitude = "", formLocationPair = "")
    }

    fun captureLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(capturingLocation = true, error = null) }
            runCatching { LocationClient(getApplication()).current() }
                .onSuccess { fix -> applyCoords(formatCoord(fix.latitude), formatCoord(fix.longitude), capturing = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            capturingLocation = false,
                            error = error.message ?: "GPS တည်နေရာ မရပါ — ဂဏန်းဖြင့် ကူးထည့်နိုင်သည်"
                        )
                    }
                }
        }
    }

    fun save() {
        val s = _uiState.value
        if (s.formName.isBlank()) { _uiState.update { it.copy(error = "ဖောက်သည်အမည် ဖြည့်ပါ") }; return }
        if (s.formPhone.isBlank()) { _uiState.update { it.copy(error = "ဖုန်းနံပါတ် ဖြည့်ပါ") }; return }
        if (s.formAddress.isBlank()) { _uiState.update { it.copy(error = "လိပ်စာ ဖြည့်ပါ") }; return }
        if (s.formCreditHold && s.formCreditHoldReason.isBlank()) { _uiState.update { it.copy(error = "Credit Hold အကြောင်းရင်း ဖြည့်ပါ") }; return }
        if (s.formBlacklisted && s.formBlacklistReason.isBlank()) { _uiState.update { it.copy(error = "Blacklist အကြောင်းရင်း ဖြည့်ပါ") }; return }

        val coords = resolveFormCoords(s) ?: return

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
                    blacklistReason = if (s.formBlacklisted) s.formBlacklistReason.trim() else "",
                    latitude = coords.first,
                    longitude = coords.second
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

                if (s.editingCustomer?.id != null && locationChanged(s.editingCustomer, coords.first, coords.second)) {
                    val locRes = ApiClient.service.updateCustomerLocation(
                        token,
                        saved.id,
                        CustomerLocationRequest(
                            latitude = coords.first,
                            longitude = coords.second,
                            source = "MANUAL"
                        )
                    )
                    if (!locRes.isSuccessful) {
                        _uiState.update {
                            it.copy(
                                saving = false,
                                error = locRes.body()?.message
                                    ?: "ဖောက်သည် သိမ်းပြီးသော်လည်း တည်နေရာ မပြင်နိုင်ပါ (${locRes.code()})"
                            )
                        }
                        load()
                        return@launch
                    }
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
        val formCreditDays: String = "0",
        val formLatitude: String = "",
        val formLongitude: String = "",
        val formLocationPair: String = "",
        val capturingLocation: Boolean = false
    )

    private fun applyCoords(lat: String, lng: String, capturing: Boolean = false) {
        _uiState.update {
            it.copy(
                formLatitude = lat,
                formLongitude = lng,
                formLocationPair = locationPair(lat, lng),
                capturingLocation = capturing
            )
        }
    }

    private fun resolveFormCoords(s: CustomerManagementUiState): Pair<Double?, Double?>? {
        val pasted = parseLatLngPaste(s.formLocationPair)
        val latText = pasted?.first ?: s.formLatitude
        val lngText = pasted?.second ?: s.formLongitude
        val latBlank = latText.isBlank()
        val lngBlank = lngText.isBlank()
        if (latBlank && lngBlank) return null to null
        if (latBlank != lngBlank) {
            _uiState.update { it.copy(error = "Latitude နှင့် Longitude နှစ်ခုလုံး ဖြည့်ပါ၊ သို့မဟုတ် နှစ်ခုလုံးရှင်းပါ။") }
            return null
        }
        val lat = latText.toDoubleOrNull()
        val lng = lngText.toDoubleOrNull()
        if (lat == null || lng == null) {
            _uiState.update { it.copy(error = "တည်နေရာ ဂဏန်း မမှန်ကန်ပါ — ဥပမာ 16.831799,96.184902") }
            return null
        }
        if (lat !in -90.0..90.0) {
            _uiState.update { it.copy(error = "Latitude သည် -90 မှ 90 အတွင်း ဖြစ်ရမည်။") }
            return null
        }
        if (lng !in -180.0..180.0) {
            _uiState.update { it.copy(error = "Longitude သည် -180 မှ 180 အတွင်း ဖြစ်ရမည်။") }
            return null
        }
        return lat to lng
    }

    private companion object {
        fun formatCoord(value: Double?): String {
            if (value == null) return ""
            return String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }

        fun locationPair(lat: String, lng: String): String =
            if (lat.isBlank() && lng.isBlank()) "" else listOf(lat, lng).filter { it.isNotBlank() }.joinToString(",")

        fun sanitizeCoord(raw: String): String =
            raw.filter { it.isDigit() || it == '.' || it == '-' || it == '+' }

        fun parseLatLngPaste(raw: String): Pair<String, String>? {
            val cleaned = raw.trim().replace(';', ',').replace('|', ',')
            val parts = when {
                ',' in cleaned -> cleaned.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                else -> cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
            }
            if (parts.size != 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lng = parts[1].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            return formatCoord(lat) to formatCoord(lng)
        }

        fun locationChanged(existing: CustomerDTO, lat: Double?, lng: Double?): Boolean =
            !sameCoord(existing.latitude, lat) || !sameCoord(existing.longitude, lng)

        fun sameCoord(a: Double?, b: Double?): Boolean {
            if (a == null && b == null) return true
            if (a == null || b == null) return false
            return abs(a - b) < 1e-7
        }
    }
}
