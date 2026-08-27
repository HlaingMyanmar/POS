package com.sspd.servicemgmt.feature.sale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.*
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToLong

data class CartItem(
    val product:        ProductDTO,
    val qty:            Int           = 1,
    val unitPrice:      Long          = 0,
    val discountAmount: Long          = 0,
    val foc:            Boolean       = false,
    val serialNumbers:  List<String>  = emptyList()
)

class NewSaleViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(NewSaleUiState())
    val uiState: StateFlow<NewSaleUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(hasBackdatePermission = prefs.hasPermission("CAN_ACCESS_SALE_BACKDATE"))
        }
        loadMasterData()
    }

    private fun loadMasterData() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val pd = async { ApiClient.service.getProducts(token) }
                val cd = async { ApiClient.service.getCustomers(token) }
                val sd = async { ApiClient.service.getActiveStaff(token) }
                val md = async { ApiClient.service.getActivePaymentMethods(token) }
                _uiState.update {
                    it.copy(
                        products       = pd.await().body()?.data ?: emptyList(),
                        customers      = cd.await().body()?.data ?: emptyList(),
                        staffList      = sd.await().body()?.data ?: emptyList(),
                        paymentMethods = md.await().body()?.data ?: emptyList(),
                        loading        = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    // ── Cart ─────────────────────────────────────────────────────────────────

    fun addToCart(product: ProductDTO, serial: String? = null) {
        _uiState.update { state ->
            val cart = state.cart.toMutableList()
            val idx  = cart.indexOfFirst { it.product.id == product.id }
            if (idx >= 0) {
                val item = cart[idx]
                if (product.hasSerial == true) {
                    if (serial != null && !item.serialNumbers.contains(serial)) {
                        cart[idx] = item.copy(
                            serialNumbers = item.serialNumbers + serial,
                            qty           = item.serialNumbers.size + 1
                        )
                    }
                } else {
                    cart[idx] = item.copy(qty = item.qty + 1)
                }
            } else {
                cart.add(CartItem(
                    product       = product,
                    qty           = 1,
                    unitPrice     = product.sellingPrice.roundToLong(),
                    serialNumbers = if (serial != null) listOf(serial) else emptyList()
                ))
            }
            state.copy(cart = cart)
        }
    }

    fun selectProductForSale(product: ProductDTO) {
        _uiState.update { it.copy(showProductPicker = false) }
        if (product.hasSerial == true) {
            _uiState.update {
                it.copy(
                    serialSelectProduct = product,
                    serialSelectOptions = emptyList(),
                    serialSelectLoading = true,
                    serialSelectError = null
                )
            }
            loadSerialOptions(product)
        } else {
            addToCart(product)
        }
    }

    private fun loadSerialOptions(product: ProductDTO) {
        viewModelScope.launch {
            try {
                val res = ApiClient.service.getProductSerials(ApiClient.bearer(prefs.authToken), product.id)
                val selected = _uiState.value.cart.flatMap { it.serialNumbers }.toSet()
                val options = (if (res.isSuccessful) res.body()?.data ?: emptyList() else emptyList())
                    .filter { serial ->
                        val status = serial.status?.uppercase()
                        serial.serialNumber.isNotBlank() &&
                            status != "SOLD" &&
                            status != "USED" &&
                            status != "DAMAGED" &&
                            status != "LOST" &&
                            !selected.contains(serial.serialNumber)
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

    fun selectSerialForSale(serial: ProductSerialDTO) {
        val product = _uiState.value.serialSelectProduct ?: return
        addToCart(product, serial.serialNumber)
        dismissSerialSelector()
    }

    fun dismissSerialSelector() = _uiState.update {
        it.copy(
            serialSelectProduct = null,
            serialSelectOptions = emptyList(),
            serialSelectLoading = false,
            serialSelectError = null
        )
    }

    fun updateQty(idx: Int, delta: Int) {
        _uiState.update { state ->
            val cart = state.cart.toMutableList()
            val item = cart.getOrNull(idx) ?: return@update state
            if (item.product.hasSerial == true) return@update state
            cart[idx] = item.copy(qty = maxOf(1, item.qty + delta))
            state.copy(cart = cart)
        }
    }

    fun updateDiscount(idx: Int, value: String) {
        val amt = value.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        _uiState.update { state ->
            state.copy(cart = state.cart.mapIndexed { i, ci ->
                if (i == idx) ci.copy(discountAmount = amt) else ci
            })
        }
    }

    fun updateFoc(idx: Int, foc: Boolean) {
        _uiState.update { state ->
            state.copy(cart = state.cart.mapIndexed { i, item ->
                if (i == idx) item.copy(foc = foc, discountAmount = if (foc) 0 else item.discountAmount) else item
            })
        }
    }

    fun removeItem(idx: Int) {
        _uiState.update { state -> state.copy(cart = state.cart.filterIndexed { i, _ -> i != idx }) }
    }

    fun removeSerial(itemIdx: Int, serial: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.mapIndexed { i, ci ->
                if (i != itemIdx) ci
                else {
                    val serials = ci.serialNumbers.filter { it != serial }
                    ci.copy(serialNumbers = serials, qty = maxOf(1, serials.size))
                }
            })
        }
    }

    fun addSerial(itemIdx: Int, serial: String) {
        _uiState.update { state ->
            val cart = state.cart.toMutableList()
            val item = cart.getOrNull(itemIdx) ?: return@update state
            if (item.serialNumbers.contains(serial)) {
                return@update state.copy(serialError = "Serial \"$serial\" ထပ်ပြီးရှိနေပြီ")
            }
            cart[itemIdx] = item.copy(
                serialNumbers = item.serialNumbers + serial,
                qty           = item.serialNumbers.size + 1
            )
            state.copy(cart = cart)
        }
    }

    // ── Scan ─────────────────────────────────────────────────────────────────

    fun onProductScan(code: String) {
        _uiState.update { it.copy(showProductScanner = false, scanLoading = true) }
        val products = _uiState.value.products
        val byCode   = products.find { it.productCode.equals(code, ignoreCase = true) }
        if (byCode != null) {
            addToCart(byCode)
            _uiState.update { it.copy(scanLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.findProductBySerial(token, code)
                val found = res.body()?.data
                if (res.isSuccessful && found?.productId != null) {
                    val product = products.find { it.id == found.productId }
                    if (product != null) { addToCart(product, code) }
                    else _uiState.update { it.copy(scanError = "\"$code\" ကုန်ပစ္စည်း မတွေ့ပါ") }
                } else {
                    _uiState.update { it.copy(scanError = "\"$code\" မတွေ့ပါ") }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(scanError = "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
            _uiState.update { it.copy(scanLoading = false) }
        }
    }

    fun onSerialScan(itemIdx: Int, serial: String) {
        _uiState.update { it.copy(serialScanIdx = null) }
        addSerial(itemIdx, serial)
    }

    // ── Pickers ───────────────────────────────────────────────────────────────

    fun setCustomer(c: CustomerDTO) {
        _uiState.update { it.copy(selectedCustomer = c, creditTerm = null, creditTermLoading = true) }
        viewModelScope.launch {
            try {
                val id = c.id ?: return@launch
                val res = ApiClient.service.getCreditTerm(ApiClient.bearer(prefs.authToken), id)
                _uiState.update { it.copy(
                    creditTerm        = if (res.isSuccessful) res.body()?.data else null,
                    creditTermLoading = false
                ) }
            } catch (_: Exception) {
                _uiState.update { it.copy(creditTermLoading = false) }
            }
        }
    }
    fun setStaff(s: StaffDTO)                = _uiState.update { it.copy(selectedStaff = s) }
    fun setPayMethod(m: PaymentMethodDTO)    = _uiState.update { it.copy(selectedPayMethod = m) }
    fun setSaleDate(v: String)               = _uiState.update { it.copy(saleDate = v) }
    fun setPaidAmount(v: String)             = _uiState.update { it.copy(paidAmount = v) }
    fun setPaymentTransactionNo(v: String)   = _uiState.update { it.copy(paymentTransactionNo = v) }
    fun setOverallDiscount(v: String)        = _uiState.update { it.copy(overallDiscount = v) }
    fun setTaxAmount(v: String)              = _uiState.update { it.copy(taxAmount = v) }
    fun setRemark(v: String)                 = _uiState.update { it.copy(remark = v) }
    fun showProductScanner()                 = _uiState.update { it.copy(showProductScanner = true) }
    fun dismissProductScanner()              = _uiState.update { it.copy(showProductScanner = false) }
    fun showSerialScanner(idx: Int)          = _uiState.update { it.copy(serialScanIdx = idx) }
    fun dismissSerialScanner()               = _uiState.update { it.copy(serialScanIdx = null) }
    fun showCustomerPicker()                 = _uiState.update { it.copy(showCustomerPicker = true) }
    fun dismissCustomerPicker()              = _uiState.update { it.copy(showCustomerPicker = false) }
    fun showStaffPicker()                    = _uiState.update { it.copy(showStaffPicker = true) }
    fun dismissStaffPicker()                 = _uiState.update { it.copy(showStaffPicker = false) }
    fun showPayPicker()                      = _uiState.update { it.copy(showPayPicker = true) }
    fun dismissPayPicker()                   = _uiState.update { it.copy(showPayPicker = false) }
    fun showProductPicker()                  = _uiState.update { it.copy(showProductPicker = true) }
    fun dismissProductPicker()               = _uiState.update { it.copy(showProductPicker = false) }
    fun clearScanError()                     = _uiState.update { it.copy(scanError = null) }
    fun clearSerialError()                   = _uiState.update { it.copy(serialError = null) }

    fun addSplitPayment() {
        val s = _uiState.value
        val method = s.selectedPayMethod ?: run {
            _uiState.update { it.copy(scanError = "ငွေလက်ခံနည်း ရွေးပါ") }
            return
        }
        val amount = s.paidAmount.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            _uiState.update { it.copy(scanError = "Split amount ထည့်ပါ") }
            return
        }
        val next = s.splitPayments + PaymentTransactionDTO(
            paymentMethodId = method.id,
            paymentMethodName = method.methodName,
            amount = amount,
            transactionNo = s.paymentTransactionNo.ifBlank { null }
        )
        _uiState.update {
            it.copy(
                splitPayments = next,
                paidAmount = splitTotal(next).formatMoneyInput(),
                paymentTransactionNo = ""
            )
        }
    }

    fun removeSplitPayment(index: Int) = _uiState.update { s ->
        val next = s.splitPayments.filterIndexed { i, _ -> i != index }
        s.copy(splitPayments = next, paidAmount = if (next.isEmpty()) "" else splitTotal(next).formatMoneyInput())
    }

    // ── New customer ──────────────────────────────────────────────────────────

    fun createCustomer(name: String, phone: String, address: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.createCustomer(
                    token,
                    CustomerDTO(
                        name    = name,
                        phone   = phone.ifBlank { null },
                        address = address.ifBlank { null }
                    )
                )
                if (res.isSuccessful && res.body()?.data != null) {
                    val newC = res.body()!!.data!!
                    _uiState.update { it.copy(
                        customers        = it.customers + newC,
                        selectedCustomer = newC
                    ) }
                    onDone(null)
                } else {
                    onDone(res.body()?.message ?: "မအောင်မြင်ပါ")
                }
            } catch (e: Exception) {
                onDone(e.message)
            }
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    fun submit(onSuccess: (SaleDTO) -> Unit, onError: (String) -> Unit) {
        val state    = _uiState.value
        val customer = state.selectedCustomer
        if (customer == null)     { onError("Customer ရွေးပါ"); return }
        if (state.cart.isEmpty()) { onError("Item တစ်ခုမျှ မထည့်ရသေးပါ"); return }

        val saleLocalDate = try {
            LocalDate.parse(state.saleDate)
        } catch (_: Exception) {
            onError("Sale date မှန်ကန်စွာ ရွေးပါ")
            return
        }
        if (saleLocalDate.isBefore(LocalDate.now()) && !state.hasBackdatePermission) {
            onError("Back date sale သိမ်းရန် permission လိုအပ်ပါသည်")
            return
        }

        // Compute totals once (Long for validation, Double for API body)
        val grossL    = state.cart.sumOf { it.unitPrice * it.qty.toLong() }
        val lineDiscL = state.cart.filterNot { it.foc }.sumOf { it.discountAmount }
        val focAmountL = state.cart.filter { it.foc }.sumOf { it.unitPrice * it.qty.toLong() }
        val overallDL = state.overallDiscount.toLongOrNull() ?: 0L
        val taxL      = maxOf(0L, state.taxAmount.toLongOrNull() ?: 0L)
        val netL      = maxOf(0L, grossL - focAmountL - lineDiscL - overallDL) + taxL
        val splitPayments = normalizePayments(state.splitPayments)
        val paidL     = if (splitPayments.isNotEmpty()) splitTotal(splitPayments).toLong() else (state.paidAmount.toLongOrNull() ?: netL)
        val dueL      = maxOf(0L, netL - paidL)

        // Credit checks
        if (dueL > 0) {
            if (customer.blacklisted == true) {
                onError("🚫 Blacklist ဖောက်သည် — အကြွေးဖြင့် ရောင်းချ၍ မရပါ\n(Cash sale ကိုသာ ခွင့်ပြုသည်)")
                return
            }
            if (customer.creditHold == true) {
                onError("⚠ Credit Hold — ဤဖောက်သည်၏ အကြွေးကို ရပ်ဆိုင်းထားသည်\n(Cash sale ကိုသာ ခွင့်ပြုသည်)")
                return
            }
            val term = state.creditTerm
            if (term == null) {
                onError("❌ ဤဖောက်သည်အတွက် Credit Terms မသတ်မှတ်ရသေးပါ\nSystem မှ Admin ထံ ဆက်သွယ်ပါ (Cash sale ကိုသာ ခွင့်ပြုသည်)")
                return
            }
            if (term.creditAllowed != true) {
                onError("🚫 ဤဖောက်သည်အား Credit ခွင့်မပြုပါ\n(Cash sale ကိုသာ ခွင့်ပြုသည်)")
                return
            }
        }

        for (item in state.cart) {
            if (item.product.hasSerial == true && item.serialNumbers.size != item.qty) {
                onError("\"${item.product.name}\" — Serial ${item.qty} ခု လိုအပ်သည် (${item.serialNumbers.size}/${item.qty})")
                return
            }
        }

        val gross    = grossL.toDouble()
        val lineDisc = lineDiscL.toDouble()
        val overallD = overallDL.toDouble()
        val tax       = taxL.toDouble()
        val subtotal = maxOf(0.0, gross - focAmountL.toDouble() - lineDisc)
        val net      = maxOf(0.0, subtotal - overallD) + tax
        val paid     = if (splitPayments.isNotEmpty()) splitTotal(splitPayments) else (state.paidAmount.toDoubleOrNull() ?: net)
        val due      = maxOf(0.0, net - paid)

        // Compute dueDate from credit term (today + creditDays)
        val dueDateStr: String? = if (due > 0) {
            val days = state.creditTerm?.creditDays ?: 30
            saleLocalDate.plusDays(days.toLong()).toString()
        } else null

        val body = SaleDTO(
            customerId      = state.selectedCustomer.id,
            staffId         = state.selectedStaff?.id,
            saleDate        = "${state.saleDate}T00:00:00",
            totalAmount     = gross,
            discountAmount  = overallD,
            taxAmount       = tax,
            netAmount       = net,
            paidAmount      = paid,
            dueAmount       = due,
            paymentMethodId = if (paid > 0.0) (splitPayments.firstOrNull()?.paymentMethodId ?: state.selectedPayMethod?.id) else null,
            payments        = splitPayments.ifEmpty { null },
            transactionNo   = state.paymentTransactionNo.ifBlank { null },
            dueDate         = dueDateStr,
            remark          = state.remark.ifBlank { null },
            details         = state.cart.map { item ->
                SaleItemDTO(
                    productId      = item.product.id,
                    productName    = item.product.name,
                    qty            = item.qty,
                    unitPrice      = item.unitPrice.toDouble(),
                    subtotal       = if (item.foc) 0.0 else (item.unitPrice * item.qty - item.discountAmount).toDouble(),
                    discountAmount = item.discountAmount.toDouble(),
                    foc            = item.foc,
                    warrantyMonths = item.product.warrantyMonths,
                    serialNumbers  = item.serialNumbers.ifEmpty { null }
                )
            }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.createSale(token, body)
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(submitting = false) }
                    onSuccess(res.body()!!.data!!)
                } else {
                    _uiState.update { it.copy(submitting = false) }
                    onError(res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false) }
                onError(e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း")
            }
        }
    }

    data class NewSaleUiState(
        val loading:           Boolean                = true,
        val products:          List<ProductDTO>       = emptyList(),
        val customers:         List<CustomerDTO>      = emptyList(),
        val staffList:         List<StaffDTO>         = emptyList(),
        val paymentMethods:    List<PaymentMethodDTO> = emptyList(),
        val cart:              List<CartItem>          = emptyList(),
        val selectedCustomer:  CustomerDTO?            = null,
        val selectedStaff:     StaffDTO?               = null,
        val selectedPayMethod: PaymentMethodDTO?       = null,
        val splitPayments:    List<PaymentTransactionDTO> = emptyList(),
        val saleDate:          String                  = LocalDate.now().toString(),
        val hasBackdatePermission: Boolean             = false,
        val creditTerm:        CustomerCreditTermDTO?  = null,
        val creditTermLoading: Boolean                 = false,
        val paidAmount:        String               = "",
        val paymentTransactionNo: String            = "",
        val overallDiscount:   String               = "",
        val taxAmount:         String               = "",
        val remark:            String               = "",
        val submitting:        Boolean              = false,
        val showProductScanner:Boolean              = false,
        val showProductPicker: Boolean              = false,
        val showCustomerPicker:Boolean              = false,
        val showStaffPicker:   Boolean              = false,
        val showPayPicker:     Boolean              = false,
        val serialScanIdx:     Int?                 = null,
        val serialSelectProduct: ProductDTO?        = null,
        val serialSelectOptions: List<ProductSerialDTO> = emptyList(),
        val serialSelectLoading: Boolean            = false,
        val serialSelectError: String?              = null,
        val scanLoading:       Boolean              = false,
        val scanError:         String?              = null,
        val serialError:       String?              = null
    )
}

private fun normalizePayments(payments: List<PaymentTransactionDTO>): List<PaymentTransactionDTO> =
    payments
        .mapNotNull { p ->
            val methodId = p.paymentMethodId ?: 0
            val amount = p.amount ?: 0.0
            if (methodId <= 0 || amount <= 0.0) null else p.copy(amount = amount, transactionNo = p.transactionNo?.ifBlank { null })
        }

private fun splitTotal(payments: List<PaymentTransactionDTO>): Double =
    payments.sumOf { it.amount ?: 0.0 }

private fun Double.formatMoneyInput(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
