package org.sspd.servicemgmt.technicianvisitoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.exceptionhandler.ConflictException;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceMode;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.technicianvisitoptions.dto.LocationPingRequest;
import org.sspd.servicemgmt.technicianvisitoptions.dto.LocationPingDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.TechnicianVisitDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.TechnicianVisitReportDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.VisitEventDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.VisitReasonRequest;
import org.sspd.servicemgmt.technicianvisitoptions.model.StaffLiveLocation;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianLocationPing;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisit;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitEvent;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitEventType;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitStatus;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitPurpose;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitOutcome;
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
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TechnicianVisitService {

    private static final List<TechnicianVisitStatus> ACTIVE = List.of(
            TechnicianVisitStatus.EN_ROUTE, TechnicianVisitStatus.ON_SITE, TechnicianVisitStatus.RETURNING);
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
    public TechnicianVisitDTO start(Integer jobId, String purpose, LocationPingRequest loc, Authentication auth) {
        Staff staff = currentStaff(auth);
        staffs.findByIdForUpdate(staff.getId()).orElseThrow(() -> new AccessDeniedException("Staff not found"));
        validate(loc);
        if (!visits.lockActive(staff.getId(), ACTIVE).isEmpty()) {
            throw new ConflictException("သင့်တွင် Active visit ရှိနေပါသည်။ အရင် Visit ကို ပိတ်ပါ");
        }
        ServiceJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Service job not found"));
        // Outdoor technicians open any customer job; they cannot assign themselves.
        // Visit start is already gated by CAN_ACCESS_TECHNICIAN_VISIT_START.
        if (job.getCustomer() == null) {
            throw new IllegalArgumentException("Service job has no customer");
        }
        if (job.getServiceMode() != ServiceMode.OUTDOOR) {
            throw new IllegalArgumentException("INDOOR Job တွင် Outdoor Visit စတင်၍မရပါ");
        }
        LocalDateTime now = LocalDateTime.now();
        TechnicianVisit visit = visits.save(TechnicianVisit.builder()
                .staff(staff)
                .serviceJob(job)
                .customer(job.getCustomer())
                .status(TechnicianVisitStatus.EN_ROUTE)
                .purpose(parsePurpose(purpose))
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
    public TechnicianVisitDTO departCustomer(Long id, String outcome, String note, LocationPingRequest loc, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        validate(loc);
        if (visit.getStatus() != TechnicianVisitStatus.ON_SITE) {
            throw new IllegalStateException("Visit is not ON_SITE");
        }
        TechnicianVisitOutcome parsedOutcome = parseOutcome(outcome);
        visit.setOutcome(parsedOutcome);
        visit.setOutcomeNote(note == null || note.isBlank() ? null : note.trim());
        ServiceJob job = visit.getServiceJob();
        if (parsedOutcome == TechnicianVisitOutcome.BROUGHT_TO_SHOP
                && job.getStatus() != ServiceJobStatus.COMPLETED
                && job.getStatus() != ServiceJobStatus.DELIVERED
                && job.getStatus() != ServiceJobStatus.CANCELLED) {
            job.setStatus(ServiceJobStatus.IN_PROGRESS);
            jobs.save(job);
        } else if (parsedOutcome == TechnicianVisitOutcome.PARTS_REQUIRED
                && job.getStatus() != ServiceJobStatus.COMPLETED
                && job.getStatus() != ServiceJobStatus.DELIVERED
                && job.getStatus() != ServiceJobStatus.CANCELLED) {
            job.setStatus(ServiceJobStatus.WAITING_PARTS);
            jobs.save(job);
        }
        visit.setStatus(TechnicianVisitStatus.RETURNING);
        visit.setLeftCustomerAt(LocalDateTime.now());
        visit.setDepartureLatitude(loc.latitude());
        visit.setDepartureLongitude(loc.longitude());
        visit.setLastMovedAt(LocalDateTime.now());
        savePing(visit, loc);
        upsertLive(visit, loc);
        addEvent(visit, TechnicianVisitEventType.CUSTOMER_DEPARTED, loc, parsedOutcome.name(), visit.getOutcomeNote());
        return publish(visits.save(visit));
    }

    @Transactional
    public TechnicianVisitDTO end(Long id, LocationPingRequest loc, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        validate(loc);
        if (!ACTIVE.contains(visit.getStatus())) {
            throw new IllegalStateException("Visit is not active");
        }
        if (visit.getLeftCustomerAt() == null && visit.getStatus() == TechnicianVisitStatus.ON_SITE) {
            visit.setLeftCustomerAt(LocalDateTime.now());
            visit.setDepartureLatitude(loc.latitude());
            visit.setDepartureLongitude(loc.longitude());
            addEvent(visit, TechnicianVisitEventType.CUSTOMER_DEPARTED, loc, null, "Legacy direct completion");
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
        String note = request == null || request.note() == null || request.note().isBlank()
                ? null : request.note().trim();
        if ("OTHER".equals(code) && note == null) {
            throw new IllegalArgumentException("ကိုယ်တိုင်ရေးသည့် အကြောင်းပြချက် ထည့်ပါ");
        }
        LocationPingRequest loc = request == null ? null : request.location();
        if (loc != null) {
            validate(loc);
            savePing(visit, loc);
            upsertLive(visit, loc);
        } else {
            StaffLiveLocation current = live.findByStaffId(visit.getStaff().getId()).orElse(null);
            loc = current == null ? null : new LocationPingRequest(
                    null, current.getLatitude(), current.getLongitude(), current.getAccuracy(), null);
        }
        addEvent(visit, TechnicianVisitEventType.REASON_ADDED, loc, code, note);
        return publish(visit);
    }

    @Transactional
    public TechnicianVisitDTO resumeJourney(Long id, LocationPingRequest loc, Authentication auth) {
        TechnicianVisit visit = owned(id, auth);
        validate(loc);
        if (visit.getStatus() != TechnicianVisitStatus.EN_ROUTE
                && visit.getStatus() != TechnicianVisitStatus.RETURNING) {
            throw new IllegalStateException("ခရီးသွားနေသော Visit မဟုတ်ပါ");
        }

        List<TechnicianVisitEvent> timeline =
                events.findByVisitIdOrderByOccurredAtAscIdAsc(visit.getId());
        TechnicianVisitEvent latestStop = null;
        TechnicianVisitEvent latestResume = null;
        for (TechnicianVisitEvent event : timeline) {
            if (event.getEventType() == TechnicianVisitEventType.STOPPED
                    || event.getEventType() == TechnicianVisitEventType.LONG_STOP) {
                latestStop = event;
            } else if (event.getEventType() == TechnicianVisitEventType.RESUMED) {
                latestResume = event;
            }
        }
        if (latestStop == null || (latestResume != null
                && !latestResume.getOccurredAt().isBefore(latestStop.getOccurredAt()))) {
            throw new IllegalStateException("လက်ရှိခရီးစဉ်တွင် ဆက်သွားရန်လိုသော Stop မရှိပါ");
        }
        if (latestStop.getEventType() == TechnicianVisitEventType.LONG_STOP) {
            LocalDateTime stoppedAt = latestStop.getOccurredAt();
            boolean hasReason = timeline.stream().anyMatch(event ->
                    event.getEventType() == TechnicianVisitEventType.REASON_ADDED
                            && !event.getOccurredAt().isBefore(stoppedAt));
            if (!hasReason) {
                throw new IllegalStateException("Long Stop အကြောင်းပြချက်ကို အရင်သိမ်းပါ");
            }
        }

        savePing(visit, loc);
        upsertLive(visit, loc);
        visit.setLastMovedAt(LocalDateTime.now());
        addEvent(visit, TechnicianVisitEventType.RESUMED, loc, "MANUAL", "Technician မှ ခရီးဆက်ပြီဟု အတည်ပြု");
        return publish(visits.save(visit));
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
    public List<TechnicianVisitDTO> today() {
        LocalDateTime now = LocalDateTime.now();
        return visits.findByStartedAtGreaterThanEqualAndStartedAtLessThanEqualOrderByStartedAtDesc(
                        now.toLocalDate().atStartOfDay(), now)
                .stream()
                .map(visit -> toDto(visit, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public TechnicianVisitDTO historyDetail(Long id) {
        TechnicianVisit visit = visits.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Visit not found"));
        return toDto(visit, true);
    }

    @Transactional(readOnly = true)
    public List<LocationPingDTO> historyPings(Long id) {
        if (!visits.existsById(id)) {
            throw new IllegalArgumentException("Visit not found");
        }
        return pings.findByVisit_IdOrderByRecordedAtAscIdAsc(id).stream()
                .map(ping -> new LocationPingDTO(
                        ping.getId(),
                        ping.getLatitude(),
                        ping.getLongitude(),
                        ping.getAccuracy(),
                        ping.getRecordedAt()
                ))
                .toList();
    }

    @Transactional
    public int deleteVisitGpsHistory(
            Long id,
            String confirmation,
            String reason,
            Authentication authentication
    ) {
        TechnicianVisit visit = visits.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Visit not found"));
        String requiredConfirmation = "DELETE GPS " + id;
        if (!requiredConfirmation.equals(confirmation)) {
            throw new IllegalArgumentException("Confirmation must be exactly: " + requiredConfirmation);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("GPS history deletion reason is required");
        }
        int deleted = pings.deleteByVisit_Id(id);
        addEvent(
                visit,
                TechnicianVisitEventType.GPS_HISTORY_DELETED,
                null,
                "ADMIN_DELETE",
                "Deleted " + deleted + " GPS points by " + authentication.getName()
                        + ". Reason: " + reason.trim()
        );
        return deleted;
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

    @Transactional(readOnly = true)
    public List<TechnicianVisitReportDTO> report(
            LocalDateTime from,
            LocalDateTime to,
            String job,
            String customer
    ) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(7) : from;
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Report start date must be before end date");
        }
        String jobQuery = normalizeSearch(job);
        String customerQuery = normalizeSearch(customer);
        return visits.findByStartedAtGreaterThanEqualAndStartedAtLessThanEqualOrderByStartedAtDesc(start, end)
                .stream()
                .filter(visit -> matchesSearch(visit.getServiceJob().getJobNo(), jobQuery))
                .filter(visit -> matchesSearch(visit.getCustomer().getName(), customerQuery))
                .map(this::toReportDto)
                .toList();
    }

    private TechnicianVisitReportDTO toReportDto(TechnicianVisit visit) {
        List<TechnicianLocationPing> tripPings =
                pings.findByVisit_IdOrderByRecordedAtAscIdAsc(visit.getId());
        List<TechnicianVisitEvent> timeline =
                events.findByVisitIdOrderByOccurredAtAscIdAsc(visit.getId());

        double distanceMeters = 0d;
        long maxGapMinutes = 0L;
        boolean inaccurateGps = false;
        for (int index = 0; index < tripPings.size(); index++) {
            TechnicianLocationPing ping = tripPings.get(index);
            inaccurateGps = inaccurateGps
                    || (ping.getAccuracy() != null
                    && ping.getAccuracy().compareTo(BigDecimal.valueOf(100)) > 0);
            if (index == 0) {
                continue;
            }
            TechnicianLocationPing previous = tripPings.get(index - 1);
            distanceMeters += haversineMeters(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    ping.getLatitude(),
                    ping.getLongitude()
            );
            maxGapMinutes = Math.max(
                    maxGapMinutes,
                    Math.max(0, Duration.between(previous.getRecordedAt(), ping.getRecordedAt()).toMinutes())
            );
        }

        StopAggregation stop = aggregateStops(timeline, visit.getEndedAt());
        Double arrivalDistance = null;
        if (visit.getArriveLatitude() != null && visit.getArriveLongitude() != null
                && visit.getCustomer().getLatitude() != null && visit.getCustomer().getLongitude() != null) {
            arrivalDistance = haversineMeters(
                    visit.getArriveLatitude(),
                    visit.getArriveLongitude(),
                    visit.getCustomer().getLatitude(),
                    visit.getCustomer().getLongitude()
            );
        }
        List<String> gpsIssues = new ArrayList<>();
        if (tripPings.isEmpty()) gpsIssues.add("NO_GPS");
        if (maxGapMinutes > 5) gpsIssues.add("GPS_GAP");
        if (inaccurateGps) gpsIssues.add("LOW_ACCURACY");

        return new TechnicianVisitReportDTO(
                visit.getId(),
                visit.getStaff().getId(),
                visit.getStaff().getName(),
                visit.getServiceJob().getId(),
                visit.getServiceJob().getJobNo(),
                visit.getCustomer().getId(),
                visit.getCustomer().getName(),
                visit.getStatus().name(),
                visit.getStartedAt(),
                visit.getArrivedAt(),
                visit.getLeftCustomerAt(),
                visit.getEndedAt(),
                minutesBetween(visit.getStartedAt(), visit.getArrivedAt()),
                minutesBetween(visit.getArrivedAt(), visit.getLeftCustomerAt()),
                minutesBetween(visit.getLeftCustomerAt(), visit.getEndedAt()),
                minutesBetween(visit.getStartedAt(), visit.getEndedAt()),
                Math.round(distanceMeters * 10d) / 10d,
                arrivalDistance == null ? null : Math.round(arrivalDistance * 10d) / 10d,
                arrivalDistance == null ? null : arrivalDistance <= NEAR_CUSTOMER_METERS,
                stop.count(),
                stop.minutes(),
                stop.reasons(),
                tripPings.size(),
                maxGapMinutes,
                gpsIssues.isEmpty() ? null : String.join(",", gpsIssues)
        );
    }

    private static StopAggregation aggregateStops(
            List<TechnicianVisitEvent> timeline,
            LocalDateTime visitEndedAt
    ) {
        LocalDateTime stopStarted = null;
        int count = 0;
        long minutes = 0L;
        List<String> reasons = new ArrayList<>();
        for (TechnicianVisitEvent event : timeline) {
            if ((event.getEventType() == TechnicianVisitEventType.STOPPED
                    || event.getEventType() == TechnicianVisitEventType.LONG_STOP)
                    && stopStarted == null) {
                stopStarted = event.getOccurredAt();
                count++;
                continue;
            }
            if (event.getEventType() == TechnicianVisitEventType.REASON_ADDED) {
                String code = event.getReasonCode();
                String note = event.getNote();
                String reason = code == null || code.isBlank()
                        ? note
                        : note == null || note.isBlank() ? code : code + ": " + note;
                if (reason != null) reasons.add(reason);
                continue;
            }
            if (stopStarted != null && (event.getEventType() == TechnicianVisitEventType.RESUMED
                    || event.getEventType() == TechnicianVisitEventType.ARRIVED
                    || event.getEventType() == TechnicianVisitEventType.CUSTOMER_DEPARTED
                    || event.getEventType() == TechnicianVisitEventType.ENDED
                    || event.getEventType() == TechnicianVisitEventType.CANCELLED)) {
                minutes += Math.max(0, Duration.between(stopStarted, event.getOccurredAt()).toMinutes());
                stopStarted = null;
            }
        }
        if (stopStarted != null) {
            LocalDateTime stopEnd = visitEndedAt == null ? LocalDateTime.now() : visitEndedAt;
            minutes += Math.max(0, Duration.between(stopStarted, stopEnd).toMinutes());
        }
        return new StopAggregation(count, minutes, List.copyOf(reasons));
    }

    private static Long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || to.isBefore(from)) return null;
        return Duration.between(from, to).toMinutes();
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSearch(String value, String query) {
        if (query == null) return true;
        String normalized = normalizeSearch(value);
        return normalized != null && normalized.contains(query);
    }

    private record StopAggregation(int count, long minutes, List<String> reasons) {}

    private void applyPing(TechnicianVisit visit, LocationPingRequest loc) {
        validate(loc);
        if (!ACTIVE.contains(visit.getStatus())) {
            throw new IllegalStateException("Visit is not active");
        }
        StaffLiveLocation previous = live.findByStaffId(visit.getStaff().getId()).orElse(null);
        double moved = previous == null ? MOVE_METERS + 1 : haversineMeters(
                previous.getLatitude(), previous.getLongitude(), loc.latitude(), loc.longitude());
        LocalDateTime lastMoved = visit.getLastMovedAt() == null
                ? visit.getStartedAt() : visit.getLastMovedAt();
        List<TechnicianVisitEvent> timeline =
                events.findByVisitIdOrderByOccurredAtAscIdAsc(visit.getId());
        if (moved >= MOVE_METERS) {
            boolean stoppedSinceLastMove = timeline.stream().anyMatch(event ->
                    (event.getEventType() == TechnicianVisitEventType.STOPPED
                            || event.getEventType() == TechnicianVisitEventType.LONG_STOP)
                            && lastMoved != null
                            && !event.getOccurredAt().isBefore(lastMoved));
            if (stoppedSinceLastMove) {
                addEvent(visit, TechnicianVisitEventType.RESUMED, loc, null, null);
            }
            visit.setLastMovedAt(LocalDateTime.now());
        } else if (lastMoved != null
                && (visit.getStatus() == TechnicianVisitStatus.EN_ROUTE
                || visit.getStatus() == TechnicianVisitStatus.RETURNING)) {
            long idleMinutes = Duration.between(lastMoved, LocalDateTime.now()).toMinutes();
            boolean stoppedRecorded = timeline.stream().anyMatch(event ->
                    event.getEventType() == TechnicianVisitEventType.STOPPED
                            && !event.getOccurredAt().isBefore(lastMoved));
            boolean longStopRecorded = timeline.stream().anyMatch(event ->
                    event.getEventType() == TechnicianVisitEventType.LONG_STOP
                            && !event.getOccurredAt().isBefore(lastMoved));
            if (idleMinutes >= STOPPED_MINUTES && !stoppedRecorded) {
                addEvent(visit, TechnicianVisitEventType.STOPPED, loc, null, null);
            }
            if (idleMinutes >= LONG_STOP_MINUTES && !longStopRecorded) {
                addEvent(visit, TechnicianVisitEventType.LONG_STOP, loc, null, null);
            }
        }
        savePing(visit, loc);
        upsertLive(visit, loc);
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
        User user = users.findWithStaffByUsernameOrEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        if (user.getStaff() == null) {
            throw new AccessDeniedException("ဤအကောင့်ကို Staff နှင့် ချိတ်မထားပါ");
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
            throw new IllegalArgumentException("GPS တည်နေရာ မမှန်ကန်ပါ");
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messaging.convertAndSend("/topic/technician-location", dto);
                }
            });
        } else {
            messaging.convertAndSend("/topic/technician-location", dto);
        }
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
        boolean needsReason = (visit.getStatus() == TechnicianVisitStatus.EN_ROUTE
                || visit.getStatus() == TechnicianVisitStatus.RETURNING)
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
        LocalDateTime arrivedAt = visit.getArrivedAt();
        if (arrivedAt == null && includeEvents) {
            arrivedAt = eventDtos.stream()
                    .filter(event -> "ARRIVED".equals(event.eventType()))
                    .map(VisitEventDTO::occurredAt)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return new TechnicianVisitDTO(
                visit.getId(),
                visit.getStaff().getId(),
                visit.getStaff().getName(),
                visit.getServiceJob().getId(),
                visit.getServiceJob().getJobNo(),
                customer.getId(),
                customer.getName(),
                visit.getPurpose() == null ? TechnicianVisitPurpose.SERVICE.name() : visit.getPurpose().name(),
                visit.getOutcome() == null ? null : visit.getOutcome().name(),
                visit.getOutcomeNote(),
                visit.getStatus().name(),
                motionStatus(visit, row),
                needsReason,
                visit.getStartedAt(),
                arrivedAt,
                visit.getLeftCustomerAt(),
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
        return visit.getStatus() == TechnicianVisitStatus.RETURNING ? "RETURNING" : "MOVING";
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

    private static TechnicianVisitPurpose parsePurpose(String raw) {
        try { return TechnicianVisitPurpose.valueOf(raw == null ? "SERVICE" : raw.trim().toUpperCase()); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid visit purpose: " + raw); }
    }

    private static TechnicianVisitOutcome parseOutcome(String raw) {
        try { return TechnicianVisitOutcome.valueOf(raw == null ? "FIXED_ON_SITE" : raw.trim().toUpperCase()); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid visit outcome: " + raw); }
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
