package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto.AssignmentActionRequest;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto.AssignmentRequest;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentRole;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentWorkAction;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobAssignment;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository.ServiceJobAssignmentLogRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository.ServiceJobAssignmentRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository.ServiceJobHandoverRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobActivityRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceJobTeamServiceTest {
    @Mock ServiceJobRepository jobRepository;
    @Mock ServiceJobAssignmentRepository assignmentRepository;
    @Mock ServiceJobAssignmentLogRepository logRepository;
    @Mock ServiceJobHandoverRepository handoverRepository;
    @Mock ServiceJobActivityRepository activityRepository;
    @Mock StaffRepository staffRepository;
    @Mock UserRepository userRepository;
    @Mock DataEventPublisher dataEventPublisher;
    @Mock CompanySettingsRepository companySettingsRepository;

    private ServiceJobTeamService service;

    @BeforeEach
    void setUp() {
        service = new ServiceJobTeamService(jobRepository, assignmentRepository, logRepository,
                handoverRepository, activityRepository, staffRepository, userRepository, dataEventPublisher,
                companySettingsRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a",
                        List.of(new SimpleGrantedAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canComplete_isFalseWhenNoLeadAssignmentExists() {
        ServiceJob job = job(1, null, null);
        when(jobRepository.findById(1)).thenReturn(Optional.of(job));
        when(jobRepository.findByIdForUpdate(1)).thenReturn(Optional.of(job));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(1), any()))
                .thenReturn(List.of());

        assertFalse(service.canComplete(1));
    }

    @Test
    void syncFromJob_createsLeadAssignmentFromAssignedStaff() {
        Staff lead = staff(5, "Lead Tech");
        ServiceJob job = job(2, lead, null);
        when(jobRepository.findByIdForUpdate(2)).thenReturn(Optional.of(job));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(2), any()))
                .thenReturn(List.of());
        when(assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(eq(2), eq(5), any()))
                .thenReturn(false);
        when(assignmentRepository.save(any(ServiceJobAssignment.class))).thenAnswer(invocation -> {
            ServiceJobAssignment saved = invocation.getArgument(0);
            saved.setId(99);
            return saved;
        });
        when(jobRepository.save(job)).thenReturn(job);

        service.syncFromJob(2);

        ArgumentCaptor<ServiceJobAssignment> captor = ArgumentCaptor.forClass(ServiceJobAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertEquals(AssignmentRole.LEAD, captor.getValue().getRole());
        assertEquals(5, captor.getValue().getStaff().getId());
    }

    @Test
    void assign_rejectsSecondLead() {
        ServiceJob job = openJob(3);
        when(jobRepository.findByIdForUpdate(3)).thenReturn(Optional.of(job));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(3), any()))
                .thenReturn(List.of(assignment(10, staff(5, "Lead"), AssignmentRole.LEAD, AssignmentStatus.ACTIVE)));
        when(assignmentRepository.existsByServiceJobIdAndRoleAndStatusIn(eq(3), eq(AssignmentRole.LEAD), any()))
                .thenReturn(true);
        when(staffRepository.findById(6)).thenReturn(Optional.of(staff(6, "Other")));
        when(assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(eq(3), eq(6), any()))
                .thenReturn(false);

        AssignmentRequest request = new AssignmentRequest();
        request.setStaffId(6);
        request.setRole(AssignmentRole.LEAD);

        assertThrows(IllegalStateException.class, () -> service.assign(3, request));
    }

    @Test
    void assign_requiresTaskDescriptionForMember() {
        ServiceJob job = openJob(4);
        when(jobRepository.findByIdForUpdate(4)).thenReturn(Optional.of(job));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(4), any()))
                .thenReturn(List.of());

        AssignmentRequest request = new AssignmentRequest();
        request.setStaffId(7);
        request.setRole(AssignmentRole.MEMBER);

        assertThrows(IllegalArgumentException.class, () -> service.assign(4, request));
    }

    @Test
    void assertCanComplete_blocksWhenMemberNotCompleted() {
        Staff lead = staff(5, "Lead");
        Staff member = staff(8, "Member");
        ServiceJob job = job(5, lead, null);
        when(jobRepository.findById(5)).thenReturn(Optional.of(job));
        when(jobRepository.findByIdForUpdate(5)).thenReturn(Optional.of(job));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(5), any()))
                .thenReturn(List.of(
                        assignment(11, lead, AssignmentRole.LEAD, AssignmentStatus.COMPLETED),
                        assignment(12, member, AssignmentRole.MEMBER, AssignmentStatus.ACTIVE)
                ));
        when(assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(eq(5), eq(5), any()))
                .thenReturn(true);
        when(jobRepository.save(job)).thenReturn(job);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.assertCanComplete(5));
        assertTrue(ex.getMessage().contains("Complete all technician assignments first"));
    }

    @Test
    void assertCanComplete_allowsWhenLeadAndMembersCompleted() {
        Staff lead = staff(5, "Lead");
        Staff member = staff(8, "Member");
        ServiceJob job = job(6, lead, null);
        when(jobRepository.findById(6)).thenReturn(Optional.of(job));
        when(jobRepository.findByIdForUpdate(6)).thenReturn(Optional.of(job));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(6), any()))
                .thenReturn(List.of(
                        assignment(11, lead, AssignmentRole.LEAD, AssignmentStatus.COMPLETED),
                        assignment(12, member, AssignmentRole.MEMBER, AssignmentStatus.COMPLETED)
                ));
        when(assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(eq(6), eq(5), any()))
                .thenReturn(true);
        when(jobRepository.save(job)).thenReturn(job);

        service.assertCanComplete(6);
    }

    @Test
    void acceptAssignment_rejectsSecondLeadWhenAnotherLeadIsCurrent() {
        ServiceJob job = openJob(7);
        ServiceJobAssignment currentLead = assignment(20, staff(5, "Lead Tech"), AssignmentRole.LEAD, AssignmentStatus.ACTIVE);
        currentLead.setServiceJob(job);
        ServiceJobAssignment pendingLead = assignment(21, staff(6, "Replacement Lead"), AssignmentRole.LEAD, AssignmentStatus.PENDING);
        pendingLead.setServiceJob(job);

        when(jobRepository.findByIdForUpdate(7)).thenReturn(Optional.of(job));
        when(assignmentRepository.findForUpdate(7, 21)).thenReturn(Optional.of(pendingLead));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(7), any()))
                .thenReturn(List.of(currentLead));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.acceptAssignment(7, 21));
        assertTrue(ex.getMessage().contains("current lead already exists"));
    }

    @Test
    void acceptHandover_rejectsSecondLeadWhenAnotherLeadIsCurrent() {
        ServiceJob job = openJob(8);
        ServiceJobAssignment currentLead = assignment(30, staff(5, "Lead Tech"), AssignmentRole.LEAD, AssignmentStatus.ACTIVE);
        currentLead.setServiceJob(job);
        ServiceJobAssignment source = assignment(31, staff(6, "Current Lead"), AssignmentRole.LEAD, AssignmentStatus.ACTIVE);
        source.setServiceJob(job);
        org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobHandover handover =
                org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobHandover.builder()
                        .id(40)
                        .serviceJob(job)
                        .fromAssignment(source)
                        .toStaff(staff(7, "Replacement Lead"))
                        .role(AssignmentRole.LEAD)
                        .remainingWork("Finish testing")
                        .status(org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING)
                        .requestedBy("manager")
                        .requestedAt(java.time.LocalDateTime.now())
                        .build();

        when(jobRepository.findByIdForUpdate(8)).thenReturn(Optional.of(job));
        when(handoverRepository.findForUpdate(8, 40)).thenReturn(Optional.of(handover));
        when(assignmentRepository.findForUpdate(8, 31)).thenReturn(Optional.of(source));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(8), any()))
                .thenReturn(List.of(currentLead, source));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.acceptHandover(8, 40));
        assertTrue(ex.getMessage().contains("current lead already exists"));
    }

    @Test
    void recordWork_blocksLeadCompleteBeforeMembers() {
        Staff lead = staff(5, "Lead");
        Staff member = staff(8, "Member");
        ServiceJob job = openJob(9);
        ServiceJobAssignment leadAssignment = assignment(31, lead, AssignmentRole.LEAD, AssignmentStatus.ACTIVE);
        leadAssignment.setServiceJob(job);
        ServiceJobAssignment memberAssignment = assignment(32, member, AssignmentRole.MEMBER, AssignmentStatus.ACTIVE);
        memberAssignment.setServiceJob(job);

        when(jobRepository.findByIdForUpdate(9)).thenReturn(Optional.of(job));
        when(assignmentRepository.findForUpdate(9, 31)).thenReturn(Optional.of(leadAssignment));
        when(assignmentRepository.findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(eq(9), any()))
                .thenReturn(List.of(leadAssignment, memberAssignment));
        when(assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(eq(9), eq(5), any()))
                .thenReturn(true);

        AssignmentActionRequest request = new AssignmentActionRequest();
        request.setAction(AssignmentWorkAction.COMPLETE);
        request.setCompletedWork("Lead finished");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.recordWork(9, 31, request));
        assertTrue(ex.getMessage().contains("Complete all Member and Helper assignments before Lead completion"));
    }

    private static ServiceJob job(int id, Staff lead, Staff helper) {
        return ServiceJob.builder()
                .id(id)
                .jobNo("JOB-" + id)
                .status(ServiceJobStatus.IN_PROGRESS)
                .assignedStaff(lead)
                .helperStaff(helper)
                .build();
    }

    private static ServiceJob openJob(int id) {
        return ServiceJob.builder()
                .id(id)
                .jobNo("JOB-" + id)
                .status(ServiceJobStatus.IN_PROGRESS)
                .build();
    }

    private static Staff staff(int id, String name) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setName(name);
        return staff;
    }

    private static ServiceJobAssignment assignment(int id, Staff staff, AssignmentRole role, AssignmentStatus status) {
        return ServiceJobAssignment.builder()
                .id(id)
                .staff(staff)
                .role(role)
                .status(status)
                .build();
    }
}
