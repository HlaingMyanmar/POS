package org.sspd.servicemgmt.serviceoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.serviceoptions.dto.SubServiceTypeDTO;
import org.sspd.servicemgmt.serviceoptions.model.ServiceType;
import org.sspd.servicemgmt.serviceoptions.model.SubServiceType;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceTypeRepository;
import org.sspd.servicemgmt.serviceoptions.repository.SubServiceTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubServiceTypeService {

    private final SubServiceTypeRepository repository;
    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional(readOnly = true)
    public List<SubServiceTypeDTO> findByServiceType(Integer serviceTypeId) {
        return repository.findByServiceTypeId(serviceTypeId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SubServiceTypeDTO> findActiveByServiceType(Integer serviceTypeId) {
        return repository.findByServiceTypeIdAndIsActiveTrue(serviceTypeId).stream().map(this::toDto).toList();
    }

    @Transactional
    public SubServiceTypeDTO save(SubServiceTypeDTO dto) {
        ServiceType serviceType = serviceTypeRepository.findById(dto.getServiceTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("ServiceType not found: " + dto.getServiceTypeId()));
        SubServiceType e = new SubServiceType();
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setActive(true);
        e.setServiceType(serviceType);
        return toDto(repository.save(e));
    }

    @Transactional
    public SubServiceTypeDTO update(Integer id, SubServiceTypeDTO dto) {
        SubServiceType e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SubServiceType not found: " + id));
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setActive(dto.isActive());
        return toDto(repository.save(e));
    }

    @Transactional
    public void delete(Integer id) {
        SubServiceType e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SubServiceType not found: " + id));
        e.setActive(false);
        repository.save(e);
    }

    private SubServiceTypeDTO toDto(SubServiceType e) {
        SubServiceTypeDTO dto = new SubServiceTypeDTO();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setActive(e.isActive());
        dto.setServiceTypeId(e.getServiceType().getId());
        dto.setServiceTypeName(e.getServiceType().getName());
        return dto;
    }
}
