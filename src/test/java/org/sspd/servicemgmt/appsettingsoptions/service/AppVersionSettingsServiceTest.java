package org.sspd.servicemgmt.appsettingsoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.appsettingsoptions.dto.AppVersionSettingsDTO;
import org.sspd.servicemgmt.appsettingsoptions.model.AppVersionSettings;
import org.sspd.servicemgmt.appsettingsoptions.repository.AppVersionSettingsRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppVersionSettingsServiceTest {

    @Test
    void savesTechnicianVersionIndependentlyFromPosVersion() {
        AppVersionSettingsRepository repository = mock(AppVersionSettingsRepository.class);
        AppVersionSettings existing = AppVersionSettings.builder()
                .id(1)
                .versionCode(17)
                .versionName("1.3.1")
                .technicianVersionCode(1)
                .technicianVersionName("1.0.0")
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.save(any(AppVersionSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppVersionSettingsDTO dto = new AppVersionSettingsDTO();
        dto.setVersionCode(17);
        dto.setVersionName("1.3.1");
        dto.setTechnicianVersionCode(2);
        dto.setTechnicianVersionName("1.0.1");
        dto.setTechnicianForceUpdate(true);
        dto.setTechnicianChangelog("GPS recovery");

        AppVersionSettingsDTO saved = new AppVersionSettingsService(repository).saveSettings(dto);

        assertEquals(17, saved.getVersionCode());
        assertEquals("1.3.1", saved.getVersionName());
        assertEquals(2, saved.getTechnicianVersionCode());
        assertEquals("1.0.1", saved.getTechnicianVersionName());
        assertTrue(saved.isTechnicianForceUpdate());
        assertEquals("GPS recovery", saved.getTechnicianChangelog());
    }
}
