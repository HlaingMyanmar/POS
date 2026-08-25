package org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.dto.PurchaseReturnReasonDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.model.PurchaseReturnReason;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.repository.PurchaseReturnReasonRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseReturnReasonService {
    private final PurchaseReturnReasonRepository repository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_READ')")
    @Transactional(readOnly = true)
    public List<PurchaseReturnReasonDTO> findAll(boolean activeOnly) {
        return (activeOnly ? repository.findByActiveTrueOrderByNameAsc() : repository.findAllByOrderByNameAsc())
                .stream().map(this::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_REASON_MANAGE')")
    @Transactional
    public PurchaseReturnReasonDTO save(PurchaseReturnReasonDTO dto) {
        return toDto(repository.save(apply(new PurchaseReturnReason(), dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_REASON_MANAGE')")
    @Transactional
    public PurchaseReturnReasonDTO update(Integer id, PurchaseReturnReasonDTO dto) {
        PurchaseReturnReason entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return reason not found"));
        return toDto(repository.save(apply(entity, dto)));
    }

    private PurchaseReturnReason apply(PurchaseReturnReason entity, PurchaseReturnReasonDTO dto) {
        String code = dto.getCode() == null ? "" : dto.getCode().trim().toUpperCase();
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (code.isBlank() || name.isBlank()) throw new IllegalArgumentException("Reason code and name are required");
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, entity.getId() == null ? -1 : entity.getId()))
            throw new IllegalArgumentException("Reason code already exists");
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(dto.getDescription());
        entity.setActive(dto.getActive() == null || dto.getActive());
        return entity;
    }

    private PurchaseReturnReasonDTO toDto(PurchaseReturnReason entity) {
        PurchaseReturnReasonDTO dto = new PurchaseReturnReasonDTO();
        dto.setId(entity.getId()); dto.setCode(entity.getCode()); dto.setName(entity.getName());
        dto.setDescription(entity.getDescription()); dto.setActive(entity.getActive());
        return dto;
    }
}
