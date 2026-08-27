package com.sspd.servicemgmt.feature.purchase

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.PaymentTransactionDTO
import com.sspd.servicemgmt.core.network.ProductDTO
import com.sspd.servicemgmt.core.network.PurchaseDTO
import com.sspd.servicemgmt.core.network.PurchaseItemDTO
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.network.SupplierDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class PurchaseFormViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadMasters() }

    fun loadMasters() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                coroutineScope {
                    val suppliers = async { ApiClient.service.getSuppliers(token) }
                    val staff    = async { ApiClient.service.getActiveStaff(token) }
                    val products = async { ApiClient.service.getProducts(token) }
                    val methods  = async { ApiClient.service.getActivePaymentMethods(token) }
                    _uiState.update {
                        it.copy(
                            suppliers      = suppliers.await().body()?.data ?: emptyList(),
                            staff          = staff.await().body()?.data    ?: emptyList(),
                            products       = products.await().body()?.data ?: emptyList(),
                            paymentMethods = methods.await().body()?.data  ?: emptyList(),
                            loading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, saveError = e.message ?: "ဒေတာယူမရပါ") }
            }
        }
    }

    fun selectSupplier(v: SupplierDTO?) = _uiState.update { state ->
        val days = maxOf(0, v?.defaultCreditDays ?: 30)
        val due = runCatching { LocalDate.parse(state.purchaseDate).plusDays(days.toLong()).toString() }.getOrDefault(state.dueDate)
        state.copy(selectedSupplier = v, paymentTermDays = days, dueDate = due)
    }
    fun selectStaff(v: StaffDTO?)                 = _uiState.update { it.copy(selectedStaff = v) }
    fun selectPaymentMethod(v: PaymentMethodDTO?) = _uiState.update { it.copy(selectedPaymentMethod = v) }
    fun setPurchaseDate(v: String) = _uiState.update { state ->
        val due = if (state.paymentTermDays >= 0) runCatching {
            LocalDate.parse(v).plusDays(state.paymentTermDays.toLong()).toString()
        }.getOrDefault(state.dueDate) else state.dueDate
        state.copy(purchaseDate = v, dueDate = due)
    }
    fun setDueDate(v: String)                     = _uiState.update { it.copy(dueDate = v, paymentTermDays = -1) }
    fun setPaymentTermDays(days: Int) = _uiState.update { state ->
        val safeDays = maxOf(0, days)
        val due = runCatching { LocalDate.parse(state.purchaseDate).plusDays(safeDays.toLong()).toString() }.getOrDefault(state.dueDate)
        state.copy(paymentTermDays = safeDays, dueDate = due)
    }
    fun setPaidAmount(v: String)                  = _uiState.update { it.copy(paidAmount = v.filterMoney()) }
    fun setPaymentTransactionNo(v: String)        = _uiState.update { it.copy(paymentTransactionNo = v) }
    fun setDiscountAmount(v: String)              = _uiState.update { it.copy(discountAmount = v.filterMoney()) }.also { recalcTax() }
    fun setTaxAmount(v: String)                   = _uiState.update { it.copy(taxAmount = v.filterMoney()) }
    fun setTaxRate(v: String)                     = _uiState.update { it.copy(taxRate = v.filterMoney()) }.also { recalcTax() }
    fun setTaxMode(v: String)                     = _uiState.update { it.copy(taxMode = v) }.also { recalcTax() }
    fun setWithholdingTaxAmount(v: String)        = _uiState.update { it.copy(withholdingTaxAmount = v.filterMoney()) }
    fun setLandedCostAllocationMethod(v: String)  = _uiState.update { it.copy(landedCostAllocationMethod = v) }
    fun setCurrencyCode(v: String) {
        val code = v.uppercase().filter { it.isLetter() }.take(3)
        _uiState.update {
            it.copy(
                currencyCode = code,
                exchangeRate = if (code == "MMK") "1" else it.exchangeRate
            )
        }
    }
    fun setExchangeRate(v: String)                = _uiState.update { it.copy(exchangeRate = v.filterMoney()) }
    fun setOtherCharges(v: String)                = _uiState.update { it.copy(otherCharges = v.filterMoney()) }
    fun setSupplierInvoiceNo(v: String)           = _uiState.update { it.copy(supplierInvoiceNo = v) }
    fun setWarehouseName(v: String)               = _uiState.update { it.copy(warehouseName = v) }
    fun setRemark(v: String)                      = _uiState.update { it.copy(remark = v) }
    fun clearError()                              = _uiState.update { it.copy(saveError = null) }

    fun openSupplierPicker() = _uiState.update { it.copy(showSupplierPicker  = true, pickerQuery = "") }
    fun openStaffPicker()    = _uiState.update { it.copy(showStaffPicker     = true, pickerQuery = "") }
    fun openPaymentPicker()  = _uiState.update { it.copy(showPaymentPicker   = true, pickerQuery = "") }
    fun openProductPicker()  = _uiState.update { it.copy(showProductPicker   = true, pickerQuery = "") }
    fun dismissPicker() = _uiState.update {
        it.copy(
            showSupplierPicker = false, showStaffPicker   = false,
            showPaymentPicker  = false, showProductPicker = false,
            pickerQuery = ""
        )
    }
    fun setPickerQuery(v: String) = _uiState.update { it.copy(pickerQuery = v) }

    fun addSplitPayment() {
        val s = _uiState.value
        val method = s.selectedPaymentMethod ?: return fail("ငွေပေးချေနည်း ရွေးပါ")
        val amount = s.paidAmount.toDoubleOrNull() ?: 0.0
        if (amount <= 0) return fail("Split amount ထည့်ပါ")
        val next = s.splitPayments + PaymentTransactionDTO(
            paymentMethodId   = method.id,
            paymentMethodName = method.methodName,
            amount            = amount,
            transactionNo     = s.paymentTransactionNo.ifBlank { null }
        )
        _uiState.update {
            it.copy(splitPayments = next, paidAmount = splitTotal(next).formatMoneyInput(), paymentTransactionNo = "", saveError = null)
        }
    }

    fun removeSplitPayment(index: Int) = _uiState.update { s ->
        val next = s.splitPayments.filterIndexed { i, _ -> i != index }
        s.copy(splitPayments = next, paidAmount = if (next.isEmpty()) "0" else splitTotal(next).formatMoneyInput())
    }

    fun selectProduct(product: ProductDTO) {
        if (_uiState.value.lines.any { it.productId == product.id }) {
            _uiState.update { it.copy(saveError = "${product.name} ထပ်နေပါသည်", showProductPicker = false) }
            return
        }
        val cost            = (product.lastPurchaseCost ?: product.costPrice)?.toString() ?: "0"
        val defaultWarranty = (product.warrantyMonths ?: 0).toString()
        val isSerial        = product.hasSerial == true
        val orphaned        = if (isSerial) maxOf(0, product.stockQty - (product.availableSerialCount ?: product.stockQty)) else 0
        _uiState.update {
            it.copy(
                lines = it.lines + PurchaseLine(
                    productId      = product.id,
                    productName    = product.name,
                    productCode    = product.productCode,
                    hasSerial      = isSerial,
                    stockQty       = product.stockQty,
                    orphanedQty    = orphaned,
                    qty            = "1",
                    unitCost       = cost,
                    warrantyMonths = defaultWarranty,
                    serials        = if (isSerial) listOf("") else emptyList(),
                    conditions     = if (isSerial) listOf("") else emptyList(),
                    warranties     = if (isSerial) listOf("") else emptyList(),
                    serialPhotos   = if (isSerial) listOf("") else emptyList()
                ),
                showProductPicker = false,
                pickerQuery = "",
                saveError   = null
            )
        }
    }

    fun removeLine(index: Int) = updateLine(index, null)

    fun setLineQty(index: Int, v: String) = updateLine(index) { line ->
        val qty     = v.filterDigits().toIntOrNull() ?: 0
        val trackSn = line.hasSerial || line.assignSerials
        line.copy(
            qty          = v.filterDigits(),
            serials      = if (trackSn) resizeList(line.serials, qty)      else line.serials,
            conditions   = if (trackSn) resizeList(line.conditions, qty)   else line.conditions,
            warranties   = if (trackSn) resizeList(line.warranties, qty)   else line.warranties,
            serialPhotos = if (trackSn) resizeList(line.serialPhotos, qty) else line.serialPhotos
        )
    }

    fun toggleAssignSerials(index: Int) = updateLine(index) { line ->
        val next = !line.assignSerials
        val qty  = line.qty.toIntOrNull() ?: 1
        line.copy(
            assignSerials = next,
            serials       = if (next) resizeList(emptyList(), qty) else emptyList(),
            conditions    = if (next) resizeList(emptyList(), qty) else emptyList(),
            warranties    = if (next) resizeList(emptyList(), qty) else emptyList(),
            serialPhotos  = if (next) resizeList(emptyList(), qty) else emptyList()
        )
    }

    fun setLineCost(index: Int, v: String)     = updateLine(index) { it.copy(unitCost = v.filterMoney()) }
    fun setLineWarranty(index: Int, v: String) = updateLine(index) { it.copy(warrantyMonths = v.filterDigits()) }
    fun setLineBatchNumber(index: Int, v: String) = updateLine(index) { it.copy(batchNumber = v) }
    fun setLineExpiryDate(index: Int, v: String)  = updateLine(index) { it.copy(expiryDate = v) }
    fun setLineAllocatedLandedCost(index: Int, v: String) = updateLine(index) { it.copy(allocatedLandedCost = v.filterMoney()) }

    fun setLineSerial(lineIndex: Int, serialIndex: Int, v: String) = updateLine(lineIndex) { line ->
        val updated = line.serials.toMutableList()
        if (serialIndex in updated.indices) updated[serialIndex] = v
        line.copy(serials = updated)
    }

    fun setLineCondition(lineIndex: Int, conditionIndex: Int, v: String) = updateLine(lineIndex) { line ->
        val updated = line.conditions.toMutableList()
        if (conditionIndex in updated.indices) updated[conditionIndex] = v
        line.copy(conditions = updated)
    }

    fun setLineWarrantyItem(lineIndex: Int, itemIndex: Int, v: String) = updateLine(lineIndex) { line ->
        val updated = line.warranties.toMutableList()
        if (itemIndex in updated.indices) updated[itemIndex] = v.filter { it.isDigit() }
        line.copy(warranties = updated)
    }

    fun setLineSerialPhoto(lineIndex: Int, serialIndex: Int, base64: String) = updateLine(lineIndex) { line ->
        val updated = line.serialPhotos.toMutableList()
        if (serialIndex in updated.indices) updated[serialIndex] = base64
        line.copy(serialPhotos = updated)
    }

    fun autoAssignSerials(index: Int) {
        val line = _uiState.value.lines.getOrNull(index) ?: return
        val qty  = line.qty.toIntOrNull() ?: 0
        if (qty <= 0) { fail("Qty ကို အရင်ထည့်ပါ"); return }

        val existing = _uiState.value.lines
            .flatMapIndexed { i, l -> if (i == index) emptyList() else l.serials }
            .filter { it.isNotEmpty() }
            .toMutableSet()

        val prefix = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val rng = java.util.Random()
        val generated = buildList<String> {
            while (size < qty) {
                val c = "$prefix${rng.nextInt(1_000).toString().padStart(3, '0')}"
                if (existing.add(c)) add(c)
            }
        }
        updateLine(index) { it.copy(serials = generated) }
    }

    private fun resizeList(list: List<String>, size: Int, default: String = ""): List<String> {
        val n = maxOf(0, size)
        return when {
            list.size > n -> list.take(n)
            list.size < n -> list + List(n - list.size) { default }
            else          -> list
        }
    }

    private fun updateLine(index: Int, transform: ((PurchaseLine) -> PurchaseLine)?) {
        _uiState.update { s ->
            val lines = s.lines.toMutableList()
            if (index in lines.indices) {
                if (transform == null) lines.removeAt(index) else lines[index] = transform(lines[index])
            }
            s.copy(lines = lines)
        }
    }

    fun save(onSuccess: (PurchaseDTO) -> Unit) {
        val s             = _uiState.value
        val supplier      = s.selectedSupplier
        val staff         = s.selectedStaff
        val splitPayments = normalizePayments(s.splitPayments)
        val paid          = if (splitPayments.isNotEmpty()) splitTotal(splitPayments) else (s.paidAmount.toDoubleOrNull() ?: 0.0)
        val discount      = s.discountAmount.toDoubleOrNull() ?: 0.0
        val tax           = s.taxAmount.toDoubleOrNull() ?: 0.0
        val other         = s.otherCharges.toDoubleOrNull() ?: 0.0
        val wht           = s.withholdingTaxAmount.toDoubleOrNull() ?: 0.0
        val taxRate       = s.taxRate.toDoubleOrNull() ?: 0.0
        val payableBeforeWht = totalAmount(s.lines) - discount + other + if (s.taxMode == "INCLUSIVE") 0.0 else tax
        val net           = maxOf(0.0, payableBeforeWht - wht)
        val currency      = s.currencyCode.ifBlank { "MMK" }.uppercase()
        val exchangeRate  = if (currency == "MMK") 1.0 else (s.exchangeRate.toDoubleOrNull() ?: 0.0)

        if (supplier == null)  return fail("Supplier ရွေးပါ")
        if (staff == null)     return fail("Staff ရွေးပါ")
        if (s.lines.isEmpty()) return fail("ပစ္စည်း အနည်းဆုံးတစ်ခု ထည့်ပါ")
        if (discount < 0)      return fail("Discount မမှန်ပါ")
        if (discount > totalAmount(s.lines)) return fail("Discount သည် စုစုပေါင်းတန်ဖိုးထက် မကျော်ရပါ")
        if (currency.length != 3) return fail("Currency code ၃ လုံး ထည့်ပါ")
        if (exchangeRate <= 0) return fail("Exchange rate ထည့်ပါ")
        if (wht > payableBeforeWht + 0.01) return fail("Withholding tax သည် ကျသင့်ငွေထက် မကျော်ရပါ")
        if (paid > net)        return fail("Paid amount သည် Net amount ထက် မကျော်ရပါ")
        if (s.landedCostAllocationMethod == "MANUAL") {
            val allocated = s.lines.sumOf { it.allocatedLandedCost.toDoubleOrNull() ?: 0.0 }
            if (kotlin.math.abs(allocated - other) > 0.01) return fail("Manual landed cost စုစုပေါင်းသည် အခြားကုန်ကျနှင့် ကိုက်ညီရမည်")
        }
        if (net - paid > 0 && s.dueDate.isBlank()) return fail("အကြွေးဝယ်ယူမှုအတွက် ငွေချေရမည့်ရက် လိုအပ်သည်")
        if (s.dueDate.isNotBlank() && runCatching { LocalDate.parse(s.dueDate).isBefore(LocalDate.parse(s.purchaseDate)) }.getOrDefault(true))
            return fail("ငွေချေရမည့်ရက်သည် ဝယ်ရက်ထက် မစောရပါ")
        if (paid > 0 && s.selectedPaymentMethod == null && splitPayments.isEmpty())
            return fail("ငွေပေးချေမှုနည်းလမ်း ရွေးပါ")

        val detailDtos = mutableListOf<PurchaseItemDTO>()
        for ((idx, line) in s.lines.withIndex()) {
            val qty      = line.qty.toIntOrNull() ?: 0
            val cost     = line.unitCost.toDoubleOrNull() ?: 0.0
            val warranty = line.warrantyMonths.toIntOrNull() ?: 0
            val serials   = line.serials.filter { it.isNotBlank() }
            val trackSn   = line.hasSerial || line.assignSerials

            if (qty  <= 0) return fail("Line ${idx + 1}: Qty မှန်မှန်ထည့်ပါ")
            if (cost <= 0) return fail("Line ${idx + 1}: Unit cost ထည့်ပါ")
            if (trackSn && serials.size != qty)
                return fail("Line ${idx + 1}: Serial ${serials.size} ခု ဖြည့်ထားပြီး Qty $qty ဖြစ်ရမယ်")

            val conditionsList = if (serials.isNotEmpty()) {
                (0 until qty).map { i -> line.conditions.getOrElse(i) { "" } }
                    .takeIf { list -> list.any { it.isNotBlank() } }
            } else null

            val warrantiesList = if (line.warranties.any { it.isNotBlank() }) {
                (0 until qty).map { i -> line.warranties.getOrElse(i) { "0" }.toIntOrNull() ?: 0 }
            } else null

            val photosList = (0 until qty)
                .map { i -> line.serialPhotos.getOrElse(i) { "" } }
                .takeIf { list -> list.any { it.isNotBlank() } }

            detailDtos += PurchaseItemDTO(
                productId        = line.productId,
                productName      = line.productName,
                qty              = qty,
                unitCost         = cost,
                subtotal         = qty * cost,
                batchNumber      = line.batchNumber.trim().ifBlank { null },
                expiryDate       = line.expiryDate.trim().ifBlank { null },
                warrantyMonths   = if (warrantiesList == null) warranty else null,
                itemWarranties   = warrantiesList,
                serialNumbers    = serials.takeIf { it.isNotEmpty() },
                serialConditions = conditionsList,
                serialPhotos     = photosList,
                allocatedLandedCost = if (s.landedCostAllocationMethod == "MANUAL")
                    line.allocatedLandedCost.toDoubleOrNull() else null
            )
        }

        val dto = PurchaseDTO(
            supplierId      = supplier.id,
            staffId         = staff.id,
            purchaseDate    = "${s.purchaseDate}T00:00:00",
            dueDate         = s.dueDate.ifBlank { null },
            paymentTermDays = s.paymentTermDays.takeIf { it >= 0 },
            discountAmount  = discount,
            taxAmount       = tax,
            taxMode         = s.taxMode,
            taxRate         = taxRate.takeIf { it > 0 },
            withholdingTaxAmount = wht.takeIf { it > 0 },
            otherCharges    = other,
            landedCostAllocationMethod = s.landedCostAllocationMethod,
            currencyCode    = currency,
            exchangeRate    = exchangeRate,
            foreignNetAmount = if (currency == "MMK") null else net / exchangeRate,
            paidAmount      = paid,
            remark          = s.remark.ifBlank { null },
            supplierInvoiceNo = s.supplierInvoiceNo.ifBlank { null },
            warehouseName   = s.warehouseName.trim().ifBlank { null },
            paymentMethodId = if (paid > 0) (splitPayments.firstOrNull()?.paymentMethodId ?: s.selectedPaymentMethod?.id) else null,
            transactionNo   = s.paymentTransactionNo.ifBlank { null },
            payments        = splitPayments.ifEmpty { null },
            details         = detailDtos
        )

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.createPurchase(token, dto)
                val saved = res.body()?.data
                if (res.isSuccessful && saved != null) {
                    _uiState.update { it.copy(saving = false) }
                    onSuccess(saved)
                } else {
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "သိမ်းမရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    private fun fail(message: String) { _uiState.update { it.copy(saveError = message) } }

    private fun recalcTax() {
        val s = _uiState.value
        val rate = s.taxRate.toDoubleOrNull() ?: 0.0
        if (rate <= 0) return
        val taxable = maxOf(0.0, totalAmount(s.lines) - (s.discountAmount.toDoubleOrNull() ?: 0.0))
        val tax = if (s.taxMode == "INCLUSIVE") taxable * rate / (100 + rate) else taxable * rate / 100
        _uiState.update { it.copy(taxAmount = tax.formatMoneyInput()) }
    }

    data class PurchaseLine(
        val productId      : Int,
        val productName    : String,
        val productCode    : String,
        val hasSerial      : Boolean,
        val assignSerials  : Boolean      = false,
        val stockQty       : Int          = 0,
        val orphanedQty    : Int          = 0,
        val qty            : String       = "1",
        val unitCost       : String       = "0",
        val warrantyMonths : String       = "0",
        val batchNumber    : String       = "",
        val expiryDate     : String       = "",
        val serials        : List<String> = emptyList(),
        val conditions     : List<String> = emptyList(),
        val warranties     : List<String> = emptyList(),
        val serialPhotos   : List<String> = emptyList(),
        val allocatedLandedCost: String = "0"
    )

    data class UiState(
        val suppliers             : List<SupplierDTO>          = emptyList(),
        val staff                 : List<StaffDTO>             = emptyList(),
        val products              : List<ProductDTO>           = emptyList(),
        val paymentMethods        : List<PaymentMethodDTO>     = emptyList(),
        val selectedSupplier      : SupplierDTO?               = null,
        val selectedStaff         : StaffDTO?                  = null,
        val selectedPaymentMethod : PaymentMethodDTO?          = null,
        val splitPayments         : List<PaymentTransactionDTO> = emptyList(),
        val purchaseDate          : String = LocalDate.now().toString(),
        val dueDate               : String = "",
        val paymentTermDays       : Int = 30,
        val discountAmount        : String = "0",
        val taxAmount             : String = "0",
        val taxRate               : String = "0",
        val taxMode               : String = "EXCLUSIVE",
        val withholdingTaxAmount  : String = "0",
        val otherCharges          : String = "0",
        val landedCostAllocationMethod: String = "VALUE",
        val currencyCode          : String = "MMK",
        val exchangeRate          : String = "1",
        val supplierInvoiceNo     : String = "",
        val warehouseName         : String = "",
        val paidAmount            : String = "0",
        val paymentTransactionNo  : String = "",
        val remark                : String = "",
        val lines                 : List<PurchaseLine> = emptyList(),
        val loading               : Boolean = true,
        val saving                : Boolean = false,
        val saveError             : String? = null,
        val autoAssigningIndex    : Int? = null,
        val showSupplierPicker    : Boolean = false,
        val showStaffPicker       : Boolean = false,
        val showPaymentPicker     : Boolean = false,
        val showProductPicker     : Boolean = false,
        val pickerQuery           : String = ""
    )
}

fun totalAmount(lines: List<PurchaseFormViewModel.PurchaseLine>): Double =
    lines.sumOf { (it.qty.toIntOrNull() ?: 0) * (it.unitCost.toDoubleOrNull() ?: 0.0) }

fun netAmount(lines: List<PurchaseFormViewModel.PurchaseLine>, discount: String, tax: String = "0", other: String = "0"): Double =
    purchasePayable(lines, discount, tax, other, "EXCLUSIVE", "0")

fun purchasePayable(
    lines: List<PurchaseFormViewModel.PurchaseLine>,
    discount: String,
    tax: String,
    other: String,
    taxMode: String,
    withholding: String
): Double {
    val gross = totalAmount(lines)
    val disc = discount.toDoubleOrNull() ?: 0.0
    val taxAmt = tax.toDoubleOrNull() ?: 0.0
    val otherAmt = other.toDoubleOrNull() ?: 0.0
    val wht = withholding.toDoubleOrNull() ?: 0.0
    val payable = gross - disc + otherAmt + if (taxMode == "INCLUSIVE") 0.0 else taxAmt
    return maxOf(0.0, payable - wht)
}

private fun normalizePayments(payments: List<PaymentTransactionDTO>): List<PaymentTransactionDTO> =
    payments.mapNotNull { p ->
        val id = p.paymentMethodId ?: 0; val amount = p.amount ?: 0.0
        if (id <= 0 || amount <= 0.0) null
        else p.copy(amount = amount, transactionNo = p.transactionNo?.ifBlank { null })
    }

private fun splitTotal(payments: List<PaymentTransactionDTO>): Double = payments.sumOf { it.amount ?: 0.0 }

private fun Double.formatMoneyInput(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun String.filterDigits(): String = filter { it.isDigit() }
private fun String.filterMoney(): String = filter { it.isDigit() || it == '.' }.let {
    val dot = it.indexOf('.')
    if (dot < 0) it else it.take(dot + 1) + it.drop(dot + 1).replace(".", "")
}
