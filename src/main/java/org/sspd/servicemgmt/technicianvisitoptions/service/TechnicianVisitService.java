package org.sspd.servicemgmt.technicianvisitoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.exceptionhandler.ConflictException;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.technicianvisitoptions.dto.LocationPingRequest;
import org.sspd.servicemgmt.technicianvisitoptions.dto.TechnicianVisitDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.VisitEventDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.VisitReasonRequest;
import org.sspd.servicemgmt.technicianvisitoptions.model.StaffLiveLocation;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianLocationPing;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisit;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitEvent;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitEventType;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitStatus;
import org.sspd.servicemgmt.technicianvisitoptions.repository.StaffLiveLocationRepository;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianLocationPingRepository;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianVisitEventRepository;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianVisitRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TechnicianVisitService {

    private static final List<TechnicianVisitStatus> ACTIVE = List.of(
            TechnicianVisitStatus.EN_ROUTE, TechnicianVisitStatus.ON_SITE);
    private static final ZoneId ZONE = ZoneId.of("Asia/Rangoon");
    private static final double MOVE_METERS = 40d;
    private static final double NEAR_CUSTOMER_METERS = 100d;
    private static final int STOPPED_MINUTES = 5;
    private static final int LONG_STOP_MINUTES = 15;
    private static final int STALE_MINUTES = 3;

    private final TechnicianVisitRepository visits;
    private final StaffLiveLocationRepository live;
    private final TechnicianLocationPingRepository pings;
    private final TechnicianVisitEventRepository events;
    private final ServiceJobRepository jobs;
    private final StaffRepository staffs;
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;

    @Transactional
    public TechnicianVisitDTO start(Integer jobId, LocationPingRequest loc, Authentication auth) {
        Staff staff = currentStaff(auth);
        staffs.findByIdForUpdate(staff.getId()).orElseThrow(() -> new AccessDeniedException("Staff not found"));
        validate(loc);
        if (!visits.lockActive(staff.getId(), ACTIVE).isEmpty()) {
            throw new ConflictException("Technician already has an active visit");
        }
        ServiceJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Service job not found"));
        if (job.getAssignedStaff() == null || !Objects.equals(job.getAssignedStaff().getId(), staff.getId())) {
            throw new AccessDeniedException("Only the assigned technician can start this visit");
        }
        if (job.getCustomer() == null) {
            throw new IllegalArgumentException("Service job has no customer");
        }
        LocalDateTime now = LocalDateTime.now();
        TechnicianVisit visit = visits.save(TechnicianVisit.builder()
                .staff(staff)
                .serviceJob(job)
                .customer(job.getCustomer())
                .status(TechnicianVisitStatus.EN_ROUTE)
                .startedAt(now)
                .lastMovedAt(now)
                .startLatitude(loc.latitude())
                .startLongitude(loc.longitude())
                .build());
        savePing(visit, loc);
        upsertLive(visit, loc);
        addEvent(visit, TechnicianVisitEventType.STARTED, loc, null, null);
        return publish(visit);
    }

    @Transactional
    public TechnicianVisitDTO arrive(Long id, LocationPingRequest loc, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        validate(loc);
        if (visit.getStatus() != TechnicianVisitStatus.EN_ROUTE) {
            throw new IllegalStateException("Visit is not EN_ROUTE");
        }
        visit.setStatus(TechnicianVisitStatus.ON_SITE);
        visit.setArrivedAt(LocalDateTime.now());
        visit.setArriveLatitude(loc.latitude());
        visit.setArriveLongitude(loc.longitude());
        visit.setLastMovedAt(LocalDateTime.now());
        savePing(visit, loc);
        upsertLive(visit, loc);
        addEvent(visit, TechnicianVisitEventType.ARRIVED, loc, null, null);
        return publish(visits.save(visit));
    }

    @Transactional
    public TechnicianVisitDTO end(Long id, LocationPingRequest loc, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        validate(loc);
        if (!ACTIVE.contains(visit.getStatus())) {
            throw new IllegalStateException("Visit is not active");
        }
        visit.setStatus(TechnicianVisitStatus.COMPLETED);
        visit.setEndedAt(LocalDateTime.now());
        visit.setEndLatitude(loc.latitude());
        visit.setEndLongitude(loc.longitude());
        savePing(visit, loc);
        clearLive(visit, loc);
        addEvent(visit, TechnicianVisitEventType.ENDED, loc, null, null);
        return publish(visits.save(visit));
    }

    @Transactional
    public TechnicianVisitDTO cancel(Long id, String reason, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        if (!ACTIVE.contains(visit.getStatus())) {
            throw new IllegalStateException("Visit is not active");
        }
        visit.setStatus(TechnicianVisitStatus.CANCELLED);
        visit.setEndedAt(LocalDateTime.now());
        visit.setCancelReason(reason);
        clearLive(visit, null);
        addEvent(visit, TechnicianVisitEventType.CANCELLED, null, "WRONG_VISIT", reason);
        return publish(visits.save(visit));
    }

    @Transactional
    public TechnicianVisitDTO ping(Long id, LocationPingRequest loc, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        applyPing(visit, loc);
        return publish(visit);
    }

    @Transactional
    public TechnicianVisitDTO pingBatch(Long id, List<LocationPingRequest> batch, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        if (batch == null || batch.isEmpty()) {
            return toDto(visit, false);
        }
        for (LocationPingRequest loc : batch) {
            applyPing(visit, loc);
        }
        return publish(visit);
    }

    @Transactional
    public TechnicianVisitDTO addReason(Long id, VisitReasonRequest request, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        if (!ACTIVE.contains(visit.getStatus())) {
            throw new IllegalStateException("Visit is not active");
        }
        String code = request == null || request.reasonCode() == null || request.reasonCode().isBlank()
                ? "OTHER" : request.reasonCode().trim().toUpperCase();
        String note = request == null ? null : request.note();
        StaffLiveLocation current = live.findByStaffId(visit.getStaff().getId()).orElse(null);
        LocationPingRequest loc = current == null ? null : new LocationPingRequest(
                null, current.getLatitude(), current.getLongitude(), current.getAccuracy(), null);
        addEvent(visit, TechnicianVisitEventType.REASON_ADDED, loc, code, note);
        return publish(visit);
    }

    @Transactional(readOnly = true)
    public TechnicianVisitDTO active(Authentication auth) {
        Staff staff = currentStaff(auth);
        return visits.findFirstByStaffIdAndStatusInOrderByIdDesc(staff.getId(), ACTIVE)
                .map(visit -> toDto(visit, true))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TechnicianVisitDTO> live() {
        return live.findAll().stream()
                .filter(row -> row.getActiveVisit() != null)
                .map(row -> toDto(row.getActiveVisit(), row, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public TechnicianVisitDTO historyDetail(Long id) {
        TechnicianVisit visit = visits.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Visit not found"));
        return toDto(visit, true);
    }

    @Transactional(readOnly = true)
    public List<TechnicianVisitDTO> history(LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(7) : from;
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        return visits.findByStartedAtGreaterThanEqualAndStartedAtLessThanEqualOrderByStartedAtDesc(start, end)
                .stream()
                .map(visit -> toDto(visit, true))
                .toList();
    }

    private void applyPing(TechnicianVisit visit, LocationPingRequest loc) {
        validate(loc);
        if (!ACTIVE.contains(visit.getStatus())) {
            throw new IllegalStateException("Visit is not active");
        }
        StaffLiveLocation previous = live.findByStaffId(visit.getStaff().getId()).orElse(null);
        double moved = previous == null ? MOVE_METERS + 1 : haversineMeters(
                previous.getLatitude(), previous.getLongitude(), loc.latitude(), loc.longitude());
        String previousMotion = motionStatus(visit, previous);
        if (moved >= MOVE_METERS) {
            visit.setLastMovedAt(LocalDateTime.now());
            if ("STOPPED".equals(previousMotion) || "LONG_STOP".equals(previousMotion)) {
                addEvent(visit, TechnicianVisitEventType.RESUMED, loc, null, null);
            }
        }
        savePing(visit, loc);
        upsertLive(visit, loc);
        StaffLiveLocation updated = live.findByStaffId(visit.getStaff().getId()).orElse(null);
        String nextMotion = motionStatus(visit, updated);
        if ("STOPPED".equals(nextMotion) && !"STOPPED".equals(previousMotion) && !"LONG_STOP".equals(previousMotion)) {
            addEvent(visit, TechnicianVisitEventType.STOPPED, loc, null, null);
        }
        if ("LONG_STOP".equals(nextMotion) && !"LONG_STOP".equals(previousMotion)
                && visit.getStatus() == TechnicianVisitStatus.EN_ROUTE) {
            addEvent(visit, TechnicianVisitEventType.LONG_STOP, loc, null, null);
        }
        Double distance = distanceToCustomer(visit, loc.latitude(), loc.longitude());
        if (distance != null && distance <= NEAR_CUSTOMER_METERS
                && visit.getStatus() == TechnicianVisitStatus.EN_ROUTE
                && events.findByVisitIdOrderByOccurredAtAscIdAsc(visit.getId()).stream()
                .noneMatch(event -> event.getEventType() == TechnicianVisitEventType.NEAR_CUSTOMER)) {
            addEvent(visit, TechnicianVisitEventType.NEAR_CUSTOMER, loc, null, null);
        }
    }

    private TechnicianVisit owned(Long id, Authentication auth) {
        TechnicianVisit visit = visits.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Visit not found"));
        if (!Objects.equals(visit.getStaff().getId(), currentStaff(auth).getId())) {
            throw new AccessDeniedException("Visit belongs to another technician");
        }
        return visit;
    }

    private Staff currentStaff(Authentication auth) {
        User user = users.findByUsernameOrEmail(auth.getName(), auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        if (user.getStaff() == null) {
            throw new AccessDeniedException("User is not linked to staff");
        }
        return user.getStaff();
    }

    private void validate(LocationPingRequest ping) {
        if (ping == null || ping.latitude() == null || ping.longitude() == null
                || ping.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || ping.latitude().compareTo(BigDecimal.valueOf(90)) > 0
                || ping.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || ping.longitude().compareTo(BigDecimal.valueOf(180)) > 0
                || (ping.accuracy() != null && ping.accuracy().signum() < 0)) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
    }

    private void savePing(TechnicianVisit visit, LocationPingRequest ping) {
        String key = ping.clientPingId() == null || ping.clientPingId().isBlank()
                ? UUID.randomUUID().toString() : ping.clientPingId();
        if (pings.existsByClientPingId(key)) {
            return;
        }
        pings.save(TechnicianLocationPing.builder()
                .visit(visit)
                .clientPingId(key)
                .latitude(ping.latitude())
                .longitude(ping.longitude())
                .accuracy(ping.accuracy())
                .recordedAt(parseRecordedAt(ping.recordedAt()))
                .receivedAt(LocalDateTime.now())
                .build());
    }

    private void upsertLive(TechnicianVisit visit, LocationPingRequest ping) {
        StaffLiveLocation row = live.findByStaffId(visit.getStaff().getId())
                .orElseGet(() -> StaffLiveLocation.builder().staff(visit.getStaff()).build());
        row.setActiveVisit(visit);
        row.setLatitude(ping.latitude());
        row.setLongitude(ping.longitude());
        row.setAccuracy(ping.accuracy());
        row.setRecordedAt(parseRecordedAt(ping.recordedAt()));
        row.setServerReceivedAt(LocalDateTime.now());
        live.save(row);
    }

    private void clearLive(TechnicianVisit visit, LocationPingRequest ping) {
        StaffLiveLocation row = live.findByStaffId(visit.getStaff().getId()).orElse(null);
        if (row == null) {
            return;
        }
        if (ping != null) {
            row.setLatitude(ping.latitude());
            row.setLongitude(ping.longitude());
            row.setAccuracy(ping.accuracy());
            row.setRecordedAt(parseRecordedAt(ping.recordedAt()));
        }
        row.setActiveVisit(null);
        row.setServerReceivedAt(LocalDateTime.now());
        live.save(row);
    }

    private void addEvent(
            TechnicianVisit visit,
            TechnicianVisitEventType type,
            LocationPingRequest loc,
            String reason,
            String note
    ) {
        events.save(TechnicianVisitEvent.builder()
                .visit(visit)
                .eventType(type)
                .latitude(loc == null ? null : loc.latitude())
                .longitude(loc == null ? null : loc.longitude())
                .reasonCode(reason)
                .note(note)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    private TechnicianVisitDTO publish(TechnicianVisit visit) {
        TechnicianVisitDTO dto = toDto(visit, false);
        messaging.convertAndSend("/topic/technician-location", dto);
        return dto;
    }

    private TechnicianVisitDTO toDto(TechnicianVisit visit, boolean includeEvents) {
        return live.findByStaffId(visit.getStaff().getId())
                .map(row -> toDto(visit, row, includeEvents))
                .orElseGet(() -> toDto(visit, null, includeEvents));
    }

    private TechnicianVisitDTO toDto(TechnicianVisit visit, StaffLiveLocation row, boolean includeEvents) {
        BigDecimal lat = row == null ? null : row.getLatitude();
        BigDecimal lng = row == null ? null : row.getLongitude();
        Customer customer = visit.getCustomer();
        List<VisitEventDTO> eventDtos = includeEvents
                ? events.findByVisitIdOrderByOccurredAtAscIdAsc(visit.getId()).stream().map(this::toEventDto).toList()
                : List.of();
        boolean needsReason = visit.getStatus() == TechnicianVisitStatus.EN_ROUTE
                && "LONG_STOP".equals(motionStatus(visit, row))
                && eventDtos.stream().noneMatch(event ->
                "REASON_ADDED".equals(event.eventType())
                        && event.occurredAt() != null
                        && visit.getLastMovedAt() != null
                        && !event.occurredAt().isBefore(visit.getLastMovedAt()));
        if (!includeEvents && needsReason) {
            List<VisitEventDTO> recent = events.findByVisitIdOrderByOccurredAtAscIdAsc(visit.getId())
                    .stream().map(this::toEventDto).toList();
            needsReason = recent.stream().noneMatch(event ->
                    "REASON_ADDED".equals(event.eventType())
                            && event.occurredAt() != null
                            && visit.getLastMovedAt() != null
                            && !event.occurredAt().isBefore(visit.getLastMovedAt()));
        }
        return new TechnicianVisitDTO(
                visit.getId(),
                visit.getStaff().getId(),
                visit.getStaff().getName(),
                visit.getServiceJob().getId(),
                visit.getServiceJob().getJobNo(),
                customer.getId(),
                customer.getName(),
                visit.getStatus().name(),
                motionStatus(visit, row),
                needsReason,
                visit.getStartedAt(),
                visit.getArrivedAt(),
                visit.getEndedAt(),
                lat,
                lng,
                row == null ? null : row.getAccuracy(),
                row == null ? null : row.getRecordedAt(),
                customer.getLatitude(),
                customer.getLongitude(),
                distanceToCustomer(visit, lat, lng),
                includeEvents ? eventDtos : List.of()
        );
    }

    private VisitEventDTO toEventDto(TechnicianVisitEvent event) {
        return new VisitEventDTO(
                event.getId(),
                event.getEventType().name(),
                event.getLatitude(),
                event.getLongitude(),
                event.getReasonCode(),
                event.getNote(),
                event.getOccurredAt()
        );
    }

    private String motionStatus(TechnicianVisit visit, StaffLiveLocation row) {
        if (!ACTIVE.contains(visit.getStatus())) {
            return visit.getStatus().name();
        }
        if (visit.getStatus() == TechnicianVisitStatus.ON_SITE) {
            return "ON_SITE";
        }
        LocalDateTime recorded = row == null ? visit.getLastMovedAt() : row.getServerReceivedAt();
        if (recorded != null && Duration.between(recorded, LocalDateTime.now()).toMinutes() >= STALE_MINUTES) {
            return "STALE";
        }
        LocalDateTime lastMoved = visit.getLastMovedAt() == null ? visit.getStartedAt() : visit.getLastMovedAt();
        if (lastMoved == null) {
            return "MOVING";
        }
        long idleMinutes = Duration.between(lastMoved, LocalDateTime.now()).toMinutes();
        if (idleMinutes >= LONG_STOP_MINUTES) {
            return "LONG_STOP";
        }
        if (idleMinutes >= STOPPED_MINUTES) {
            return "STOPPED";
        }
        return "MOVING";
    }

    private Double distanceToCustomer(TechnicianVisit visit, BigDecimal lat, BigDecimal lng) {
        Customer customer = visit.getCustomer();
        if (customer == null || lat == null || lng == null
                || customer.getLatitude() == null || customer.getLongitude() == null) {
            return null;
        }
        return haversineMeters(lat, lng, customer.getLatitude(), customer.getLongitude());
    }

    private static double haversineMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return 0d;
        }
        double rLat1 = Math.toRadians(lat1.doubleValue());
        double rLat2 = Math.toRadians(lat2.doubleValue());
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371000d * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static LocalDateTime parseRecordedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(raw).atZone(ZONE).toLocalDateTime();
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }
}
