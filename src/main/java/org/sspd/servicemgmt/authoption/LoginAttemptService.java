package org.sspd.servicemgmt.authoption;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptStateRepository repository;

    @Value("${application.security.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${application.security.login.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    public static String normalizeKey(String usernameOrEmail) {
        if (usernameOrEmail == null) {
            return "";
        }
        return usernameOrEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void assertNotLocked(String loginKey) {
        if (loginKey == null || loginKey.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        repository.findByLoginKey(loginKey).ifPresent(state -> {
            if (state.isLocked(now)) {
                throw new LockedException(lockMessage(state.getLockedUntil(), now));
            }
        });
    }

    /**
     * @return true when this failure caused a new lockout window
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(String loginKey) {
        if (loginKey == null || loginKey.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        LoginAttemptState state = repository.findByLoginKeyForUpdate(loginKey)
                .orElseGet(() -> LoginAttemptState.builder()
                        .loginKey(loginKey)
                        .failedAttempts(0)
                        .updatedAt(now)
                        .build());

        if (state.isLocked(now)) {
            state.setUpdatedAt(now);
            repository.save(state);
            return false;
        }

        if (state.getLockedUntil() != null && !state.getLockedUntil().isAfter(now)) {
            state.setLockedUntil(null);
            state.setFailedAttempts(0);
        }

        int attempts = state.getFailedAttempts() + 1;
        state.setFailedAttempts(attempts);
        state.setUpdatedAt(now);

        boolean lockedNow = false;
        if (attempts >= Math.max(1, maxFailedAttempts)) {
            state.setLockedUntil(now.plus(Duration.ofMinutes(Math.max(1, lockDurationMinutes))));
            state.setFailedAttempts(0);
            lockedNow = true;
        }
        repository.save(state);
        return lockedNow;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(String... loginKeys) {
        if (loginKeys == null) {
            return;
        }
        for (String key : loginKeys) {
            if (key != null && !key.isBlank()) {
                repository.deleteByLoginKey(key);
            }
        }
    }

    public int maxFailedAttempts() {
        return Math.max(1, maxFailedAttempts);
    }

    public long lockDurationMinutes() {
        return Math.max(1, lockDurationMinutes);
    }

    private static String lockMessage(Instant lockedUntil, Instant now) {
        long minutes = Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
        return "Account temporarily locked due to failed login attempts. Try again in about "
                + minutes + " minute(s).";
    }
}
