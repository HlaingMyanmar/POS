package com.sspd.servicemgmt.feature.cart

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sspd.servicemgmt.core.network.CatalogProduct

class CartPersistence(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("customer_carts", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<PersistedCartLine>>() {}.type

    suspend fun load(customerId: Int): List<CartItem> {
        if (customerId <= 0) return emptyList()
        val raw = preferences.getString(key(customerId), null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val lines = runCatching { gson.fromJson<List<PersistedCartLine>>(raw, listType) }.getOrNull().orEmpty()
        return lines.mapNotNull { line ->
            val product = line.product ?: return@mapNotNull null
            if (product.id <= 0 || line.qty <= 0) return@mapNotNull null
            CartItem(product = product, qty = line.qty, savedPrice = line.savedPrice ?: product.sellingPrice ?: 0.0)
        }
    }

    suspend fun save(customerId: Int, items: List<CartItem>) {
        if (customerId <= 0) return
        val editor = preferences.edit()
        if (items.isEmpty()) editor.remove(key(customerId))
        else editor.putString(key(customerId), gson.toJson(items.map {
            PersistedCartLine(product = it.product, qty = it.qty, savedPrice = it.savedPrice)
        }))
        editor.apply()
    }

    suspend fun clear(customerId: Int) {
        if (customerId > 0) preferences.edit().remove(key(customerId)).apply()
    }

    private fun key(customerId: Int) = "cart_$customerId"
}

private data class PersistedCartLine(
    val product: CatalogProduct? = null,
    val qty: Int = 0,
    val savedPrice: Double? = null
)