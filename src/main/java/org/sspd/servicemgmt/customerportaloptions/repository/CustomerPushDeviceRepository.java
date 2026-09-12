package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPushDevice;
import java.util.List;
import java.util.Optional;

public interface CustomerPushDeviceRepository extends JpaRepository<CustomerPushDevice, Long> {
    Optional<CustomerPushDevice> findByToken(String token);
    List<CustomerPushDevice> findByCustomer_IdAndActiveTrue(Integer customerId);
}
