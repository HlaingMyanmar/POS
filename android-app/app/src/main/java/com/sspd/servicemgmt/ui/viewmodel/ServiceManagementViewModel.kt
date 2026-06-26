package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ServiceItemDTO
import com.sspd.servicemgmt.api.ServiceTypeDTO
import com.sspd.servicemgmt.api.SubServiceTypeDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServiceManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token  = ApiClient.bearer(prefs.authToken)
                val typesD = async { ApiClient.service.getServiceTypes(token) }
                val itemsD = async { ApiClient.service.getAllServiceItems(token) }
                _uiState.update {
                    it.copy(
                        types   = typesD.await().body()?.data ?: emptyList(),
                        items   = itemsD.await().body()?.data ?: emptyList(),
                        loading = false
                    )
                }
            } catch (_: Exception) { _uiState.update { it.copy(loading = false) } }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }

    // ══════════════════════════════════════════════════════════════════════════
    // SERVICE TYPE CRUD
    // ══════════════════════════════════════════════════════════════════════════

    fun openAddTypeDialog()              = _uiState.update { it.copy(typeDialog = TypeDialog(null)) }
    fun openEditTypeDialog(t: ServiceTypeDTO) = _uiState.update { it.copy(typeDialog = TypeDialog(t)) }
    fun closeTypeDialog()                = _uiState.update { it.copy(typeDialog = null, actionError = null) }

    fun saveType(name: String, description: String, active: Boolean) {
        val target = _uiState.value.typeDialog?.target
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val dto   = ServiceTypeDTO(id = target?.id, name = name.trim(), description = description.trim().ifBlank { null }, isActive = active)
                val res   = if (target?.id != null)
                    ApiClient.service.updateServiceType(token, target.id, dto)
                else
                    ApiClient.service.createServiceType(token, dto)

                if (res.isSuccessful && res.body()?.data != null) {
                    val saved = res.body()!!.data!!
                    _uiState.update { s ->
                        s.copy(
                            types         = if (target?.id != null) s.types.map { if (it.id == target.id) saved else it }
                                            else s.types + saved,
                            saving        = false,
                            typeDialog    = null,
                            actionSuccess = if (target != null) "ပြင်ဆင်မှု အောင်မြင်ပြီး" else "ထည့်သွင်းမှု အောင်မြင်ပြီး"
                        )
                    }
                } else {
                    _uiState.update { it.copy(saving = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun confirmDeleteType(t: ServiceTypeDTO) = _uiState.update { it.copy(deleteTypeTarget = t) }
    fun cancelDeleteType()                   = _uiState.update { it.copy(deleteTypeTarget = null, actionError = null) }

    fun deleteType() {
        val target = _uiState.value.deleteTypeTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, actionError = null) }
            try {
                val res = ApiClient.service.deleteServiceType(ApiClient.bearer(prefs.authToken), target.id!!)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(
                        types            = it.types.filter { t -> t.id != target.id },
                        saving           = false,
                        deleteTypeTarget = null,
                        actionSuccess    = "ဖျက်မှု အောင်မြင်ပြီး"
                    ) }
                } else {
                    _uiState.update { it.copy(saving = false, actionError = "ဖျက်မှု မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SERVICE ITEM CRUD
    // ══════════════════════════════════════════════════════════════════════════

    fun openAddItemDialog() = _uiState.update { it.copy(itemDialog = ItemDialog(null)) }
    fun openEditItemDialog(item: ServiceItemDTO) {
        _uiState.update { it.copy(itemDialog = ItemDialog(item)) }
        item.serviceTypeId?.let { loadSubTypes(it) }
    }
    fun closeItemDialog() = _uiState.update { it.copy(itemDialog = null, subTypes = emptyList(), actionError = null) }

    fun loadSubTypes(typeId: Int) {
        viewModelScope.launch {
            try {
                val res = ApiClient.service.getSubServiceTypes(ApiClient.bearer(prefs.authToken), typeId)
                _uiState.update { it.copy(subTypes = res.body()?.data ?: emptyList()) }
            } catch (_: Exception) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUB-SERVICE TYPE CRUD
    // ══════════════════════════════════════════════════════════════════════════

    fun openSubTypeSheet(parent: ServiceTypeDTO) {
        _uiState.update { it.copy(subTypeSheetParent = parent, subTypes = emptyList()) }
        parent.id?.let { loadSubTypes(it) }
    }

    fun closeSubTypeSheet() =
        _uiState.update { it.copy(subTypeSheetParent = null, subTypeDialog = null, actionError = null) }

    fun openAddSubDialog() =
        _uiState.update { it.copy(subTypeDialog = SubTypeDialog(null)) }

    fun openEditSubDialog(sub: SubServiceTypeDTO) =
        _uiState.update { it.copy(subTypeDialog = SubTypeDialog(sub)) }

    fun closeSubDialog() =
        _uiState.update { it.copy(subTypeDialog = null, actionError = null) }

    fun saveSubType(name: String, description: String, active: Boolean) {
        val parent = _uiState.value.subTypeSheetParent ?: return
        val target = _uiState.value.subTypeDialog?.target
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val dto   = SubServiceTypeDTO(
                    id            = target?.id,
                    name          = name.trim(),
                    description   = description.trim().ifBlank { null },
                    isActive      = active,
                    serviceTypeId = parent.id
                )
                val res = if (target?.id != null)
                    ApiClient.service.updateSubServiceType(token, target.id, dto)
                else
                    ApiClient.service.createSubServiceType(token, dto)

                if (res.isSuccessful && res.body()?.data != null) {
                    val saved = res.body()!!.data!!
                    _uiState.update { s ->
                        s.copy(
                            subTypes      = if (target?.id != null)
                                s.subTypes.map { if (it.id == target.id) saved else it }
                            else s.subTypes + saved,
                            saving        = false,
                            subTypeDialog = null,
                            actionSuccess = if (target != null) "ပြင်ဆင်မှု အောင်မြင်ပြီး" else "ထည့်သွင်းမှု အောင်မြင်ပြီး"
                        )
                    }
                } else {
                    _uiState.update { it.copy(saving = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun confirmDeleteSub(sub: SubServiceTypeDTO) =
        _uiState.update { it.copy(deleteSubTarget = sub) }

    fun cancelDeleteSub() =
        _uiState.update { it.copy(deleteSubTarget = null, actionError = null) }

    fun deleteSub() {
        val target = _uiState.value.deleteSubTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, actionError = null) }
            try {
                val res = ApiClient.service.deleteSubServiceType(ApiClient.bearer(prefs.authToken), target.id!!)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(
                        subTypes       = it.subTypes.filter { s -> s.id != target.id },
                        saving         = false,
                        deleteSubTarget = null,
                        actionSuccess  = "ဖျက်မှု အောင်မြင်ပြီး"
                    ) }
                } else {
                    _uiState.update { it.copy(saving = false, actionError = "ဖျက်မှု မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun saveItem(item: String, price: Double, typeId: Int, subTypeId: Int?, active: Boolean) {
        val target = _uiState.value.itemDialog?.target
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val dto   = ServiceItemDTO(
                    id               = target?.id,
                    item             = item.trim(),
                    price            = price,
                    serviceTypeId    = typeId,
                    subServiceTypeId = subTypeId,
                    isActive         = active
                )
                val res = if (target?.id != null)
                    ApiClient.service.updateServiceItem(token, target.id, dto)
                else
                    ApiClient.service.createServiceItem(token, dto)

                if (res.isSuccessful && res.body()?.data != null) {
                    val saved = res.body()!!.data!!
                    _uiState.update { s ->
                        s.copy(
                            items         = if (target?.id != null) s.items.map { if (it.id == target.id) saved else it }
                                            else s.items + saved,
                            saving        = false,
                            itemDialog    = null,
                            subTypes      = emptyList(),
                            actionSuccess = if (target != null) "ပြင်ဆင်မှု အောင်မြင်ပြီး" else "ထည့်သွင်းမှု အောင်မြင်ပြီး"
                        )
                    }
                } else {
                    _uiState.update { it.copy(saving = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun confirmDeleteItem(item: ServiceItemDTO) = _uiState.update { it.copy(deleteItemTarget = item) }
    fun cancelDeleteItem()                      = _uiState.update { it.copy(deleteItemTarget = null, actionError = null) }

    fun deleteItem() {
        val target = _uiState.value.deleteItemTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, actionError = null) }
            try {
                val res = ApiClient.service.deleteServiceItem(ApiClient.bearer(prefs.authToken), target.id!!)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(
                        items            = it.items.filter { i -> i.id != target.id },
                        saving           = false,
                        deleteItemTarget = null,
                        actionSuccess    = "ဖျက်မှု အောင်မြင်ပြီး"
                    ) }
                } else {
                    _uiState.update { it.copy(saving = false, actionError = "ဖျက်မှု မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Common
    // ══════════════════════════════════════════════════════════════════════════

    fun clearActionSuccess() = _uiState.update { it.copy(actionSuccess = null) }
    fun clearActionError()   = _uiState.update { it.copy(actionError = null) }

    // ── State & Dialogs ───────────────────────────────────────────────────────

    data class TypeDialog(val target: ServiceTypeDTO?)
    data class ItemDialog(val target: ServiceItemDTO?)
    data class SubTypeDialog(val target: SubServiceTypeDTO?)

    data class UiState(
        val types:              List<ServiceTypeDTO>    = emptyList(),
        val items:              List<ServiceItemDTO>    = emptyList(),
        val subTypes:           List<SubServiceTypeDTO> = emptyList(),
        val loading:            Boolean                 = true,
        val saving:             Boolean                 = false,
        val search:             String                  = "",
        // service type dialogs
        val typeDialog:         TypeDialog?             = null,
        val itemDialog:         ItemDialog?             = null,
        // sub-type sheet + dialog
        val subTypeSheetParent: ServiceTypeDTO?         = null,
        val subTypeDialog:      SubTypeDialog?          = null,
        // delete targets
        val deleteTypeTarget:   ServiceTypeDTO?         = null,
        val deleteItemTarget:   ServiceItemDTO?         = null,
        val deleteSubTarget:    SubServiceTypeDTO?      = null,
        // feedback
        val actionSuccess:      String?                 = null,
        val actionError:        String?                 = null
    )
}
