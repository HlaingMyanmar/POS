package org.sspd.servicemgmt.auditoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.auditoptions.model.AuditLog;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:actor IS NULL OR :actor = '' OR LOWER(a.actor) LIKE LOWER(CONCAT('%',:actor,'%')))
          AND (:action IS NULL OR :action = '' OR a.action = :action)
          AND (:module IS NULL OR :module = '' OR LOWER(a.module) LIKE LOWER(CONCAT('%',:module,'%')))
          AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom)
          AND (:dateTo   IS NULL OR a.createdAt <= :dateTo)
        ORDER BY a.createdAt DESC
    """)
    Page<AuditLog> search(
        @Param("actor")    String actor,
        @Param("action")   String action,
        @Param("module")   String module,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateTo")   LocalDateTime dateTo,
        Pageable pageable
    );
}
