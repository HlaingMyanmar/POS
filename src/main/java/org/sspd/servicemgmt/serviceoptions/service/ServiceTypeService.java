package org.sspd.servicemgmt.serviceoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.serviceoptions.dto.ServiceTypeDTO;
import org.sspd.servicemgmt.serviceoptions.model.ServiceType;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTypeService {

    private final ServiceTypeRepository repository;

    @Transactional(readOnly = true)
    public List<ServiceTypeDTO> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceTypeDTO> findActive() {
        return repository.findByIsActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional
    public ServiceTypeDTO save(ServiceTypeDTO dto) {
        ServiceType e = new ServiceType();
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setActive(true);
        return toDto(repository.save(e));
    }

    @Transactional
    public ServiceTypeDTO update(Integer id, ServiceTypeDTO dto) {
        ServiceType e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ServiceType not found: " + id));
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setActive(dto.isActive());
        return toDto(repository.save(e));
    }

    @Transactional
    public void delete(Integer id) {
        ServiceType e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ServiceType not found: " + id));
        e.setActive(false);
        repository.save(e);
    }

    private ServiceTypeDTO toDto(ServiceType e) {
        ServiceTypeDTO dto = new ServiceTypeDTO();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setActive(e.isActive());
        return dto;
    }
}
