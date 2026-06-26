package org.sspd.servicemgmt.brandoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.brandoptions.dto.BrandDTO;
import org.sspd.servicemgmt.brandoptions.mapper.BrandMapper;
import org.sspd.servicemgmt.brandoptions.model.Brand;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String BRAND_TOPIC = "/topic/brand";

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_CREATE')")
    @Transactional
    public BrandDTO save(BrandDTO dto){
        Brand entity = mapper.toEntitiy(dto);
        if(brandRepository.existsByName(entity.getName())){
           throw  new RuntimeException("Brand '" + dto.getName() + "' is already registered!");
        }
        Brand savedEntity = brandRepository.save(entity);
        messagingTemplate.convertAndSend(BRAND_TOPIC,"BRAND_CREATED");
        return mapper.toDto(savedEntity);

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_READ')")
    @Transactional(readOnly = true)
    public List<BrandDTO> findAll(){
        return brandRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_READ')")
    @Transactional(readOnly = true)
    public BrandDTO findById(Long id){
        Brand entity = brandRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Brand Not Found with id " + id));
        return mapper.toDto(entity);

    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_UPDATE')")
    @Transactional
    public BrandDTO update(Long id, BrandDTO brandDTO){
        Brand existingEntity = brandRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Brand Not Found with id " + id));
        mapper.updateEntityFromDto(brandDTO,existingEntity);
        messagingTemplate.convertAndSend(BRAND_TOPIC,"BRAND_UPDATED");
        return mapper.toDto(brandRepository.save(existingEntity));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_DELETE')")
    @Transactional
    public void delete(Long id){
       Brand entity = brandRepository.findById(id)
               .orElseThrow(()->new ResourceNotFoundException("Brand Not Found with id"+ id));
       brandRepository.delete(entity);
       messagingTemplate.convertAndSend(BRAND_TOPIC,"BRAND_DELETE");
    }

}
