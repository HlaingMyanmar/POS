package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingSettingsDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingSettings;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingSettingsRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBookingSettingsRulesTest {

    @Mock ServiceBookingSettingsRepository repository;
    private ServiceBookingSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ServiceBookingSettingsService(repository);
    }

    @Test
    void loadsExistingSettingsWithRuleDefaults() {
        ServiceBookingSettings existing = ServiceBookingSettings.builder()
                .id(1)
                .maxPhotosPerItem(50)
                .outdoorBookingEnabled(true)
                .bookingRejectionMessage("rejected")
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));

        ServiceBookingSettingsDTO dto = service.getSettings();
        assertEquals(7, dto.getMaxAdvanceBookingDays());
        assertEquals(2, dto.getMinNoticeHours());
        assertTrue(dto.getAllowSameDayBooking());
        assertTrue(dto.getAllowEmergencyRequest());
    }

    @Test
    void rejectsNegativeAdvanceDays() {
        when(repository.findAll()).thenReturn(List.of(ServiceBookingSettings.builder()
                .id(1).maxPhotosPerItem(50).outdoorBookingEnabled(true)
                .bookingRejectionMessage("x").maxAdvanceBookingDays(7).minNoticeHours(2)
                .allowSameDayBooking(true).allowEmergencyRequest(true).build()));

        ServiceBookingSettingsDTO dto = service.getSettings();
        dto.setMaxAdvanceBookingDays(-1);
        assertThrows(IllegalArgumentException.class, () -> service.saveSettings(dto));
    }
}
