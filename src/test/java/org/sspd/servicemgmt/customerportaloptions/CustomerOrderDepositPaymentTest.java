package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.OrderPaymentRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderPaymentProof;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderPaymentProofRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerStockReservationService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerOrderDepositPaymentTest {

    private CustomerOrderPaymentService service(
            CustomerOrderRepository orders,
            CustomerOrderPaymentProofRepository proofs,
            CustomerStockReservationService stock,
            DataEventPublisher events) {
        CustomerOrderPaymentService payments = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(payments, "orders", orders);
        ReflectionTestUtils.setField(payments, "proofs", proofs);
        ReflectionTestUtils.setField(payments, "stock", stock);
        ReflectionTestUtils.setField(payments, "events", events);
        return payments;
    }

    private CustomerOrder pickupWithDeposit() {
        Customer customer = new Customer();
        customer.setId(7);
        Product product = new Product();
        product.setId(11);
        product.setHasSerial(false);
        CustomerOrderLine line = CustomerOrderLine.builder()
                .product(product)
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
                .paymentState("CHECKING")
                .paymentChoice("TRANSFER")
                .depositPercent(new BigDecimal("30.00"))
                .depositAmount(new BigDecimal("30000.00"))
                .itemsTotal(new BigDecimal("100000.00"))
                .deliveryCharge(BigDecimal.ZERO)
                .reservationActive(true)
                .paymentMethodId(3)
                .latestProofId(9)
                .updatedAt(LocalDateTime.now())
                .lines(List.of(line))
                .build();
        line.setOrder(order);
        return order;
    }

    private PaymentMethod activeMethod(int id) {
        PaymentMethod method = new PaymentMethod();
        method.setId(id);
        method.setMethodName("CASH");
        method.setActive(true);
        return method;
    }

    @Test
    void remainingDueIsTotalMinusDeposit() {
        CustomerOrderPaymentService payments = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        CustomerOrder order = pickupWithDeposit();
        assertEquals(new BigDecimal("30000.00"), payments.expectedTransfer(order));
        assertEquals(new BigDecimal("70000.00"), payments.remainingDue(order));
    }

    @Test
    void approvingDepositMarksDepositPaidNotFullyPaid() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = pickupWithDeposit();
        CustomerOrderPaymentProof proof = new CustomerOrderPaymentProof();
        proof.setId(9);
        proof.setAmount(new BigDecimal("30000.00"));
        proof.setPaymentMethodId(3);
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        when(proofs.findById(9)).thenReturn(Optional.of(proof));

        OrderPaymentRequest request = new OrderPaymentRequest();
        request.setAction("APPROVE");
        request.setAmount(new BigDecimal("30000.00"));
        service(orders, proofs, stock, events).review(42, request);

        assertEquals("DEPOSIT_PAID", order.getPaymentState());
        assertNotEquals("PAID", order.getPaymentState());
        assertNull(order.getCompletedSaleId());
    }

    @Test
    void collectingRemainderMarksPaidWithSeparateChannel() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = pickupWithDeposit();
        order.setPaymentState("DEPOSIT_PAID");
        order.setLatestProofId(null);
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);

        CustomerOrderPaymentService payments = service(orders, proofs, stock, events);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        ReflectionTestUtils.setField(payments, "methods", methods);
        when(methods.findById(8)).thenReturn(Optional.of(activeMethod(8)));

        OrderPaymentRequest request = new OrderPaymentRequest();
        request.setAction("COLLECT_REMAINDER");
        request.setAmount(new BigDecimal("70000.00"));
        request.setPaymentMethodId(8);
        payments.review(42, request);

        assertEquals("PAID", order.getPaymentState());
        assertEquals(8, order.getCollectionPaymentMethodId());
        assertEquals(new BigDecimal("70000.00"), order.getCollectionAmount());
        assertEquals(3, order.getPaymentMethodId());
    }

    @Test
    void deliveryRemainderBlockedUntilGoodsAreOnTheWay() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = pickupWithDeposit();
        order.setOrderType("DELIVERY");
        order.setShippingState("ACCEPTED");
        order.setDeliveryStatus("PACKING");
        order.setPaymentState("DEPOSIT_PAID");
        order.setLatestProofId(null);
        when(orders.findLocked(42)).thenReturn(Optional.of(order));

        CustomerOrderPaymentService payments = service(orders, proofs, stock, events);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        ReflectionTestUtils.setField(payments, "methods", methods);
        when(methods.findById(8)).thenReturn(Optional.of(activeMethod(8)));

        OrderPaymentRequest request = new OrderPaymentRequest();
        request.setAction("COLLECT_REMAINDER");
        request.setAmount(new BigDecimal("70000.00"));
        request.setPaymentMethodId(8);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> payments.review(42, request));
        assertTrue(ex.getMessage().contains("ပို့"));
        assertEquals("DEPOSIT_PAID", order.getPaymentState());

        order.setDeliveryStatus("HANDED_TO_RIDER");
        when(orders.saveAndFlush(order)).thenReturn(order);
        payments.review(42, request);
        assertEquals("PAID", order.getPaymentState());
        assertEquals(new BigDecimal("70000.00"), order.getCollectionAmount());
    }

    @Test
    void wrongRemainderAmountIsRejected() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = pickupWithDeposit();
        order.setPaymentState("DEPOSIT_PAID");
        order.setLatestProofId(null);
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);

        CustomerOrderPaymentService payments = service(orders, proofs, stock, events);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        ReflectionTestUtils.setField(payments, "methods", methods);
        when(methods.findById(8)).thenReturn(Optional.of(activeMethod(8)));

        OrderPaymentRequest request = new OrderPaymentRequest();
        request.setAction("COLLECT_REMAINDER");
        request.setAmount(new BigDecimal("50000.00"));
        request.setPaymentMethodId(8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> payments.review(42, request));
        assertTrue(ex.getMessage().contains("70000"));
        assertEquals("DEPOSIT_PAID", order.getPaymentState());
        assertNull(order.getCollectionAmount());
    }

    @Test
    void fulfillBlockedUntilRemainderCollected() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        SaleService sales = mock(SaleService.class);
        CustomerOrder order = pickupWithDeposit();
        order.setPaymentState("DEPOSIT_PAID");
        when(orders.findLocked(42)).thenReturn(Optional.of(order));

        CustomerOrderPaymentService payments = service(orders, proofs, stock, events);
        ReflectionTestUtils.setField(payments, "sales", sales);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> payments.fulfill(42, new OrderPaymentRequest()));
        assertTrue(ex.getMessage().contains("ကျန်ငွေ"));
        verify(sales, never()).save(any());
        assertNull(order.getCompletedSaleId());
        assertEquals("DEPOSIT_PAID", order.getPaymentState());
    }

    @Test
    void fulfillBlockedWhenRecordedRemainderDoesNotMatchDue() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        SaleService sales = mock(SaleService.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        CustomerOrder order = pickupWithDeposit();
        order.setPaymentState("PAID");
        order.setCollectionPaymentMethodId(8);
        order.setCollectionAmount(new BigDecimal("50000.00"));
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(methods.findById(3)).thenReturn(Optional.of(activeMethod(3)));

        CustomerOrderPaymentService payments = service(orders, proofs, stock, events);
        ReflectionTestUtils.setField(payments, "sales", sales);
        ReflectionTestUtils.setField(payments, "methods", methods);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> payments.fulfill(42, new OrderPaymentRequest()));
        assertTrue(ex.getMessage().contains("70000"));
        verify(sales, never()).save(any());
    }

    @Test
    void fulfillSplitsSalePaymentsByDepositAndRemainderChannels() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        SaleService sales = mock(SaleService.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        CustomerOrder order = pickupWithDeposit();
        order.setPaymentState("PAID");
        order.setCollectionPaymentMethodId(8);
        order.setCollectionAmount(new BigDecimal("70000.00"));
        CustomerOrderPaymentProof proof = new CustomerOrderPaymentProof();
        proof.setId(9);
        proof.setTransactionReference("KBZ-DEP");
        proof.setAmount(new BigDecimal("30000.00"));
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        when(proofs.findById(9)).thenReturn(Optional.of(proof));
        when(methods.findById(3)).thenReturn(Optional.of(activeMethod(3)));
        SaleDTO saved = new SaleDTO();
        saved.setId(99);
        when(sales.save(any(SaleDTO.class))).thenReturn(saved);

        CustomerOrderPaymentService payments = service(orders, proofs, stock, events);
        ReflectionTestUtils.setField(payments, "sales", sales);
        ReflectionTestUtils.setField(payments, "methods", methods);

        Integer saleId = payments.fulfill(42, new OrderPaymentRequest());
        assertEquals(99, saleId);
        assertEquals("FULFILLED", order.getPaymentState());
        assertEquals(99, order.getCompletedSaleId());

        ArgumentCaptor<SaleDTO> sale = ArgumentCaptor.forClass(SaleDTO.class);
        verify(sales).save(sale.capture());
        List<PaymentTransactionDTO> lines = sale.getValue().getPayments();
        assertEquals(2, lines.size());
        assertEquals(3, lines.get(0).getPaymentMethodId());
        assertEquals(new BigDecimal("30000.00"), lines.get(0).getAmount());
        assertEquals(8, lines.get(1).getPaymentMethodId());
        assertEquals(new BigDecimal("70000.00"), lines.get(1).getAmount());
        assertEquals(new BigDecimal("100000.00"), sale.getValue().getPaidAmount());
    }
}
