package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogProductDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerWishlistStatusDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerWishlist;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerWishlistRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerWishlistService {

    private final CustomerWishlistRepository wishlists;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final CustomerPortalService portal;

    @Transactional
    public CustomerWishlistStatusDTO add(Integer productId) {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        Product product = requireActiveProduct(productId);
        if (wishlists.existsByCustomer_IdAndProduct_Id(customerId, product.getId())) {
            return new CustomerWishlistStatusDTO(product.getId(), true);
        }
        saveNew(customerId, product);
        return new CustomerWishlistStatusDTO(product.getId(), true);
    }

    @Transactional
    public CustomerWishlistStatusDTO remove(Integer productId) {
        if (productId == null) throw new IllegalArgumentException("ပစ္စည်း ရွေးပါ");
        int customerId = CustomerPortalAuth.require().getCustomerId();
        wishlists.deleteByCustomer_IdAndProduct_Id(customerId, productId);
        return new CustomerWishlistStatusDTO(productId, false);
    }

    @Transactional
    public CustomerWishlistStatusDTO toggle(Integer productId) {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        Product product = requireActiveProduct(productId);
        var existing = wishlists.findByCustomer_IdAndProduct_Id(customerId, product.getId()).orElse(null);
        if (existing != null) {
            wishlists.delete(existing);
            return new CustomerWishlistStatusDTO(product.getId(), false);
        }
        saveNew(customerId, product);
        return new CustomerWishlistStatusDTO(product.getId(), true);
    }

    @Transactional(readOnly = true)
    public List<CustomerCatalogProductDTO> list() {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        return wishlists.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                .map(CustomerWishlist::getProduct)
                .filter(p -> p != null && !Boolean.TRUE.equals(p.getArchived()))
                .map(portal::toCatalogProduct)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Integer> ids() {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        return wishlists.findActiveProductIds(customerId);
    }

    private void saveNew(int customerId, Product product) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        try {
            wishlists.saveAndFlush(CustomerWishlist.builder()
                    .customer(customer)
                    .product(product)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // concurrent add of the same product for this account
        }
    }

    private Product requireActiveProduct(Integer productId) {
        if (productId == null) throw new IllegalArgumentException("ပစ္စည်း ရွေးပါ");
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (Boolean.TRUE.equals(product.getArchived())) {
            throw new IllegalStateException("ရပ်ဆိုင်းထားသော ပစ္စည်းကို အကြိုက်စာရင်းထဲ မထည့်နိုင်ပါ");
        }
        return product;
    }
}
