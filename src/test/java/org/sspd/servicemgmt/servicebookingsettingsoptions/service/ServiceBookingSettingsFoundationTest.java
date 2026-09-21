package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingArrivalWindowDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingDateExceptionDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingWeekdayHoursDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingArrivalWindow;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingDateException;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingWeekdayHours;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingArrivalWindowRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingDateExceptionRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingWeekdayHoursRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBookingSettingsFoundationTest {

    @Mock ServiceBookingWeekdayHoursRepository weekdayRepo;
    @Mock ServiceBookingArrivalWindowRepository windowRepo;
    @Mock ServiceBookingDateExceptionRepository exceptionRepo;
    @Mock BookingRepository bookingRepository;

    private ServiceBookingWeekdayHoursService weekdayService;
    private ServiceBookingArrivalWindowService windowService;
    private ServiceBookingDateExceptionService exceptionService;

    @BeforeEach
    void setUp() {
        weekdayService = new ServiceBookingWeekdayHoursService(weekdayRepo);
        windowService = new ServiceBookingArrivalWindowService(windowRepo, bookingRepository, exceptionRepo);
        exceptionService = new ServiceBookingDateExceptionService(exceptionRepo, windowRepo);
    }

    @Test
    void allowsMultiplePeriodsSameWeekday() {
        when(weekdayRepo.save(any())).thenAnswer(inv -> {
            ServiceBookingWeekdayHours e = inv.getArgument(0);
            e.setId(e.getStartTime().getHour());
            return e;
        });
        ServiceBookingWeekdayHoursDTO morning = period("MONDAY", 9, 12, 1);
        ServiceBookingWeekdayHoursDTO afternoon = period("MONDAY", 13, 18, 2);
        assertEquals("MONDAY", weekdayService.create(morning).getDayOfWeek());
        assertEquals("MONDAY", weekdayService.create(afternoon).getDayOfWeek());
        verify(weekdayRepo, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void rejectsInvalidWeekdayHourRange() {
        ServiceBookingWeekdayHoursDTO dto = period("MONDAY", 12, 9, 1);
        assertThrows(IllegalArgumentException.class, () -> weekdayService.create(dto));
    }

    @Test
    void rejectsInvalidWeekdayName() {
        ServiceBookingWeekdayHoursDTO dto = period("FUNDAY", 9, 18, 1);
        assertThrows(IllegalArgumentException.class, () -> weekdayService.create(dto));
    }

    @Test
    void rejectsCapacityBelowOne() {
        ServiceBookingArrivalWindowDTO dto = window("Morning", 9, 12, 0);
        assertThrows(IllegalArgumentException.class, () -> windowService.create(dto));
    }

    @Test
    void createsAndUpdatesArrivalWindowIncludingInactive() {
        when(windowRepo.save(any())).thenAnswer(inv -> {
            ServiceBookingArrivalWindow w = inv.getArgument(0);
            if (w.getId() == null) w.setId(5);
            return w;
        });
        when(windowRepo.findById(5)).thenAnswer(inv -> {
            ServiceBookingArrivalWindow w = new ServiceBookingArrivalWindow();
            w.setId(5);
            w.setName("Morning");
            w.setStartTime(LocalTime.of(9, 0));
            w.setEndTime(LocalTime.of(12, 0));
            w.setMaxCapacity(2);
            w.setActive(true);
            w.setDisplayOrder(1);
            return Optional.of(w);
        });

        ServiceBookingArrivalWindowDTO created = windowService.create(window("Morning", 9, 12, 2));
        assertEquals(2, created.getMaxCapacity());

        ServiceBookingArrivalWindowDTO patch = window("Morning", 9, 12, 2);
        patch.setActive(false);
        ServiceBookingArrivalWindowDTO updated = windowService.update(5, patch);
        assertFalse(updated.getActive());
    }

    @Test
    void refusesDeleteWhenBookingReferencesWindow() {
        when(windowRepo.existsById(3)).thenReturn(true);
        when(bookingRepository.countByArrivalWindowId(3)).thenReturn(2L);
        assertThrows(IllegalStateException.class, () -> windowService.delete(3));
        verify(windowRepo, never()).deleteById(anyInt());
    }

    @Test
    void rejectsDuplicateExceptionDate() {
        when(exceptionRepo.findByExceptionDate(LocalDate.of(2026, 9, 21)))
                .thenReturn(Optional.of(new ServiceBookingDateException()));
        ServiceBookingDateExceptionDTO dto = closedException(LocalDate.of(2026, 9, 21));
        assertThrows(IllegalArgumentException.class, () -> exceptionService.create(dto));
    }

    @Test
    void rejectsClosedExceptionWithCustomHours() {
        ServiceBookingDateExceptionDTO dto = closedException(LocalDate.of(2026, 10, 1));
        dto.setOpensAt(LocalTime.of(9, 0));
        dto.setClosesAt(LocalTime.of(13, 0));
        assertThrows(IllegalArgumentException.class, () -> exceptionService.create(dto));
    }

    @Test
    void persistsDisabledWindowIdsOnException() {
        when(exceptionRepo.findByExceptionDate(any())).thenReturn(Optional.empty());
        when(windowRepo.existsById(7)).thenReturn(true);
        when(exceptionRepo.save(any())).thenAnswer(inv -> {
            ServiceBookingDateException e = inv.getArgument(0);
            e.setId(9);
            return e;
        });
        ServiceBookingDateExceptionDTO dto = closedException(LocalDate.of(2026, 12, 25));
        dto.setDisabledArrivalWindowIds(Set.of(7));
        ServiceBookingDateExceptionDTO saved = exceptionService.create(dto);
        assertEquals(Set.of(7), saved.getDisabledArrivalWindowIds());

        ArgumentCaptor<ServiceBookingDateException> captor = ArgumentCaptor.forClass(ServiceBookingDateException.class);
        verify(exceptionRepo).save(captor.capture());
        assertTrue(captor.getValue().getDisabledArrivalWindowIds().contains(7));
    }

    @Test
    void deletingExceptionDoesNotDeleteArrivalWindows() {
        when(exceptionRepo.existsById(9)).thenReturn(true);
        exceptionService.delete(9);
        verify(exceptionRepo).deleteById(9);
        verify(windowRepo, never()).deleteById(anyInt());
        verify(windowRepo, never()).delete(any());
    }

    private ServiceBookingWeekdayHoursDTO period(String day, int startH, int endH, int order) {
        ServiceBookingWeekdayHoursDTO dto = new ServiceBookingWeekdayHoursDTO();
        dto.setDayOfWeek(day);
        dto.setStartTime(LocalTime.of(startH, 0));
        dto.setEndTime(LocalTime.of(endH, 0));
        dto.setActive(true);
        dto.setDisplayOrder(order);
        return dto;
    }

    private ServiceBookingArrivalWindowDTO window(String name, int startH, int endH, int capacity) {
        ServiceBookingArrivalWindowDTO dto = new ServiceBookingArrivalWindowDTO();
        dto.setName(name);
        dto.setStartTime(LocalTime.of(startH, 0));
        dto.setEndTime(LocalTime.of(endH, 0));
        dto.setMaxCapacity(capacity);
        dto.setActive(true);
        dto.setDisplayOrder(1);
        return dto;
    }

    private ServiceBookingDateExceptionDTO closedException(LocalDate date) {
        ServiceBookingDateExceptionDTO dto = new ServiceBookingDateExceptionDTO();
        dto.setExceptionDate(date);
        dto.setClosed(true);
        dto.setReason("Holiday");
        dto.setDisabledArrivalWindowIds(new HashSet<>());
        return dto;
    }
}
