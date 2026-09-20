package com.sspd.servicemgmt.feature.home

import com.sspd.servicemgmt.core.network.CustomerOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderPaymentPolicyTest {
    @Test
    fun `new order requests deposit when configured`() {
        val order = CustomerOrder(
            total = 100_000.0,
            depositAmount = 20_000.0,
            remainingAmount = 80_000.0,
            paymentState = "AWAITING_PAYMENT"
        )

        assertEquals(20_000.0, orderPayNowAmount(order), 0.0)
    }

    @Test
    fun `confirmed deposit requests only remaining balance`() {
        val order = CustomerOrder(
            total = 100_000.0,
            depositAmount = 20_000.0,
            remainingAmount = 80_000.0,
            paymentState = "DEPOSIT_PAID",
            orderType = "PICKUP"
        )

        assertEquals(80_000.0, orderPayNowAmount(order), 0.0)
    }

    @Test
    fun `paid and submitted remainder never request duplicate transfer`() {
        assertEquals(
            0.0,
            orderPayNowAmount(CustomerOrder(total = 100_000.0, paymentState = "PAID")),
            0.0
        )
        assertEquals(
            0.0,
            orderPayNowAmount(
                CustomerOrder(
                    total = 100_000.0,
                    depositAmount = 20_000.0,
                    remainingAmount = 80_000.0,
                    paymentState = "REMAINDER_PROOF_SUBMITTED"
                )
            ),
            0.0
        )
    }

    @Test
    fun `order without deposit requests full finite nonnegative total`() {
        assertEquals(
            55_000.0,
            orderPayNowAmount(CustomerOrder(total = 55_000.0, paymentState = "AWAITING_PAYMENT")),
            0.0
        )
        assertEquals(
            0.0,
            orderPayNowAmount(CustomerOrder(total = Double.NaN, paymentState = "AWAITING_PAYMENT")),
            0.0
        )
    }
}
