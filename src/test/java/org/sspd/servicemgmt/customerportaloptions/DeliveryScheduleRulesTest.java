package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules.ClosedDate;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules.DayWindow;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules.Schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryScheduleRulesTest {
    private final LocalDate today = LocalDate.of(2026, 9, 13);

    @Test void acceptsTomorrowDuringBusinessHours() {
        assertDoesNotThrow(() -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 14, 9, 0), today));
        assertDoesNotThrow(() -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 14, 17, 59), today));
    }

    @Test void rejectsTodayAndClosedHours() {
        assertThrows(IllegalArgumentException.class, () -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 13, 10, 0), today));
        assertThrows(IllegalArgumentException.class, () -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 14, 8, 59), today));
        assertThrows(IllegalArgumentException.class, () -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 14, 18, 0), today));
    }

    @Test void usesPerDayHoursAndClosedDates() {
        Schedule schedule = DeliveryScheduleRules.fromLegacy(
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                "MONDAY,TUESDAY,THURSDAY",
                1,
                Map.of(
                        "MONDAY", new DayWindow(true, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        "TUESDAY", new DayWindow(true, LocalTime.of(10, 0), LocalTime.of(17, 0)),
                        "WEDNESDAY", new DayWindow(false, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        "THURSDAY", new DayWindow(true, LocalTime.of(9, 0), LocalTime.of(20, 0))
                ),
                List.of()
        );
        assertDoesNotThrow(() -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 14, 9, 0), today, schedule));
        assertThrows(IllegalArgumentException.class, () -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 15, 9, 0), today, schedule));
        assertDoesNotThrow(() -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 15, 10, 0), today, schedule));
        assertThrows(IllegalArgumentException.class, () -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 16, 11, 0), today, schedule));
        assertDoesNotThrow(() -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 17, 19, 30), today, schedule));
        Schedule holiday = DeliveryScheduleRules.fromLegacy(
                LocalTime.of(9, 0), LocalTime.of(18, 0), "THURSDAY", 1,
                Map.of("THURSDAY", new DayWindow(true, LocalTime.of(9, 0), LocalTime.of(20, 0))),
                List.of(new ClosedDate(LocalDate.of(2026, 9, 17), "Holiday")));
        assertThrows(IllegalArgumentException.class, () -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 17, 11, 0), today, holiday));
    }

    @Test void respectsMinLeadDays() {
        Schedule twoDays = DeliveryScheduleRules.fromLegacy(
                LocalTime.of(9, 0), LocalTime.of(18, 0), DeliveryScheduleRules.EVERY_DAY, 2, Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 14, 10, 0), today, twoDays));
        assertDoesNotThrow(() -> DeliveryScheduleRules.validate(LocalDateTime.of(2026, 9, 15, 10, 0), today, twoDays));
        assertEquals(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 14).getDayOfWeek());
    }
}
