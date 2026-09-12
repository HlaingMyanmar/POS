package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppActivity;

import java.util.List;

public interface CustomerAppActivityRepository extends JpaRepository<CustomerAppActivity, Long> {

    List<CustomerAppActivity> findTop100ByAccountIdOrderByCreatedAtDescIdDesc(Integer accountId);

    List<CustomerAppActivity> findTop100ByCustomerIdOrderByCreatedAtDescIdDesc(Integer customerId);
}
