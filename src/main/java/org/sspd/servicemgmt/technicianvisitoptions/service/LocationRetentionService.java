package org.sspd.servicemgmt.technicianvisitoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianLocationPingRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LocationRetentionService {

    private final TechnicianLocationPingRepository repository;

    @Transactional
    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Rangoon")
    public void purge() {
        repository.deleteOlderThan(LocalDateTime.now().minusDays(7));
    }
}
