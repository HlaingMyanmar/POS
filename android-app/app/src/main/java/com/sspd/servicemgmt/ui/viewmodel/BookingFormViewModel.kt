package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.BookingDTO
import com.sspd.servicemgmt.api.BookingDeviceDTO
import com.sspd.servicemgmt.api.CustomerDTO
import com.sspd.servicemgmt.api.ShelfLocationDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookingFormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs     = PreferenceManager(application)
    private val bookingId: Int? = savedStateHandle.get<Int>("bookingId")?.takeIf { it != -1 }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadDependencies() }

    private fun loadDependencies() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token     = ApiClient.bearer(prefs.authToken)
                val custD     = async { ApiClient.service.getCustomers(token) }
                val shelfD    = async { ApiClient.service.getActiveShelfLocations(token) }
                val customers = custD.await().body()?.data ?: emptyList()
                val shelves   = shelfD.await().body()?.data ?: emptyList()

                if (bookingId != null) {
                    val bookingRes = ApiClient.service.getBookingById(token, bookingId)
                    bookingRes.body()?.data?.let { b ->
                        // Build device list from devices[] or fall back to legacy fields
                        val devices = if (!b.devices.isNullOrEmpty()) {
                            b.devices.map { d ->
                                DeviceDraft(
                                    deviceType   = d.deviceType   ?: "",
                                    brand        = d.brand        ?: "",
                                    model        = d.model        ?: "",
                                    serialNumber = d.serialNumber ?: "",
                                    color        = d.color        ?: "",
                                    accessories  = d.accessories  ?: "",
                                    problemDesc  = d.problemDesc  ?: ""
                                )
                            }
                        } else {
                            listOf(DeviceDraft(
                                deviceType   = b.deviceType   ?: "",
                                brand        = b.brand        ?: "",
                                model        = b.model        ?: "",
                                serialNumber = b.serialNumber ?: "",
                                color        = b.color        ?: "",
                                accessories  = b.accessories  ?: "",
                                problemDesc  = b.problemDesc  ?: ""
                            ))
                        }
                        _uiState.update { it.copy(
                            customers        = customers,
                            shelfLocations   = shelves,
                            selectedCustomer = customers.find { c -> c.id == b.customerId },
                            customerQuery    = b.customerName ?: "",
                            devices          = devices,
                            selectedShelf    = shelves.find { s -> s.code == b.shelfLocation },
                            totalAmount      = b.totalAmount?.toString() ?: "",
                            remark           = b.remark ?: "",
                            loading          = false
                        ) }
                        return@launch
                    }
                }
                _uiState.update { it.copy(customers = customers, shelfLocations = shelves, loading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    // ── Customer ──────────────────────────────────────────────────────────────
    fun setCustomerQuery(q: String) = _uiState.update { it.copy(customerQuery = q, selectedCustomer = null) }
    fun selectCustomer(c: CustomerDTO) = _uiState.update { it.copy(selectedCustomer = c, customerQuery = c.name) }

    fun showNewCustomerDialog() = _uiState.update {
        it.copy(showNewCustomerDialog = true, newCustomerName = it.customerQuery, newCustomerPhone = "")
    }
    fun dismissNewCustomerDialog() = _uiState.update { it.copy(showNewCustomerDialog = false) }
    fun setNewCustomerName(v: String)  = _uiState.update { it.copy(newCustomerName = v) }
    fun setNewCustomerPhone(v: String) = _uiState.update { it.copy(newCustomerPhone = v) }

    fun createCustomer() {
        val s = _uiState.value
        if (s.newCustomerName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(creatingCustomer = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.createCustomer(
                    token,
                    CustomerDTO(name = s.newCustomerName.trim(), phone = s.newCustomerPhone.ifBlank { null })
                )
                val created = res.body()?.data
                if (res.isSuccessful && created != null) {
                    _uiState.update { it.copy(
                        customers            = it.customers + created,
                        selectedCustomer     = created,
                        customerQuery        = created.name,
                        showNewCustomerDialog = false,
                        creatingCustomer     = false
                    ) }
                } else {
                    _uiState.update { it.copy(creatingCustomer = false, saveError = res.body()?.message ?: "ဖောက်သည် မသိမ်းနိုင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(creatingCustomer = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ── Devices ───────────────────────────────────────────────────────────────
    fun addDevice() = _uiState.update { it.copy(devices = it.devices + DeviceDraft()) }

    fun removeDevice(index: Int) = _uiState.update {
        if (it.devices.size <= 1) it
        else it.copy(devices = it.devices.toMutableList().also { list -> list.removeAt(index) })
    }

    fun updateDevice(index: Int, device: DeviceDraft) = _uiState.update {
        it.copy(devices = it.devices.toMutableList().also { list -> list[index] = device })
    }

    // ── Other fields ──────────────────────────────────────────────────────────
    fun selectShelf(s: ShelfLocationDTO?) = _uiState.update { it.copy(selectedShelf = s) }
    fun setTotalAmount(v: String)         = _uiState.update { it.copy(totalAmount = v) }
    fun setRemark(v: String)              = _uiState.update { it.copy(remark = v) }

    // ── Save ──────────────────────────────────────────────────────────────────
    fun save(onSuccess: (BookingDTO) -> Unit) {
        val s = _uiState.value
        if (s.selectedCustomer == null) { _uiState.update { it.copy(saveError = "ဖောက်သည် ရွေးပါ") }; return }
        if (s.devices.none { it.brand.isNotBlank() }) { _uiState.update { it.copy(saveError = "ပစ္စည်း Brand အနည်းဆုံး တစ်ခု ရိုက်ထည့်ပါ") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token       = ApiClient.bearer(prefs.authToken)
                val validDevices = s.devices.filter { it.brand.isNotBlank() }
                val first        = validDevices.firstOrNull()
                val dto = BookingDTO(
                    id            = bookingId,
                    customerId    = s.selectedCustomer.id,
                    customerName  = s.selectedCustomer.name,
                    // legacy single-device fields from first device
                    deviceType    = first?.deviceType?.ifBlank { null },
                    brand         = first?.brand?.ifBlank { null },
                    model         = first?.model?.ifBlank { null },
                    serialNumber  = first?.serialNumber?.ifBlank { null },
                    color         = first?.color?.ifBlank { null },
                    accessories   = first?.accessories?.ifBlank { null },
                    problemDesc   = first?.problemDesc?.ifBlank { null },
                    // full devices list
                    devices       = validDevices.map { d ->
                        BookingDeviceDTO(
                            deviceType   = d.deviceType.ifBlank { null },
                            brand        = d.brand.ifBlank { null },
                            model        = d.model.ifBlank { null },
                            serialNumber = d.serialNumber.ifBlank { null },
                            color        = d.color.ifBlank { null },
                            accessories  = d.accessories.ifBlank { null },
                            problemDesc  = d.problemDesc.ifBlank { null }
                        )
                    },
                    shelfLocation = s.selectedShelf?.code,
                    totalAmount   = s.totalAmount.toDoubleOrNull(),
                    remark        = s.remark.ifBlank { null }
                )
                val res = if (bookingId != null)
                    ApiClient.service.updateBooking(token, bookingId, dto)
                else
                    ApiClient.service.createBooking(token, dto)

                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(saving = false) }
                    onSuccess(res.body()!!.data!!)
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

    // ── DeviceDraft ───────────────────────────────────────────────────────────
    data class DeviceDraft(
        val deviceType:   String = "",
        val brand:        String = "",
        val model:        String = "",
        val serialNumber: String = "",
        val color:        String = "",
        val accessories:  String = "",
        val problemDesc:  String = ""
    )

    data class UiState(
        val customers:             List<CustomerDTO>      = emptyList(),
        val shelfLocations:        List<ShelfLocationDTO> = emptyList(),
        val loading:               Boolean                = true,
        val saving:                Boolean                = false,
        val saveError:             String?                = null,
        val customerQuery:         String                 = "",
        val selectedCustomer:      CustomerDTO?           = null,
        val devices:               List<DeviceDraft>      = listOf(DeviceDraft()),
        val selectedShelf:         ShelfLocationDTO?      = null,
        val totalAmount:           String                 = "",
        val remark:                String                 = "",
        val showNewCustomerDialog: Boolean                = false,
        val newCustomerName:       String                 = "",
        val newCustomerPhone:      String                 = "",
        val creatingCustomer:      Boolean                = false
    )
}
