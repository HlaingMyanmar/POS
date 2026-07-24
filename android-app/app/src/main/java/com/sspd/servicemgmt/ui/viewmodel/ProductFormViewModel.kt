package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.BrandDTO
import com.sspd.servicemgmt.api.CategoryDTO
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.api.UnitDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductFormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val productId: Int? = savedStateHandle.get<Int>("productId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    val isEdit get() = productId != null

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val brandsD = async { ApiClient.service.getBrands(token) }
                val catsD = async { ApiClient.service.getCategoryTree(token) }
                val unitsD = async { ApiClient.service.getUnits(token) }
                val productD = productId?.let { async { ApiClient.service.getProduct(token, it) } }

                val brands = brandsD.await().body()?.data?.filter { it.isActive } ?: emptyList()
                val categories = flattenCategories(catsD.await().body()?.data ?: emptyList())
                val units = unitsD.await().body()?.data?.filter { it.isActive } ?: emptyList()

                val product = productD?.await()?.body()?.data
                _uiState.update { s ->
                    if (product != null) {
                        s.copy(
                            brands = brands,
                            categories = categories,
                            units = units,
                            name = product.name,
                            hasSerial = product.hasSerial != false,
                            stockQty = (product.stockQty).toString(),
                            productType = product.productType.ifBlank { "New" },
                            sellingPrice = product.sellingPrice.toString(),
                            costPrice = (product.costPrice ?: 0.0).toString(),
                            reorderLevel = (product.reorderLevel ?: 0).toString(),
                            warrantyMonths = (product.warrantyMonths ?: 0).toString(),
                            warrantyTerms = product.warrantyTerms.orEmpty(),
                            remark = product.remark.orEmpty(),
                            selectedBrand = brands.find { it.id == product.brandId || it.name == product.brandName },
                            selectedCategory = categories.find { it.id == product.categoryId || it.name == product.categoryName },
                            selectedUnit = units.find { it.id == product.unitId || unitLabel(it) == product.unitName },
                            photoBase64 = product.photoBase64,
                            loading = false
                        )
                    } else {
                        s.copy(brands = brands, categories = categories, units = units, loading = false)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "ဒေတာ မဖတ်နိုင်ပါ") }
            }
        }
    }

    private fun flattenCategories(nodes: List<CategoryDTO>, level: Int = 0): List<CategoryDTO> =
        nodes.flatMap { node ->
            val label = if (level == 0) node.name else "${"  ".repeat(level)}↳ ${node.name}"
            listOf(node.copy(name = label)) + flattenCategories(node.children ?: emptyList(), level + 1)
        }

    private fun unitLabel(unit: UnitDTO): String =
        unit.unitName?.takeIf { it.isNotBlank() } ?: unit.name

    private fun decimalInput(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        return if (firstDot < 0) filtered
        else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
    fun setName(v: String) = _uiState.update { it.copy(name = v, error = null) }
    fun setHasSerial(v: Boolean) = _uiState.update { it.copy(hasSerial = v) }
    fun setStockQty(v: String) = _uiState.update { it.copy(stockQty = v.filter(Char::isDigit)) }
    fun setProductType(v: String) = _uiState.update { it.copy(productType = v) }
    fun setSellingPrice(v: String) = _uiState.update { it.copy(sellingPrice = decimalInput(v)) }
    fun setCostPrice(v: String) = _uiState.update { it.copy(costPrice = decimalInput(v)) }
    fun setReorderLevel(v: String) = _uiState.update { it.copy(reorderLevel = v.filter(Char::isDigit)) }
    fun setWarrantyMonths(v: String) = _uiState.update { it.copy(warrantyMonths = v.filter(Char::isDigit)) }
    fun setWarrantyTerms(v: String) = _uiState.update { it.copy(warrantyTerms = v) }
    fun setRemark(v: String) = _uiState.update { it.copy(remark = v) }
    fun selectBrand(v: BrandDTO) = _uiState.update { it.copy(selectedBrand = v) }
    fun selectCategory(v: CategoryDTO) = _uiState.update { it.copy(selectedCategory = v) }
    fun selectUnit(v: UnitDTO) = _uiState.update { it.copy(selectedUnit = v) }
    fun setPhotoBase64(v: String?) = _uiState.update { it.copy(photoBase64 = v, photoChanged = true) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun createBrand(name: String, onCreated: () -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank() || _uiState.value.creatingBrand) return
        viewModelScope.launch {
            _uiState.update { it.copy(creatingBrand = true, error = null) }
            try {
                val res = ApiClient.service.createBrand(
                    ApiClient.bearer(prefs.authToken),
                    BrandDTO(name = cleanName, isActive = true)
                )
                val created = res.body()?.data
                if (res.isSuccessful && created != null) {
                    _uiState.update { s ->
                        val nextBrands = (s.brands + created).distinctBy { it.id ?: it.name.lowercase() }
                        s.copy(brands = nextBrands, selectedBrand = created, creatingBrand = false)
                    }
                    onCreated()
                } else {
                    _uiState.update { it.copy(creatingBrand = false, error = res.body()?.message ?: "Brand အသစ်ထည့်မရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(creatingBrand = false, error = e.message ?: "Brand အသစ်ထည့်မရပါ") }
            }
        }
    }

    fun createCategory(name: String, onCreated: () -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank() || _uiState.value.creatingCategory) return
        viewModelScope.launch {
            _uiState.update { it.copy(creatingCategory = true, error = null) }
            try {
                val res = ApiClient.service.createCategory(
                    ApiClient.bearer(prefs.authToken),
                    CategoryDTO(name = cleanName, isActive = true)
                )
                val created = res.body()?.data
                if (res.isSuccessful && created != null) {
                    _uiState.update { s ->
                        val nextCategories = (s.categories + created).distinctBy { it.id ?: it.name.lowercase() }
                        s.copy(categories = nextCategories, selectedCategory = created, creatingCategory = false)
                    }
                    onCreated()
                } else {
                    _uiState.update { it.copy(creatingCategory = false, error = res.body()?.message ?: "အမျိုးအစား အသစ်ထည့်မရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(creatingCategory = false, error = e.message ?: "အမျိုးအစား အသစ်ထည့်မရပါ") }
            }
        }
    }

    fun save(onSuccess: (Int) -> Unit) {
        val s = _uiState.value
        if (s.name.isBlank()) { _uiState.update { it.copy(error = "ကုန်ပစ္စည်းအမည် ဖြည့်ပါ") }; return }
        if (s.selectedBrand == null) { _uiState.update { it.copy(error = "Brand ရွေးပါ") }; return }
        if (s.selectedCategory == null) { _uiState.update { it.copy(error = "အမျိုးအစား ရွေးပါ") }; return }
        if (s.selectedUnit == null) { _uiState.update { it.copy(error = "Unit ရွေးပါ") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val body = ProductDTO(
                    id = productId ?: 0,
                    name = s.name.trim(),
                    hasSerial = s.hasSerial,
                    stockQty = if (s.hasSerial) 0 else s.stockQty.toIntOrNull() ?: 0,
                    productType = s.productType,
                    sellingPrice = s.sellingPrice.toDoubleOrNull() ?: 0.0,
                    costPrice = s.costPrice.toDoubleOrNull(),
                    categoryId = s.selectedCategory.id,
                    brandId = s.selectedBrand.id,
                    unitId = s.selectedUnit.id,
                    reorderLevel = s.reorderLevel.toIntOrNull() ?: 0,
                    warrantyMonths = s.warrantyMonths.toIntOrNull() ?: 0,
                    warrantyTerms = s.warrantyTerms.ifBlank { null },
                    remark = s.remark.ifBlank { null }
                )
                val res = if (productId != null) ApiClient.service.updateProduct(token, productId, body)
                else ApiClient.service.createProduct(token, body)
                val saved = res.body()?.data
                if (res.isSuccessful && saved != null) {
                    if (s.photoChanged) {
                        ApiClient.service.updateProductPhoto(token, saved.id, mapOf("photoBase64" to (s.photoBase64 ?: "")))
                    }
                    _uiState.update { it.copy(saving = false) }
                    onSuccess(saved.id)
                } else {
                    _uiState.update { it.copy(saving = false, error = res.body()?.message ?: "သိမ်းမရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, error = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = productId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, error = null) }
            try {
                val res = ApiClient.service.deleteProduct(ApiClient.bearer(prefs.authToken), id)
                if (res.isSuccessful) onDeleted()
                else _uiState.update { it.copy(error = res.body()?.message ?: "ဖျက်မရပါ (${res.code()})") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
            _uiState.update { it.copy(deleting = false) }
        }
    }

    data class ProductFormUiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val deleting: Boolean = false,
        val creatingBrand: Boolean = false,
        val creatingCategory: Boolean = false,
        val error: String? = null,
        val brands: List<BrandDTO> = emptyList(),
        val categories: List<CategoryDTO> = emptyList(),
        val units: List<UnitDTO> = emptyList(),
        val name: String = "",
        val hasSerial: Boolean = true,
        val stockQty: String = "0",
        val productType: String = "New",
        val sellingPrice: String = "",
        val costPrice: String = "0",
        val reorderLevel: String = "0",
        val warrantyMonths: String = "0",
        val warrantyTerms: String = "",
        val remark: String = "",
        val selectedBrand: BrandDTO? = null,
        val selectedCategory: CategoryDTO? = null,
        val selectedUnit: UnitDTO? = null,
        val photoBase64: String? = null,
        val photoChanged: Boolean = false
    )
}
