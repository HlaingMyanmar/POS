package org.sspd.servicemgmt.authoption;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class LoginAttemptService {

    private static final int MAX_RECORD_RETRIES = 10;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 5L;
    private static final long MAX_RETRY_DELAY_MILLIS = 100L;

    private final LoginAttemptStateRepository repository;
    private final TransactionTemplate requiresNewTx;

    @Value("${application.security.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${application.security.login.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    public LoginAttemptService(
            LoginAttemptStateRepository repository,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public static String normalizeKey(String usernameOrEmail) {
        if (usernameOrEmail == null) {
            return "";
        }
        return usernameOrEmail.trim().toLowerCase(Locale.ROOT);
    }

    /** Stable lockout bucket for a known account (username and email share one limit). */
    public static String canonicalUserKey(long userId) {
        return "user:" + userId;
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
    public boolean recordFailure(String loginKey) {
        if (loginKey == null || loginKey.isBlank()) {
            return false;
        }
        RuntimeException lastConflict = null;
        for (int attempt = 0; attempt < MAX_RECORD_RETRIES; attempt++) {
            try {
                Boolean locked = requiresNewTx.execute(status -> recordFailureOnce(loginKey));
                return Boolean.TRUE.equals(locked);
            } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
                // Concurrent first-insert / InnoDB deadlock — retry in a fresh transaction.
                lastConflict = ex;
                pauseBeforeRetry(attempt);
            }
        }
        if (lastConflict != null) {
            throw lastConflict;
        }
        return false;
    }

    private static void pauseBeforeRetry(int attempt) {
        long multiplier = 1L << Math.min(attempt, 5);
        long delay = Math.min(MAX_RETRY_DELAY_MILLIS, INITIAL_RETRY_DELAY_MILLIS * multiplier);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying login-attempt update", ex);
        }
    }

    private boolean recordFailureOnce(String loginKey) {
        Instant now = Instant.now();
        // INSERT IGNORE so two concurrent first failures do not both try to create the row.
        repository.insertIgnoreNew(loginKey, now);

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
