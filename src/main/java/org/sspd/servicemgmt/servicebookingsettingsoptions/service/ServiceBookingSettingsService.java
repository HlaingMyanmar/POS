package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingSettingsDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingSettings;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingSettingsRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceBookingSettingsService {

    public static final int MIN_PHOTOS_PER_ITEM = 1;
    public static final int MAX_PHOTOS_PER_ITEM_CAP = 100;
    public static final int DEFAULT_PHOTOS_PER_ITEM = 50;
    public static final int MIN_ADVANCE_DAYS = 0;
    public static final int MAX_ADVANCE_DAYS = 90;
    public static final int MIN_NOTICE_HOURS = 0;
    public static final int MAX_NOTICE_HOURS = 168;

    private static final String DEFAULT_OUTDOOR_NOTICE =
            "အိမ်အရောက်ဝန်ဆောင်မှုအတွက် သယ်ယူခကျသင့်ပါသည်။ ခရီးအကွာအဝေးအလိုက် ဆိုင်သတ်မှတ်နှုန်းအတိုင်း ကောက်ခံပါမည်။";

    public static final String DEFAULT_REJECTION_MESSAGE =
            "သင့် service တောင်းဆိုမှုကို ဆိုင်မှ လက်မခံနိုင်ပါ။ နောက်ထပ်အသေးစိတ်အတွက် ဆိုင်သို့ ဆက်သွယ်ပေးပါ။";

    public static final String DEFAULT_OUTDOOR_DISABLED_REASON =
            "အိမ်အရောက်ဝန်ဆောင်မှုကို ယာယီပိတ်ထားပါသည်။ ဆိုင်သို့ ယူလာပေးပါ သို့မဟုတ် ဆိုင်နှင့် ညှိနှိုင်းပါ။";

    private final ServiceBookingSettingsRepository repository;

    @Value("${app.booking-photo.max-per-item:50}")
    private int propertyDefaultMaxPhotosPerItem = DEFAULT_PHOTOS_PER_ITEM;

    @Transactional(readOnly = true)
    public ServiceBookingSettingsDTO getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional(readOnly = true)
    public int getMaxPhotosPerItem() {
        Integer value = getOrCreate().getMaxPhotosPerItem();
        if (value == null || value < MIN_PHOTOS_PER_ITEM) {
            return clampPhotosPerItem(propertyDefaultMaxPhotosPerItem);
        }
        return clampPhotosPerItem(value);
    }

    @Transactional(readOnly = true)
    public boolean isOutdoorBookingEnabled() {
        Boolean enabled = getOrCreate().getOutdoorBookingEnabled();
        return enabled == null || enabled;
    }

    @Transactional
    public ServiceBookingSettingsDTO saveSettings(ServiceBookingSettingsDTO dto) {
        ServiceBookingSettings s = getOrCreate();
        String notice = trimToEmpty(dto.getOutdoorTransportationNotice());
        s.setOutdoorTransportationNotice(notice.isEmpty() ? null : notice);

        BigDecimal fee = dto.getOutdoorTransportationFee();
        if (fee != null && fee.signum() < 0) {
            throw new IllegalArgumentException("Transportation fee cannot be negative");
        }
        s.setOutdoorTransportationFee(fee != null && fee.signum() == 0 ? null : fee);

        int maxPhotos = dto.getMaxPhotosPerItem() == null
                ? DEFAULT_PHOTOS_PER_ITEM
                : dto.getMaxPhotosPerItem();
        if (maxPhotos < MIN_PHOTOS_PER_ITEM || maxPhotos > MAX_PHOTOS_PER_ITEM_CAP) {
            throw new IllegalArgumentException(
                    "Max photos per item must be between " + MIN_PHOTOS_PER_ITEM + " and " + MAX_PHOTOS_PER_ITEM_CAP);
        }
        s.setMaxPhotosPerItem(maxPhotos);

        String rejection = trimToEmpty(dto.getBookingRejectionMessage());
        s.setBookingRejectionMessage(rejection.isEmpty() ? DEFAULT_REJECTION_MESSAGE : rejection);

        boolean outdoorEnabled = dto.getOutdoorBookingEnabled() == null || dto.getOutdoorBookingEnabled();
        s.setOutdoorBookingEnabled(outdoorEnabled);

        String disabledReason = trimToEmpty(dto.getOutdoorBookingDisabledReason());
        if (!outdoorEnabled && disabledReason.isEmpty()) {
            disabledReason = DEFAULT_OUTDOOR_DISABLED_REASON;
        }
        s.setOutdoorBookingDisabledReason(disabledReason.isEmpty() ? null : disabledReason);

        int advanceDays = dto.getMaxAdvanceBookingDays() == null ? 7 : dto.getMaxAdvanceBookingDays();
        if (advanceDays < MIN_ADVANCE_DAYS || advanceDays > MAX_ADVANCE_DAYS) {
            throw new IllegalArgumentException(
                    "Max advance booking days must be between " + MIN_ADVANCE_DAYS + " and " + MAX_ADVANCE_DAYS);
        }
        s.setMaxAdvanceBookingDays(advanceDays);

        int noticeHours = dto.getMinNoticeHours() == null ? 2 : dto.getMinNoticeHours();
        if (noticeHours < MIN_NOTICE_HOURS || noticeHours > MAX_NOTICE_HOURS) {
            throw new IllegalArgumentException(
                    "Min notice hours must be between " + MIN_NOTICE_HOURS + " and " + MAX_NOTICE_HOURS);
        }
        s.setMinNoticeHours(noticeHours);

        s.setAllowSameDayBooking(dto.getAllowSameDayBooking() == null || dto.getAllowSameDayBooking());
        s.setAllowEmergencyRequest(dto.getAllowEmergencyRequest() == null || dto.getAllowEmergencyRequest());

        return toDto(repository.save(s));
    }

    public ServiceBookingSettings getOrCreate() {
        List<ServiceBookingSettings> all = repository.findAll();
        if (!all.isEmpty()) {
            ServiceBookingSettings existing = all.get(0);
            if (existing.getMaxPhotosPerItem() == null || existing.getMaxPhotosPerItem() < MIN_PHOTOS_PER_ITEM) {
                existing.setMaxPhotosPerItem(clampPhotosPerItem(propertyDefaultMaxPhotosPerItem));
            }
            if (existing.getOutdoorBookingEnabled() == null) {
                existing.setOutdoorBookingEnabled(true);
            }
            if (existing.getBookingRejectionMessage() == null || existing.getBookingRejectionMessage().isBlank()) {
                existing.setBookingRejectionMessage(DEFAULT_REJECTION_MESSAGE);
            }
            if (existing.getMaxAdvanceBookingDays() == null) {
                existing.setMaxAdvanceBookingDays(7);
            }
            if (existing.getMinNoticeHours() == null) {
                existing.setMinNoticeHours(2);
            }
            if (existing.getAllowSameDayBooking() == null) {
                existing.setAllowSameDayBooking(true);
            }
            if (existing.getAllowEmergencyRequest() == null) {
                existing.setAllowEmergencyRequest(true);
            }
            return existing;
        }
        return repository.save(ServiceBookingSettings.builder()
                .outdoorTransportationNotice(DEFAULT_OUTDOOR_NOTICE)
                .maxPhotosPerItem(clampPhotosPerItem(propertyDefaultMaxPhotosPerItem))
                .bookingRejectionMessage(DEFAULT_REJECTION_MESSAGE)
                .outdoorBookingEnabled(true)
                .maxAdvanceBookingDays(7)
                .minNoticeHours(2)
                .allowSameDayBooking(true)
                .allowEmergencyRequest(true)
                .build());
    }

    private int clampPhotosPerItem(int value) {
        return Math.max(MIN_PHOTOS_PER_ITEM, Math.min(MAX_PHOTOS_PER_ITEM_CAP, value));
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private ServiceBookingSettingsDTO toDto(ServiceBookingSettings s) {
        ServiceBookingSettingsDTO dto = new ServiceBookingSettingsDTO();
        dto.setOutdoorTransportationNotice(s.getOutdoorTransportationNotice());
        dto.setOutdoorTransportationFee(s.getOutdoorTransportationFee());
        dto.setMaxPhotosPerItem(s.getMaxPhotosPerItem() == null
                ? clampPhotosPerItem(propertyDefaultMaxPhotosPerItem)
                : clampPhotosPerItem(s.getMaxPhotosPerItem()));
        String rejection = s.getBookingRejectionMessage();
        dto.setBookingRejectionMessage(
                rejection == null || rejection.isBlank() ? DEFAULT_REJECTION_MESSAGE : rejection);
        dto.setOutdoorBookingEnabled(s.getOutdoorBookingEnabled() == null || s.getOutdoorBookingEnabled());
        dto.setOutdoorBookingDisabledReason(s.getOutdoorBookingDisabledReason());
        dto.setMaxAdvanceBookingDays(s.getMaxAdvanceBookingDays() == null ? 7 : s.getMaxAdvanceBookingDays());
        dto.setMinNoticeHours(s.getMinNoticeHours() == null ? 2 : s.getMinNoticeHours());
        dto.setAllowSameDayBooking(s.getAllowSameDayBooking() == null || s.getAllowSameDayBooking());
        dto.setAllowEmergencyRequest(s.getAllowEmergencyRequest() == null || s.getAllowEmergencyRequest());
        return dto;
    }
}
