package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerWishlist;

import java.util.List;
import java.util.Optional;

public interface CustomerWishlistRepository extends JpaRepository<CustomerWishlist, Integer> {

    Optional<CustomerWishlist> findByCustomer_IdAndProduct_Id(Integer customerId, Integer productId);

    boolean existsByCustomer_IdAndProduct_Id(Integer customerId, Integer productId);

    void deleteByCustomer_IdAndProduct_Id(Integer customerId, Integer productId);

    @EntityGraph(attributePaths = {"product", "product.category", "product.brand", "product.photos", "product.category.parent"})
    List<CustomerWishlist> findByCustomer_IdOrderByCreatedAtDesc(Integer customerId);

    @Query("""
            select w.product.id from CustomerWishlist w
            where w.customer.id = :customerId
              and coalesce(w.product.archived, false) = false
            """)
    List<Integer> findActiveProductIds(@Param("customerId") Integer customerId);
}
