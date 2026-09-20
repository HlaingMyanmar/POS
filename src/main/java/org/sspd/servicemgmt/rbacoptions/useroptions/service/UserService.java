package org.sspd.servicemgmt.rbacoptions.useroptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.authoption.RefreshSessionRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.dto.UserDTO;
import org.sspd.servicemgmt.rbacoptions.useroptions.mapper.UserMapper;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.security.PasswordPolicy;
import org.sspd.servicemgmt.security.UsernamePolicy;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final RefreshSessionRepository refreshSessionRepository;
    private static final String USER_TOPIC = "/topic/user";

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_CREATE')")
    @Transactional
    public UserDTO save(UserDTO dto) {
        String username = UsernamePolicy.requireValid(dto.getUsername());
        String email = requireEmail(dto.getEmail());
        String password = requirePasswordForRoles(dto.getPassword(), dto.getRoles());
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already registered!");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email '" + email + "' is already registered!");
        }

        User entity = mapper.toEntity(dto);
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setStaff(resolveStaff(dto.getStaffId()));
        entity.setPassword(passwordEncoder.encode(password));
        if (entity.getTokenVersion() == null) {
            entity.setTokenVersion(0);
        }
        User savedEntity = userRepository.save(entity);
        messagingTemplate.convertAndSend(USER_TOPIC, "USER_CREATED");
        return mapper.toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USERS_READ')")
    @Transactional(readOnly = true)
    public List<UserDTO> findAll() {
        return userRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USERS_READ')")
    @Transactional(readOnly = true)
    public UserDTO findById(Long id) {
        User entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found  with id " + id));
        return mapper.toDto(entity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_UPDATE')")
    @Transactional
    public UserDTO update(Long id, UserDTO userDTO) {
        User existingEntity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found with id " + id));

        if (userDTO.getUsername() != null) {
            String username = UsernamePolicy.requireValid(userDTO.getUsername());
            if (userRepository.existsByUsernameAndIdNot(username, id)) {
                throw new IllegalArgumentException("Username '" + username + "' is already registered!");
            }
            existingEntity.setUsername(username);
        }
        if (userDTO.getEmail() != null) {
            String email = requireEmail(userDTO.getEmail());
            if (userRepository.existsByEmailAndIdNot(email, id)) {
                throw new IllegalArgumentException("Email '" + email + "' is already registered!");
            }
            existingEntity.setEmail(email);
        }

        if (userDTO.getPassword() != null && !userDTO.getPassword().isBlank()) {
            Set<String> rolesForPolicy = userDTO.getRoles() != null
                    ? userDTO.getRoles()
                    : existingEntity.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
            String password = requirePasswordForRoles(userDTO.getPassword(), rolesForPolicy);
            existingEntity.setPassword(passwordEncoder.encode(password));
            invalidateSessions(existingEntity);
        }

        // Null fields in the DTO are ignored (partial updates) — see UserMapper.
        mapper.updateEntityFromDto(userDTO, existingEntity);

        // staffId null = omit; 0 = explicitly unlink
        if (userDTO.getStaffId() != null) {
            existingEntity.setStaff(resolveStaff(userDTO.getStaffId()));
        }

        messagingTemplate.convertAndSend(USER_TOPIC, "USER_UPDATED");
        return mapper.toDto(userRepository.save(existingEntity));
    }

    private static String requireEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (value.isBlank() || !value.contains("@")) {
            throw new IllegalArgumentException("A valid email is required.");
        }
        return value;
    }

    private static String requirePasswordForRoles(String password, Set<String> roles) {
        boolean admin = roles != null && roles.stream().anyMatch(role ->
                "ADMINISTRATOR".equalsIgnoreCase(role) || "ROLE_ADMINISTRATOR".equalsIgnoreCase(role));
        return admin ? PasswordPolicy.requireAdminValid(password) : PasswordPolicy.requireValid(password);
    }

    private void invalidateSessions(User user) {
        int nextVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        user.setTokenVersion(nextVersion);
        if (user.getId() != null) {
            refreshSessionRepository.revokeAllActiveForUser(user.getId(), Instant.now());
        }
    }

    private Staff resolveStaff(Integer staffId) {
        if (staffId == null || staffId == 0) return null;
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_DELETE')")
    @Transactional
    public void delete(Long id) {
        User entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found  with id " + id));
        userRepository.delete(entity);
        messagingTemplate.convertAndSend(USER_TOPIC, "USER_DELETED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_ASSIGN_ROLES')")
    @Transactional
    public void assignRole(Long userId, Set<Long> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        user.setRoles(roles);
        userRepository.save(user);
        messagingTemplate.convertAndSend(USER_TOPIC, "USER_ASSIGN_CREATED");
    }

    @Transactional(readOnly = true)
    public UserDTO getByUsername(String username) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapper.toDto(user);
    }

    @Transactional
    public UserDTO updateProfile(String username, UserDTO dto) {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (dto.getName() != null) user.setName(dto.getName());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        return mapper.toDto(userRepository.save(user));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_REMOVE_ROLES')")
    @Transactional
    public void removeRole(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
        user.getRoles().remove(role);
        userRepository.save(user);
        messagingTemplate.convertAndSend(USER_TOPIC, "USER_ASSIGN_REMOVE");
    }
}
