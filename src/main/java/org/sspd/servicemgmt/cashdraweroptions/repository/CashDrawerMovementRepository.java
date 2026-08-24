package org.sspd.servicemgmt.cashdraweroptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.cashdraweroptions.model.CashDrawerMovement;
import java.util.List;

public interface CashDrawerMovementRepository extends JpaRepository<CashDrawerMovement, Integer> {
    List<CashDrawerMovement> findBySessionIdOrderByCreatedAtAsc(Integer sessionId);
}
