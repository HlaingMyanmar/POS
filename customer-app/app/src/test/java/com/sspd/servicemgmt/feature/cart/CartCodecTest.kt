package com.sspd.servicemgmt.feature.cart

import com.sspd.servicemgmt.core.network.CatalogProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartCodecTest {
    @Test
    fun `round trip preserves account cart quantities and acknowledged price`() {
        val item = CartItem(
            product = CatalogProduct(id = 42, name = "Phone", sellingPrice = 125_000.0, stockQty = 5),
            qty = 3,
            savedPrice = 120_000.0
        )

        val restored = CartCodec.decode(CartCodec.encode(listOf(item)))

        assertEquals(1, restored.size)
        assertEquals(42, restored.single().product.id)
        assertEquals(3, restored.single().qty)
        assertEquals(120_000.0, restored.single().savedPrice, 0.0)
    }

    @Test
    fun `corrupt and invalid persisted lines are discarded`() {
        assertTrue(CartCodec.decode("not-json").isEmpty())
        assertTrue(
            CartCodec.decode(
                """[{"product":{"id":0,"name":"bad"},"qty":1},{"product":{"id":2},"qty":0}]"""
            ).isEmpty()
        )
    }

    @Test
    fun `legacy line without saved price falls back to product price`() {
        val restored = CartCodec.decode(
            """[{"product":{"id":9,"sellingPrice":3500.0,"stockQty":2},"qty":2}]"""
        )

        assertEquals(3500.0, restored.single().savedPrice, 0.0)
    }
}
