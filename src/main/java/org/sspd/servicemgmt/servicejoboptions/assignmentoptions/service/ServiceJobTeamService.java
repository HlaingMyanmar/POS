package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.service;

import lombok.RequiredArgsConstructor;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto.*;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.*;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository.*;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobActivity;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobActivityRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ServiceJobTeamService {
    private static final EnumSet<AssignmentStatus> CURRENT = EnumSet.of(
            AssignmentStatus.PENDING, AssignmentStatus.ACTIVE,
            AssignmentStatus.PAUSED, AssignmentStatus.COMPLETED);
    private static final Set<HandoverStatus> SENT_HANDOVER_STATUSES = EnumSet.of(
            HandoverStatus.PENDING, HandoverStatus.ACCEPTED, HandoverStatus.REJECTED);

    private final ServiceJobRepository jobRepository;
    private final ServiceJobAssignmentRepository assignmentRepository;
    private final ServiceJobAssignmentLogRepository logRepository;
    private final ServiceJobHandoverRepository handoverRepository;
    private final ServiceJobActivityRepository activityRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final DataEventPublisher dataEventPublisher;
    private final CompanySettingsRepository companySettingsRepository;

    @Transactional
    public TeamSnapshotDTO snapshot(Integer jobId) {
        ServiceJob job = requireJob(jobId);
        syncFromJob(job);
        return toSnapshot(job);
    }

    @Transactional
    public void syncFromJob(Integer jobId) {
        ServiceJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + jobId));
        syncFromJob(job);
    }

    @Transactional
    public void assertCanComplete(Integer jobId) {
        ServiceJob job = requireJob(jobId);
        syncFromJob(job);
        if (!evaluateCanComplete(jobId))
            throw new IllegalStateException(evaluateCompletionBlockReason(jobId));
    }

    @Transactional
    public AssignmentDTO assign(Integer jobId, AssignmentRequest request) {
        requireManager();
        ServiceJob job = requireOpenJobForUpdate(jobId);
        syncFromJob(job);
        if (request == null || request.getStaffId() == null || request.getRole() == null)
            throw new IllegalArgumentException("Staff and assignment role are required");
        requireTaskDescription(request.getRole(), request.getTaskDescription());
        Staff staff = requireStaff(request.getStaffId());
        if (assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(jobId, staff.getId(), CURRENT))
            throw new IllegalStateException("This technician already has a current assignment on the job");
        if (request.getRole() == AssignmentRole.LEAD
                && assignmentRepository.existsByServiceJobIdAndRoleAndStatusIn(jobId, AssignmentRole.LEAD, CURRENT))
            throw new IllegalStateException("A current lead already exists; use Hand Over to change the lead");

        ServiceJobAssignment assignment = assignmentRepository.save(ServiceJobAssignment.builder()
                .serviceJob(job).staff(staff).role(request.getRole())
                .status(AssignmentStatus.PENDING)
                .taskDescription(trimToNull(request.getTaskDescription()))
                .approvalStatus(request.getRole() == AssignmentRole.HELPER
                    ? AssignmentApprovalStatus.PENDING : AssignmentApprovalStatus.APPROVED)
                .assignedBy(currentUsername()).assignedAt(LocalDateTime.now())
                .lastActionAt(LocalDateTime.now()).build());
            if (job.getStatus() == ServiceJobStatus.RECEIVED) job.setStatus(ServiceJobStatus.ASSIGNED);
            jobRepository.save(job);
        syncLegacyFields(job);
        recordActivity(job, "ASSIGNMENT_CREATED", request.getRole() + " -> " + staff.getName());
        broadcast("JOB_TEAM_CHANGED");
        return toDto(assignment);
    }

    @Transactional
    public AssignmentDTO updateAssignment(Integer jobId, Integer assignmentId, AssignmentRequest request) {
        requireManager();
        requireOpenJobForUpdate(jobId);
        ServiceJobAssignment assignment = requireAssignment(jobId, assignmentId);
        if (!assignment.getStatus().isCurrent())
            throw new IllegalStateException("Closed assignment cannot be edited");
        if (request != null && request.getTaskDescription() != null)
            assignment.setTaskDescription(trimToNull(request.getTaskDescription()));
        if (request != null && request.getRole() != null && request.getRole() != assignment.getRole()) {
            if (request.getRole() == AssignmentRole.LEAD
                    && assignmentRepository.existsByServiceJobIdAndRoleAndStatusIn(jobId, AssignmentRole.LEAD, CURRENT))
                throw new IllegalStateException("A current lead already exists");
            assignment.setRole(request.getRole());
        }
        requireTaskDescription(assignment.getRole(), assignment.getTaskDescription());
        assignment.setLastActionAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        syncLegacyFields(assignment.getServiceJob());
        recordActivity(assignment.getServiceJob(), "ASSIGNMENT_UPDATED", assignment.getStaff().getName());
        broadcast("JOB_TEAM_CHANGED");
        return toDto(assignment);
    }

    @Transactional
    public void cancelAssignment(Integer jobId, Integer assignmentId) {
        requireManager();
        requireOpenJobForUpdate(jobId);
        ServiceJobAssignment assignment = requireAssignment(jobId, assignmentId);
        if (!assignment.getStatus().isCurrent())
            throw new IllegalStateException("Assignment is already closed");
        if (assignment.getRole() == AssignmentRole.LEAD)
            throw new IllegalStateException("Lead assignment cannot be removed; use Hand Over");
        closeTimer(assignment, LocalDateTime.now());
        assignment.setStatus(AssignmentStatus.CANCELED);
        assignment.setEndedAt(LocalDateTime.now());
        assignment.setLastActionAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        syncLegacyFields(assignment.getServiceJob());
        recordActivity(assignment.getServiceJob(), "ASSIGNMENT_CANCELED", assignment.getStaff().getName());
        broadcast("JOB_TEAM_CHANGED");
    }

    @Transactional
    public AssignmentDTO acceptAssignment(Integer jobId, Integer assignmentId) {
        requireOpenJobForUpdate(jobId);
        ServiceJobAssignment assignment = requireAssignment(jobId, assignmentId);
        requireSelfOrManager(assignment.getStaff().getId());
        if (assignment.getStatus() != AssignmentStatus.PENDING)
            throw new IllegalStateException("Only pending assignment can be accepted");
        if (assignment.getApprovalStatus() != AssignmentApprovalStatus.APPROVED)
            throw new IllegalStateException("Supervisor approval is required before accepting this assignment");
        if (assignment.getRole() == AssignmentRole.LEAD) {
            List<ServiceJobAssignment> currentLeads = assignmentRepository
                    .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(jobId, CURRENT).stream()
                    .filter(a -> a.getRole() == AssignmentRole.LEAD)
                    .filter(a -> !a.getId().equals(assignment.getId()))
                    .toList();
            if (!currentLeads.isEmpty())
                throw new IllegalStateException("A current lead already exists; use Hand Over to change the lead");
        }
        LocalDateTime now = LocalDateTime.now();
        assignment.setStatus(AssignmentStatus.ACTIVE);
        assignment.setAcceptedAt(now);
        assignment.setLastActionAt(now);
        assignmentRepository.save(assignment);
        addLog(assignment, AssignmentWorkAction.NOTE, "Assignment accepted");
        syncLegacyFields(assignment.getServiceJob());
        recordActivity(assignment.getServiceJob(), "ASSIGNMENT_ACCEPTED", assignment.getStaff().getName());
        broadcast("JOB_TEAM_CHANGED");
        return toDto(assignment);
    }

    @Transactional
    public AssignmentDTO approveAssignment(Integer jobId, Integer assignmentId) {
        requireManager();
        requireOpenJobForUpdate(jobId);
        ServiceJobAssignment assignment = requireAssignment(jobId, assignmentId);
        if (assignment.getRole() != AssignmentRole.HELPER)
            throw new IllegalStateException("Only Helper assignments require supervisor approval");
        if (assignment.getApprovalStatus() != AssignmentApprovalStatus.PENDING)
            throw new IllegalStateException("Assignment approval is already decided");
        assignment.setApprovalStatus(AssignmentApprovalStatus.APPROVED);
        assignment.setApprovedBy(currentUsername());
        assignment.setApprovedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        recordActivity(assignment.getServiceJob(), "HELPER_APPROVED", assignment.getStaff().getName());
        broadcast("JOB_TEAM_CHANGED");
        return toDto(assignment);
    }
    @Transactional
    public AssignmentDTO rejectAssignment(Integer jobId, Integer assignmentId, AssignmentDecisionRequest request) {
        requireOpenJobForUpdate(jobId);
        ServiceJobAssignment assignment = requireAssignment(jobId, assignmentId);
        requireSelfOrManager(assignment.getStaff().getId());
        if (assignment.getStatus() != AssignmentStatus.PENDING)
            throw new IllegalStateException("Only pending assignment can be rejected");
        LocalDateTime now = LocalDateTime.now();
        assignment.setStatus(AssignmentStatus.REJECTED);
        assignment.setEndedAt(now);
        assignment.setLastActionAt(now);
        assignment.setCompletionNote(request == null ? null : trimToNull(request.getReason()));
        assignmentRepository.save(assignment);
        syncLegacyFields(assignment.getServiceJob());
        recordActivity(assignment.getServiceJob(), "ASSIGNMENT_REJECTED",
                assignment.getStaff().getName() + noteSuffix(assignment.getCompletionNote()));
        broadcast("JOB_TEAM_CHANGED");
        return toDto(assignment);
    }

    @Transactional
    public AssignmentDTO recordWork(Integer jobId, Integer assignmentId, AssignmentActionRequest request) {
        ServiceJob job = requireOpenJobForUpdate(jobId);
        ServiceJobAssignment assignment = requireAssignment(jobId, assignmentId);
        requireSelfOrManager(assignment.getStaff().getId());
        if (request == null || request.getAction() == null)
            throw new IllegalArgumentException("Work action is required");
        LocalDateTime now = LocalDateTime.now();
        switch (request.getAction()) {
            case START -> {
                requireStatus(assignment, AssignmentStatus.ACTIVE);
                if (assignment.getWorkStartedAt() != null)
                    throw new IllegalStateException("Work timer is already running");
                assignment.setWorkStartedAt(now);
            }
            case PAUSE -> {
                requireStatus(assignment, AssignmentStatus.ACTIVE);
                if (assignment.getWorkStartedAt() == null)
                    throw new IllegalStateException("Start work before pausing");
                closeTimer(assignment, now);
                assignment.setStatus(AssignmentStatus.PAUSED);
            }
            case RESUME -> {
                requireStatus(assignment, AssignmentStatus.PAUSED);
                assignment.setStatus(AssignmentStatus.ACTIVE);
                assignment.setWorkStartedAt(now);
            }
            case NOTE -> {
                if (assignment.getStatus() != AssignmentStatus.ACTIVE
                        && assignment.getStatus() != AssignmentStatus.PAUSED)
                    throw new IllegalStateException("Notes can only be added to an active assignment");
                if (trimToNull(request.getNote()) == null
                        && trimToNull(request.getCompletedWork()) == null
                        && trimToNull(request.getServiceDetails()) == null
                        && trimToNull(request.getPartsDetails()) == null)
                    throw new IllegalArgumentException("Work, service, parts or note detail is required");
            }
            case COMPLETE -> {
                if (assignment.getStatus() != AssignmentStatus.ACTIVE
                        && assignment.getStatus() != AssignmentStatus.PAUSED)
                    throw new IllegalStateException("Only active assignment can be completed");
                if (trimToNull(request.getCompletedWork()) == null)
                    throw new IllegalArgumentException("Completed work is required");
                if (assignment.getRole() == AssignmentRole.LEAD) {
                    boolean unfinishedMember = assignmentRepository
                            .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(jobId, CURRENT).stream()
                            .filter(a -> !a.getId().equals(assignment.getId()))
                            .anyMatch(a -> a.getStatus() != AssignmentStatus.COMPLETED);
                    if (unfinishedMember)
                        throw new IllegalStateException("Complete all Member and Helper assignments before Lead completion");
                }
                closeTimer(assignment, now);
                assignment.setStatus(AssignmentStatus.COMPLETED);
                assignment.setCompletionNote(trimToNull(request.getCompletedWork()));
                assignment.setCompletedAt(now);
            }
        }
        assignment.setLastActionAt(now);
        assignmentRepository.save(assignment);
        addLog(assignment, request);
        recordActivity(job, "ASSIGNMENT_" + request.getAction().name(), assignment.getStaff().getName());
        broadcast("JOB_TEAM_CHANGED");
        return toDto(assignment);
    }

    @Transactional
    public HandoverDTO requestHandover(Integer jobId, HandoverRequest request) {
        requireOpenJobForUpdate(jobId);
        if (request == null || request.getFromAssignmentId() == null || request.getToStaffId() == null)
            throw new IllegalArgumentException("Source assignment and target technician are required");
        if (trimToNull(request.getRemainingWork()) == null)
            throw new IllegalArgumentException("Remaining work is required");
        ServiceJobAssignment source = requireAssignment(jobId, request.getFromAssignmentId());
        requireSelfOrManager(source.getStaff().getId());
        if (source.getStatus() != AssignmentStatus.ACTIVE && source.getStatus() != AssignmentStatus.PAUSED)
            throw new IllegalStateException("Only active or paused assignment can be handed over");
        if (handoverRepository.existsByFromAssignmentIdAndStatus(source.getId(), HandoverStatus.PENDING))
            throw new IllegalStateException("A pending Hand Over already exists for this assignment");
        Staff target = requireStaff(request.getToStaffId());
        if (target.getId().equals(source.getStaff().getId()))
            throw new IllegalArgumentException("Select a different technician");
        if (assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(jobId, target.getId(), CURRENT))
            throw new IllegalStateException("Target technician already belongs to this job");

        ServiceJobHandover handover = handoverRepository.save(ServiceJobHandover.builder()
                .serviceJob(source.getServiceJob()).fromAssignment(source).toStaff(target)
                .role(source.getRole()).completedWork(trimToNull(request.getCompletedWork()))
                .remainingWork(request.getRemainingWork().trim())
                .diagnosisNote(trimToNull(request.getDiagnosisNote()))
                .status(HandoverStatus.PENDING).requestedBy(currentUsername())
                .requestedAt(LocalDateTime.now()).build());
        recordActivity(source.getServiceJob(), "HANDOVER_REQUESTED",
                source.getStaff().getName() + " -> " + target.getName());
        broadcastHandover("JOB_HANDOVER_REQUESTED");
        return toDto(handover);
    }

    @Transactional
    public HandoverDTO acceptHandover(Integer jobId, Integer handoverId) {
        ServiceJob job = requireOpenJobForUpdate(jobId);
        ServiceJobHandover handover = requireHandover(jobId, handoverId);
        requireSelfOrManager(handover.getToStaff().getId());
        if (handover.getStatus() != HandoverStatus.PENDING)
            throw new IllegalStateException("Only pending Hand Over can be accepted");
        ServiceJobAssignment source = requireAssignment(jobId, handover.getFromAssignment().getId());
        if (source.getStatus() != AssignmentStatus.ACTIVE && source.getStatus() != AssignmentStatus.PAUSED)
            throw new IllegalStateException("Source assignment is no longer active");
        if (handover.getRole() == AssignmentRole.LEAD) {
            List<ServiceJobAssignment> currentLeads = assignmentRepository
                    .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(jobId, CURRENT).stream()
                    .filter(a -> a.getRole() == AssignmentRole.LEAD)
                    .filter(a -> !a.getId().equals(source.getId()))
                    .toList();
            if (!currentLeads.isEmpty())
                throw new IllegalStateException("A current lead already exists; use Hand Over to change the lead");
        }
        if (assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(
                jobId, handover.getToStaff().getId(), CURRENT))
            throw new IllegalStateException("Target technician already belongs to this job");

        LocalDateTime now = LocalDateTime.now();
        closeTimer(source, now);
        source.setStatus(AssignmentStatus.HANDED_OVER);
        source.setEndedAt(now);
        source.setLastActionAt(now);
        assignmentRepository.save(source);
        addLog(source, AssignmentWorkAction.NOTE, "Handed over to " + handover.getToStaff().getName());

        ServiceJobAssignment successor = assignmentRepository.save(ServiceJobAssignment.builder()
                .serviceJob(job).staff(handover.getToStaff()).role(handover.getRole())
                .status(AssignmentStatus.ACTIVE).taskDescription(handover.getRemainingWork())
                .assignedBy(handover.getRequestedBy()).assignedAt(now).acceptedAt(now)
                .lastActionAt(now).build());
        addLog(successor, AssignmentWorkAction.NOTE,
                "Hand Over accepted from " + source.getStaff().getName());

        handover.setStatus(HandoverStatus.ACCEPTED);
        handover.setActedBy(currentUsername());
        handover.setActedAt(now);
        handover.setSuccessorAssignment(successor);
        handoverRepository.save(handover);
        syncLegacyFields(job);
        recordActivity(job, "HANDOVER_ACCEPTED",
                source.getStaff().getName() + " -> " + successor.getStaff().getName());
        broadcastHandover("JOB_HANDOVER_ACCEPTED");
        return toDto(handover);
    }

    @Transactional
    public HandoverDTO rejectHandover(Integer jobId, Integer handoverId, AssignmentDecisionRequest request) {
        requireOpenJobForUpdate(jobId);
        ServiceJobHandover handover = requireHandover(jobId, handoverId);
        requireSelfOrManager(handover.getToStaff().getId());
        if (handover.getStatus() != HandoverStatus.PENDING)
            throw new IllegalStateException("Only pending Hand Over can be rejected");
handover.setStatus(HandoverStatus.REJECTED);
        handover.setActedBy(currentUsername());
        handover.setActedAt(LocalDateTime.now());
        handover.setRejectionReason(request == null ? null : trimToNull(request.getReason()));
        handoverRepository.save(handover);
        recordActivity(handover.getServiceJob(), "HANDOVER_REJECTED",
                handover.getToStaff().getName() + noteSuffix(handover.getRejectionReason()));
        broadcastHandover("JOB_HANDOVER_REJECTED");
        return toDto(handover);
    }

    @Transactional(readOnly = true)
    public List<HandoverDTO> myPendingHandovers() {
        Integer mine = currentStaffId();
        if (mine == null) return List.of();
        return handoverRepository.findAllByToStaffIdAndStatusOrderByRequestedAtDesc(mine, HandoverStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<HandoverDTO> mySentHandovers() {
        Integer mine = currentStaffId();
        if (mine == null) return List.of();
        return handoverRepository.findAllByFromStaffIdAndStatusInOrderByRequestedAtDesc(mine, SENT_HANDOVER_STATUSES)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ServiceJobHandover> findById(Integer handoverId) {
        return handoverRepository.findById(handoverId);
    }

    @Transactional
    public boolean canComplete(Integer jobId) {
        ServiceJob job = requireJob(jobId);
        syncFromJob(job);
        return evaluateCanComplete(jobId);
    }

    @Transactional
    public String completionBlockReason(Integer jobId) {
        ServiceJob job = requireJob(jobId);
        syncFromJob(job);
        return evaluateCompletionBlockReason(jobId);
    }

    @Transactional
    public void assertLeadCanFinalCheck(Integer jobId) {
        ServiceJob job = requireJob(jobId);
        syncFromJob(job);
        if (!evaluateCanComplete(jobId))
            throw new IllegalStateException(evaluateCompletionBlockReason(jobId));
        ServiceJobAssignment lead = assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(jobId, CURRENT).stream()
                .filter(a -> a.getRole() == AssignmentRole.LEAD)
                .findFirst().orElseThrow(() -> new IllegalStateException("A current lead technician is required"));
        requireSelfOrManager(lead.getStaff().getId());
    }

    @Transactional
    public void reopenLeadForRework(Integer jobId, String reason) {
        ServiceJob job = requireOpenJobForUpdate(jobId);
        ServiceJobAssignment lead = assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(jobId, CURRENT).stream()
                .filter(a -> a.getRole() == AssignmentRole.LEAD)
                .findFirst().orElseThrow(() -> new IllegalStateException("A current lead technician is required"));
        lead.setStatus(AssignmentStatus.ACTIVE);
        lead.setCompletionNote(null);
        lead.setCompletedAt(null);
        lead.setLastActionAt(LocalDateTime.now());
        assignmentRepository.save(lead);
        addLog(lead, AssignmentWorkAction.NOTE, "Supervisor returned for rework" + noteSuffix(reason));
        recordActivity(job, "FINAL_CHECK_RETURNED", reason);
        syncLegacyFields(job);
        broadcast("JOB_TEAM_CHANGED");
    }

    private boolean evaluateCanComplete(Integer jobId) {
        List<ServiceJobAssignment> current = assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(jobId, CURRENT);
        if (current.isEmpty()) return false;
        boolean hasLead = current.stream().anyMatch(a -> a.getRole() == AssignmentRole.LEAD);
        return hasLead && current.stream().allMatch(a -> a.getStatus() == AssignmentStatus.COMPLETED);
    }

    private String evaluateCompletionBlockReason(Integer jobId) {
        List<ServiceJobAssignment> current = assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(jobId, CURRENT);
        if (current.isEmpty()) return "A current lead technician is required";
        if (current.stream().noneMatch(a -> a.getRole() == AssignmentRole.LEAD))
            return "A current lead technician is required";
        List<String> pending = current.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.COMPLETED)
                .map(a -> a.getStaff().getName() + " (" + a.getRole() + ": " + a.getStatus() + ")")
                .toList();
        return pending.isEmpty() ? null
                : "Complete all technician assignments first: " + String.join(", ", pending);
    }

    private TeamSnapshotDTO toSnapshot(ServiceJob job) {
        TeamSnapshotDTO dto = new TeamSnapshotDTO();
        dto.setServiceJobId(job.getId());
        dto.setJobNo(job.getJobNo());
        dto.setAssignments(assignmentRepository.findAllByServiceJobIdOrderByAssignedAtAscIdAsc(job.getId())
                .stream().map(this::toDto).toList());
        dto.setHandovers(handoverRepository.findAllByServiceJobIdOrderByRequestedAtDesc(job.getId())
                .stream().map(this::toDto).toList());
        dto.setMyPendingHandovers(dto.getHandovers().stream()
                .filter(h -> h.getStatus() == HandoverStatus.PENDING && h.isTargetMine())
                .toList());
        dto.setCanComplete(evaluateCanComplete(job.getId()));
        dto.setCompletionBlockReason(evaluateCompletionBlockReason(job.getId()));
        dto.setLeadFinalCheckStatus(Boolean.TRUE.equals(job.getLeadFinalCheckStatus()));
        dto.setLeadFinalCheckedBy(job.getLeadFinalCheckedBy());
        dto.setLeadFinalCheckedAt(job.getLeadFinalCheckedAt() == null ? null : job.getLeadFinalCheckedAt().toString());
        dto.setLeadFinalCheckNote(job.getLeadFinalCheckNote());
        dto.setFinalReturnReason(job.getFinalReturnReason());
        dto.setSupervisorApprovalRequired(companySettingsRepository.findAll().stream().findFirst()
                .map(s -> !Boolean.FALSE.equals(s.getServiceSupervisorApprovalRequired())).orElse(true));
        dto.setFinalApprovalStatus(Boolean.TRUE.equals(job.getFinalApprovalStatus()));
        return dto;
    }

    private void syncFromJob(ServiceJob job) {
        syncLeadFromJob(job);
        syncHelperFromJob(job);
        syncLegacyFields(job);
    }

    private void syncLeadFromJob(ServiceJob job) {
        Staff desired = job.getAssignedStaff();
        if (desired == null) return;
        List<ServiceJobAssignment> leads = assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(job.getId(), CURRENT).stream()
                .filter(a -> a.getRole() == AssignmentRole.LEAD)
                .toList();
        boolean matched = leads.stream().anyMatch(a -> desired.getId().equals(a.getStaff().getId()));
        if (matched) return;

        // Accidental overwrite protection: if desired staff is already a non-lead member/helper
        // and a lead already exists, do not cancel the lead (restore via syncLegacyFields).
        boolean desiredIsNonLeadTeammate = assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(job.getId(), CURRENT).stream()
                .anyMatch(a -> desired.getId().equals(a.getStaff().getId())
                        && a.getRole() != AssignmentRole.LEAD);
        if (!leads.isEmpty() && desiredIsNonLeadTeammate) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (ServiceJobAssignment lead : leads) {
            closeTimer(lead, now);
            lead.setStatus(AssignmentStatus.CANCELED);
            lead.setEndedAt(now);
            lead.setLastActionAt(now);
            assignmentRepository.save(lead);
        }
        if (!assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(job.getId(), desired.getId(), CURRENT)) {
            assignmentRepository.save(legacy(job, desired, AssignmentRole.LEAD, "Primary technician"));
        }
    }

    private void syncHelperFromJob(ServiceJob job) {
        Staff helper = job.getHelperStaff();
        if (helper == null || helper.equals(job.getAssignedStaff())) return;
        if (!assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(job.getId(), helper.getId(), CURRENT)) {
            assignmentRepository.save(legacy(job, helper, AssignmentRole.HELPER, "Helper technician"));
        }
    }

    private AssignmentDTO toDto(ServiceJobAssignment assignment) {
        AssignmentDTO dto = new AssignmentDTO();
        dto.setId(assignment.getId());
        dto.setServiceJobId(assignment.getServiceJob().getId());
        dto.setJobNo(assignment.getServiceJob().getJobNo());
        dto.setStaffId(assignment.getStaff().getId());
        dto.setStaffName(assignment.getStaff().getName());
        dto.setRole(assignment.getRole());
        dto.setStatus(assignment.getStatus());
        dto.setApprovalStatus(assignment.getApprovalStatus());
        dto.setApprovedBy(assignment.getApprovedBy());
        dto.setApprovedAt(assignment.getApprovedAt());
        dto.setTaskDescription(assignment.getTaskDescription());
        dto.setCompletionNote(assignment.getCompletionNote());
        dto.setAssignedBy(assignment.getAssignedBy());
        dto.setAssignedAt(assignment.getAssignedAt());
        dto.setAcceptedAt(assignment.getAcceptedAt());
        dto.setWorkStartedAt(assignment.getWorkStartedAt());
        dto.setLastActionAt(assignment.getLastActionAt());
        dto.setCompletedAt(assignment.getCompletedAt());
        dto.setEndedAt(assignment.getEndedAt());
        dto.setAccumulatedMinutes(totalMinutes(assignment, LocalDateTime.now()));
        Integer mine = currentStaffId();
        dto.setMine(mine != null && mine.equals(assignment.getStaff().getId()));
        dto.setLogs(logRepository.findAllByAssignmentIdOrderByOccurredAtAsc(assignment.getId())
                .stream().map(this::toDto).toList());
        return dto;
    }

    private AssignmentLogDTO toDto(ServiceJobAssignmentLog log) {
        AssignmentLogDTO dto = new AssignmentLogDTO();
        dto.setId(log.getId());
        dto.setAction(log.getAction());
        dto.setNote(log.getNote());
        dto.setCompletedWork(log.getCompletedWork());
        dto.setServiceDetails(log.getServiceDetails());
        dto.setPartsDetails(log.getPartsDetails());
        dto.setActor(log.getActor());
        dto.setOccurredAt(log.getOccurredAt());
        return dto;
    }

    private HandoverDTO toDto(ServiceJobHandover handover) {
        HandoverDTO dto = new HandoverDTO();
        dto.setId(handover.getId());
        dto.setServiceJobId(handover.getServiceJob().getId());
        dto.setJobNo(handover.getServiceJob().getJobNo());
        dto.setFromAssignmentId(handover.getFromAssignment().getId());
        dto.setFromStaffId(handover.getFromAssignment().getStaff().getId());
        dto.setFromStaffName(handover.getFromAssignment().getStaff().getName());
        dto.setToStaffId(handover.getToStaff().getId());
        dto.setToStaffName(handover.getToStaff().getName());
        dto.setRole(handover.getRole());
        dto.setCompletedWork(handover.getCompletedWork());
        dto.setRemainingWork(handover.getRemainingWork());
        dto.setDiagnosisNote(handover.getDiagnosisNote());
        dto.setStatus(handover.getStatus());
        dto.setRequestedBy(handover.getRequestedBy());
        dto.setRequestedAt(handover.getRequestedAt());
        dto.setActedBy(handover.getActedBy());
        dto.setActedAt(handover.getActedAt());
        dto.setRejectionReason(handover.getRejectionReason());
        dto.setSuccessorAssignmentId(handover.getSuccessorAssignment() == null
                ? null : handover.getSuccessorAssignment().getId());
        Integer mine = currentStaffId();
        dto.setTargetMine(mine != null && mine.equals(handover.getToStaff().getId()));
        dto.setFromMine(mine != null && mine.equals(handover.getFromAssignment().getStaff().getId()));
        return dto;
    }

    private ServiceJobAssignment legacy(ServiceJob job, Staff staff, AssignmentRole role, String task) {
        LocalDateTime now = LocalDateTime.now();
        return ServiceJobAssignment.builder().serviceJob(job).staff(staff).role(role)
                .status(AssignmentStatus.ACTIVE).taskDescription(task).assignedBy("LEGACY_SYNC")
                .assignedAt(now).acceptedAt(now).lastActionAt(now).build();
    }

    private void syncLegacyFields(ServiceJob job) {
        List<ServiceJobAssignment> current = assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(job.getId(), CURRENT);
        job.setAssignedStaff(current.stream().filter(a -> a.getRole() == AssignmentRole.LEAD)
                .map(ServiceJobAssignment::getStaff).findFirst().orElse(null));
        job.setHelperStaff(current.stream().filter(a -> a.getRole() == AssignmentRole.HELPER)
                .map(ServiceJobAssignment::getStaff).findFirst().orElse(null));
        jobRepository.save(job);
    }

    private void addLog(ServiceJobAssignment assignment, AssignmentWorkAction action, String note) {
        logRepository.save(ServiceJobAssignmentLog.builder().assignment(assignment).action(action)
                .note(note).actor(currentUsername()).occurredAt(LocalDateTime.now()).build());
    }

    private void addLog(ServiceJobAssignment assignment, AssignmentActionRequest request) {
        logRepository.save(ServiceJobAssignmentLog.builder()
                .assignment(assignment).action(request.getAction())
                .note(trimToNull(request.getNote()))
                .completedWork(trimToNull(request.getCompletedWork()))
                .serviceDetails(trimToNull(request.getServiceDetails()))
                .partsDetails(trimToNull(request.getPartsDetails()))
                .actor(currentUsername()).occurredAt(LocalDateTime.now()).build());
    }

    private void recordActivity(ServiceJob job, String type, String note) {
        activityRepository.save(ServiceJobActivity.builder().serviceJob(job).eventType(type)
                .fromStatus(job.getStatus() == null ? null : job.getStatus().name())
                .toStatus(job.getStatus() == null ? null : job.getStatus().name())
                .note(note).actor(currentUsername()).occurredAt(LocalDateTime.now()).build());
    }

    private void closeTimer(ServiceJobAssignment assignment, LocalDateTime now) {
        if (assignment.getWorkStartedAt() == null) return;
        long elapsed = Math.max(0, Duration.between(assignment.getWorkStartedAt(), now).toMinutes());
        assignment.setAccumulatedMinutes((assignment.getAccumulatedMinutes() == null ? 0L
                : assignment.getAccumulatedMinutes()) + elapsed);
        assignment.setWorkStartedAt(null);
    }

    private long totalMinutes(ServiceJobAssignment assignment, LocalDateTime now) {
        long total = assignment.getAccumulatedMinutes() == null ? 0L : assignment.getAccumulatedMinutes();
        return assignment.getWorkStartedAt() == null ? total
                : total + Math.max(0, Duration.between(assignment.getWorkStartedAt(), now).toMinutes());
    }

    private ServiceJob requireJob(Integer jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + jobId));
    }

    private ServiceJob requireOpenJobForUpdate(Integer jobId) {
        ServiceJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + jobId));
        if (job.getStatus() == ServiceJobStatus.DELIVERED || job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Closed service job team cannot be changed");
        return job;
    }

    private ServiceJobAssignment requireAssignment(Integer jobId, Integer assignmentId) {
        return assignmentRepository.findForUpdate(jobId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Service job assignment not found"));
    }

    private ServiceJobHandover requireHandover(Integer jobId, Integer handoverId) {
        return handoverRepository.findForUpdate(jobId, handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Service job Hand Over not found"));
    }

    private Staff requireStaff(Integer id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id));
    }

    private void requireStatus(ServiceJobAssignment assignment, AssignmentStatus status) {
        if (assignment.getStatus() != status)
            throw new IllegalStateException("Assignment must be " + status);
    }

    private void requireSelfOrManager(Integer staffId) {
        if (hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")) return;
        Integer mine = currentStaffId();
        if (mine == null || !mine.equals(staffId))
            throw new AccessDeniedException("You can only act on your own technician assignment");
    }

    private void requireManager() {
        if (!hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN"))
            throw new AccessDeniedException("Technician assignment permission is required");
    }

    private Integer currentStaffId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        String name = authentication.getName();
        if (name == null || name.isBlank()) return null;
        return userRepository.findWithStaffByUsernameOrEmail(name.trim())
                .map(user -> user.getStaff() == null ? null : user.getStaff().getId())
                .or(() -> userRepository.findByUsernameOrEmail(name.trim(), name.trim())
                        .map(user -> user.getStaff() == null ? null : user.getStaff().getId()))
                .orElse(null);
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "SYSTEM" : authentication.getName();
    }

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String noteSuffix(String note) {
        return note == null ? "" : ": " + note;
    }

    private static void requireTaskDescription(AssignmentRole role, String taskDescription) {
        if (role == AssignmentRole.MEMBER && trimToNull(taskDescription) == null)
            throw new IllegalArgumentException("Team member task description is required");
    }

    private void broadcast(String event) {
        dataEventPublisher.publishTopic("/topic/service-jobs", event);
    }

    private void broadcastHandover(String event) {
        dataEventPublisher.publishTopic("/topic/handovers", event);
        dataEventPublisher.publishTopic("/topic/service-jobs", event);
    }
}
