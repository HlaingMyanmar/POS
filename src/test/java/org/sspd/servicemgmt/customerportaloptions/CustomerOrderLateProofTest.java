package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderPaymentProof;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderPaymentProofRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerStockReservationService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerOrderLateProofTest {

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiredOrderProofGoesToLateReviewWithoutReservingStock() throws Exception {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentProofRepository proofs = mock(CustomerOrderPaymentProofRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);

        Customer customer = new Customer();
        customer.setId(7);
        CustomerOrderLine line = CustomerOrderLine.builder()
                .productName("Item")
                .qty(1)
                .unitPrice(new BigDecimal("10000.00"))
                .subtotal(new BigDecimal("10000.00"))
                .build();
        CustomerOrder order = CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer)
                .status(CustomerOrderStatus.CANCELLED)
                .orderType("PICKUP")
                .shippingState("LEGACY")
                .paymentState("EXPIRED")
                .paymentChoice("TRANSFER")
                .itemsTotal(new BigDecimal("10000.00"))
                .deliveryCharge(BigDecimal.ZERO)
                .reservationActive(false)
                .paymentMethodId(3)
                .updatedAt(LocalDateTime.now())
                .lines(List.of(line))
                .build();
        line.setOrder(order);
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        when(proofs.findByPaymentMethodIdAndTransactionReference(any(), any())).thenReturn(Optional.empty());
        when(proofs.saveAndFlush(any(CustomerOrderPaymentProof.class))).thenAnswer(invocation -> {
            CustomerOrderPaymentProof proof = invocation.getArgument(0);
            proof.setId(99);
            return proof;
        });

        CustomerPortalUserDetails details = new CustomerPortalUserDetails(
                "customer:id:7", "", true, List.of(), 0, 7, "C", "09");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));

        CustomerOrderPaymentService payments = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(payments, "orders", orders);
        ReflectionTestUtils.setField(payments, "proofs", proofs);
        ReflectionTestUtils.setField(payments, "stock", stock);
        ReflectionTestUtils.setField(payments, "events", events);
        ReflectionTestUtils.setField(payments, "methods", methods);

        MockMultipartFile image = new MockMultipartFile("image", "proof.png", "image/png", pngBytes());
        payments.submit(42, "TXN-LATE-1", new BigDecimal("10000.00"), image, 3);

        assertEquals("LATE_REVIEW", order.getPaymentState());
        assertEquals(CustomerOrderStatus.CANCELLED, order.getStatus());
        assertEquals(99, order.getLatestProofId());
        assertFalse(order.isReservationActive());
        ArgumentCaptor<CustomerOrderPaymentProof> saved = ArgumentCaptor.forClass(CustomerOrderPaymentProof.class);
        verify(proofs).saveAndFlush(saved.capture());
        assertEquals("LATE_REVIEW", saved.getValue().getReviewState());
        assertEquals(42, saved.getValue().getOrderId());
        assertArrayEquals(image.getBytes(), saved.getValue().getImageData());
        verify(stock, never()).reserve(any());
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
