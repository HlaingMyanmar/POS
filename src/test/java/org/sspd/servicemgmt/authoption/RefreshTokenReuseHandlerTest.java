package org.sspd.servicemgmt.authoption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshTokenReuseHandlerTest {

    private final RefreshSessionRepository refreshSessionRepository = mock(RefreshSessionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private RefreshTokenReuseHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RefreshTokenReuseHandler(refreshSessionRepository, userRepository);
    }

    @Test
    void revokeFamilyAndInvalidateUserPersistsBothChanges() {
        User user = new User();
        user.setId(4L);
        user.setTokenVersion(2);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        handler.revokeFamilyAndInvalidateUser("family-1", 4L);

        verify(refreshSessionRepository).revokeAllActiveInFamily(eq("family-1"), any());
        assertEquals(3, user.getTokenVersion());
        verify(userRepository).save(user);
    }

    @Test
    void revokeFamilyDoesNotTouchUser() {
        handler.revokeFamily("family-9");

        verify(refreshSessionRepository).revokeAllActiveInFamily(eq("family-9"), any());
        verifyNoInteractions(userRepository);
    }
}
