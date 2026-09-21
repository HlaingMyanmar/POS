package org.sspd.servicemgmt.authoption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Purges obsolete refresh sessions and unlocked login-attempt rows so auth tables stay bounded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthSessionCleanupJob {

    private final RefreshSessionRepository refreshSessionRepository;
    private final LoginAttemptStateRepository loginAttemptStateRepository;

    @Value("${application.security.auth.refresh-session-retention-days:30}")
    private long refreshSessionRetentionDays;

    @Value("${application.security.auth.login-attempt-retention-days:7}")
    private long loginAttemptRetentionDays;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Rangoon")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        Instant refreshCutoff = now.minus(Duration.ofDays(Math.max(1, refreshSessionRetentionDays)));
        Instant attemptCutoff = now.minus(Duration.ofDays(Math.max(1, loginAttemptRetentionDays)));

        int sessionsRemoved = refreshSessionRepository.deleteObsoleteBefore(refreshCutoff);
        int attemptsRemoved = loginAttemptStateRepository.deleteStaleUnlocked(attemptCutoff, now);
        if (sessionsRemoved > 0 || attemptsRemoved > 0) {
            log.info("Auth cleanup removed {} refresh session(s) and {} login-attempt row(s)",
                    sessionsRemoved, attemptsRemoved);
        }
    }
}
