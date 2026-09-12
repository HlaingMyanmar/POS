package com.sspd.servicemgmt.feature.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.network.SupplierDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ComposeModuleViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)
    private val _state = MutableStateFlow(MasterUiState())
    val state: StateFlow<MasterUiState> = _state.asStateFlow()

    var moduleId: String = "suppliers"
        private set

    private var editingId: Int? = null

    fun bind(id: String) {
        moduleId = id
        load()
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, editorTitle = null, infoBody = null, metrics = emptyList()) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val api = ApiClient.service
                when (moduleId) {
                    "suppliers" -> {
                        val data = api.getSuppliers(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "ပေးသွင်းသူများ",
                                loading = false,
                                canCreate = true,
                                rows = data.map { s ->
                                    ListRow(
                                        id = s.id.toString(),
                                        title = s.name,
                                        subtitle = listOfNotNull(s.code, s.phone).joinToString(" · "),
                                        trailing = s.currentBalance?.let { b -> "%,.0f".format(b) } ?: "",
                                    )
                                }
                            )
                        }
                    }
                    "staff" -> {
                        val data = api.getStaffs(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "ဝန်ထမ်းများ",
                                loading = false,
                                canCreate = true,
                                rows = data.map { s ->
                                    ListRow(s.id.toString(), s.name, listOfNotNull(s.role, s.phone).joinToString(" · "))
                                }
                            )
                        }
                    }
                    "payment-methods" -> {
                        val data = api.getPaymentMethods(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "ငွေပေးချေနည်း",
                                loading = false,
                                canCreate = true,
                                rows = data.map { p ->
                                    ListRow(p.id.toString(), p.methodName, if (p.active) "ဖွင့်" else "ပိတ်")
                                }
                            )
                        }
                    }
                    "coa" -> {
                        val data = api.getChartOfAccounts(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "စာရင်းဇယား (COA)",
                                loading = false,
                                rows = data.map { a ->
                                    ListRow(a.id?.toString() ?: "", a.accountName ?: "-", a.accountType ?: "", a.code ?: "")
                                }
                            )
                        }
                    }
                    "users" -> {
                        val data = api.getUsers(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "အသုံးပြုသူများ",
                                loading = false,
                                rows = data.map { u ->
                                    ListRow(
                                        u.id?.toString() ?: u.username,
                                        u.username,
                                        listOfNotNull(u.email, u.staffName, u.roles.joinToString()).joinToString(" · "),
                                        if (u.isActive) "Active" else "Off"
                                    )
                                }
                            )
                        }
                    }
                    "roles" -> {
                        val data = api.getRoles(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "အခန်းကဏ္ဍ / ခွင့်ပြုချက်",
                                loading = false,
                                rows = data.map { r ->
                                    ListRow(r.id?.toString() ?: r.name, r.name, r.description ?: "")
                                }
                            )
                        }
                    }
                    "quotations" -> {
                        val data = api.getQuotations(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "ကိုးကားချက်",
                                loading = false,
                                rows = data.map { q ->
                                    ListRow(
                                        q.id?.toString() ?: "",
                                        q.quotationCode ?: "QT",
                                        listOfNotNull(q.customerName, q.status, q.quotationDate).joinToString(" · "),
                                        q.netAmount?.let { n -> "%,.0f".format(n) } ?: ""
                                    )
                                }
                            )
                        }
                    }
                    "videos" -> {
                        val data = api.getVideoCatalog(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "ဗီဒီယိုများ",
                                loading = false,
                                rows = data.map { v ->
                                    ListRow(v.id.toString(), v.title ?: "Video", v.category ?: v.targetAudience ?: "")
                                }
                            )
                        }
                    }
                    "customer-history" -> {
                        val customers = api.getCustomers(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = "ဖောက်သည် မှတ်တမ်း",
                                loading = false,
                                rows = customers.map { c ->
                                    ListRow(c.id?.toString() ?: "", c.name, listOfNotNull(c.phone, c.address).joinToString(" · "))
                                }
                            )
                        }
                    }
                    "profit-loss" -> {
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val cal = Calendar.getInstance()
                        val to = fmt.format(cal.time)
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        val from = fmt.format(cal.time)
                        val report = api.getProfitLoss(token, from, to).body()?.data
                        _state.update {
                            it.copy(
                                title = "အမြတ်အရှုံး",
                                loading = false,
                                metrics = listOf(
                                    "ကာလ" to "$from → $to",
                                    "အသားတင်ရောင်း" to money(report?.netRevenue),
                                    "အသားတင်ဝယ်" to money(report?.netPurchases),
                                    "အကြမ်းအမြတ်" to money(report?.grossProfit),
                                    "ကုန်ကျ" to money(report?.totalExpenses),
                                    "အသားတင်အမြတ်" to money(report?.netProfit),
                                )
                            )
                        }
                    }
                    "trial-balance", "balance-sheet" -> {
                        val data = api.getAccountBalances(token).body()?.data.orEmpty()
                        _state.update {
                            it.copy(
                                title = if (moduleId == "trial-balance") "စမ်းသပ်လက်ကျန်" else "လက်ကျန်ရှင်းတမ်း",
                                loading = false,
                                rows = data.map { b ->
                                    ListRow(
                                        b.id?.toString() ?: b.accountId?.toString() ?: "",
                                        b.accountName ?: "-",
                                        "FY ${b.fiscalYear ?: "-"}",
                                        "%,.0f".format(b.currentBalance ?: b.openingBalance ?: 0.0)
                                    )
                                }
                            )
                        }
                    }
                    "accounting-dashboard" -> {
                        val stats = api.getStats(token).body()?.data
                        _state.update {
                            it.copy(
                                title = "စာရင်းကိုင် ဒက်ရှ်ဘုတ်",
                                loading = false,
                                metrics = listOf(
                                    "ယနေ့ရောင်း" to money(stats?.todaySalesAmount?.toDouble()),
                                    "ရောင်းကြိမ်" to (stats?.todaySalesCount?.toString() ?: "0"),
                                    "ကုန်နည်း" to (stats?.lowStockCount?.toString() ?: "0"),
                                    "ဆိုင်းငံ့အလုပ်" to (stats?.pendingServiceJobs?.toString() ?: "0"),
                                    "ကျော် AR" to money(stats?.totalOverdueAR?.toDouble()),
                                )
                            )
                        }
                    }
                    "company" -> {
                        val c = api.getCompanySettings(token).body()?.data
                        _state.update {
                            it.copy(
                                title = "ကုမ္ပဏီ ဆက်တင်",
                                loading = false,
                                metrics = listOf(
                                    "အမည်" to (c?.companyName ?: "-"),
                                    "ဖုန်း" to (c?.companyPhone ?: "-"),
                                    "အီးမေးလ်" to (c?.companyEmail ?: "-"),
                                    "လိပ်စာ" to (c?.companyAddress ?: "-"),
                                    "ဘောင်ချာခေါင်း" to (c?.invoiceTitle ?: "-"),
                                    "အကြွေးဖြင့်ပေးပို့" to if (c?.serviceAllowDeliveryWithDue == true) "ခွင့်" else "ပိတ်",
                                )
                            )
                        }
                    }
                    "service-help" -> _state.update {
                        it.copy(
                            title = "ဝန်ဆောင်မှု အကူအညီ",
                            loading = false,
                            infoBody = "၁။ Booking လက်ခံ → ၂။ Service Job ဖွင့် → ၃။ Technician assign → ၄။ အလုပ်မှတ်တမ်း → ၅။ Final check → ၆။ ငွေရှင်း / ပေးပို့။\n\nOutdoor job အတွက် Technician app မှ visit start / arrive / end သုံးပါ။"
                        )
                    }
                    "backup" -> _state.update {
                        it.copy(
                            title = "Backup",
                            loading = false,
                            infoBody = "ဒေတာဘေ့စ် backup / restore ကို ဆာဗာကွန်ပျူတာမှ သုံးပါ။ ဖုန်း Compose မှာ ဖိုင်အရွယ်အစားကြီးသော restore မထည့်ထားပါ။"
                        )
                    }
                    "voucher" -> _state.update {
                        it.copy(
                            title = "ဘောင်ချာ ဆက်တင်",
                            loading = false,
                            infoBody = "ဘောင်ချာ layout ကို Company / Voucher settings မှ ကွန်ပျူတာ Web မှာ ဒီဇိုင်းဆွဲပါ။ ပုံနှိပ်ခြင်းကို အက်ပ်ထဲက Print ခလုတ်များက သုံးပြီးသား ဖြစ်သည်။"
                        )
                    }
                    "admin-queries" -> _state.update {
                        it.copy(
                            title = "Admin Query",
                            loading = false,
                            infoBody = "SQL admin query ကို ဖုန်း Compose မှာ မထည့်ထားပါ။ ကွန်ပျူတာ Web POS မှသာ သုံးပါ။"
                        )
                    }
                    else -> _state.update { it.copy(title = "Module", loading = false, error = "မသိသော module") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "ဒေတာ မဖတ်နိုင်ပါ") }
            }
        }
    }

    fun openCreate() {
        editingId = null
        _state.update {
            it.copy(editorTitle = "အသစ်", fieldA = "", fieldB = "", fieldC = "", toggle = true)
        }
    }

    fun openEdit(row: ListRow) {
        editingId = row.id.toIntOrNull()
        _state.update {
            it.copy(editorTitle = "ပြင်ဆင်", fieldA = row.title, fieldB = row.subtitle, fieldC = "", toggle = true)
        }
    }

    fun closeEditor() = _state.update { it.copy(editorTitle = null) }

    fun setFieldA(v: String) = _state.update { it.copy(fieldA = v) }
    fun setFieldB(v: String) = _state.update { it.copy(fieldB = v) }
    fun setToggle(v: Boolean) = _state.update { it.copy(toggle = v) }

    fun save() {
        val name = _state.value.fieldA.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "အမည် ဖြည့်ပါ") }
            return
        }
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val api = ApiClient.service
                val id = editingId
                when (moduleId) {
                    "suppliers" -> {
                        val body = SupplierDTO(id = id ?: 0, name = name, phone = _state.value.fieldB.trim().ifBlank { null })
                        if (id == null || id == 0) api.createSupplier(token, body.copy(id = 0))
                        else api.updateSupplier(token, id, body)
                    }
                    "staff" -> {
                        val body = StaffDTO(id = id ?: 0, name = name, phone = _state.value.fieldB.trim().ifBlank { null }, role = "TECH", isActive = _state.value.toggle)
                        if (id == null || id == 0) api.createStaff(token, body.copy(id = 0))
                        else api.updateStaff(token, id, body)
                    }
                    "payment-methods" -> {
                        val body = PaymentMethodDTO(id = id ?: 0, methodName = name, active = _state.value.toggle)
                        if (id == null || id == 0) api.createPaymentMethod(token, body.copy(id = 0))
                        else api.updatePaymentMethod(token, id, body)
                    }
                    else -> return@launch
                }
                closeEditor()
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "သိမ်းမရပါ") }
            }
        }
    }

    private fun money(v: Double?) = "%,.0f".format(v ?: 0.0)
}
