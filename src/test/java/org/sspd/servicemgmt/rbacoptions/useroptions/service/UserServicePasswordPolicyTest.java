package org.sspd.servicemgmt.rbacoptions.useroptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.sspd.servicemgmt.authoption.RefreshSessionRepository;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.dto.UserDTO;
import org.sspd.servicemgmt.rbacoptions.useroptions.mapper.UserMapper;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServicePasswordPolicyTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserMapper mapper = mock(UserMapper.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RefreshSessionRepository refreshSessionRepository = mock(RefreshSessionRepository.class);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(
                userRepository,
                staffRepository,
                roleRepository,
                passwordEncoder,
                mapper,
                messagingTemplate,
                refreshSessionRepository);
    }

    @Test
    void createRequiresPasswordMeetingPolicy() {
        UserDTO dto = new UserDTO();
        dto.setUsername("alice");
        dto.setEmail("a@example.com");
        dto.setPassword("1");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        assertTrue(ex.getMessage().contains("8"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingPassword() {
        UserDTO dto = new UserDTO();
        dto.setUsername("alice");
        dto.setEmail("a@example.com");
        dto.setPassword(null);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordChangeInvalidatesSessions() {
        User existing = new User();
        existing.setId(9L);
        existing.setTokenVersion(3);
        existing.setPassword("old-hash");

        UserDTO dto = new UserDTO();
        dto.setUsername("tech");
        dto.setEmail("tech@example.com");
        dto.setPassword("newpass12");
        dto.setStaffId(0);

        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUsernameAndIdNot("tech", 9L)).thenReturn(false);
        when(userRepository.existsByEmailAndIdNot("tech@example.com", 9L)).thenReturn(false);
        when(passwordEncoder.encode("newpass12")).thenReturn("new-hash");
        when(userRepository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(new UserDTO());

        service.update(9L, dto);

        assertEquals("new-hash", existing.getPassword());
        assertEquals(4, existing.getTokenVersion());
        verify(refreshSessionRepository).revokeAllActiveForUser(eq(9L), any());
        verify(mapper).updateEntityFromDto(dto, existing);
    }

    @Test
    void updateWithoutPasswordKeepsSessions() {
        User existing = new User();
        existing.setId(9L);
        existing.setTokenVersion(3);
        existing.setPassword("old-hash");

        UserDTO dto = new UserDTO();
        dto.setUsername("tech");
        dto.setEmail("tech@example.com");
        dto.setPassword("  ");
        dto.setStaffId(0);

        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUsernameAndIdNot("tech", 9L)).thenReturn(false);
        when(userRepository.existsByEmailAndIdNot("tech@example.com", 9L)).thenReturn(false);
        when(userRepository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(new UserDTO());

        service.update(9L, dto);

        assertEquals("old-hash", existing.getPassword());
        assertEquals(3, existing.getTokenVersion());
        verify(refreshSessionRepository, never()).revokeAllActiveForUser(anyLong(), any());
    }
}
