package org.sspd.servicemgmt.authoption;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.jti = :jti")
    Optional<RefreshSession> findByJtiForUpdate(@Param("jti") String jti);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshSession s
               set s.revokedAt = :now
             where s.userId = :userId
               and s.revokedAt is null
            """)
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshSession s
               set s.revokedAt = :now
             where s.familyId = :familyId
               and s.revokedAt is null
            """)
    int revokeAllActiveInFamily(@Param("familyId") String familyId, @Param("now") Instant now);
}
