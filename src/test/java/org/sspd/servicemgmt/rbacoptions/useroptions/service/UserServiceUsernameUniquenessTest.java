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

class UserServiceUsernameUniquenessTest {

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
    void createRejectsDuplicateUsername() {
        UserDTO dto = validCreateDto();
        when(userRepository.existsByUsername("tech1")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        assertTrue(ex.getMessage().contains("Username"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createRejectsBlankUsername() {
        UserDTO dto = validCreateDto();
        dto.setUsername("  ");

        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRejectsUsernameTakenByAnotherUser() {
        User existing = new User();
        existing.setId(2L);
        existing.setUsername("tech1");
        existing.setEmail("tech1@example.com");

        UserDTO dto = new UserDTO();
        dto.setUsername("taken");
        dto.setEmail("tech1@example.com");
        dto.setStaffId(0);

        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUsernameAndIdNot("taken", 2L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.update(2L, dto));
        assertTrue(ex.getMessage().contains("Username"));
        verify(userRepository, never()).save(any());
    }

    private static UserDTO validCreateDto() {
        UserDTO dto = new UserDTO();
        dto.setUsername("tech1");
        dto.setEmail("tech1@example.com");
        dto.setPassword("password1");
        dto.setStaffId(0);
        return dto;
    }
}
