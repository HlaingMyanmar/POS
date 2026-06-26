package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.creditoptions.model.CustomerPayment;

import java.util.List;

@Repository
public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Integer> {
    List<CustomerPayment> findByCustomerId(Integer customerId);
    List<CustomerPayment> findBySaleId(Integer saleId);
}
