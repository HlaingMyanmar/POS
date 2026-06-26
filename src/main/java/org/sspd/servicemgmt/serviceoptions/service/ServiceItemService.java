package org.sspd.servicemgmt.serviceoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.serviceoptions.dto.ServiceItemDTO;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;
import org.sspd.servicemgmt.serviceoptions.model.ServiceType;
import org.sspd.servicemgmt.serviceoptions.model.SubServiceType;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemRepository;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceTypeRepository;
import org.sspd.servicemgmt.serviceoptions.repository.SubServiceTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceItemService {

    private final ServiceItemRepository repository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final SubServiceTypeRepository subServiceTypeRepository;

    @Transactional(readOnly = true)
    public List<ServiceItemDTO> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceItemDTO> findActive() {
        return repository.findByIsActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceItemDTO> findByType(Integer typeId) {
        return repository.findByServiceTypeId(typeId).stream().map(this::toDto).toList();
    }

    @Transactional
    public ServiceItemDTO save(ServiceItemDTO dto) {
        ServiceType type = serviceTypeRepository.findById(dto.getServiceTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("ServiceType not found"));
        Integer subTypeId = dto.getSubServiceTypeId();
        SubServiceType subType = subTypeId != null
            ? subServiceTypeRepository.findById(subTypeId).orElse(null)
            : null;
        String code = generateCode();
        ServiceItem e = ServiceItem.builder()
            .code(code)
            .item(dto.getItem())
            .price(dto.getPrice())
            .isActive(true)
            .serviceType(type)
            .subServiceType(subType)
            .build();
        return toDto(repository.save(e));
    }

    @Transactional
    public ServiceItemDTO update(Integer id, ServiceItemDTO dto) {
        ServiceItem e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
        ServiceType type = serviceTypeRepository.findById(dto.getServiceTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("ServiceType not found"));
        Integer subTypeId = dto.getSubServiceTypeId();
        SubServiceType subType = subTypeId != null
            ? subServiceTypeRepository.findById(subTypeId).orElse(null)
            : null;
        e.setItem(dto.getItem());
        e.setPrice(dto.getPrice());
        e.setActive(dto.isActive());
        e.setServiceType(type);
        e.setSubServiceType(subType);
        return toDto(repository.save(e));
    }

    @Transactional
    public void delete(Integer id) {
        ServiceItem e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
        e.setActive(false);
        repository.save(e);
    }

    private String generateCode() {
        int next = repository.findTopByOrderByIdDesc()
            .map(s -> s.getId() + 1).orElse(1);
        return String.format("SVC-%04d", next);
    }

    private ServiceItemDTO toDto(ServiceItem e) {
        ServiceItemDTO dto = new ServiceItemDTO();
        dto.setId(e.getId());
        dto.setCode(e.getCode());
        dto.setItem(e.getItem());
        dto.setPrice(e.getPrice());
        dto.setActive(e.isActive());
        dto.setServiceTypeId(e.getServiceType().getId());
        dto.setServiceTypeName(e.getServiceType().getName());
        if (e.getSubServiceType() != null) {
            dto.setSubServiceTypeId(e.getSubServiceType().getId());
            dto.setSubServiceTypeName(e.getSubServiceType().getName());
        }
        return dto;
    }
}
