package com.sspd.servicemgmt.feature.cart

data class CartIssue(
    val kind: Kind,
    val productId: Int,
    val name: String,
    val detail: String
) {
    enum class Kind { UNAVAILABLE, OUT_OF_STOCK, STOCK_CAPPED, PRICE_CHANGED }
}
