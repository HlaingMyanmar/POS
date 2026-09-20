package com.sspd.servicemgmt.feature.cart

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sspd.servicemgmt.core.network.CatalogProduct

object CartCodec {
    private val gson = Gson()
    private val listType = object : TypeToken<List<PersistedCartLine>>() {}.type

    fun encode(items: List<CartItem>): String = gson.toJson(items.map {
        PersistedCartLine(product = it.product, qty = it.qty, savedPrice = it.savedPrice)
    })

    fun decode(raw: String): List<CartItem> {
        if (raw.isBlank()) return emptyList()
        val lines = runCatching {
            gson.fromJson<List<PersistedCartLine>>(raw, listType)
        }.getOrNull().orEmpty()
        return lines.mapNotNull { line ->
            val product = line.product ?: return@mapNotNull null
            if (product.id <= 0 || line.qty <= 0) return@mapNotNull null
            CartItem(
                product = product,
                qty = line.qty,
                savedPrice = line.savedPrice ?: product.sellingPrice ?: 0.0
            )
        }
    }
}

private data class PersistedCartLine(
    val product: CatalogProduct? = null,
    val qty: Int = 0,
    val savedPrice: Double? = null
)
