package org.sspd.servicemgmt.supplieroptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.supplieroptions.dto.SupplierDTO;
import org.sspd.servicemgmt.supplieroptions.mapper.SupplierMapper;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SimpMessagingTemplate messagingTemplate;
    private static final String SUPPLIER_TOPIC = "/topic/supplier";
    private final SupplierRepository supplierRepository;
    private final SupplierMapper mapper;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_CREATE')")
    @Transactional
    public SupplierDTO save(SupplierDTO dto) {
        if (supplierRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Supplier '" + dto.getName() + "' is already registered!");
        }
        Supplier entity = mapper.toEntity(dto);
        Integer lastId = supplierRepository.findTopByOrderByIdDesc()
                .map(Supplier::getId)
                .orElse(0);
        String generatedCode = String.format("SUP-%03d", lastId + 1);
        entity.setCode(generatedCode);
        if (entity.getOpeningBalance() != null) {
            entity.setCurrentBalance(entity.getOpeningBalance());
        } else {
            entity.setCurrentBalance(java.math.BigDecimal.ZERO);
            entity.setOpeningBalance(java.math.BigDecimal.ZERO);
        }
        Supplier savedEntity = supplierRepository.save(entity);
        messagingTemplate.convertAndSend(SUPPLIER_TOPIC, "SUPPLIER_CREATED");
        return mapper.toDto(savedEntity);
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_READ')")
    @Transactional(readOnly = true)
    public List<SupplierDTO> findAll(){
        return supplierRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_READ')")
    @Transactional(readOnly = true)
    public Page<SupplierDTO> findAllPaginated(Pageable pageable) {
        return supplierRepository.findAll(pageable)
                .map(mapper::toDto);
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_READ')")
    @Transactional(readOnly = true)
    public List<SupplierDTO> searchSuppliers(String keyword) {
        return supplierRepository.findByNameContainingIgnoreCase(keyword)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_READ')")
    @Transactional(readOnly = true)
    public SupplierDTO findById(Integer id){
        Supplier entity = supplierRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Supplier Not Found with Id : "+id));
        return mapper.toDto(entity);
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_UPDATE')")
    @Transactional
    public SupplierDTO update(Integer id, SupplierDTO dto) {
        Supplier existingEntity = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier Not Found with Id : " + id));
        if (!existingEntity.getName().equals(dto.getName()) &&
                supplierRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Supplier name '" + dto.getName() + "' is already taken!");
        }
        mapper.updateEntityFromDto(dto, existingEntity);
        messagingTemplate.convertAndSend(SUPPLIER_TOPIC, "SUPPLIER_UPDATED");
        return mapper.toDto(supplierRepository.save(existingEntity));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_DELETE')")
    @Transactional
    public void delete(Integer id) {
        Supplier existingEntity = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier Not Found with Id : " + id));
        if (existingEntity.getCurrentBalance().compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Cannot delete supplier with outstanding balance!");
        }
        supplierRepository.delete(existingEntity);
        messagingTemplate.convertAndSend(SUPPLIER_TOPIC, "SUPPLIER_DELETED");
    }
}
