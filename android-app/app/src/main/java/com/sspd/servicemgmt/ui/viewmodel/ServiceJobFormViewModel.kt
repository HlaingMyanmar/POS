package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.CustomerDTO
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.api.ProductSerialDTO
import com.sspd.servicemgmt.api.ServiceItemDTO
import com.sspd.servicemgmt.api.ServiceJobDTO
import com.sspd.servicemgmt.api.ServiceJobLineDTO
import com.sspd.servicemgmt.api.ServiceJobPartDTO
import com.sspd.servicemgmt.api.ShelfLocationDTO
import com.sspd.servicemgmt.api.StaffDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServiceJobFormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val jobId: Int? = savedStateHandle.get<Int>("jobId")?.takeIf { it != -1 }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isEdit get() = jobId != null

    init { loadDependencies() }

    private fun loadDependencies() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token   = ApiClient.bearer(prefs.authToken)
                val custD   = async { ApiClient.service.getCustomers(token) }
                val staffD  = async { ApiClient.service.getActiveStaff(token) }
                val itemsD  = async { ApiClient.service.getActiveServiceItems(token) }
                val pmD     = async { ApiClient.service.getActivePaymentMethods(token) }
                val prodD   = async { ApiClient.service.getProducts(token) }
                val shelfD  = async { ApiClient.service.getActiveShelfLocations(token) }

                val customers      = custD.await().body()?.data  ?: emptyList()
                val staffList      = staffD.await().body()?.data ?: emptyList()
                val serviceItems   = itemsD.await().body()?.data ?: emptyList()
                val paymentMethods = pmD.await().body()?.data    ?: emptyList()
                val productList    = prodD.await().body()?.data  ?: emptyList()
                val shelfLocations = shelfD.await().body()?.data ?: emptyList()

                if (jobId != null) {
                    val jobRes = ApiClient.service.getServiceJobById(token, jobId)
                    jobRes.body()?.data?.let { j ->
                        _uiState.update { it.copy(
                            customers        = customers,
                            staffList        = staffList,
                            serviceItems     = serviceItems,
                            paymentMethods   = paymentMethods,
                            productList      = productList,
                            shelfLocations   = shelfLocations,
                            selectedCustomer = customers.find { c -> c.id == j.customerId },
                            customerQuery    = j.customerName ?: "",
                            selectedStaff    = staffList.find { s -> s.id == j.assignedStaffId },
                            selectedShelfLocation = shelfLocations.find { loc -> loc.id == j.shelfLocationId },
                            itemName         = j.itemName ?: "",
                            itemCondition    = j.itemCondition ?: "",
                            deviceConditions = j.deviceConditions ?: "",
                            serialNo         = j.serialNo ?: "",
                            color            = j.color ?: "",
                            accessories      = j.accessories ?: "",
                            problemDesc      = j.problemDesc ?: "",
                            diagnosisNotes   = j.diagnosisNotes ?: "",
                            estimatedCost    = j.estimatedCost?.let { v -> String.format("%.0f", v) } ?: "",
                            estimatedCompletion = j.estimatedCompletion?.take(16) ?: "",
                            lines            = j.lines?.map { l ->
                                LineDraft(
                                    serviceItem    = serviceItems.find { si -> si.id == l.serviceItemId },
                                    qty            = l.qty?.toString() ?: "1",
                                    price          = l.price?.let { p -> String.format("%.0f", p) } ?: "",
                                    warrantyMonths = l.warrantyMonths?.toString() ?: "0"
                                )
                            } ?: emptyList(),
                            parts            = j.productParts?.map { p ->
                                PartDraft(
                                    product       = productList.find { pr -> pr.id == p.productId }
                                        ?: ProductDTO(id = p.productId ?: 0, productCode = p.productCode ?: "", name = p.productName ?: "", stockQty = 0, productType = "", sellingPrice = p.unitPrice?.toDouble() ?: 0.0),
                                    qty           = p.qty?.toString() ?: "1",
                                    unitPrice     = p.unitPrice?.let { v -> String.format("%.0f", v) } ?: "",
                                    discount      = p.discountAmount?.let { v -> String.format("%.0f", v) } ?: "0",
                                    serialNumbers = p.serialNumbers ?: emptyList()
                                )
                            } ?: emptyList(),
                            remark           = j.remark ?: "",
                            loading          = false
                        ) }
                        return@launch
                    }
                }
                _uiState.update { it.copy(
                    customers = customers, staffList = staffList,
                    serviceItems = serviceItems, paymentMethods = paymentMethods,
                    productList = productList, shelfLocations = shelfLocations, loading = false
                ) }
            } catch (_: Exception) { _uiState.update { it.copy(loading = false) } }
        }
    }

    // ── Field setters ─────────────────────────────────────────────────────────
    fun setCustomerQuery(q: String)        = _uiState.update { it.copy(customerQuery = q, selectedCustomer = null) }
    fun selectCustomer(c: CustomerDTO)     = _uiState.update { it.copy(selectedCustomer = c, customerQuery = c.name) }
    fun showNewCustomerDialog()            = _uiState.update {
        it.copy(showNewCustomerDialog = true, newCustomerName = it.customerQuery)
    }
    fun dismissNewCustomerDialog()         = _uiState.update { it.copy(showNewCustomerDialog = false, newCustomerError = null) }
    fun setNewCustomerName(v: String)      = _uiState.update { it.copy(newCustomerName = v, newCustomerError = null) }
    fun setNewCustomerPhone(v: String)     = _uiState.update { it.copy(newCustomerPhone = v) }
    fun setNewCustomerAddress(v: String)   = _uiState.update { it.copy(newCustomerAddress = v) }
    fun selectStaff(s: StaffDTO?)          = _uiState.update { it.copy(selectedStaff = s) }
    fun selectShelfLocation(loc: ShelfLocationDTO?) = _uiState.update { it.copy(selectedShelfLocation = loc) }
    fun setItemName(v: String)             = _uiState.update { it.copy(itemName = v) }
    fun setItemCondition(v: String)        = _uiState.update { it.copy(itemCondition = v) }
    fun setDeviceConditions(v: String)     = _uiState.update { it.copy(deviceConditions = v) }
    fun setSerialNo(v: String)             = _uiState.update { it.copy(serialNo = v) }
    fun setColor(v: String)               = _uiState.update { it.copy(color = v) }
    fun setAccessories(v: String)         = _uiState.update { it.copy(accessories = v) }
    fun setProblemDesc(v: String)         = _uiState.update { it.copy(problemDesc = v) }
    fun setDiagnosisNotes(v: String)      = _uiState.update { it.copy(diagnosisNotes = v) }
    fun setEstimatedCost(v: String)       = _uiState.update { it.copy(estimatedCost = v) }
    fun setEstimatedCompletion(v: String) = _uiState.update { it.copy(estimatedCompletion = v) }
    fun setRemark(v: String)              = _uiState.update { it.copy(remark = v) }

    // ── Service Lines ─────────────────────────────────────────────────────────
    fun addLine()               = _uiState.update { it.copy(lines = it.lines + LineDraft()) }
    fun removeLine(index: Int)  = _uiState.update { it.copy(lines = it.lines.toMutableList().also { l -> l.removeAt(index) }) }
    fun updateLine(index: Int, line: LineDraft) = _uiState.update {
        it.copy(lines = it.lines.toMutableList().also { l -> l[index] = line })
    }

    // ── Product Parts ─────────────────────────────────────────────────────────
    fun addPart()               = _uiState.update { it.copy(parts = it.parts + PartDraft()) }
    fun removePart(index: Int)  = _uiState.update { it.copy(parts = it.parts.toMutableList().also { l -> l.removeAt(index) }) }
    fun updatePart(index: Int, part: PartDraft) = _uiState.update {
        it.copy(parts = it.parts.toMutableList().also { l -> l[index] = part })
    }

    fun selectPartProduct(partIdx: Int, product: ProductDTO) {
        if (partIdx !in _uiState.value.parts.indices) return
        if (product.hasSerial == true) {
            _uiState.update { s ->
                val parts = s.parts.toMutableList()
                parts[partIdx] = parts[partIdx].copy(
                    product = product,
                    unitPrice = String.format("%.0f", product.sellingPrice.toDouble()),
                    serialNumbers = emptyList(),
                    qty = "1"
                )
                s.copy(
                    parts = parts,
                    serialSelectPartIdx = partIdx,
                    serialSelectProduct = product,
                    serialSelectOptions = emptyList(),
                    serialSelectLoading = true,
                    serialSelectError = null
                )
            }
            loadSerialOptions(partIdx, product)
        } else {
            updatePart(
                partIdx,
                _uiState.value.parts[partIdx].copy(
                    product = product,
                    unitPrice = String.format("%.0f", product.sellingPrice.toDouble()),
                    serialNumbers = emptyList(),
                    qty = "1"
                )
            )
        }
    }

    private fun loadSerialOptions(partIdx: Int, product: ProductDTO) {
        viewModelScope.launch {
            try {
                val res = ApiClient.service.getProductSerials(ApiClient.bearer(prefs.authToken), product.id)
                val selectedElsewhere = _uiState.value.parts
                    .filterIndexed { idx, _ -> idx != partIdx }
                    .flatMap { it.serialNumbers }
                    .toSet()
                val options = (if (res.isSuccessful) res.body()?.data ?: emptyList() else emptyList())
                    .filter { serial ->
                        val status = serial.status?.uppercase()
                        serial.serialNumber.isNotBlank() &&
                            status != "SOLD" &&
                            status != "USED" &&
                            status != "DAMAGED" &&
                            status != "LOST" &&
                            !selectedElsewhere.contains(serial.serialNumber)
                    }
                _uiState.update {
                    it.copy(
                        serialSelectOptions = options,
                        serialSelectLoading = false,
                        serialSelectError = if (options.isEmpty()) "အသုံးပြုနိုင်သော Serial number မရှိပါ" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        serialSelectLoading = false,
                        serialSelectError = e.message ?: "Serial number များ မဖတ်နိုင်ပါ"
                    )
                }
            }
        }
    }

    fun selectSerialForPart(serial: ProductSerialDTO) {
        val partIdx = _uiState.value.serialSelectPartIdx ?: return
        addSerialToPart(partIdx, serial.serialNumber)
        dismissSerialSelector()
    }

    fun dismissSerialSelector() = _uiState.update {
        it.copy(
            serialSelectPartIdx = null,
            serialSelectProduct = null,
            serialSelectOptions = emptyList(),
            serialSelectLoading = false,
            serialSelectError = null
        )
    }

    // ── Part scan ─────────────────────────────────────────────────────────────
    fun showPartScanner()    = _uiState.update { it.copy(showPartScanner = true) }
    fun dismissPartScanner() = _uiState.update { it.copy(showPartScanner = false) }
    fun clearPartScanError() = _uiState.update { it.copy(partScanError = null) }
    fun clearSerialError()   = _uiState.update { it.copy(serialError = null) }

    fun onPartProductScan(code: String) {
        _uiState.update { it.copy(showPartScanner = false, partScanLoading = true, partScanError = null) }
        val products = _uiState.value.productList
        val byCode   = products.find { it.productCode.equals(code, ignoreCase = true) }
        if (byCode != null) {
            addOrIncrementPart(byCode, null)
            _uiState.update { it.copy(partScanLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.findProductBySerial(token, code)
                val found = res.body()?.data
                if (res.isSuccessful && found?.productId != null) {
                    val product = products.find { it.id == found.productId }
                    if (product != null) addOrIncrementPart(product, code)
                    else _uiState.update { it.copy(partScanError = "\"$code\" ကုန်ပစ္စည်း မတွေ့ပါ") }
                } else {
                    _uiState.update { it.copy(partScanError = "\"$code\" မတွေ့ပါ") }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(partScanError = "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
            _uiState.update { it.copy(partScanLoading = false) }
        }
    }

    private fun addOrIncrementPart(product: ProductDTO, serial: String?) {
        _uiState.update { s ->
            val parts   = s.parts.toMutableList()
            val idx     = parts.indexOfFirst { it.product?.id == product.id }
            if (idx >= 0) {
                val p = parts[idx]
                if (product.hasSerial == true && serial != null && !p.serialNumbers.contains(serial)) {
                    parts[idx] = p.copy(serialNumbers = p.serialNumbers + serial, qty = (p.serialNumbers.size + 1).toString())
                } else if (product.hasSerial != true) {
                    parts[idx] = p.copy(qty = ((p.qty.toIntOrNull() ?: 1) + 1).toString())
                }
            } else {
                parts.add(PartDraft(
                    product       = product,
                    unitPrice     = String.format("%.0f", product.sellingPrice.toDouble()),
                    serialNumbers = if (serial != null && product.hasSerial == true) listOf(serial) else emptyList()
                ))
            }
            s.copy(parts = parts)
        }
    }

    fun showSerialScanner(partIdx: Int) = _uiState.update { it.copy(serialScanPartIdx = partIdx) }
    fun dismissSerialScanner()          = _uiState.update { it.copy(serialScanPartIdx = null) }

    fun onPartSerialScan(partIdx: Int, serial: String) {
        dismissSerialScanner()
        addSerialToPart(partIdx, serial)
    }

    fun addSerialToPart(partIdx: Int, serial: String) {
        _uiState.update { s ->
            val parts = s.parts.toMutableList()
            val part  = parts.getOrNull(partIdx) ?: return@update s
            if (part.serialNumbers.contains(serial))
                return@update s.copy(serialError = "\"$serial\" ထပ်နေသည်")
            parts[partIdx] = part.copy(
                serialNumbers = part.serialNumbers + serial,
                qty           = (part.serialNumbers.size + 1).toString()
            )
            s.copy(parts = parts)
        }
    }

    fun removeSerialFromPart(partIdx: Int, serial: String) {
        _uiState.update { s ->
            val parts     = s.parts.toMutableList()
            val part      = parts.getOrNull(partIdx) ?: return@update s
            val newSerials = part.serialNumbers.filter { it != serial }
            parts[partIdx] = part.copy(serialNumbers = newSerials, qty = maxOf(1, newSerials.size).toString())
            s.copy(parts = parts)
        }
    }

    fun createCustomer() {
        val s = _uiState.value
        if (s.newCustomerName.isBlank()) {
            _uiState.update { it.copy(newCustomerError = "ဖောက်သည်အမည် ဖြည့်ပါ") }
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
                        phone = s.newCustomerPhone.ifBlank { null },
                        address = s.newCustomerAddress.ifBlank { null }
                    )
                )
                if (res.isSuccessful && res.body()?.data != null) {
                    val created = res.body()!!.data!!
                    _uiState.update {
                        it.copy(
                            customers = it.customers + created,
                            selectedCustomer = created,
                            customerQuery = created.name,
                            showNewCustomerDialog = false,
                            newCustomerName = "",
                            newCustomerPhone = "",
                            newCustomerAddress = "",
                            creatingCustomer = false,
                            newCustomerError = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            creatingCustomer = false,
                            newCustomerError = res.body()?.message ?: "ဖောက်သည် မသိမ်းနိုင်ပါ (${res.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        creatingCustomer = false,
                        newCustomerError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း"
                    )
                }
            }
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────
    fun save(onSuccess: (ServiceJobDTO) -> Unit) {
        val s = _uiState.value
        if (s.selectedCustomer == null) { _uiState.update { it.copy(saveError = "ဖောက်သည် ရွေးပါ") }; return }
        if (s.itemName.isBlank())       { _uiState.update { it.copy(saveError = "ပစ္စည်းအမည် ရိုက်ထည့်ပါ") }; return }
        if (s.problemDesc.isBlank())    { _uiState.update { it.copy(saveError = "ပြဿနာ ဖော်ပြချက် ရိုက်ထည့်ပါ") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token      = ApiClient.bearer(prefs.authToken)
                val validLines = s.lines.filter { l -> l.serviceItem != null }
                val validParts = s.parts.filter { p -> p.product != null }

                // Serial-tracked parts must have serials (count must match qty)
                val missingSerial = validParts.filter { p ->
                    p.product?.hasSerial == true && p.serialNumbers.isEmpty()
                }
                if (missingSerial.isNotEmpty()) {
                    val names = missingSerial.joinToString { "\"${it.product?.name}\"" }
                    _uiState.update { it.copy(saving = false, saveError = "$names — Serial number ထည့်ပါ") }
                    return@launch
                }
                val dto        = ServiceJobDTO(
                    id                  = jobId,
                    customerId          = s.selectedCustomer.id,
                    customerName        = s.selectedCustomer.name,
                    assignedStaffId     = s.selectedStaff?.id,
                    itemName            = s.itemName.ifBlank { null },
                    itemCondition       = s.itemCondition.ifBlank { null },
                    deviceConditions    = s.deviceConditions.ifBlank { null },
                    serialNo            = s.serialNo.ifBlank { null },
                    color               = s.color.ifBlank { null },
                    shelfLocationId     = s.selectedShelfLocation?.id,
                    accessories         = s.accessories.ifBlank { null },
                    problemDesc         = s.problemDesc.ifBlank { null },
                    diagnosisNotes      = s.diagnosisNotes.ifBlank { null },
                    estimatedCost       = s.estimatedCost.toDoubleOrNull(),
                    estimatedCompletion = s.estimatedCompletion.ifBlank { null },
                    remark              = s.remark.ifBlank { null },
                    status              = if (jobId == null) "RECEIVED" else null,
                    lines               = if (validLines.isEmpty()) null else validLines.map { l ->
                        ServiceJobLineDTO(
                            serviceItemId   = l.serviceItem!!.id,
                            serviceItemName = l.serviceItem.item,
                            qty             = l.qty.toIntOrNull() ?: 1,
                            price           = l.price.toDoubleOrNull() ?: l.serviceItem.price,
                            warrantyMonths  = l.warrantyMonths.toIntOrNull() ?: 0,
                            warrantyCovered = l.serviceItem.focDefault == true
                        )
                    },
                    productParts        = if (validParts.isEmpty()) null else validParts.map { p ->
                        val isSerial   = p.product!!.hasSerial == true
                        val qty        = if (isSerial) p.serialNumbers.size else p.qty.toIntOrNull() ?: 1
                        val unitPrice  = p.unitPrice.toDoubleOrNull() ?: p.product.sellingPrice.toDouble()
                        val discount   = p.discount.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
                        ServiceJobPartDTO(
                            productId      = p.product.id,
                            productName    = p.product.name,
                            productCode    = p.product.productCode,
                            qty            = qty,
                            unitPrice      = unitPrice,
                            discountAmount = discount.takeIf { it > 0 },
                            serialNumbers  = if (isSerial) p.serialNumbers else emptyList()
                        )
                    }
                )
                val res = if (jobId != null)
                    ApiClient.service.updateServiceJob(token, jobId, dto)
                else
                    ApiClient.service.createServiceJob(token, dto)

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

    // ── Data classes ──────────────────────────────────────────────────────────

    data class LineDraft(
        val serviceItem:    ServiceItemDTO? = null,
        val qty:            String          = "1",
        val price:          String          = "",
        val warrantyMonths: String          = "0"
    )

    data class PartDraft(
        val product:       ProductDTO?  = null,
        val qty:           String       = "1",
        val unitPrice:     String       = "",
        val discount:      String       = "0",
        val serialNumbers: List<String> = emptyList()
    )

    data class UiState(
        val customers:           List<CustomerDTO>       = emptyList(),
        val staffList:           List<StaffDTO>          = emptyList(),
        val shelfLocations:      List<ShelfLocationDTO>  = emptyList(),
        val serviceItems:        List<ServiceItemDTO>    = emptyList(),
        val productList:         List<ProductDTO>        = emptyList(),
        val paymentMethods:      List<PaymentMethodDTO>  = emptyList(),
        val loading:             Boolean                 = true,
        val saving:              Boolean                 = false,
        val saveError:           String?                 = null,
        // form
        val customerQuery:       String                  = "",
        val selectedCustomer:    CustomerDTO?            = null,
        val showNewCustomerDialog: Boolean               = false,
        val newCustomerName:     String                  = "",
        val newCustomerPhone:    String                  = "",
        val newCustomerAddress:  String                  = "",
        val creatingCustomer:    Boolean                 = false,
        val newCustomerError:    String?                 = null,
        val selectedStaff:       StaffDTO?               = null,
        val selectedShelfLocation: ShelfLocationDTO?     = null,
        val itemName:            String                  = "",
        val itemCondition:       String                  = "",
        val deviceConditions:    String                  = "",
        val serialNo:            String                  = "",
        val color:               String                  = "",
        val accessories:         String                  = "",
        val problemDesc:         String                  = "",
        val diagnosisNotes:      String                  = "",
        val estimatedCost:       String                  = "",
        val estimatedCompletion: String                  = "",
        val lines:               List<LineDraft>         = emptyList(),
        val parts:               List<PartDraft>         = emptyList(),
        val remark:              String                  = "",
        // part scan
        val showPartScanner:     Boolean                 = false,
        val partScanLoading:     Boolean                 = false,
        val partScanError:       String?                 = null,
        val serialSelectPartIdx: Int?                    = null,
        val serialSelectProduct: ProductDTO?             = null,
        val serialSelectOptions: List<ProductSerialDTO>  = emptyList(),
        val serialSelectLoading: Boolean                 = false,
        val serialSelectError:   String?                 = null,
        val serialScanPartIdx:   Int?                    = null,
        val serialError:         String?                 = null
    )
}
