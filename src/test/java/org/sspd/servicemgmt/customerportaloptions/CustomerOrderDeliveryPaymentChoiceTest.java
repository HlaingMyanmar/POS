package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerStockReservationService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderDeliveryPaymentChoiceTest {

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private CustomerOrderPaymentService service(
            CustomerOrderRepository orders,
            CustomerStockReservationService stock,
            DataEventPublisher events,
            CompanySettingsService companySettings) {
        CustomerOrderPaymentService payments = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(payments, "orders", orders);
        ReflectionTestUtils.setField(payments, "stock", stock);
        ReflectionTestUtils.setField(payments, "events", events);
        ReflectionTestUtils.setField(payments, "companySettings", companySettings);
        return payments;
    }

    private CustomerOrder acceptedDelivery() {
        Customer customer = new Customer();
        customer.setId(7);
        CustomerOrderLine line = CustomerOrderLine.builder()
                .productName("Item")
                .qty(1)
                .unitPrice(new BigDecimal("100000.00"))
                .subtotal(new BigDecimal("100000.00"))
                .build();
        CustomerOrder order = CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer)
                .status(CustomerOrderStatus.PENDING)
                .orderType("DELIVERY")
                .shippingState("ACCEPTED")
                .paymentState("NONE")
                .paymentChoice("PENDING")
                .itemsTotal(new BigDecimal("100000.00"))
                .deliveryCharge(new BigDecimal("2000.00"))
                .reservationActive(false)
                .updatedAt(LocalDateTime.now())
                .lines(List.of(line))
                .build();
        line.setOrder(order);
        return order;
    }

    private void asCustomer(int id) {
        CustomerPortalUserDetails details = new CustomerPortalUserDetails(
                "customer:id:" + id, "", true, List.of(), 0, id, "C", "09");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @Test
    void acceptedQuoteDoesNotForcePrepaidUntilCustomerChooses() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = acceptedDelivery();
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);

        service(orders, stock, events, mock(CompanySettingsService.class)).ensureTransferOpened(42);

        assertEquals("PENDING", order.getPaymentChoice());
        assertEquals("NONE", order.getPaymentState());
        assertEquals(CustomerOrderStatus.PENDING, order.getStatus());
        verify(stock, never()).reserve(order);
    }

    @Test
    void choosingFullTransferOpensPaymentWindow() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerOrder order = acceptedDelivery();
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        asCustomer(7);

        service(orders, stock, events, mock(CompanySettingsService.class)).choosePayment(42, "TRANSFER");

        assertEquals("TRANSFER", order.getPaymentChoice());
        assertNull(order.getDepositAmount());
        assertEquals("AWAITING_PAYMENT", order.getPaymentState());
        assertEquals(CustomerOrderStatus.CONFIRMED, order.getStatus());
        verify(stock).reserve(order);
    }

    @Test
    void choosingCodAppliesDepositAndOpensTransferForDeposit() {
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CompanySettingsService companySettings = mock(CompanySettingsService.class);
        CompanySettingsDTO settings = new CompanySettingsDTO();
        settings.setPickupDepositPercent(new BigDecimal("30.00"));
        when(companySettings.getSettings()).thenReturn(settings);
        CustomerOrder order = acceptedDelivery();
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        asCustomer(7);

        service(orders, stock, events, companySettings).choosePayment(42, "PAY_ON_COLLECTION");

        assertEquals("PAY_ON_COLLECTION", order.getPaymentChoice());
        assertEquals(new BigDecimal("30000.00"), order.getDepositAmount());
        assertEquals("AWAITING_PAYMENT", order.getPaymentState());
        assertEquals(CustomerOrderStatus.CONFIRMED, order.getStatus());
        verify(stock).reserve(order);
    }
}
