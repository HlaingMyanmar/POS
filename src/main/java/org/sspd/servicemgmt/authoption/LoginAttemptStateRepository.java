package org.sspd.servicemgmt.authoption;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginAttemptStateRepository extends JpaRepository<LoginAttemptState, Long> {

    Optional<LoginAttemptState> findByLoginKey(String loginKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LoginAttemptState s where s.loginKey = :loginKey")
    Optional<LoginAttemptState> findByLoginKeyForUpdate(@Param("loginKey") String loginKey);

    void deleteByLoginKey(String loginKey);
}
