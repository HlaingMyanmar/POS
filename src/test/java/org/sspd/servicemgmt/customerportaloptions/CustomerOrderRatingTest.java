package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingModerateRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderRating;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRatingRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderRatingService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerOrderRatingTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rateRequiresConfirmedReceiptThenAllowsOneEditWindow() {
        CustomerOrderRatingService svc = service(order("CONFIRMED"), Optional.empty());
        CustomerOrderRatingRequest req = new CustomerOrderRatingRequest();
        req.setProductRating(5);
        req.setServiceRating(4);
        req.setComment("ကောင်းပါတယ်");

        var dto = svc.rate(42, req);
        assertEquals(5, dto.getProductRating());
        assertEquals(4, dto.getServiceRating());
        assertEquals(5, dto.getRating());
        assertEquals("ကောင်းပါတယ်", dto.getComment());
        assertEquals(42, dto.getOrderId());
        assertTrue(dto.isEditable());
    }

    @Test
    void rateRejectedUntilReceiptConfirmed() {
        CustomerOrderRatingService svc = service(order("NONE"), Optional.empty());
        CustomerOrderRatingRequest req = new CustomerOrderRatingRequest();
        req.setProductRating(5);
        req.setServiceRating(5);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.rate(42, req));
        assertTrue(ex.getMessage().contains("လက်ခံအတည်ပြု"));
    }

    @Test
    void cannotEditAfterWindow() {
        CustomerOrderRating existing = CustomerOrderRating.builder()
                .id(1)
                .orderId(42)
                .customer(customer())
                .productRating(3)
                .serviceRating(3)
                .rating(3)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(10))
                .editableUntil(LocalDateTime.now().minusDays(1))
                .build();
        CustomerOrderRatingService svc = service(order("CONFIRMED"), Optional.of(existing));
        CustomerOrderRatingRequest req = new CustomerOrderRatingRequest();
        req.setProductRating(5);
        req.setServiceRating(5);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.rate(42, req));
        assertTrue(ex.getMessage().contains("ပြင်နိုင်သောကာလ"));
    }

    @Test
    void shopCanHideAndUnhide() {
        CustomerOrderRating existing = CustomerOrderRating.builder()
                .id(8)
                .orderId(42)
                .customer(customer())
                .productRating(2)
                .serviceRating(2)
                .rating(2)
                .review("spam")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .editableUntil(LocalDateTime.now().plusDays(7))
                .build();
        CustomerOrderRatingRepository ratings = mock(CustomerOrderRatingRepository.class);
        when(ratings.findById(8)).thenReturn(Optional.of(existing));
        when(ratings.saveAndFlush(any(CustomerOrderRating.class))).thenAnswer(inv -> inv.getArgument(0));
        CustomerOrderRatingService svc = mock(CustomerOrderRatingService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(svc, "ratings", ratings);
        ReflectionTestUtils.setField(svc, "orders", mock(CustomerOrderRepository.class));
        ReflectionTestUtils.setField(svc, "events", mock(DataEventPublisher.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));

        CustomerOrderRatingModerateRequest hide = new CustomerOrderRatingModerateRequest();
        hide.setHidden(true);
        hide.setReason("မသင့်လျော်");
        var hidden = svc.moderate(8, hide);
        assertTrue(hidden.isHidden());
        assertEquals("admin", hidden.getHiddenBy());
        assertEquals("မသင့်လျော်", hidden.getHideReason());

        CustomerOrderRatingModerateRequest show = new CustomerOrderRatingModerateRequest();
        show.setHidden(false);
        var visible = svc.moderate(8, show);
        assertFalse(visible.isHidden());
        assertNull(visible.getHiddenBy());
    }

    private CustomerOrderRatingService service(CustomerOrder order, Optional<CustomerOrderRating> existing) {
        Customer customer = customer();
        asCustomer(customer);
        CustomerOrderRatingRepository ratings = mock(CustomerOrderRatingRepository.class);
        when(ratings.findByOrderId(42)).thenReturn(existing);
        when(ratings.saveAndFlush(any(CustomerOrderRating.class))).thenAnswer(inv -> {
            CustomerOrderRating r = inv.getArgument(0);
            if (r.getId() == null) r.setId(1);
            return r;
        });
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findByIdWithLines(42)).thenReturn(Optional.of(order));
        SaleRepository sales = mock(SaleRepository.class);
        when(sales.findById(9)).thenReturn(Optional.of(Sale.builder().id(9).saleCode("S-9").customer(customer).build()));

        CustomerOrderRatingService svc = mock(CustomerOrderRatingService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(svc, "ratings", ratings);
        ReflectionTestUtils.setField(svc, "orders", orders);
        ReflectionTestUtils.setField(svc, "sales", sales);
        ReflectionTestUtils.setField(svc, "events", mock(DataEventPublisher.class));
        ReflectionTestUtils.setField(svc, "editDays", 7);
        return svc;
    }

    private static CustomerOrder order(String receipt) {
        return CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer())
                .status(CustomerOrderStatus.CONFIRMED)
                .completedSaleId(9)
                .customerReceiptState(receipt)
                .build();
    }

    private static Customer customer() {
        Customer customer = new Customer();
        customer.setId(7);
        customer.setName("Test");
        return customer;
    }

    private static void asCustomer(Customer customer) {
        var details = new CustomerPortalUserDetails("customer:id:7", "", true, List.of(), 0, customer.getId(), "T", "09");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
