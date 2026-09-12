package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogProductDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerWishlist;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerWishlistRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerWishlistService;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerWishlistTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toggleAddsThenRemovesForLoggedInCustomer() {
        Product product = product(11, false);
        CustomerWishlistRepository repo = mock(CustomerWishlistRepository.class);
        when(repo.findByCustomer_IdAndProduct_Id(7, 11)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(row(product)));
        when(repo.saveAndFlush(any(CustomerWishlist.class))).thenAnswer(inv -> inv.getArgument(0));
        CustomerWishlistService svc = service(repo, product);

        var added = svc.toggle(11);
        assertTrue(added.isWishlisted());
        verify(repo).saveAndFlush(any(CustomerWishlist.class));

        var removed = svc.toggle(11);
        assertFalse(removed.isWishlisted());
        verify(repo).delete(any(CustomerWishlist.class));
    }

    @Test
    void listSkipsArchivedProductsAndStaysOnOwnAccount() {
        Product live = product(11, false);
        live.setName("Live");
        Product archived = product(12, true);
        archived.setName("Gone");
        CustomerWishlistRepository repo = mock(CustomerWishlistRepository.class);
        when(repo.findByCustomer_IdOrderByCreatedAtDesc(7)).thenReturn(List.of(row(live), row(archived)));
        CustomerPortalService portal = mock(CustomerPortalService.class);
        when(portal.toCatalogProduct(live)).thenAnswer(inv -> {
            CustomerCatalogProductDTO dto = new CustomerCatalogProductDTO();
            dto.setId(11);
            dto.setName("Live");
            return dto;
        });
        CustomerWishlistService svc = service(repo, live);
        ReflectionTestUtils.setField(svc, "portal", portal);

        var list = svc.list();
        assertEquals(1, list.size());
        assertEquals(11, list.get(0).getId());
        verify(portal, never()).toCatalogProduct(archived);
    }

    @Test
    void cannotFavoriteArchivedProduct() {
        CustomerWishlistService svc = service(mock(CustomerWishlistRepository.class), product(11, true));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.add(11));
        assertTrue(ex.getMessage().contains("ရပ်ဆိုင်း"));
    }

    private CustomerWishlistService service(CustomerWishlistRepository repo, Product product) {
        asCustomer(7);
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById(11)).thenReturn(Optional.of(product));
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findById(7)).thenReturn(Optional.of(customer(7)));
        CustomerWishlistService svc = mock(CustomerWishlistService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(svc, "wishlists", repo);
        ReflectionTestUtils.setField(svc, "products", products);
        ReflectionTestUtils.setField(svc, "customers", customers);
        ReflectionTestUtils.setField(svc, "portal", mock(CustomerPortalService.class));
        return svc;
    }

    private static CustomerWishlist row(Product product) {
        return CustomerWishlist.builder()
                .id(1)
                .customer(customer(7))
                .product(product)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static Product product(int id, boolean archived) {
        Product p = new Product();
        p.setId(id);
        p.setName("P" + id);
        p.setArchived(archived);
        return p;
    }

    private static Customer customer(int id) {
        Customer c = new Customer();
        c.setId(id);
        c.setName("C" + id);
        return c;
    }

    private static void asCustomer(int id) {
        var details = new CustomerPortalUserDetails("customer:id:" + id, "", true, List.of(), 0, id, "T", "09");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
