package org.sspd.servicemgmt.customerportaloptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPromoValidateRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPromoCode;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPromoRedemption;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerPromoCodeRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerPromoRedemptionRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPromoService;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerPromoServiceTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewUsesServerMathAndCapsPercent() {
        Product product = product(11, "10000.00");
        CustomerPromoCode promo = promo("SAVE50", "PERCENT", "50.00", "1000.00", "1.00");
        CustomerPromoService svc = service(promo, product, 0, 0);

        var quote = svc.preview(request("save50", 11, 1));
        assertEquals("SAVE50", quote.getPromoCode());
        assertEquals(new BigDecimal("1000.00"), quote.getDiscountAmount());
        assertEquals(new BigDecimal("10000.00"), quote.getEligibleSubtotal());
    }

    @Test
    void previewRejectsBelowMinimumAndExpired() {
        Product product = product(11, "500.00");
        CustomerPromoCode promo = promo("MIN", "FIXED", "100.00", null, "1000.00");
        CustomerPromoService svc = service(promo, product, 0, 0);
        IllegalStateException min = assertThrows(IllegalStateException.class, () -> svc.preview(request("MIN", 11, 1)));
        assertTrue(min.getMessage().contains("အနည်းဆုံး"));

        promo.setMinOrderAmount(null);
        promo.setEndsAt(LocalDateTime.now().minusDays(1));
        IllegalStateException expired = assertThrows(IllegalStateException.class, () -> svc.preview(request("MIN", 11, 1)));
        assertTrue(expired.getMessage().contains("သက်တမ်း"));
    }

    @Test
    void usageAndPerCustomerLimitsBlockPreview() {
        Product product = product(11, "5000.00");
        CustomerPromoCode promo = promo("ONCE", "FIXED", "500.00", null, null);
        promo.setUsageLimit(1);
        promo.setPerCustomerLimit(1);
        CustomerPromoService usedUp = service(promo, product, 1, 0);
        assertThrows(IllegalStateException.class, () -> usedUp.preview(request("ONCE", 11, 1)));

        CustomerPromoService perCustomer = service(promo, product, 0, 1);
        assertThrows(IllegalStateException.class, () -> perCustomer.preview(request("ONCE", 11, 1)));
    }

    @Test
    void productScopeAndRelease() {
        Product allowed = product(11, "2000.00");
        Product other = product(12, "2000.00");
        CustomerPromoCode promo = promo("LAPTOP", "FIXED", "200.00", null, null);
        promo.getProducts().add(allowed);
        CustomerPromoService svc = service(promo, allowed, 0, 0);
        ProductRepository products = (ProductRepository) ReflectionTestUtils.getField(svc, "products");
        when(products.findWithDetailsById(12)).thenReturn(Optional.of(other));

        assertEquals(new BigDecimal("200.00"), svc.preview(request("LAPTOP", 11, 1)).getDiscountAmount());
        assertThrows(IllegalStateException.class, () -> svc.preview(request("LAPTOP", 12, 1)));

        CustomerPromoRedemption row = CustomerPromoRedemption.builder().id(3).released(false).orderId(42).build();
        CustomerPromoRedemptionRepository redemptions = (CustomerPromoRedemptionRepository) ReflectionTestUtils.getField(svc, "redemptions");
        when(redemptions.findByOrderId(42)).thenReturn(Optional.of(row));
        svc.release(42);
        assertTrue(row.isReleased());
    }

    private CustomerPromoService service(CustomerPromoCode promo, Product product, long used, long mine) {
        asCustomer(7);
        CustomerPromoCodeRepository promos = mock(CustomerPromoCodeRepository.class);
        when(promos.findByCode(promo.getCode())).thenReturn(Optional.of(promo));
        when(promos.findLockedByCode(promo.getCode())).thenReturn(Optional.of(promo));
        CustomerPromoRedemptionRepository redemptions = mock(CustomerPromoRedemptionRepository.class);
        when(redemptions.countByPromo_IdAndReleasedFalse(1)).thenReturn(used);
        when(redemptions.countByPromo_IdAndCustomer_IdAndReleasedFalse(1, 7)).thenReturn(mine);
        when(redemptions.save(any(CustomerPromoRedemption.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductRepository products = mock(ProductRepository.class);
        when(products.findWithDetailsById(product.getId())).thenReturn(Optional.of(product));
        CustomerPromoService svc = mock(CustomerPromoService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(svc, "promos", promos);
        ReflectionTestUtils.setField(svc, "redemptions", redemptions);
        ReflectionTestUtils.setField(svc, "products", products);
        ReflectionTestUtils.setField(svc, "categories", mock(CategoryRepository.class));
        ReflectionTestUtils.setField(svc, "objectMapper", new ObjectMapper());
        return svc;
    }

    private static CustomerPromoValidateRequest request(String code, int productId, int qty) {
        CustomerPromoValidateRequest req = new CustomerPromoValidateRequest();
        req.setCode(code);
        CustomerPromoValidateRequest.Line line = new CustomerPromoValidateRequest.Line();
        line.setProductId(productId);
        line.setQty(qty);
        req.setLines(List.of(line));
        return req;
    }

    private static CustomerPromoCode promo(String code, String type, String value, String max, String min) {
        return CustomerPromoCode.builder()
                .id(1)
                .code(code)
                .name(code)
                .active(true)
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(7))
                .discountType(type)
                .discountValue(new BigDecimal(value))
                .maxDiscount(max == null ? null : new BigDecimal(max))
                .minOrderAmount(min == null ? null : new BigDecimal(min))
                .build();
    }

    private static Product product(int id, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName("P" + id);
        product.setSellingPrice(new BigDecimal(price));
        product.setArchived(false);
        return product;
    }

    private static void asCustomer(int id) {
        var details = new CustomerPortalUserDetails("customer:id:" + id, "", true, List.of(), 0, id, "T", "09");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
