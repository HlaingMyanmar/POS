package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingDateExceptionDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingDateException;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingArrivalWindowRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingDateExceptionRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ServiceBookingDateExceptionService {

    private final ServiceBookingDateExceptionRepository repository;
    private final ServiceBookingArrivalWindowRepository arrivalWindowRepository;

    @Transactional(readOnly = true)
    public List<ServiceBookingDateExceptionDTO> list() {
        return repository.findAllByOrderByExceptionDateAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceBookingDateExceptionDTO get(Integer id) {
        return toDto(require(id));
    }

    @Transactional
    public ServiceBookingDateExceptionDTO create(ServiceBookingDateExceptionDTO dto) {
        ServiceBookingDateException entity = new ServiceBookingDateException();
        apply(entity, dto, null);
        return toDto(repository.save(entity));
    }

    @Transactional
    public ServiceBookingDateExceptionDTO update(Integer id, ServiceBookingDateExceptionDTO dto) {
        ServiceBookingDateException entity = require(id);
        apply(entity, dto, id);
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Date exception not found");
        }
        repository.deleteById(id);
    }

    private ServiceBookingDateException require(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date exception not found"));
    }

    private void apply(ServiceBookingDateException entity, ServiceBookingDateExceptionDTO dto, Integer selfId) {
        LocalDate date = dto.getExceptionDate();
        if (date == null) {
            throw new IllegalArgumentException("exceptionDate is required");
        }
        if (selfId == null) {
            if (repository.findByExceptionDate(date).isPresent()) {
                throw new IllegalArgumentException("An exception already exists for " + date);
            }
        } else if (repository.existsByExceptionDateAndIdNot(date, selfId)) {
            throw new IllegalArgumentException("An exception already exists for " + date);
        }

        boolean closed = dto.getClosed() != null && dto.getClosed();
        LocalTime opens = dto.getOpensAt();
        LocalTime closes = dto.getClosesAt();
        if (closed) {
            if (opens != null || closes != null) {
                throw new IllegalArgumentException(
                        "Closed date exceptions cannot include opensAt/closesAt; leave them empty");
            }
            opens = null;
            closes = null;
        } else if (opens != null || closes != null) {
            if (opens == null || closes == null) {
                throw new IllegalArgumentException("opensAt and closesAt are both required for a short working day");
            }
            if (!opens.isBefore(closes)) {
                throw new IllegalArgumentException("opensAt must be before closesAt");
            }
        }

        Set<Integer> disabled = dto.getDisabledArrivalWindowIds() == null
                ? Set.of()
                : new HashSet<>(dto.getDisabledArrivalWindowIds());
        for (Integer windowId : disabled) {
            if (windowId == null || !arrivalWindowRepository.existsById(windowId)) {
                throw new IllegalArgumentException("Unknown arrival window id: " + windowId);
            }
        }

        entity.setExceptionDate(date);
        entity.setClosed(closed);
        entity.setOpensAt(opens);
        entity.setClosesAt(closes);
        String reason = dto.getReason() == null ? null : dto.getReason().trim();
        entity.setReason(reason == null || reason.isEmpty() ? null : reason);
        entity.setDisabledArrivalWindowIds(new HashSet<>(disabled));
    }

    private ServiceBookingDateExceptionDTO toDto(ServiceBookingDateException e) {
        ServiceBookingDateExceptionDTO dto = new ServiceBookingDateExceptionDTO();
        dto.setId(e.getId());
        dto.setExceptionDate(e.getExceptionDate());
        dto.setClosed(e.getClosed());
        dto.setOpensAt(e.getOpensAt());
        dto.setClosesAt(e.getClosesAt());
        dto.setReason(e.getReason());
        dto.setDisabledArrivalWindowIds(e.getDisabledArrivalWindowIds() == null
                ? new HashSet<>()
                : new HashSet<>(e.getDisabledArrivalWindowIds()));
        return dto;
    }
}
