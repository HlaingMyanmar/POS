package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryMilestone;

import java.util.Collection;
import java.util.List;

public interface CustomerDeliveryMilestoneRepository extends JpaRepository<CustomerDeliveryMilestone, Integer> {

    List<CustomerDeliveryMilestone> findByOrderIdOrderByIdAsc(Integer orderId);

    List<CustomerDeliveryMilestone> findByOrderIdInOrderByIdAsc(Collection<Integer> orderIds);
}
