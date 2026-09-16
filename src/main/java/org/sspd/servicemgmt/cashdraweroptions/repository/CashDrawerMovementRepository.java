package org.sspd.servicemgmt.cashdraweroptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.cashdraweroptions.model.CashDrawerMovement;
import java.util.List;

public interface CashDrawerMovementRepository extends JpaRepository<CashDrawerMovement, Integer> {
    List<CashDrawerMovement> findBySessionIdOrderByCreatedAtAsc(Integer sessionId);

    @Query("""
            select m from CashDrawerMovement m
            join fetch m.session
            where m.type = :type
              and m.referenceType = :referenceType
              and m.referenceId = :referenceId
              and m.reversed = false
            order by m.id asc
            """)
    List<CashDrawerMovement> findByTypeAndReferenceTypeAndReferenceIdAndReversedFalseOrderByIdAsc(
            @Param("type") String type,
            @Param("referenceType") String referenceType,
            @Param("referenceId") Integer referenceId);

    boolean existsByTypeAndReferenceTypeAndReferenceIdAndReversedFalse(
            String type, String referenceType, Integer referenceId);
}
