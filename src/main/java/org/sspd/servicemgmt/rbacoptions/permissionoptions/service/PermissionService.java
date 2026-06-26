package org.sspd.servicemgmt.rbacoptions.permissionoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.dto.PermissionDTO;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.mapper.PermissionMapper;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.model.Permission;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.repository.PermissionRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String PERMISSION_TOPIC = "/topic/permissions";


    // 1. Permission အသစ်သိမ်းခြင်း (Create)
    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_PERMISSION_CREATE')")
    @Transactional
    public PermissionDTO save(PermissionDTO dto){
        Permission entity  = mapper.toEntity(dto);
        Permission savadEntity =  permissionRepository.save(entity);
        messagingTemplate.convertAndSend(PERMISSION_TOPIC, "PERMISSION_CREATED");
        return mapper.toDto(savadEntity);
    }

    // 2. Permission အားလုံးကို List လိုက် ဆွဲထုတ်ခြင်း (Read All)
    @PreAuthorize("hasAuthority('CAN_ACCESS_PERMISSIONS_READ')")
    @Transactional(readOnly = true)
    public List<PermissionDTO> findAll(){
        return permissionRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    // 3. ID တစ်ခုတည်းနဲ့ ရှာခြင်း (Read by ID)
    @PreAuthorize("hasAuthority('CAN_ACCESS_PERMISSIONS_READ')")
    @Transactional(readOnly = true)
    public PermissionDTO findById(Long id){

        Permission entity =  permissionRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Permission Not Found with id : "+id));

        return mapper.toDto(entity);
    }

    // 4. Permission id ဖြင့်ရှာပြီး update လုပ်ခြင်း
    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_PERMISSION_UPDATE')")
    @Transactional
    public PermissionDTO update(Long id,PermissionDTO dto){
        Permission existingEntity =  permissionRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Permission not Not Found with id : "+id));

        mapper.updateEntityFromDto(dto,existingEntity);
        messagingTemplate.convertAndSend(PERMISSION_TOPIC, "PERMISSION_UPDATED");
        return mapper.toDto(permissionRepository.save(existingEntity));
    }

    // 4. Permission id ဖြင့်ရှာပြီး delete လုပ်ခြင်း
    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_PERMISSION_DELETE')")
    @Transactional
    public void delete(Long id){
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Permission not Not Found with id : "+id));
        permissionRepository.delete(permission);
        messagingTemplate.convertAndSend(PERMISSION_TOPIC, "PERMISSION_DELETED");
    }







}
