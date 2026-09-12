package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.OrderPaymentRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderPaymentProofRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerStockReservationService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderSettlementTest {

    private CustomerOrderPaymentService service(
            CustomerOrderRepository orders,
            CustomerStockReservationService stock,
            DataEventPublisher events,
            PaymentMethodRepository methods,
            PaymentTransactionRepository paymentTransactions) {
        CustomerOrderPaymentService payments = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(payments, "orders", orders);
        ReflectionTestUtils.setField(payments, "proofs", mock(CustomerOrderPaymentProofRepository.class));
        ReflectionTestUtils.setField(payments, "stock", stock);
        ReflectionTestUtils.setField(payments, "events", events);
        ReflectionTestUtils.setField(payments, "methods", methods);
        ReflectionTestUtils.setField(payments, "paymentTransactions", paymentTransactions);
        return payments;
    }

    private CustomerOrder depositPaid() {
        Customer customer = new Customer();
        customer.setId(7);
        CustomerOrderLine line = CustomerOrderLine.builder()
                .productName("Laptop")
                .qty(1)
                .unitPrice(new BigDecimal("100000.00"))
                .subtotal(new BigDecimal("100000.00"))
                .build();
        CustomerOrder order = CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer)
                .status(CustomerOrderStatus.CONFIRMED)
                .orderType("PICKUP")
                .shippingState("LEGACY")
                .paymentState("DEPOSIT_PAID")
                .paymentChoice("TRANSFER")
                .depositPercent(new BigDecimal("30.00"))
                .depositAmount(new BigDecimal("30000.00"))
                .itemsTotal(new BigDecimal("100000.00"))
                .deliveryCharge(BigDecimal.ZERO)
                .reservationActive(true)
                .paymentMethodId(3)
                .updatedAt(LocalDateTime.now())
                .lines(List.of(line))
                .build();
        line.setOrder(order);
        return order;
    }

    @Test
    void shopCancelStillBlockedAfterDepositPaid() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrder order = depositPaid();
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        CustomerOrderPaymentService payments = service(orders, mock(CustomerStockReservationService.class),
                mock(DataEventPublisher.class), mock(PaymentMethodRepository.class), mock(PaymentTransactionRepository.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> payments.cancel(42));
        assertTrue(ex.getMessage().toLowerCase().contains("refund") || ex.getMessage().contains("Resolve"));
        assertEquals(CustomerOrderStatus.CONFIRMED, order.getStatus());
        assertEquals("DEPOSIT_PAID", order.getPaymentState());
    }

    @Test
    void forfeitKeepsDepositAndCancelsWithoutOutboundPayment() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        PaymentTransactionRepository txs = mock(PaymentTransactionRepository.class);
        CustomerOrder order = depositPaid();
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);

        OrderPaymentRequest request = new OrderPaymentRequest();
        request.setAction("FORFEIT_DEPOSIT");
        request.setNote("Customer cancelled after deposit");
        service(orders, stock, mock(DataEventPublisher.class), mock(PaymentMethodRepository.class), txs)
                .review(42, request);

        assertEquals("FORFEITED", order.getPaymentState());
        assertEquals(CustomerOrderStatus.CANCELLED, order.getStatus());
        assertEquals("FORFEIT", order.getSettlementAction());
        assertEquals(new BigDecimal("0.00"), order.getSettlementAmount());
        assertEquals(new BigDecimal("30000.00"), order.getSettlementKeptAmount());
        assertEquals("FORFEIT-CA-000042", order.getSettlementReference());
        assertNotNull(order.getSettlementRecordedBy());
        verify(stock).release(order);
        verify(txs, never()).save(any());
    }

    @Test
    void refundRecordsOutboundPaymentWithAmountChannelAndReference() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        PaymentTransactionRepository txs = mock(PaymentTransactionRepository.class);
        CustomerOrder order = depositPaid();
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        PaymentMethod wave = new PaymentMethod();
        wave.setId(8);
        wave.setActive(true);
        when(methods.findById(8)).thenReturn(Optional.of(wave));

        OrderPaymentRequest request = new OrderPaymentRequest();
        request.setAction("REFUNDED");
        request.setNote("Partial refund of remainder intent");
        request.setAmount(new BigDecimal("10000.00"));
        request.setPaymentMethodId(8);
        request.setTransactionNo("WAVE-RF-1");
        service(orders, stock, mock(DataEventPublisher.class), methods, txs).review(42, request);

        assertEquals("REFUNDED", order.getPaymentState());
        assertEquals(CustomerOrderStatus.CANCELLED, order.getStatus());
        assertEquals("REFUND", order.getSettlementAction());
        assertEquals(new BigDecimal("10000.00"), order.getSettlementAmount());
        assertEquals(new BigDecimal("20000.00"), order.getSettlementKeptAmount());
        assertEquals(8, order.getSettlementPaymentMethodId());
        assertEquals("WAVE-RF-1", order.getSettlementReference());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(txs).save(captor.capture());
        assertEquals(ReferenceType.Customer_Order, captor.getValue().getReferenceType());
        assertEquals(42, captor.getValue().getReferenceId());
        assertEquals(new BigDecimal("-10000.00"), captor.getValue().getAmount());
        assertEquals("WAVE-RF-1", captor.getValue().getTransactionNo());
        assertEquals(8, captor.getValue().getPaymentMethod().getId());
        verify(stock).release(order);
    }

    @Test
    void refundAmountAboveReceivedIsRejected() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        PaymentMethod wave = new PaymentMethod();
        wave.setId(8);
        wave.setActive(true);
        when(methods.findById(8)).thenReturn(Optional.of(wave));
        CustomerOrder order = depositPaid();
        when(orders.findLocked(42)).thenReturn(Optional.of(order));

        OrderPaymentRequest request = new OrderPaymentRequest();
        request.setAction("REFUNDED");
        request.setNote("too much");
        request.setAmount(new BigDecimal("40000.00"));
        request.setPaymentMethodId(8);
        request.setTransactionNo("WAVE-RF-2");

        assertThrows(IllegalArgumentException.class,
                () -> service(orders, mock(CustomerStockReservationService.class), mock(DataEventPublisher.class), methods,
                        mock(PaymentTransactionRepository.class)).review(42, request));
        assertEquals("DEPOSIT_PAID", order.getPaymentState());
    }
}
