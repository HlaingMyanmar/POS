package org.sspd.servicemgmt.rbacoptions.useroptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.dto.UserDTO;
import org.sspd.servicemgmt.rbacoptions.useroptions.mapper.UserMapper;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String USER_TOPIC = "/topic/user";



    // 1. User အသစ်သိမ်းခြင်း (Create)
    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_CREATE')")
    @Transactional
    public UserDTO save(UserDTO dto){
        User entity = mapper.toEntity(dto);
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email '" + dto.getEmail() + "' is already registered!");
        }
        // ၄။ Password ကို Encode လုပ်တဲ့ အပိုင်း (အဓိက အချက်)
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            // Raw password ကို hash လုပ်ပြီးမှ Entity ထဲ ပြန်ထည့်မယ်
            String encodedPassword = passwordEncoder.encode(dto.getPassword());
            entity.setPassword(encodedPassword);
        }
        User savedEntity = userRepository.save(entity);
        messagingTemplate.convertAndSend(USER_TOPIC,"USER_CREATED");
        return mapper.toDto(savedEntity);

    }
    // 2. User အားလုံးကို List လိုက် ဆွဲထုတ်ခြင်း (Read All)
    @PreAuthorize("hasAuthority('CAN_ACCESS_USERS_READ')")
    @Transactional(readOnly = true)
    public List<UserDTO> findAll(){
        return userRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    // 3. ID တစ်ခုတည်းနဲ့ ရှာခြင်း (Read by ID)
    @PreAuthorize("hasAuthority('CAN_ACCESS_USERS_READ')")
    @Transactional(readOnly = true)
    public UserDTO findById(Long id){
        User entity = userRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("User Not Found  with id "+id));
        return mapper.toDto(entity);

    }

    // 4. User id ဖြင့်ရှာပြီး update လုပ်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_UPDATE')")
    @Transactional
    public UserDTO update(Long id, UserDTO userDTO) {
        User existingEntity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found with id " + id));

        // ၁။ Password Update Logic (သီးသန့်ကိုင်တွယ်မယ်)
        if (userDTO.getPassword() != null && !userDTO.getPassword().trim().isEmpty()) {
            // Password အသစ်ပါလာမှသာ Encode လုပ်ပြီး Entity ထဲ တိုက်ရိုက်ထည့်မယ်
            existingEntity.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        // Password အလွတ်လာရင် (သို့မဟုတ်) Null လာရင် ဘာမှမလုပ်ဘဲ ထားလိုက်မယ် (အဟောင်းအတိုင်း ရှိနေမယ်)

        // ၂။ Mapper ထဲမှာ Password ကို Ignore လုပ်ထားဖို့ လိုအပ်ပါတယ် (အောက်မှာ ပြထားပါတယ်)
        mapper.updateEntityFromDto(userDTO, existingEntity);

        // ၃။ WebSocket & Save
        messagingTemplate.convertAndSend(USER_TOPIC, "USER_UPDATED");
        return mapper.toDto(userRepository.save(existingEntity));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_DELETE')")
    @Transactional
    public void delete(Long id){
        User entity = userRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("User Not Found  with id "+id));
       userRepository.delete(entity);
        messagingTemplate.convertAndSend(USER_TOPIC, "USER_DELETED");

    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_ASSIGN_ROLES')")
    @Transactional
    public void assignRole(Long userId, Set<Long> roleIds){
        User user= userRepository.findById(userId)
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
        User user= userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
        user.getRoles().remove(role);
        userRepository.save(user);
        messagingTemplate.convertAndSend(USER_TOPIC, "USER_ASSIGN_REMOVE");
    }

}
