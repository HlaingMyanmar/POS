package org.sspd.servicemgmt.shelflocationoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.shelflocationoptions.dto.ShelfLocationDTO;
import org.sspd.servicemgmt.shelflocationoptions.model.ShelfLocation;
import org.sspd.servicemgmt.shelflocationoptions.repository.ShelfLocationRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShelfLocationService {

    private final ShelfLocationRepository repo;

    @Transactional(readOnly = true)
    public List<ShelfLocationDTO> findAll() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ShelfLocationDTO> findActive() {
        return repo.findByActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ShelfLocationDTO findById(Integer id) {
        return toDto(repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ShelfLocation not found: " + id)));
    }

    @Transactional
    public ShelfLocationDTO create(ShelfLocationDTO dto) {
        if (repo.existsByCodeIgnoreCase(dto.getCode()))
            throw new IllegalArgumentException("Code already exists: " + dto.getCode());
        ShelfLocation loc = ShelfLocation.builder()
            .code(dto.getCode().trim().toUpperCase())
            .label(dto.getLabel() != null ? dto.getLabel().trim() : null)
            .active(true)
            .build();
        return toDto(repo.save(loc));
    }

    @Transactional
    public ShelfLocationDTO update(Integer id, ShelfLocationDTO dto) {
        ShelfLocation loc = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ShelfLocation not found: " + id));
        if (repo.existsByCodeIgnoreCaseAndIdNot(dto.getCode(), id))
            throw new IllegalArgumentException("Code already exists: " + dto.getCode());
        loc.setCode(dto.getCode().trim().toUpperCase());
        loc.setLabel(dto.getLabel() != null ? dto.getLabel().trim() : null);
        loc.setActive(dto.isActive());
        return toDto(repo.save(loc));
    }

    @Transactional
    public void delete(Integer id) {
        if (!repo.existsById(id))
            throw new ResourceNotFoundException("ShelfLocation not found: " + id);
        repo.deleteById(id);
    }

    private ShelfLocationDTO toDto(ShelfLocation loc) {
        ShelfLocationDTO dto = new ShelfLocationDTO();
        dto.setId(loc.getId());
        dto.setCode(loc.getCode());
        dto.setLabel(loc.getLabel());
        dto.setActive(loc.isActive());
        return dto;
    }
}
