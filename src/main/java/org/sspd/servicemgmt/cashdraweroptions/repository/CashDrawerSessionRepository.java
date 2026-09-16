package org.sspd.servicemgmt.cashdraweroptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.cashdraweroptions.model.CashDrawerSession;
import java.util.Optional;

public interface CashDrawerSessionRepository extends JpaRepository<CashDrawerSession, Integer> {
    Optional<CashDrawerSession> findFirstByOpenedByAndStatusOrderByOpenedAtDesc(String openedBy, String status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashDrawerSession s where s.id = :id")
    Optional<CashDrawerSession> findByIdForUpdate(@Param("id") Integer id);
}
