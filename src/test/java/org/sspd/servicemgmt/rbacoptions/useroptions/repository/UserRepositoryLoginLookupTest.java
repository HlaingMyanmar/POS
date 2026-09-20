package org.sspd.servicemgmt.rbacoptions.useroptions.repository;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class UserRepositoryLoginLookupTest {

    @Test
    void prefersUsernameMatchOverEmail() {
        UserRepository repo = mock(UserRepository.class, CALLS_REAL_METHODS);
        User byUsername = new User();
        byUsername.setId(1L);
        byUsername.setUsername("shared");
        when(repo.findByUsername("shared")).thenReturn(Optional.of(byUsername));

        Optional<User> result = repo.findByUsernameOrEmail("shared", "shared");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        verify(repo, never()).findByEmail(any());
    }

    @Test
    void fallsBackToEmailWhenUsernameMissing() {
        UserRepository repo = mock(UserRepository.class, CALLS_REAL_METHODS);
        User byEmail = new User();
        byEmail.setId(2L);
        byEmail.setEmail("shared@example.com");
        when(repo.findByUsername("shared@example.com")).thenReturn(Optional.empty());
        when(repo.findByEmail("shared@example.com")).thenReturn(Optional.of(byEmail));

        Optional<User> result = repo.findByUsernameOrEmail("shared@example.com", "shared@example.com");

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }
}
