package com.sspd.servicemgmt.feature.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.realtime.onDataEvent
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookingFormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs     = PreferenceManager(application)
    private val bookingId: Int? = savedStateHandle.get<Int>("bookingId")?.takeIf { it != -1 }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCustomers()
        loadBooking()
        onDataEvent("Customer") { loadCustomers() }
    }

    fun loadCustomers() {
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val customers = ApiClient.service.getCustomers(token).body()?.data ?: emptyList()
                _uiState.update { s ->
                    val selected = s.selectedCustomer?.id?.let { id -> customers.find { it.id == id } } ?: s.selectedCustomer
                    s.copy(customers = customers, selectedCustomer = selected)
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadBooking() {
        if (bookingId == null) {
            _uiState.update { it.copy(loading = false, bookingDate = today()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val booking = ApiClient.service.getBookingById(token, bookingId).body()?.data
                val customers = _uiState.value.customers.ifEmpty {
                    ApiClient.service.getCustomers(token).body()?.data ?: emptyList()
                }
                if (booking != null) {
                    _uiState.update { it.copy(
                        customers        = customers,
                        selectedCustomer = customers.find { c -> c.id == booking.customerId },
                        customerQuery    = booking.customerName ?: "",
                        bookingDate      = booking.bookingDate?.take(10) ?: today(),
                        appointmentDate  = booking.appointmentDate?.take(16)?.replace("T", " ") ?: "",
                        complaintNote    = booking.complaintNote ?: booking.problemDesc ?: "",
                        remark           = booking.remark ?: "",
                        loading          = false
                    ) }
                } else {
                    _uiState.update { it.copy(loading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun setCustomerQuery(q: String) = _uiState.update { it.copy(customerQuery = q, selectedCustomer = null) }
    fun selectCustomer(c: CustomerDTO) = _uiState.update { it.copy(selectedCustomer = c, customerQuery = c.name) }

    fun showNewCustomerDialog() = _uiState.update {
        it.copy(showNewCustomerDialog = true, newCustomerName = it.customerQuery, newCustomerPhone = "", newCustomerAddress = "", newCustomerError = null)
    }
    fun dismissNewCustomerDialog() = _uiState.update { if (it.creatingCustomer) it else it.copy(showNewCustomerDialog = false, newCustomerError = null) }
    fun setNewCustomerName(v: String)  = _uiState.update { it.copy(newCustomerName = v, newCustomerError = null) }
    fun setNewCustomerPhone(v: String) = _uiState.update { it.copy(newCustomerPhone = v) }
    fun setNewCustomerAddress(v: String) = _uiState.update { it.copy(newCustomerAddress = v) }

    fun createCustomer() {
        val s = _uiState.value
        if (s.newCustomerName.isBlank()) {
            _uiState.update { it.copy(newCustomerError = "အမည် လိုအပ်ပါသည်") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(creatingCustomer = true, newCustomerError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.createCustomer(
                    token,
                    CustomerDTO(
                        name = s.newCustomerName.trim(),
                        phone = s.newCustomerPhone.trim().ifBlank { null },
                        address = s.newCustomerAddress.trim().ifBlank { null }
                    )
                )
                val created = res.body()?.data
                if (res.isSuccessful && created != null) {
                    _uiState.update { it.copy(
                        customers             = if (it.customers.any { c -> c.id == created.id }) it.customers else it.customers + created,
                        selectedCustomer      = created,
                        customerQuery         = created.name,
                        showNewCustomerDialog = false,
                        newCustomerName       = "",
                        newCustomerPhone      = "",
                        newCustomerAddress    = "",
                        creatingCustomer      = false,
                        newCustomerError      = null
                    ) }
                } else {
                    _uiState.update { it.copy(creatingCustomer = false, saveError = res.body()?.message ?: "ဖောက်သည် မသိမ်းနိုင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(creatingCustomer = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun setBookingDate(v: String)     = _uiState.update { it.copy(bookingDate = v) }
    fun setAppointmentDate(v: String) = _uiState.update { it.copy(appointmentDate = v) }
    fun setComplaintNote(v: String)   = _uiState.update { it.copy(complaintNote = v) }
    fun setRemark(v: String)          = _uiState.update { it.copy(remark = v) }

    fun save(onSuccess: (BookingDTO) -> Unit) {
        val s = _uiState.value
        if (s.selectedCustomer == null) { _uiState.update { it.copy(saveError = "ဖောက်သည် ရွေးပါ") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val dto = BookingDTO(
                    id              = bookingId,
                    customerId      = s.selectedCustomer.id,
                    customerName    = s.selectedCustomer.name,
                    bookingDate     = s.bookingDate.takeIf { it.isNotBlank() },
                    appointmentDate = s.appointmentDate.takeIf { it.isNotBlank() }?.let {
                        val raw = it.trim().replace(" ", "T")
                        if (raw.length == 16) "$raw:00" else raw
                    },
                    complaintNote   = s.complaintNote.trim().ifBlank { null },
                    remark          = s.remark.trim().ifBlank { null }
                )
                val res = if (bookingId != null)
                    ApiClient.service.updateBooking(token, bookingId, dto)
                else
                    ApiClient.service.createBooking(token, dto)

                val saved = res.body()?.data
                if (res.isSuccessful && saved != null) {
                    _uiState.update { it.copy(saving = false) }
                    onSuccess(saved)
                } else {
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(saveError = null) }

    val isEdit get() = bookingId != null

    data class UiState(
        val customers:             List<CustomerDTO> = emptyList(),
        val loading:               Boolean           = true,
        val saving:                Boolean           = false,
        val saveError:             String?           = null,
        val customerQuery:         String            = "",
        val selectedCustomer:      CustomerDTO?      = null,
        val bookingDate:           String            = today(),
        val appointmentDate:       String            = "",
        val complaintNote:         String            = "",
        val remark:                String            = "",
        val showNewCustomerDialog: Boolean           = false,
        val newCustomerName:       String            = "",
        val newCustomerPhone:      String            = "",
        val newCustomerAddress:    String            = "",
        val creatingCustomer:      Boolean           = false,
        val newCustomerError:      String?           = null
    )
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
