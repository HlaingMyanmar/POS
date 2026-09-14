package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customeroptions.model.Customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Lightweight guard tests for shipping decision payload fields. */
class CustomerOrderShippingDecisionGuardTest {

    @Test
    void shippingDecisionRecordCarriesRenegotiateFields() {
        var when = LocalDateTime.now().plusHours(2);
        var decision = new CustomerOrderPaymentService.ShippingDecision(
                BigDecimal.ZERO, "OWN", "reschedule note", 3, false, true, when);
        assertFalse(decision.accept());
        assertEquals(3, decision.version());
        assertEquals(when, decision.scheduledAt());
        assertTrue(decision.fullPaymentRequired());
        assertEquals("reschedule note", decision.reason());
    }

    @Test
    void orderStartsAwaitingShopForDelivery() {
        Customer customer = new Customer();
        customer.setId(1);
        CustomerOrder order = CustomerOrder.builder()
                .id(9)
                .orderNo("CA-9")
                .customer(customer)
                .status(CustomerOrderStatus.PENDING)
                .orderType("DELIVERY")
                .shippingState("AWAITING_SHOP")
                .shippingVersion(0)
                .paymentState("NONE")
                .build();
        assertEquals("AWAITING_SHOP", order.getShippingState());
        assertEquals(0, order.getShippingVersion());
        assertEquals("NONE", order.getPaymentState());
    }
}
