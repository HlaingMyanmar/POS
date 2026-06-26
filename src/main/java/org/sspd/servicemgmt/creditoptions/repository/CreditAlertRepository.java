package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.creditoptions.model.AlertType;
import org.sspd.servicemgmt.creditoptions.model.CreditAlert;

import java.util.List;

@Repository
public interface CreditAlertRepository extends JpaRepository<CreditAlert, Integer> {
    List<CreditAlert> findByCustomerIdAndResolvedFalse(Integer customerId);
    List<CreditAlert> findByResolvedFalse();
    List<CreditAlert> findBySaleIdAndResolvedFalse(Integer saleId);
    boolean existsBySaleIdAndAlertTypeAndResolvedFalse(Integer saleId, AlertType alertType);
}
