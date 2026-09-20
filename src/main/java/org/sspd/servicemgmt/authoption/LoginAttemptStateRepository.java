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
public interface LoginAttemptStateRepository extends JpaRepository<LoginAttemptState, Long> {

    Optional<LoginAttemptState> findByLoginKey(String loginKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LoginAttemptState s where s.loginKey = :loginKey")
    Optional<LoginAttemptState> findByLoginKeyForUpdate(@Param("loginKey") String loginKey);

    /**
     * Creates the row if missing without racing into a unique-constraint error.
     * Concurrent callers then take a pessimistic lock on the same row.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO login_attempt_state (login_key, failed_attempts, updated_at)
            VALUES (:loginKey, 0, :updatedAt)
            """, nativeQuery = true)
    int insertIgnoreNew(@Param("loginKey") String loginKey, @Param("updatedAt") Instant updatedAt);

    void deleteByLoginKey(String loginKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from LoginAttemptState s
             where s.updatedAt < :cutoff
               and (s.lockedUntil is null or s.lockedUntil < :now)
            """)
    int deleteStaleUnlocked(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
