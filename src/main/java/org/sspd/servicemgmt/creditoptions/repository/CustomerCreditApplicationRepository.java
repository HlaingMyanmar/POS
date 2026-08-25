package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditApplication;

import java.util.List;

public interface CustomerCreditApplicationRepository extends JpaRepository<CustomerCreditApplication, Integer> {
    List<CustomerCreditApplication> findBySaleIdOrderByIdDesc(Integer saleId);
    List<CustomerCreditApplication> findByServiceJobIdOrderByIdDesc(Integer serviceJobId);
}
