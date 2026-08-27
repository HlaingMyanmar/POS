package com.sspd.servicemgmt.feature.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingAttachmentDTO
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.BookingDetailItemDTO
import com.sspd.servicemgmt.core.network.BookingDeviceDTO
import com.sspd.servicemgmt.core.network.BookingDeviceInfoDTO
import com.sspd.servicemgmt.core.network.CustomerDTO
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.ServiceItemDTO
import com.sspd.servicemgmt.core.network.ShelfLocationDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
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
                val pmD       = async { ApiClient.service.getActivePaymentMethods(token) }
                val svcD      = async { ApiClient.service.getAllServiceItems(token) }
                val customers = custD.await().body()?.data ?: emptyList()
                val shelves   = shelfD.await().body()?.data ?: emptyList()
                val methods   = pmD.await().body()?.data ?: emptyList()
                val services  = (svcD.await().body()?.data ?: emptyList()).filter { it.isActive }

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
                                    problemDesc  = d.problemDesc  ?: "",
                                    deviceConditions = d.deviceConditions ?: ""
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
                                problemDesc  = b.problemDesc  ?: "",
                                deviceConditions = ""
                            ))
                        }
                        _uiState.update { it.copy(
                            customers        = customers,
                            shelfLocations   = shelves,
                            paymentMethods   = methods,
                            serviceItems     = services,
                            selectedCustomer = customers.find { c -> c.id == b.customerId },
                            customerQuery    = b.customerName ?: "",
                            devices          = devices,
                            selectedShelf    = shelves.find { s -> s.code == b.shelfLocation },
                            selectedPayMethod = methods.find { m -> m.id == b.paymentMethodId },
                            totalAmount      = b.totalAmount?.toString() ?: "",
                            depositAmount    = b.depositAmount?.takeIf { it > 0 }?.toString() ?: "",
                            appointmentDate  = b.appointmentDate?.take(16)?.replace("T", " ") ?: "",
                            remark           = b.remark ?: "",
                            checklist        = (b.deviceInfos?.takeIf { it.isNotEmpty() }?.map {
                                ChecklistDraft(it.name ?: "", it.status ?: "Good", it.notice ?: "")
                            } ?: defaultChecklist()),
                            serviceLines     = (b.details ?: emptyList()).map {
                                ServiceDraft(it.serviceId, it.serviceName ?: "", (it.qty ?: 1).toString(), (it.price ?: 0.0).toString())
                            },
                            existingPhotos   = b.attachments ?: emptyList(),
                            loading          = false
                        ) }
                        return@launch
                    }
                }
                _uiState.update { it.copy(
                    customers = customers, shelfLocations = shelves,
                    paymentMethods = methods, serviceItems = services,
                    checklist = defaultChecklist(),
                    loading = false
                ) }
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
    fun selectPayMethod(m: PaymentMethodDTO?) = _uiState.update { it.copy(selectedPayMethod = m) }
    fun setTotalAmount(v: String)         = _uiState.update { it.copy(totalAmount = v) }
    fun setDepositAmount(v: String)       = _uiState.update { it.copy(depositAmount = v) }
    fun setAppointmentDate(v: String)     = _uiState.update { it.copy(appointmentDate = v) }
    fun setRemark(v: String)              = _uiState.update { it.copy(remark = v) }

    fun updateChecklist(index: Int, item: ChecklistDraft) = _uiState.update {
        it.copy(checklist = it.checklist.toMutableList().also { list -> list[index] = item })
    }

    fun addServiceLine(item: ServiceItemDTO) = _uiState.update {
        it.copy(serviceLines = it.serviceLines + ServiceDraft(item.id, item.item, "1", item.price.toString()))
    }
    fun updateServiceLine(index: Int, line: ServiceDraft) = _uiState.update {
        it.copy(serviceLines = it.serviceLines.toMutableList().also { list -> list[index] = line })
    }
    fun removeServiceLine(index: Int) = _uiState.update {
        it.copy(serviceLines = it.serviceLines.toMutableList().also { list -> list.removeAt(index) })
    }

    fun addPendingPhoto(dataUrl: String) = _uiState.update { it.copy(pendingPhotos = it.pendingPhotos + dataUrl) }
    fun removePendingPhoto(index: Int) = _uiState.update {
        it.copy(pendingPhotos = it.pendingPhotos.toMutableList().also { list -> list.removeAt(index) })
    }

    // ── Save ──────────────────────────────────────────────────────────────────
    fun save(onSuccess: (BookingDTO) -> Unit) {
        val s = _uiState.value
        if (s.selectedCustomer == null) { _uiState.update { it.copy(saveError = "ဖောက်သည် ရွေးပါ") }; return }
        if (s.devices.none { it.brand.isNotBlank() }) { _uiState.update { it.copy(saveError = "ပစ္စည်း Brand အနည်းဆုံး တစ်ခု ရိုက်ထည့်ပါ") }; return }
        val deposit = s.depositAmount.toDoubleOrNull() ?: 0.0
        if (deposit > 0 && s.selectedPayMethod == null) {
            _uiState.update { it.copy(saveError = "လက်ခံငွေအတွက် ငွေပေးချေနည်း ရွေးပါ") }; return
        }

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
                            problemDesc  = d.problemDesc.ifBlank { null },
                            deviceConditions = d.deviceConditions.ifBlank { null }
                        )
                    },
                    details       = s.serviceLines.filter { it.serviceId != null }.map { d ->
                        BookingDetailItemDTO(
                            serviceId = d.serviceId,
                            serviceName = d.serviceName,
                            qty = d.qty.toIntOrNull() ?: 1,
                            price = d.price.toDoubleOrNull() ?: 0.0
                        )
                    }.ifEmpty { null },
                    deviceInfos   = s.checklist.map { c ->
                        BookingDeviceInfoDTO(name = c.name, status = c.status, notice = c.notice.ifBlank { null })
                    },
                    appointmentDate = s.appointmentDate.takeIf { it.isNotBlank() }?.let {
                        val raw = it.trim().replace(" ", "T")
                        if (raw.length == 16) "$raw:00" else raw
                    },
                    depositAmount = deposit.takeIf { it > 0 },
                    paymentMethodId = s.selectedPayMethod?.id,
                    shelfLocation = s.selectedShelf?.code,
                    totalAmount   = s.totalAmount.toDoubleOrNull(),
                    remark        = s.remark.ifBlank { null }
                )
                val res = if (bookingId != null)
                    ApiClient.service.updateBooking(token, bookingId, dto)
                else
                    ApiClient.service.createBooking(token, dto)

                if (res.isSuccessful && res.body()?.data != null) {
                    val saved = res.body()!!.data!!
                    saved.id?.let { id ->
                        s.pendingPhotos.forEachIndexed { index, dataUrl ->
                            runCatching {
                                ApiClient.service.addBookingAttachment(
                                    token, id,
                                    BookingAttachmentDTO(
                                        attachmentType = "INTAKE_PHOTO",
                                        fileName = "intake-${index + 1}.jpg",
                                        contentType = "image/jpeg",
                                        dataUrl = dataUrl
                                    )
                                )
                            }
                        }
                    }
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

    // ── DeviceDraft ───────────────────────────────────────────────────────────
    data class DeviceDraft(
        val deviceType:   String = "",
        val brand:        String = "",
        val model:        String = "",
        val serialNumber: String = "",
        val color:        String = "",
        val accessories:  String = "",
        val problemDesc:  String = "",
        val deviceConditions: String = ""
    )

    data class ChecklistDraft(val name: String, val status: String = "Good", val notice: String = "")
    data class ServiceDraft(
        val serviceId: Int? = null,
        val serviceName: String = "",
        val qty: String = "1",
        val price: String = "0"
    )

    data class UiState(
        val customers:             List<CustomerDTO>      = emptyList(),
        val shelfLocations:        List<ShelfLocationDTO> = emptyList(),
        val paymentMethods:        List<PaymentMethodDTO> = emptyList(),
        val serviceItems:          List<ServiceItemDTO>   = emptyList(),
        val loading:               Boolean                = true,
        val saving:                Boolean                = false,
        val saveError:             String?                = null,
        val customerQuery:         String                 = "",
        val selectedCustomer:      CustomerDTO?           = null,
        val devices:               List<DeviceDraft>      = listOf(DeviceDraft()),
        val selectedShelf:         ShelfLocationDTO?      = null,
        val selectedPayMethod:     PaymentMethodDTO?      = null,
        val totalAmount:           String                 = "",
        val depositAmount:         String                 = "",
        val appointmentDate:       String                 = "",
        val remark:                String                 = "",
        val checklist:             List<ChecklistDraft>   = emptyList(),
        val serviceLines:          List<ServiceDraft>     = emptyList(),
        val pendingPhotos:         List<String>           = emptyList(),
        val existingPhotos:        List<BookingAttachmentDTO> = emptyList(),
        val showNewCustomerDialog: Boolean                = false,
        val newCustomerName:       String                 = "",
        val newCustomerPhone:      String                 = "",
        val creatingCustomer:      Boolean                = false
    )
}

private fun defaultChecklist() = listOf(
    "Screen", "Body", "Camera", "Battery", "Buttons", "Ports", "Speaker", "Microphone"
).map { BookingFormViewModel.ChecklistDraft(it) }
