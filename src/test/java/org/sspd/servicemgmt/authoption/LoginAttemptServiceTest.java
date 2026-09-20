package org.sspd.servicemgmt.authoption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    private final LoginAttemptStateRepository repository = mock(LoginAttemptStateRepository.class);
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(repository);
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockDurationMinutes", 15L);
    }

    @Test
    void assertNotLockedThrowsWhenLocked() {
        LoginAttemptState state = LoginAttemptState.builder()
                .loginKey("alice")
                .failedAttempts(0)
                .lockedUntil(Instant.now().plusSeconds(600))
                .updatedAt(Instant.now())
                .build();
        when(repository.findByLoginKey("alice")).thenReturn(Optional.of(state));

        LockedException ex = assertThrows(LockedException.class, () -> service.assertNotLocked("alice"));
        assertTrue(ex.getMessage().toLowerCase().contains("locked"));
    }

    @Test
    void fifthFailureLocksAccount() {
        when(repository.findByLoginKeyForUpdate("alice")).thenReturn(Optional.of(
                LoginAttemptState.builder()
                        .loginKey("alice")
                        .failedAttempts(4)
                        .updatedAt(Instant.now())
                        .build()));

        boolean locked = service.recordFailure("alice");

        assertTrue(locked);
        verify(repository).save(argThat(state ->
                state.getLockedUntil() != null
                        && state.getLockedUntil().isAfter(Instant.now())
                        && state.getFailedAttempts() == 0));
    }

    @Test
    void clearDeletesAttemptState() {
        service.clear("alice", "alice@example.com");
        verify(repository).deleteByLoginKey("alice");
        verify(repository).deleteByLoginKey("alice@example.com");
    }
}
