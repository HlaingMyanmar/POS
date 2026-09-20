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
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServicePartialUpdateTest {

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
    void partialUpdateDoesNotClearUsernameEmailOrStaff() {
        Staff staff = new Staff();
        staff.setId(5);
        User existing = new User();
        existing.setId(9L);
        existing.setUsername("keeper");
        existing.setEmail("keeper@example.com");
        existing.setIsActive(true);
        existing.setStaff(staff);

        UserDTO partial = new UserDTO();
        partial.setName("Only Name");
        // username, email, staffId null

        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(new UserDTO());

        service.update(9L, partial);

        assertEquals("keeper", existing.getUsername());
        assertEquals("keeper@example.com", existing.getEmail());
        assertSame(staff, existing.getStaff());
        verify(mapper).updateEntityFromDto(partial, existing);
        verify(staffRepository, never()).findById(any());
        verify(userRepository, never()).existsByUsernameAndIdNot(anyString(), anyLong());
    }

    @Test
    void explicitStaffIdZeroUnlinksStaff() {
        Staff staff = new Staff();
        staff.setId(5);
        User existing = new User();
        existing.setId(9L);
        existing.setUsername("keeper");
        existing.setEmail("keeper@example.com");
        existing.setStaff(staff);

        UserDTO dto = new UserDTO();
        dto.setStaffId(0);

        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(new UserDTO());

        service.update(9L, dto);

        assertNull(existing.getStaff());
    }
}
