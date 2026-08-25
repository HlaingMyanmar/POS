package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.creditoptions.model.CustomerPaymentAllocation;

import java.util.List;

public interface CustomerPaymentAllocationRepository extends JpaRepository<CustomerPaymentAllocation, Integer> {
    List<CustomerPaymentAllocation> findBySaleId(Integer saleId);
}
