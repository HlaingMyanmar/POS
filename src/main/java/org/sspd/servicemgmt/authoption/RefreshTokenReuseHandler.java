package org.sspd.servicemgmt.authoption;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.time.Instant;

/**
 * Commits refresh-token theft mitigations in an independent transaction so a
 * subsequent {@code BadCredentialsException} in {@link AuthService#refresh}
 * cannot roll them back.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenReuseHandler {

    private final RefreshSessionRepository refreshSessionRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamilyAndInvalidateUser(String familyId, Long userId) {
        Instant now = Instant.now();
        refreshSessionRepository.revokeAllActiveInFamily(familyId, now);
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            int nextVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
            user.setTokenVersion(nextVersion);
            userRepository.save(user);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(String familyId) {
        refreshSessionRepository.revokeAllActiveInFamily(familyId, Instant.now());
    }
}
