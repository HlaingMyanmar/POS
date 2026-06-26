package org.sspd.servicemgmt.appsettingsoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.appsettingsoptions.dto.AppVersionSettingsDTO;
import org.sspd.servicemgmt.appsettingsoptions.model.AppVersionSettings;
import org.sspd.servicemgmt.appsettingsoptions.repository.AppVersionSettingsRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppVersionSettingsService {

    private final AppVersionSettingsRepository repository;

    @Transactional(readOnly = true)
    public AppVersionSettingsDTO getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional
    public AppVersionSettingsDTO saveSettings(AppVersionSettingsDTO dto) {
        AppVersionSettings s = getOrCreate();
        if (dto.getVersionCode() != null && dto.getVersionCode() > 0) {
            s.setVersionCode(dto.getVersionCode());
        }
        if (dto.getVersionName() != null && !dto.getVersionName().isBlank()) {
            s.setVersionName(dto.getVersionName().trim());
        }
        s.setForceUpdate(dto.isForceUpdate());
        s.setChangelog(dto.getChangelog() != null ? dto.getChangelog() : "");
        return toDto(repository.save(s));
    }

    public AppVersionSettings getOrCreate() {
        List<AppVersionSettings> all = repository.findAll();
        if (!all.isEmpty()) return all.get(0);
        return repository.save(AppVersionSettings.builder().build());
    }

    private AppVersionSettingsDTO toDto(AppVersionSettings s) {
        AppVersionSettingsDTO dto = new AppVersionSettingsDTO();
        dto.setVersionCode(s.getVersionCode());
        dto.setVersionName(s.getVersionName());
        dto.setForceUpdate(s.isForceUpdate());
        dto.setChangelog(s.getChangelog());
        return dto;
    }
}
