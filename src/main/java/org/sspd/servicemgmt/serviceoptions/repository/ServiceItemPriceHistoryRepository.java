package org.sspd.servicemgmt.serviceoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItemPriceHistory;
import java.util.List;

public interface ServiceItemPriceHistoryRepository extends JpaRepository<ServiceItemPriceHistory, Integer> {
    List<ServiceItemPriceHistory> findByServiceItemIdOrderByChangedAtDesc(Integer serviceItemId);
}
