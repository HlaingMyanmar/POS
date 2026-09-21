package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.BookingAvailabilityDateDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.BookingAvailabilityPeriodDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.BookingAvailabilityWindowDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingSettingsDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingArrivalWindow;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingDateException;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingWeekdayHours;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingArrivalWindowRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingDateExceptionRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingWeekdayHoursRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Outdoor/SHOP availability resolution (Phase 2).
 * <p>
 * Order: Date Exception → Weekly Hours → Window ∩ hours → Disabled Windows → Booking Rules → Capacity.
 * Capacity consumers = {@link BookingStatus#CONFIRMED} only.
 */
@Service
@RequiredArgsConstructor
public class BookingAvailabilityService {

    public static final String STATE_AVAILABLE = "AVAILABLE";
    public static final String STATE_FEW_LEFT = "FEW_LEFT";
    public static final String STATE_FULL = "FULL";
    public static final String STATE_UNAVAILABLE = "UNAVAILABLE";

    private final ServiceBookingSettingsService settingsService;
    private final ServiceBookingWeekdayHoursRepository weekdayHoursRepository;
    private final ServiceBookingArrivalWindowRepository arrivalWindowRepository;
    private final ServiceBookingDateExceptionRepository dateExceptionRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<BookingAvailabilityDateDTO> listDates(String mode, LocalDate from, Integer days, boolean emergency) {
        String normalized = mode == null ? "ONSITE" : mode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ONSITE", "SHOP").contains(normalized)) {
            throw new IllegalArgumentException("mode must be ONSITE or SHOP");
        }
        ServiceBookingSettingsDTO rules = settingsService.getSettings();
        LocalDate start = from == null ? LocalDate.now() : from;
        int advanceDays = rules.getMaxAdvanceBookingDays() == null ? 7 : rules.getMaxAdvanceBookingDays();
        // Validation allows today .. today+advanceDays (inclusive), so the default list
        // must emit advanceDays + 1 entries (e.g. 7 → today through today+7).
        int span = days == null
                ? Math.max(1, Math.min(91, advanceDays + 1))
                : Math.max(1, Math.min(91, days));

        if ("ONSITE".equals(normalized) && !settingsService.isOutdoorBookingEnabled()) {
            List<BookingAvailabilityDateDTO> closed = new ArrayList<>();
            for (int i = 0; i < span; i++) {
                closed.add(BookingAvailabilityDateDTO.builder()
                        .date(start.plusDays(i))
                        .available(false)
                        .reason(rules.getOutdoorBookingDisabledReason())
                        .build());
            }
            return closed;
        }

        List<BookingAvailabilityDateDTO> result = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate date = start.plusDays(i);
            DayOpen open = resolveOpenPeriods(date);
            String ruleFail = bookingRulesBlock(date, null, rules, emergency, false);
            boolean hasCapacity = true;
            if ("ONSITE".equals(normalized)) {
                hasCapacity = !listWindowsForDate(date, rules, emergency, open).stream()
                        .filter(w -> STATE_AVAILABLE.equals(w.getState()) || STATE_FEW_LEFT.equals(w.getState()))
                        .toList()
                        .isEmpty();
            }
            boolean available = open.open() && ruleFail == null && ("SHOP".equals(normalized) || hasCapacity);
            String reason = !open.open() ? open.reason()
                    : ruleFail != null ? ruleFail
                    : (!hasCapacity ? "ဤနေ့အတွက် ရနိုင်သော arrival window မရှိပါ" : null);
            List<BookingAvailabilityPeriodDTO> periods = available
                    ? open.periods().stream()
                        .map(p -> BookingAvailabilityPeriodDTO.builder()
                                .opensAt(p.start())
                                .closesAt(p.end())
                                .build())
                        .toList()
                    : List.of();
            result.add(BookingAvailabilityDateDTO.builder()
                    .date(date)
                    .available(available)
                    .reason(available ? null : reason)
                    .openPeriods(periods)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<BookingAvailabilityWindowDTO> listWindows(LocalDate date, boolean emergency) {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (!settingsService.isOutdoorBookingEnabled()) {
            return List.of();
        }
        ServiceBookingSettingsDTO rules = settingsService.getSettings();
        DayOpen open = resolveOpenPeriods(date);
        return listWindowsForDate(date, rules, emergency, open);
    }

    /**
     * Validate ONSITE selection and reserve capacity under a pessimistic window lock.
     * Caller must run inside an open transaction (e.g. portal requestService).
     */
    @Transactional
    public ServiceBookingArrivalWindow lockAndValidateOnsiteSlot(
            LocalDate serviceDate,
            Integer arrivalWindowId,
            LocalTime preferredTime,
            Boolean preferredAnytime,
            boolean emergency) {
        if (serviceDate == null) {
            throw new IllegalArgumentException("serviceDate is required for outdoor booking");
        }
        if (arrivalWindowId == null) {
            throw new IllegalArgumentException("arrivalWindowId is required for outdoor booking");
        }
        if (!settingsService.isOutdoorBookingEnabled()) {
            var s = settingsService.getSettings();
            String reason = s.getOutdoorBookingDisabledReason();
            throw new IllegalArgumentException(reason == null || reason.isBlank()
                    ? ServiceBookingSettingsService.DEFAULT_OUTDOOR_DISABLED_REASON
                    : reason);
        }

        ServiceBookingArrivalWindow window = arrivalWindowRepository.findByIdForUpdate(arrivalWindowId)
                .orElseThrow(() -> new ResourceNotFoundException("Arrival window not found"));
        if (!Boolean.TRUE.equals(window.getActive())) {
            throw new IllegalArgumentException("ရွေးထားသော arrival window မရနိုင်တော့ပါ");
        }

        ServiceBookingSettingsDTO rules = settingsService.getSettings();
        DayOpen open = resolveOpenPeriods(serviceDate);
        if (!open.open()) {
            throw new IllegalArgumentException(open.reason() != null ? open.reason() : "ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည်");
        }
        if (!intersectsAny(window.getStartTime(), window.getEndTime(), open.periods())) {
            throw new IllegalArgumentException("ရွေးထားသော window သည် ဆိုင်ဖွင့်ချိန်နှင့် မကိုက်ညီပါ");
        }
        if (isWindowDisabled(serviceDate, window.getId())) {
            throw new IllegalArgumentException("ဤနေ့အတွက် ရွေးထားသော window ပိတ်ထားပါသည်");
        }

        String ruleFail = bookingRulesBlock(serviceDate, window.getStartTime(), rules, emergency, true);
        if (ruleFail != null) {
            throw new IllegalArgumentException(ruleFail);
        }

        boolean anytime = preferredAnytime == null || preferredAnytime;
        if (!anytime) {
            if (preferredTime == null) {
                throw new IllegalArgumentException("preferredTime ထည့်ပါ သို့မဟုတ် Anytime ရွေးပါ");
            }
            if (preferredTime.isBefore(window.getStartTime()) || !preferredTime.isBefore(window.getEndTime())) {
                throw new IllegalArgumentException("preferredTime သည် window အတွင်း ဖြစ်ရပါမည်");
            }
        }

        long booked = bookingRepository.countByServiceDateAndArrivalWindowIdAndStatus(
                serviceDate, window.getId(), BookingStatus.CONFIRMED);
        if (booked >= window.getMaxCapacity()) {
            throw new IllegalArgumentException("ဤ arrival window ပြည့်နေပါပြီ။ အခြား window သို့မဟုတ် ရက် ရွေးပါ");
        }
        return window;
    }

    /** Validate SHOP / drop-off datetime against business hours + booking rules. */
    @Transactional(readOnly = true)
    public void validateShopAppointment(LocalDateTime appointment, boolean emergency) {
        if (appointment == null) {
            return;
        }
        ServiceBookingSettingsDTO rules = settingsService.getSettings();
        LocalDate date = appointment.toLocalDate();
        DayOpen open = resolveOpenPeriods(date);
        if (!open.open()) {
            throw new IllegalArgumentException(open.reason() != null ? open.reason() : "ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည်");
        }
        LocalTime time = appointment.toLocalTime();
        if (!timeWithinAny(time, open.periods())) {
            throw new IllegalArgumentException("ဆိုင်ဖွင့်ချိန်အတွင်းသာ ရွေးပါ");
        }
        String ruleFail = bookingRulesBlock(date, time, rules, emergency, true);
        if (ruleFail != null) {
            throw new IllegalArgumentException(ruleFail);
        }
    }

    private List<BookingAvailabilityWindowDTO> listWindowsForDate(
            LocalDate date,
            ServiceBookingSettingsDTO rules,
            boolean emergency,
            DayOpen open) {
        List<ServiceBookingArrivalWindow> windows = arrivalWindowRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        if (windows.isEmpty()) {
            return List.of();
        }
        List<BookingAvailabilityWindowDTO> out = new ArrayList<>();
        for (ServiceBookingArrivalWindow w : windows) {
            boolean disabled = isWindowDisabled(date, w.getId());
            boolean intersects = open.open() && intersectsAny(w.getStartTime(), w.getEndTime(), open.periods());
            String ruleFail = bookingRulesBlock(date, w.getStartTime(), rules, emergency, true);
            long booked = bookingRepository.countByServiceDateAndArrivalWindowIdAndStatus(
                    date, w.getId(), BookingStatus.CONFIRMED);
            int capacity = w.getMaxCapacity() == null ? 0 : w.getMaxCapacity();
            int remaining = Math.max(0, capacity - (int) booked);
            String state;
            if (!open.open() || disabled || !intersects || ruleFail != null) {
                state = STATE_UNAVAILABLE;
            } else if (remaining <= 0) {
                state = STATE_FULL;
            } else if (remaining <= Math.max(1, capacity / 3)) {
                state = STATE_FEW_LEFT;
            } else {
                state = STATE_AVAILABLE;
            }
            out.add(BookingAvailabilityWindowDTO.builder()
                    .windowId(w.getId())
                    .name(w.getName())
                    .startTime(w.getStartTime())
                    .endTime(w.getEndTime())
                    .capacity(capacity)
                    .booked((int) booked)
                    .remaining(remaining)
                    .state(state)
                    .build());
        }
        return out;
    }

    private DayOpen resolveOpenPeriods(LocalDate date) {
        Optional<ServiceBookingDateException> exOpt = dateExceptionRepository.findByExceptionDate(date);
        if (exOpt.isPresent()) {
            ServiceBookingDateException ex = exOpt.get();
            if (Boolean.TRUE.equals(ex.getClosed())) {
                String reason = ex.getReason() == null || ex.getReason().isBlank()
                        ? "ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည်"
                        : "ပိတ်ရက် — " + ex.getReason().trim();
                return DayOpen.closed(reason);
            }
            if (ex.getOpensAt() != null && ex.getClosesAt() != null) {
                return DayOpen.open(List.of(new Period(ex.getOpensAt(), ex.getClosesAt())), ex.getReason());
            }
            // Non-closed exception without custom hours → fall through to weekly hours,
            // still allowing disabled-window overrides via isWindowDisabled.
        }

        String day = date.getDayOfWeek().name();
        List<ServiceBookingWeekdayHours> rows =
                weekdayHoursRepository.findByDayOfWeekIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(day);
        if (rows.isEmpty()) {
            return DayOpen.closed("ဤနေ့တွင် ဆိုင်ပိတ်ထားပါသည်");
        }
        List<Period> periods = rows.stream()
                .map(r -> new Period(r.getStartTime(), r.getEndTime()))
                .toList();
        return DayOpen.open(periods, null);
    }

    private boolean isWindowDisabled(LocalDate date, Integer windowId) {
        return dateExceptionRepository.findByExceptionDate(date)
                .map(ex -> ex.getDisabledArrivalWindowIds() != null
                        && ex.getDisabledArrivalWindowIds().contains(windowId))
                .orElse(false);
    }

    /**
     * @param forWindow true when checking a concrete window/appointment time (min-notice against start)
     */
    private String bookingRulesBlock(
            LocalDate date,
            LocalTime serviceStartOrNull,
            ServiceBookingSettingsDTO rules,
            boolean emergency,
            boolean forWindow) {
        LocalDate today = LocalDate.now();
        int advance = rules.getMaxAdvanceBookingDays() == null ? 7 : rules.getMaxAdvanceBookingDays();
        if (date.isAfter(today.plusDays(advance))) {
            return "အများဆုံး " + advance + " ရက်ကြိုတင် စာရင်းသွင်းနိုင်ပါသည်";
        }
        boolean relax = emergency && Boolean.TRUE.equals(rules.getAllowEmergencyRequest());
        if (!relax) {
            if (date.isBefore(today)) {
                return "ယနေ့မတိုင်မီ ရက်ကို ရွေး၍ မရပါ";
            }
            if (date.equals(today) && !Boolean.TRUE.equals(rules.getAllowSameDayBooking())) {
                return "ယနေ့စာရင်းသွင်းခွင့် မရှိပါ";
            }
            if (forWindow) {
                int notice = rules.getMinNoticeHours() == null ? 2 : rules.getMinNoticeHours();
                LocalDateTime earliest = LocalDateTime.now().plusHours(notice);
                LocalTime start = serviceStartOrNull != null ? serviceStartOrNull : LocalTime.MAX;
                LocalDateTime slotStart = LocalDateTime.of(date, start);
                if (slotStart.isBefore(earliest)) {
                    return "အနည်းဆုံး " + notice + " နာရီ ကြိုတင် စာရင်းသွင်းပါ";
                }
            }
        } else if (date.isBefore(today)) {
            return "ယနေ့မတိုင်မီ ရက်ကို ရွေး၍ မရပါ";
        }
        return null;
    }

    private static boolean intersectsAny(LocalTime start, LocalTime end, List<Period> periods) {
        if (periods == null || periods.isEmpty()) return false;
        for (Period p : periods) {
            if (start.isBefore(p.end()) && p.start().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean timeWithinAny(LocalTime time, List<Period> periods) {
        if (periods == null) return false;
        for (Period p : periods) {
            if (!time.isBefore(p.start()) && time.isBefore(p.end())) {
                return true;
            }
        }
        return false;
    }

    private record Period(LocalTime start, LocalTime end) {}

    private record DayOpen(boolean open, List<Period> periods, String reason) {
        static DayOpen closed(String reason) {
            return new DayOpen(false, List.of(), reason);
        }

        static DayOpen open(List<Period> periods, String reason) {
            return new DayOpen(true, periods, reason);
        }
    }
}
