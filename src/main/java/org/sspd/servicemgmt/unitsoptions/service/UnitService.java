package org.sspd.servicemgmt.unitsoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.unitsoptions.dto.UnitDTO;
import org.sspd.servicemgmt.unitsoptions.mapper.UnitMapper;
import org.sspd.servicemgmt.unitsoptions.model.Unit;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final SimpMessagingTemplate messagingTemplate;
    private static final String UNIT_TOPIC = "/topic/brand";
    private final UnitMapper mapper;
    private final UnitRepository unitRepository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_CREATE')")
    @Transactional
    public UnitDTO save(UnitDTO dto){

        Unit entity = mapper.toEntity(dto);
        if(unitRepository.existsByUnitName(entity.getUnitName())){
            throw new RuntimeException("Unit "+dto.getUnitName()+" is already registered");
        }
        Unit savedEntity =unitRepository.save(entity);
        messagingTemplate.convertAndSend(UNIT_TOPIC,"UNIT_CREATED");
        return mapper.toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_READ')")
    @Transactional(readOnly = true)
    public List<UnitDTO> findAll(){
        return unitRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_READ')")
    @Transactional(readOnly = true)
    public UnitDTO findById(Long id){
        Unit existingEntity = unitRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Unit Not Found with id :"+id));
        return mapper.toDto(existingEntity);

    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_UPDATE')")
    @Transactional
    public UnitDTO update(Long id,UnitDTO unitDTO){
        Unit existingEntity = unitRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Unit Not Found with id :"+id));
        mapper.updateEntityFromDto(unitDTO,existingEntity);
        messagingTemplate.convertAndSend(UNIT_TOPIC,"UNIT_UPDATED");
        return mapper.toDto(unitRepository.save(existingEntity));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_DELETE')")
    @Transactional
    public void delete(Long id){
        Unit existingEntity = unitRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Unit Not Found with id :"+id));
        unitRepository.delete(existingEntity);
        messagingTemplate.convertAndSend(UNIT_TOPIC,"UNIT_DELETE");
    }

}
