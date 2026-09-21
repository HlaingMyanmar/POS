package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingWeekdayHoursDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingWeekdayHours;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingWeekdayHoursRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceBookingWeekdayHoursService {

    private static final Set<String> DAYS = Arrays.stream(DayOfWeek.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    private final ServiceBookingWeekdayHoursRepository repository;

    @Transactional(readOnly = true)
    public List<ServiceBookingWeekdayHoursDTO> list() {
        return repository.findAllByOrderByDayOfWeekAscDisplayOrderAscIdAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ServiceBookingWeekdayHoursDTO create(ServiceBookingWeekdayHoursDTO dto) {
        ServiceBookingWeekdayHours entity = new ServiceBookingWeekdayHours();
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    @Transactional
    public ServiceBookingWeekdayHoursDTO update(Integer id, ServiceBookingWeekdayHoursDTO dto) {
        ServiceBookingWeekdayHours entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Weekday hours not found"));
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Weekday hours not found");
        }
        repository.deleteById(id);
    }

    private void apply(ServiceBookingWeekdayHours entity, ServiceBookingWeekdayHoursDTO dto) {
        String day = dto.getDayOfWeek() == null ? "" : dto.getDayOfWeek().trim().toUpperCase(Locale.ROOT);
        if (!DAYS.contains(day)) {
            throw new IllegalArgumentException("dayOfWeek must be MONDAY…SUNDAY");
        }
        LocalTime start = dto.getStartTime();
        LocalTime end = dto.getEndTime();
        if (start == null || end == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        entity.setDayOfWeek(day);
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setActive(dto.getActive() == null || dto.getActive());
        entity.setDisplayOrder(dto.getDisplayOrder() == null ? 0 : dto.getDisplayOrder());
    }

    private ServiceBookingWeekdayHoursDTO toDto(ServiceBookingWeekdayHours e) {
        ServiceBookingWeekdayHoursDTO dto = new ServiceBookingWeekdayHoursDTO();
        dto.setId(e.getId());
        dto.setDayOfWeek(e.getDayOfWeek());
        dto.setStartTime(e.getStartTime());
        dto.setEndTime(e.getEndTime());
        dto.setActive(e.getActive());
        dto.setDisplayOrder(e.getDisplayOrder());
        return dto;
    }
}
