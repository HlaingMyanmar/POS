package com.sspd.servicemgmt.feature.cart

import android.content.Context

class CartPersistence(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("customer_carts", Context.MODE_PRIVATE)

    suspend fun load(customerId: Int): List<CartItem> {
        if (customerId <= 0) return emptyList()
        val raw = preferences.getString(key(customerId), null).orEmpty()
        return CartCodec.decode(raw)
    }

    suspend fun save(customerId: Int, items: List<CartItem>) {
        if (customerId <= 0) return
        val editor = preferences.edit()
        if (items.isEmpty()) editor.remove(key(customerId))
        else editor.putString(key(customerId), CartCodec.encode(items))
        editor.apply()
    }

    suspend fun clear(customerId: Int) {
        if (customerId > 0) preferences.edit().remove(key(customerId)).apply()
    }

    private fun key(customerId: Int) = "cart_$customerId"
}