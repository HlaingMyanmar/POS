package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerOrderDeliveryRules;

import static org.junit.jupiter.api.Assertions.*;

class CustomerOrderDeliveryRulesTest {

    private CustomerOrder delivery(String status, String pay, CustomerOrderStatus orderStatus) {
        return CustomerOrder.builder()
                .orderType("DELIVERY")
                .deliveryStatus(status)
                .paymentState(pay)
                .status(orderStatus)
                .shippingState("ACCEPTED")
                .customerReceiptState("NONE")
                .build();
    }

    @Test
    void shopCanRequireFullPaymentForOwnDelivery() {
        CustomerOrder order = delivery("PENDING", "DEPOSIT_PAID", CustomerOrderStatus.CONFIRMED);
        order.setDeliveryHandler("OWN");
        order.setFullPaymentRequired(true);
        assertTrue(CustomerOrderDeliveryRules.requiresFullTransfer(order));
        assertFalse(CustomerOrderDeliveryRules.dispatchReady(order));
        order.setPaymentState("PAID");
        assertTrue(CustomerOrderDeliveryRules.dispatchReady(order));
    }
    @Test
    void externalDeliveryRequiresFullPaymentAndAllowsRemainderBeforeDispatch() {
        CustomerOrder order = delivery("PACKED", "DEPOSIT_PAID", CustomerOrderStatus.CONFIRMED);
        order.setDeliveryHandler("HANDOFF");
        assertFalse(CustomerOrderDeliveryRules.dispatchReady(order));
        assertTrue(CustomerOrderDeliveryRules.canCollectRemainder(order));
        assertThrows(IllegalStateException.class,
                () -> CustomerOrderDeliveryRules.assertStatusTransition(order, "HANDED_TO_RIDER"));
        order.setPaymentState("REMAINDER_CHECKING");
        assertFalse(CustomerOrderDeliveryRules.dispatchReady(order));
        order.setPaymentState("PAID");
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, "HANDED_TO_RIDER"));
    }

    @Test
    void outsourcedDeliveryEndsAtRiderHandoff() {
        CustomerOrder order = delivery("PACKED", "PAID", CustomerOrderStatus.CONFIRMED);
        order.setDeliveryHandler("HANDOFF");
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, "HANDED_TO_RIDER"));
        order.setDeliveryStatus("HANDED_TO_RIDER");
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, "HANDED_TO_RIDER"));
        assertThrows(IllegalStateException.class,
                () -> CustomerOrderDeliveryRules.assertStatusTransition(order, "OUT_FOR_DELIVERY"));
        order.setDeliveryHandler("OWN");
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, "OUT_FOR_DELIVERY"));
    }

    @Test
    void cannotSkipFromPendingToDelivered() {
        CustomerOrder order = delivery("PENDING", "PAID", CustomerOrderStatus.CONFIRMED);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CustomerOrderDeliveryRules.assertStatusTransition(order, "DELIVERED"));
        assertTrue(ex.getMessage().contains("တစ်ဆင့်"));
    }

    @Test
    void cannotSkipPackingToHandedToRider() {
        CustomerOrder order = delivery("PACKING", "PAID", CustomerOrderStatus.CONFIRMED);
        assertThrows(IllegalStateException.class,
                () -> CustomerOrderDeliveryRules.assertStatusTransition(order, "HANDED_TO_RIDER"));
    }

    @Test
    void cannotDispatchUnpaid() {
        CustomerOrder unpaid = delivery("PENDING", "AWAITING_PAYMENT", CustomerOrderStatus.CONFIRMED);
        assertThrows(IllegalStateException.class,
                () -> CustomerOrderDeliveryRules.assertStatusTransition(unpaid, "PACKING"));
    }

    @Test
    void allowsDispatchWhenDepositPaidRemainderOnArrival() {
        CustomerOrder deposit = delivery("PENDING", "DEPOSIT_PAID", CustomerOrderStatus.CONFIRMED);
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(deposit, "PACKING"));
        deposit.setDeliveryStatus("PACKING");
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(deposit, "PACKED"));
    }

    @Test
    void cannotChangeCancelledOrder() {
        CustomerOrder order = delivery("PENDING", "PAID", CustomerOrderStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> CustomerOrderDeliveryRules.assertMutable(order));
        assertThrows(IllegalStateException.class,
                () -> CustomerOrderDeliveryRules.assertStatusTransition(order, "PACKING"));
    }

    @Test
    void cannotRewindAfterCustomerConfirmedReceipt() {
        CustomerOrder order = delivery("DELIVERED", "FULFILLED", CustomerOrderStatus.CONFIRMED);
        order.setCustomerReceiptState("CONFIRMED");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CustomerOrderDeliveryRules.assertStatusTransition(order, "IN_TRANSIT"));
        assertTrue(ex.getMessage().contains("လက်ခံ"));
    }

    @Test
    void allowsOneStepForwardWhenPaid() {
        CustomerOrder order = delivery("PENDING", "PAID", CustomerOrderStatus.CONFIRMED);
        for (String next : new String[]{
                "PACKING", "PACKED", "HANDED_TO_RIDER", "OUT_FOR_DELIVERY", "IN_TRANSIT", "DELIVERED"}) {
            assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, next));
            order.setDeliveryStatus(next);
        }
    }

    @Test
    void allowsOneStepBackBeforeCustomerConfirms() {
        CustomerOrder order = delivery("IN_TRANSIT", "FULFILLED", CustomerOrderStatus.CONFIRMED);
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, "OUT_FOR_DELIVERY"));
        order.setDeliveryStatus("HANDED_TO_RIDER");
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, "PACKED"));
    }

    @Test
    void sameStatusIsNoOp() {
        CustomerOrder order = delivery("OUT_FOR_DELIVERY", "PAID", CustomerOrderStatus.CONFIRMED);
        assertDoesNotThrow(() -> CustomerOrderDeliveryRules.assertStatusTransition(order, "OUT_FOR_DELIVERY"));
    }

    @Test
    void awaitingReceiptStartsWhenHandedToRider() {
        assertFalse(CustomerOrderDeliveryRules.awaitingCustomerReceipt("PACKING"));
        assertFalse(CustomerOrderDeliveryRules.awaitingCustomerReceipt("PACKED"));
        assertTrue(CustomerOrderDeliveryRules.awaitingCustomerReceipt("HANDED_TO_RIDER"));
        assertTrue(CustomerOrderDeliveryRules.awaitingCustomerReceipt("OUT_FOR_DELIVERY"));
        assertTrue(CustomerOrderDeliveryRules.awaitingCustomerReceipt("DELIVERED"));
    }

    @Test
    void remainderOnArrivalAfterRiderHandoff() {
        CustomerOrder packing = delivery("PACKING", "DEPOSIT_PAID", CustomerOrderStatus.CONFIRMED);
        assertFalse(CustomerOrderDeliveryRules.canCollectRemainder(packing));
        CustomerOrder handed = delivery("HANDED_TO_RIDER", "DEPOSIT_PAID", CustomerOrderStatus.CONFIRMED);
        assertTrue(CustomerOrderDeliveryRules.canCollectRemainder(handed));
        CustomerOrder pickup = CustomerOrder.builder()
                .orderType("PICKUP")
                .paymentState("DEPOSIT_PAID")
                .status(CustomerOrderStatus.CONFIRMED)
                .build();
        assertTrue(CustomerOrderDeliveryRules.canCollectRemainder(pickup));
    }
}
