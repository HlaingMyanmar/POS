package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditTermHistory;

import java.util.List;

@Repository
public interface CustomerCreditTermHistoryRepository extends JpaRepository<CustomerCreditTermHistory, Integer> {
    List<CustomerCreditTermHistory> findByCustomerIdOrderByChangedAtDesc(Integer customerId);
}
