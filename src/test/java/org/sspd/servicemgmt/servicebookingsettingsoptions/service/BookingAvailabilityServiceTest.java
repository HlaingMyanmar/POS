package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.BookingAvailabilityWindowDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingSettingsDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingArrivalWindow;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingWeekdayHours;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingArrivalWindowRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingDateExceptionRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingWeekdayHoursRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingAvailabilityServiceTest {

    @Mock ServiceBookingSettingsService settingsService;
    @Mock ServiceBookingWeekdayHoursRepository weekdayHoursRepository;
    @Mock ServiceBookingArrivalWindowRepository arrivalWindowRepository;
    @Mock ServiceBookingDateExceptionRepository dateExceptionRepository;
    @Mock BookingRepository bookingRepository;

    private BookingAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new BookingAvailabilityService(
                settingsService, weekdayHoursRepository, arrivalWindowRepository,
                dateExceptionRepository, bookingRepository);
    }

    @Test
    void windowFullWhenConfirmedAtCapacity() {
        LocalDate date = nextWeekday(LocalDate.now().plusDays(2));
        stubRules();
        stubWeekdayOpen(date);
        when(dateExceptionRepository.findByExceptionDate(date)).thenReturn(Optional.empty());
        ServiceBookingArrivalWindow morning = window(1, "Morning", 9, 12, 2);
        when(arrivalWindowRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(morning));
        when(bookingRepository.countByServiceDateAndArrivalWindowIdAndStatus(date, 1, BookingStatus.CONFIRMED))
                .thenReturn(2L);

        List<BookingAvailabilityWindowDTO> windows = service.listWindows(date, false);
        assertEquals(1, windows.size());
        assertEquals(BookingAvailabilityService.STATE_FULL, windows.get(0).getState());
        assertEquals(0, windows.get(0).getRemaining());
    }

    @Test
    void lockRejectsWhenFull() {
        LocalDate date = nextWeekday(LocalDate.now().plusDays(3));
        stubRules();
        stubWeekdayOpen(date);
        when(dateExceptionRepository.findByExceptionDate(date)).thenReturn(Optional.empty());
        ServiceBookingArrivalWindow morning = window(1, "Morning", 9, 12, 1);
        when(arrivalWindowRepository.findByIdForUpdate(1)).thenReturn(Optional.of(morning));
        when(bookingRepository.countByServiceDateAndArrivalWindowIdAndStatus(date, 1, BookingStatus.CONFIRMED))
                .thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.lockAndValidateOnsiteSlot(date, 1, null, true, false));
        assertTrue(ex.getMessage().contains("ပြည့်"));
    }

    @Test
    void arrivedDoesNotAffectCapacityCountQueryUsesConfirmedOnly() {
        // Documented contract: repository method always called with CONFIRMED.
        LocalDate date = nextWeekday(LocalDate.now().plusDays(2));
        stubRules();
        stubWeekdayOpen(date);
        when(dateExceptionRepository.findByExceptionDate(any())).thenReturn(Optional.empty());
        when(arrivalWindowRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(window(5, "Afternoon", 13, 16, 2)));
        when(bookingRepository.countByServiceDateAndArrivalWindowIdAndStatus(
                eq(date), eq(5), eq(BookingStatus.CONFIRMED))).thenReturn(0L);

        List<BookingAvailabilityWindowDTO> windows = service.listWindows(date, false);
        assertEquals(BookingAvailabilityService.STATE_AVAILABLE, windows.get(0).getState());
    }

    @Test
    void defaultDateRangeIncludesLastConfiguredAdvanceDay() {
        ServiceBookingSettingsDTO dto = new ServiceBookingSettingsDTO();
        dto.setMaxAdvanceBookingDays(2);
        dto.setMinNoticeHours(0);
        dto.setAllowSameDayBooking(true);
        dto.setAllowEmergencyRequest(true);
        when(settingsService.getSettings()).thenReturn(dto);
        when(dateExceptionRepository.findByExceptionDate(any())).thenReturn(Optional.empty());

        ServiceBookingWeekdayHours hours = new ServiceBookingWeekdayHours();
        hours.setStartTime(LocalTime.of(9, 0));
        hours.setEndTime(LocalTime.of(18, 0));
        hours.setActive(true);
        when(weekdayHoursRepository.findByDayOfWeekIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(anyString()))
                .thenReturn(List.of(hours));

        var dates = service.listDates("SHOP", null, null, false);

        assertEquals(3, dates.size());
        assertEquals(LocalDate.now(), dates.get(0).getDate());
        assertEquals(LocalDate.now().plusDays(2), dates.get(2).getDate());
        assertTrue(dates.get(0).isAvailable());
        assertEquals(1, dates.get(0).getOpenPeriods().size());
        assertEquals(LocalTime.of(9, 0), dates.get(0).getOpenPeriods().get(0).getOpensAt());
        assertEquals(LocalTime.of(18, 0), dates.get(0).getOpenPeriods().get(0).getClosesAt());
    }

    private void stubRules() {
        when(settingsService.isOutdoorBookingEnabled()).thenReturn(true);
        ServiceBookingSettingsDTO dto = new ServiceBookingSettingsDTO();
        dto.setMaxAdvanceBookingDays(14);
        dto.setMinNoticeHours(0);
        dto.setAllowSameDayBooking(true);
        dto.setAllowEmergencyRequest(true);
        when(settingsService.getSettings()).thenReturn(dto);
    }

    private void stubWeekdayOpen(LocalDate date) {
        ServiceBookingWeekdayHours hours = new ServiceBookingWeekdayHours();
        hours.setDayOfWeek(date.getDayOfWeek().name());
        hours.setStartTime(LocalTime.of(9, 0));
        hours.setEndTime(LocalTime.of(18, 0));
        hours.setActive(true);
        when(weekdayHoursRepository.findByDayOfWeekIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(
                date.getDayOfWeek().name())).thenReturn(List.of(hours));
    }

    private static ServiceBookingArrivalWindow window(int id, String name, int startH, int endH, int cap) {
        ServiceBookingArrivalWindow w = new ServiceBookingArrivalWindow();
        w.setId(id);
        w.setName(name);
        w.setStartTime(LocalTime.of(startH, 0));
        w.setEndTime(LocalTime.of(endH, 0));
        w.setMaxCapacity(cap);
        w.setActive(true);
        return w;
    }

    private static LocalDate nextWeekday(LocalDate start) {
        LocalDate d = start;
        while (d.getDayOfWeek().getValue() == 7) {
            d = d.plusDays(1);
        }
        return d;
    }
}
