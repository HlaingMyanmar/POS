package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPromoRedemption;

import java.util.Optional;

public interface CustomerPromoRedemptionRepository extends JpaRepository<CustomerPromoRedemption, Integer> {
    long countByPromo_IdAndReleasedFalse(Integer promoId);
    long countByPromo_IdAndCustomer_IdAndReleasedFalse(Integer promoId, Integer customerId);
    Optional<CustomerPromoRedemption> findByOrderId(Integer orderId);
}
