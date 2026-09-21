package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingArrivalWindowDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingArrivalWindow;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingArrivalWindowRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingDateExceptionRepository;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceBookingArrivalWindowService {

    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 100;

    private final ServiceBookingArrivalWindowRepository repository;
    private final BookingRepository bookingRepository;
    private final ServiceBookingDateExceptionRepository dateExceptionRepository;

    @Transactional(readOnly = true)
    public List<ServiceBookingArrivalWindowDTO> list() {
        return repository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceBookingArrivalWindowDTO get(Integer id) {
        return toDto(require(id));
    }

    @Transactional
    public ServiceBookingArrivalWindowDTO create(ServiceBookingArrivalWindowDTO dto) {
        ServiceBookingArrivalWindow entity = new ServiceBookingArrivalWindow();
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    @Transactional
    public ServiceBookingArrivalWindowDTO update(Integer id, ServiceBookingArrivalWindowDTO dto) {
        ServiceBookingArrivalWindow entity = require(id);
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    /**
     * Hard-delete only when unused. Prefer {@code active=false} for historical windows
     * still referenced by bookings or date-exception disable lists.
     */
    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Arrival window not found");
        }
        long bookingRefs = bookingRepository.countByArrivalWindowId(id);
        if (bookingRefs > 0) {
            throw new IllegalStateException(
                    "Arrival window is referenced by " + bookingRefs
                            + " booking(s). Set active=false instead of deleting.");
        }
        boolean usedByException = dateExceptionRepository.findAll().stream()
                .anyMatch(ex -> ex.getDisabledArrivalWindowIds() != null
                        && ex.getDisabledArrivalWindowIds().contains(id));
        if (usedByException) {
            throw new IllegalStateException(
                    "Arrival window is referenced by a date exception. Remove it from the exception or set active=false.");
        }
        repository.deleteById(id);
    }

    private ServiceBookingArrivalWindow require(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Arrival window not found"));
    }

    private void apply(ServiceBookingArrivalWindow entity, ServiceBookingArrivalWindowDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        if (name.length() > 80) {
            throw new IllegalArgumentException("name max length is 80");
        }
        LocalTime start = dto.getStartTime();
        LocalTime end = dto.getEndTime();
        if (start == null || end == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        Integer capacity = dto.getMaxCapacity();
        if (capacity == null || capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "maxCapacity must be between " + MIN_CAPACITY + " and " + MAX_CAPACITY);
        }
        entity.setName(name);
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setMaxCapacity(capacity);
        entity.setActive(dto.getActive() == null || dto.getActive());
        entity.setDisplayOrder(dto.getDisplayOrder() == null ? 0 : dto.getDisplayOrder());
    }

    private ServiceBookingArrivalWindowDTO toDto(ServiceBookingArrivalWindow e) {
        ServiceBookingArrivalWindowDTO dto = new ServiceBookingArrivalWindowDTO();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setStartTime(e.getStartTime());
        dto.setEndTime(e.getEndTime());
        dto.setMaxCapacity(e.getMaxCapacity());
        dto.setActive(e.getActive());
        dto.setDisplayOrder(e.getDisplayOrder());
        return dto;
    }
}
