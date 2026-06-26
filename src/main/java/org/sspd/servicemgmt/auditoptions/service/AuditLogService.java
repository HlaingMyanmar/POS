package org.sspd.servicemgmt.auditoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.auditoptions.dto.AuditLogDTO;
import org.sspd.servicemgmt.auditoptions.model.AuditLog;
import org.sspd.servicemgmt.auditoptions.repository.AuditLogRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actor, String actorRole, String action, String module,
                    String resourceId, String description, String ipAddress, String deviceType) {
        AuditLog entry = AuditLog.builder()
                .actor(actor != null ? actor : "system")
                .actorRole(actorRole)
                .action(action)
                .module(module)
                .resourceId(resourceId)
                .description(description)
                .ipAddress(ipAddress)
                .deviceType(deviceType)
                .build();
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> search(int page, int size, String actor, String action,
                                    String module, String dateFrom, String dateTo) {
        LocalDateTime from = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDateTime.parse(dateFrom + "T00:00:00") : null;
        LocalDateTime to   = (dateTo   != null && !dateTo.isBlank())
                ? LocalDateTime.parse(dateTo   + "T23:59:59") : null;

        return repository.search(actor, action, module, from, to, PageRequest.of(page, size))
                .map(this::toDto);
    }

    private AuditLogDTO toDto(AuditLog a) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(a.getId());
        dto.setActor(a.getActor());
        dto.setActorRole(a.getActorRole());
        dto.setAction(a.getAction());
        dto.setModule(a.getModule());
        dto.setResourceId(a.getResourceId());
        dto.setDescription(a.getDescription());
        dto.setIpAddress(a.getIpAddress());
        dto.setDeviceType(a.getDeviceType());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
