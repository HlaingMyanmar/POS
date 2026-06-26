package org.sspd.servicemgmt.rbacoptions.roleoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.model.Permission;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.repository.PermissionRepository;
import org.sspd.servicemgmt.rbacoptions.roleoptions.dto.RoleDTO;
import org.sspd.servicemgmt.rbacoptions.roleoptions.mapper.RoleMapper;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String ROLE_TOPIC = "/topic/role";


    // 1. Role အသစ်သိမ်းခြင်း (Create)
    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLE_CREATE')")
    @Transactional
    public RoleDTO save(RoleDTO dto){
        Role entity = mapper.toEntity(dto);
        Role savedEntity = roleRepository.save(entity);
        messagingTemplate.convertAndSend(ROLE_TOPIC,"ROLE_CREATED");
        return mapper.toDto(savedEntity);

    }

    // 2. Role အားလုံးကို List လိုက် ဆွဲထုတ်ခြင်း (Read All)
    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLES_READ')")
    @Transactional(readOnly = true)
    public List<RoleDTO>findAll(){
        return roleRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    // 3. ID တစ်ခုတည်းနဲ့ ရှာခြင်း (Read by ID)
    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLES_READ')")
    @Transactional(readOnly = true)
    public RoleDTO findById(Long id){
        Role entity = roleRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Role Not Found  with id "+id));
        return mapper.toDto(entity);

    }

    // 4. Role id ဖြင့်ရှာပြီး update လုပ်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLE_UPDATE')")
    @Transactional
    public RoleDTO update(Long id,RoleDTO roleDTO){
        Role existingEntity = roleRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Role Not Found  with id "+id));
        mapper.updateEntityFromDto(roleDTO,existingEntity);
        messagingTemplate.convertAndSend(ROLE_TOPIC,"ROLE_UPDATED");
        return mapper.toDto(roleRepository.save(existingEntity));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLE_DELETE')")
    @Transactional
    public void delete(Long id){
        Role entity = roleRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Role Not Found  with id "+id));
        roleRepository.delete(entity);
        messagingTemplate.convertAndSend(ROLE_TOPIC, "ROLE_DELETED");

    }

    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_ROLE_ASSIGN_PERMISSIONS')")
    @Transactional
    public void assignPermission(Long roleId, Set<Long>permissionIds){
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        // 2. ပို့လိုက်တဲ့ ID တွေနဲ့ Permission list ကို DB ကနေ ဆွဲထုတ်တယ်
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));

        // 3. Role ရဲ့ permission set ထဲကို အသစ်ထည့်ပေးလိုက်တယ်
        role.setPermissions(permissions);

        // 4. Role ကို save လိုက်ရင် roles_permissions table ထဲမှာ data တွေ auto update ဖြစ်သွားမယ်
        roleRepository.save(role);
        messagingTemplate.convertAndSend(ROLE_TOPIC, "ROLE_ASSIGN_CREATED");
    }

    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_ROLE_REMOVE_PERMISSIONS')")
    @Transactional
    public void removePermission(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));

        role.getPermissions().remove(permission); // Set ထဲကနေ ဖယ်ထုတ်လိုက်တာ
        roleRepository.save(role); // JPA က roles_permissions table ထဲက data ကို auto delete လုပ်ပေးသွားမယ်
        messagingTemplate.convertAndSend(ROLE_TOPIC, "ROLE_ASSIGN_REMOVE");
    }




}
