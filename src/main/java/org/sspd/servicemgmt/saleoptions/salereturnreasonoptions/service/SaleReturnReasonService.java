package org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.dto.SaleReturnReasonDTO;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.model.SaleReturnReason;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.repository.SaleReturnReasonRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleReturnReasonService {
    private final SaleReturnReasonRepository repository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_READ')")
    @Transactional(readOnly = true)
    public List<SaleReturnReasonDTO> findAll(boolean activeOnly) {
        return (activeOnly ? repository.findByActiveTrueOrderByNameAsc() : repository.findAllByOrderByNameAsc())
                .stream().map(this::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_UPDATE')")
    @Transactional
    public SaleReturnReasonDTO save(SaleReturnReasonDTO dto) {
        return toDto(repository.save(apply(new SaleReturnReason(), dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_UPDATE')")
    @Transactional
    public SaleReturnReasonDTO update(Integer id, SaleReturnReasonDTO dto) {
        SaleReturnReason entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale return reason not found"));
        return toDto(repository.save(apply(entity, dto)));
    }

    private SaleReturnReason apply(SaleReturnReason entity, SaleReturnReasonDTO dto) {
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

    private SaleReturnReasonDTO toDto(SaleReturnReason entity) {
        SaleReturnReasonDTO dto = new SaleReturnReasonDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.getActive());
        return dto;
    }
}
