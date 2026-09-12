package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalNotificationDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.OrderPaymentRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CustomerOrderStatusNotificationTest {

    private CustomerPortalService portal(
            CustomerOrderRepository repository,
            CustomerOrderPaymentService orderPayments,
            DataEventPublisher publisher) {
        CustomerPortalService service = mock(CustomerPortalService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "orderRepository", repository);
        ReflectionTestUtils.setField(service, "orderPayments", orderPayments);
        ReflectionTestUtils.setField(service, "dataEventPublisher", publisher);
        ReflectionTestUtils.setField(service, "paymentMethodRepository",
                mock(org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository.class));
        ReflectionTestUtils.setField(service, "saleRepository",
                mock(org.sspd.servicemgmt.saleoptions.repository.SaleRepository.class));
        ReflectionTestUtils.setField(service, "orderRatings",
                mock(org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderRatingService.class));
        ReflectionTestUtils.setField(service, "promos",
                mock(org.sspd.servicemgmt.customerportaloptions.service.CustomerPromoService.class));
        var milestones = mock(org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryMilestoneRepository.class);
        when(milestones.findByOrderIdOrderByIdAsc(any())).thenReturn(List.of());
        when(milestones.findByOrderIdInOrderByIdAsc(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "deliveryMilestones", milestones);
        return service;
    }

    private CustomerOrder pendingOrder() {
        Customer customer = new Customer();
        customer.setId(7);
        customer.setName("Customer");
        customer.setPhone("09123");
        return CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer)
                .status(CustomerOrderStatus.PENDING)
                .paymentState("NONE")
                .paymentChoice("TRANSFER")
                .orderType("PICKUP")
                .shippingState("LEGACY")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void acceptingOrderNotifiesOwnerOnceAndMakesNotificationAvailableAfterReconnect() {
        CustomerOrderRepository repository = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentService orderPayments = mock(CustomerOrderPaymentService.class);
        DataEventPublisher publisher = mock(DataEventPublisher.class);
        CustomerOrder order = pendingOrder();
        when(repository.findByIdWithLines(42)).thenReturn(Optional.of(order));
        when(orderPayments.shippingTimeline(42)).thenReturn(List.of());
        doAnswer(invocation -> {
            if (order.getStatus() == CustomerOrderStatus.CONFIRMED && order.isReservationActive()) {
                return null;
            }
            order.setStatus(CustomerOrderStatus.CONFIRMED);
            order.setReservationActive(true);
            order.setPaymentState("AWAITING_PAYMENT");
            CustomerPortalNotificationDTO dto = new CustomerPortalNotificationDTO();
            dto.setId(-42);
            dto.setOrderId(42);
            dto.setOrderNo("CA-000042");
            dto.setStatus("AWAITING_PAYMENT");
            dto.setChannel("CUSTOMER_ORDER");
            dto.setNote("CA-000042 — အော်ဒါကို လက်ခံပြီး ပစ္စည်းဖယ်ထားပါပြီ။ Customer က Channel ရွေးပြီး ငွေလွှဲပါ။");
            publisher.publishToUser("customer:id:7", "/topic/customer-orders", dto);
            return null;
        }).when(orderPayments).reserve(eq(42), any(OrderPaymentRequest.class));

        CustomerPortalService service = portal(repository, orderPayments, publisher);
        CustomerPortalOrderDTO confirmed = service.updateStatus(42, CustomerOrderStatus.CONFIRMED);
        assertEquals(CustomerOrderStatus.CONFIRMED, confirmed.getStatus());

        var payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishToUser(eq("customer:id:7"), eq("/topic/customer-orders"), payload.capture());
        CustomerPortalNotificationDTO notification = (CustomerPortalNotificationDTO) payload.getValue();
        assertEquals(42, notification.getOrderId());
        assertEquals(-42, notification.getId());
        assertEquals("AWAITING_PAYMENT", notification.getStatus());
        assertTrue(notification.getNote().contains("CA-000042"));

        service.updateStatus(42, CustomerOrderStatus.CONFIRMED);
        verify(orderPayments, times(2)).reserve(eq(42), any(OrderPaymentRequest.class));
        verify(publisher, times(1)).publishToUser(anyString(), anyString(), any());

        var serviceNotifications = mock(org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobNotificationRepository.class);
        ReflectionTestUtils.setField(service, "serviceJobNotificationRepository", serviceNotifications);
        when(serviceNotifications.findByServiceJob_Customer_IdOrderByNotifiedAtDesc(7)).thenReturn(List.of());
        when(repository.findByCustomer_IdOrderByIdDesc(7)).thenReturn(List.of(order));
        var details = new org.sspd.servicemgmt.jwt.CustomerPortalUserDetails(
                "customer:id:7", "", true, List.of(), 1, 7, "Customer", "09123");
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                details, null, List.of()));
        try {
            var history = service.myNotifications();
            assertEquals(1, history.size());
            assertEquals("CONFIRMED", history.get(0).getStatus());
            assertEquals(42, history.get(0).getOrderId());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void cancellingOrderNotifiesOwnerWithCancelMessage() {
        CustomerOrderRepository repository = mock(CustomerOrderRepository.class);
        CustomerOrderPaymentService orderPayments = mock(CustomerOrderPaymentService.class);
        DataEventPublisher publisher = mock(DataEventPublisher.class);
        CustomerOrder order = pendingOrder();
        when(repository.findByIdWithLines(42)).thenReturn(Optional.of(order));
        when(orderPayments.shippingTimeline(42)).thenReturn(List.of());
        doAnswer(invocation -> {
            order.setStatus(CustomerOrderStatus.CANCELLED);
            CustomerPortalNotificationDTO dto = new CustomerPortalNotificationDTO();
            dto.setId(-42);
            dto.setOrderId(42);
            dto.setOrderNo("CA-000042");
            dto.setStatus("CANCELLED");
            dto.setChannel("CUSTOMER_ORDER");
            dto.setNote("CA-000042 — အော်ဒါကို ပယ်ဖျက်လိုက်ပါသည်။");
            publisher.publishToUser("customer:id:7", "/topic/customer-orders", dto);
            return null;
        }).when(orderPayments).cancel(42);

        CustomerPortalService service = portal(repository, orderPayments, publisher);
        assertEquals(CustomerOrderStatus.CANCELLED, service.updateStatus(42, CustomerOrderStatus.CANCELLED).getStatus());

        var payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishToUser(eq("customer:id:7"), eq("/topic/customer-orders"), payload.capture());
        CustomerPortalNotificationDTO notification = (CustomerPortalNotificationDTO) payload.getValue();
        assertEquals("CANCELLED", notification.getStatus());
        assertTrue(notification.getNote().contains("ပယ်ဖျက်"));
        verify(orderPayments).cancel(42);
        verify(orderPayments, never()).reserve(any(), any());
    }
}
