package org.sspd.servicemgmt.customerportaloptions.support;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DeliveryScheduleRules {
    public static final LocalTime OPENS_AT = LocalTime.of(9, 0);
    public static final LocalTime CLOSES_AT = LocalTime.of(18, 0);
    public static final String EVERY_DAY = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";
    public static final List<DayOfWeek> WEEK = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    public record DayWindow(boolean open, LocalTime opensAt, LocalTime closesAt) {}

    public record ClosedDate(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            String reason
    ) {}

    public record Schedule(
            LocalTime defaultOpensAt,
            LocalTime defaultClosesAt,
            String deliveryDays,
            Map<DayOfWeek, DayWindow> weekdays,
            List<ClosedDate> closedDates,
            int minLeadDays
    ) {}

    private DeliveryScheduleRules() {}

    public static Schedule defaults() {
        return fromLegacy(OPENS_AT, CLOSES_AT, EVERY_DAY, 1, Map.of(), List.of());
    }

    public static Schedule fromLegacy(
            LocalTime opensAt,
            LocalTime closesAt,
            String deliveryDays,
            Integer minLeadDays,
            Map<String, DayWindow> weekdayHours,
            List<ClosedDate> closedDates
    ) {
        LocalTime open = opensAt == null ? OPENS_AT : opensAt;
        LocalTime close = closesAt == null ? CLOSES_AT : closesAt;
        Set<DayOfWeek> openDays = parseDays(deliveryDays);
        Map<DayOfWeek, DayWindow> windows = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : WEEK) {
            DayWindow custom = weekdayHours == null ? null : weekdayHours.get(day.name());
            if (custom != null) {
                LocalTime dayOpen = custom.opensAt() == null ? open : custom.opensAt();
                LocalTime dayClose = custom.closesAt() == null ? close : custom.closesAt();
                windows.put(day, new DayWindow(custom.open(), dayOpen, dayClose));
            } else {
                windows.put(day, new DayWindow(openDays.contains(day), open, close));
            }
        }
        int lead = minLeadDays == null ? 1 : Math.max(0, Math.min(30, minLeadDays));
        List<ClosedDate> closed = closedDates == null ? List.of() : List.copyOf(closedDates);
        String days = WEEK.stream().filter(d -> windows.get(d).open()).map(Enum::name).collect(Collectors.joining(","));
        return new Schedule(open, close, days.isBlank() ? deliveryDays : days, windows, closed, lead);
    }

    public static void validate(LocalDateTime requested, LocalDate today) {
        validate(requested, today, defaults());
    }

    public static void validate(LocalDateTime requested, LocalDate today, LocalTime opensAt, LocalTime closesAt, String deliveryDays) {
        validate(requested, today, fromLegacy(opensAt, closesAt, deliveryDays, 1, Map.of(), List.of()));
    }

    public static void validate(LocalDateTime requested, LocalDate today, Schedule schedule) {
        if (requested == null) throw new IllegalArgumentException("ပို့မည့် ရက်နှင့် အချိန် ရွေးပါ");
        Schedule s = schedule == null ? defaults() : schedule;
        LocalDate earliest = today.plusDays(s.minLeadDays());
        if (requested.toLocalDate().isBefore(earliest)) {
            throw new IllegalArgumentException(leadMessage(s.minLeadDays()));
        }
        LocalDate date = requested.toLocalDate();
        for (ClosedDate closed : s.closedDates()) {
            if (closed != null && date.equals(closed.date())) {
                String reason = closed.reason() == null || closed.reason().isBlank() ? "" : " — " + closed.reason().trim();
                throw new IllegalArgumentException("ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည်" + reason);
            }
        }
        DayWindow window = windowFor(s, requested.getDayOfWeek());
        if (!window.open()) {
            throw new IllegalArgumentException("ရွေးထားသောနေ့တွင် delivery ပိတ်ထားပါသည်");
        }
        LocalTime time = requested.toLocalTime();
        if (time.isBefore(window.opensAt()) || !time.isBefore(window.closesAt())) {
            throw new IllegalArgumentException("ပို့မည့်အချိန်ကို " + format(window.opensAt()) + " မှ " + format(window.closesAt()) + " မတိုင်မီ ရွေးပါ");
        }
    }

    public static DayWindow windowFor(Schedule schedule, DayOfWeek day) {
        DayWindow window = schedule == null || schedule.weekdays() == null ? null : schedule.weekdays().get(day);
        if (window != null) return window;
        Schedule fallback = schedule == null ? defaults() : schedule;
        Set<DayOfWeek> days = parseDays(fallback.deliveryDays());
        return new DayWindow(
                days.contains(day),
                fallback.defaultOpensAt() == null ? OPENS_AT : fallback.defaultOpensAt(),
                fallback.defaultClosesAt() == null ? CLOSES_AT : fallback.defaultClosesAt());
    }

    public static String leadMessage(int minLeadDays) {
        if (minLeadDays <= 0) return "ပို့မည့်ရက်ကို ယနေ့မှ ရွေးပါ";
        if (minLeadDays == 1) return "ပို့မည့်ရက်ကို အနည်းဆုံး မနက်ဖြန်မှ ရွေးပါ";
        return "ပို့မည့်ရက်ကို အနည်းဆုံး " + minLeadDays + " ရက် ကြိုရွေးပါ";
    }

    public static LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        String text = value.trim();
        if (text.length() >= 5) text = text.substring(0, 5);
        try {
            return LocalTime.parse(text.length() == 5 ? text + ":00" : text);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static String format(LocalTime time) {
        LocalTime t = time == null ? OPENS_AT : time;
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static Set<DayOfWeek> parseDays(String deliveryDays) {
        try {
            Set<DayOfWeek> days = Arrays.stream((deliveryDays == null || deliveryDays.isBlank() ? EVERY_DAY : deliveryDays).split(","))
                    .map(String::trim).filter(v -> !v.isBlank()).map(DayOfWeek::valueOf).collect(Collectors.toSet());
            if (days.isEmpty()) throw new IllegalArgumentException();
            return days;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Delivery days setting မှားနေသည်");
        }
    }

    public static Map<String, DayWindow> indexByDayName(List<DayWindowRow> rows) {
        Map<String, DayWindow> map = new LinkedHashMap<>();
        if (rows == null) return map;
        for (DayWindowRow row : rows) {
            if (row == null || row.day() == null) continue;
            map.put(row.day().trim().toUpperCase(), new DayWindow(row.open(), row.opensAt(), row.closesAt()));
        }
        return map;
    }

    public record DayWindowRow(
            String day,
            boolean open,
            @JsonFormat(pattern = "HH:mm[:ss]") LocalTime opensAt,
            @JsonFormat(pattern = "HH:mm[:ss]") LocalTime closesAt
    ) {}
}
