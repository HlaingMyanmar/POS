package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderRating;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderRatingRepository extends JpaRepository<CustomerOrderRating, Integer> {
    @EntityGraph(attributePaths = {"customer", "lines"})
    Optional<CustomerOrderRating> findByOrderId(Integer orderId);

    @EntityGraph(attributePaths = {"customer", "lines"})
    Optional<CustomerOrderRating> findBySaleId(Integer saleId);

    @EntityGraph(attributePaths = {"customer", "lines"})
    List<CustomerOrderRating> findByCustomer_IdOrderByIdDesc(Integer customerId);

    @EntityGraph(attributePaths = {"customer", "lines"})
    List<CustomerOrderRating> findAllByOrderByIdDesc();
}
