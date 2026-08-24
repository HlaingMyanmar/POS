package org.sspd.servicemgmt.cashdraweroptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.cashdraweroptions.model.CashDrawerSession;
import java.util.Optional;

public interface CashDrawerSessionRepository extends JpaRepository<CashDrawerSession, Integer> {
    Optional<CashDrawerSession> findFirstByOpenedByAndStatusOrderByOpenedAtDesc(String openedBy, String status);
}
