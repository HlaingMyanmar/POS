package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalNotificationDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerStockReservationService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderPaymentExpiryTest {

    private CustomerOrderPaymentService service(
            CustomerOrderRepository orders,
            CustomerStockReservationService stock,
            DataEventPublisher events) {
        CustomerOrderPaymentService payments = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(payments, "orders", orders);
        ReflectionTestUtils.setField(payments, "stock", stock);
        ReflectionTestUtils.setField(payments, "events", events);
        return payments;
    }

    private CustomerOrder unpaid(String paymentState) {
        Customer customer = new Customer();
        customer.setId(7);
        return CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer)
                .status(CustomerOrderStatus.CONFIRMED)
                .paymentState(paymentState)
                .reservationActive(true)
                .reservationExpiresAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void unpaidTransferWindowCancelsOrderAndAsksReorder() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = unpaid("AWAITING_PAYMENT");
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);

        service(orders, stock, events).expire(42);

        assertEquals(CustomerOrderStatus.CANCELLED, order.getStatus());
        assertEquals("EXPIRED", order.getPaymentState());
        verify(stock).release(order);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publishToUser(eq("customer:id:7"), eq("/topic/customer-orders"), payload.capture());
        CustomerPortalNotificationDTO note = (CustomerPortalNotificationDTO) payload.getValue();
        assertTrue(note.getNote().contains("ပြန်မှာယူ"));
        assertTrue(note.getNote().contains("အထောက်အထားတင်"));
    }

    @Test
    void collectionHoldExpiryReleasesStockWithoutCancelling() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = unpaid("AWAITING_COLLECTION");
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);

        service(orders, stock, events).expire(42);

        assertEquals(CustomerOrderStatus.CONFIRMED, order.getStatus());
        assertEquals("EXPIRED", order.getPaymentState());
        verify(stock).release(order);
    }

    @Test
    void approvingLateReviewReopensCancelledOrderAndReservesStock() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderPaymentProofRepository proofs =
                mock(org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderPaymentProofRepository.class);
        Customer customer = new Customer();
        customer.setId(7);
        org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine line =
                org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine.builder()
                .productName("Item")
                .qty(1)
                .unitPrice(new java.math.BigDecimal("10000.00"))
                .subtotal(new java.math.BigDecimal("10000.00"))
                .build();
        CustomerOrder order = CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer)
                .status(CustomerOrderStatus.CANCELLED)
                .orderType("PICKUP")
                .shippingState("LEGACY")
                .paymentState("LATE_REVIEW")
                .paymentChoice("TRANSFER")
                .itemsTotal(new java.math.BigDecimal("10000.00"))
                .deliveryCharge(java.math.BigDecimal.ZERO)
                .reservationActive(false)
                .paymentMethodId(3)
                .latestProofId(9)
                .updatedAt(LocalDateTime.now())
                .lines(java.util.List.of(line))
                .build();
        line.setOrder(order);
        org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderPaymentProof proof =
                new org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderPaymentProof();
        proof.setId(9);
        proof.setAmount(new java.math.BigDecimal("10000.00"));
        proof.setPaymentMethodId(3);
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        when(proofs.findById(9)).thenReturn(Optional.of(proof));

        CustomerOrderPaymentService payments = service(orders, stock, events);
        ReflectionTestUtils.setField(payments, "proofs", proofs);
        org.sspd.servicemgmt.customerportaloptions.dto.OrderPaymentRequest request =
                new org.sspd.servicemgmt.customerportaloptions.dto.OrderPaymentRequest();
        request.setAction("APPROVE");
        request.setAmount(new java.math.BigDecimal("10000.00"));
        payments.review(42, request);

        assertEquals(CustomerOrderStatus.CONFIRMED, order.getStatus());
        assertEquals("PAID", order.getPaymentState());
        verify(stock).reserve(order);
    }
}
