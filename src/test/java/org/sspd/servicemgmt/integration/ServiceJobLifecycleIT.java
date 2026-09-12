package org.sspd.servicemgmt.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.service.BookingService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentApprovalStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentRole;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobAssignment;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository.ServiceJobAssignmentRepository;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.SettleDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceMode;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Transactional
@WithMockUser(username = "it-admin", authorities = {
        "CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN",
        "CAN_ACCESS_SERVICE_JOB_CREATE",
        "CAN_ACCESS_SERVICE_JOB_UPDATE",
        "CAN_ACCESS_BOOKING_CREATE",
        "CAN_ACCESS_BOOKING_CONVERT_JOB"
})
class ServiceJobLifecycleIT extends AbstractMysqlIntegrationTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private BookingService bookingService;
    @Autowired private ServiceJobService serviceJobService;
    @Autowired private ServiceJobRepository serviceJobRepository;
    @Autowired private ServiceJobAssignmentRepository assignmentRepository;

    @Test
    void bookingOutdoorConvertThenSettleOnceThenDeliver() {
        Customer customer = customerRepository.save(Customer.builder()
                .name("IT Customer")
                .phone("09" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .address("Yangon")
                .creditHold(false)
                .blacklisted(false)
                .advanceBalance(BigDecimal.ZERO)
                .build());

        Staff lead = staffRepository.save(Staff.builder()
                .name("IT Lead Tech")
                .phone("09" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .role("Technician")
                .isActive(true)
                .basicSalary(BigDecimal.ZERO)
                .build());

        BookingDTO bookingRequest = new BookingDTO();
        bookingRequest.setCustomerId(customer.getId());
        bookingRequest.setBookingDate(LocalDate.now());
        bookingRequest.setComplaintNote("Outdoor AC not cooling");
        BookingDTO booking = bookingService.create(bookingRequest);
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());

        BookingDTO converted = bookingService.convertOutdoor(booking.getId());
        assertTrue(converted.isFullyConverted() || converted.getLinkedJobs() != null);
        assertNotNull(converted.getLinkedJobs());
        assertEquals(1, converted.getLinkedJobs().size());
        assertEquals(ServiceMode.OUTDOOR, converted.getLinkedJobs().get(0).getServiceMode());

        Integer jobId = converted.getLinkedJobs().get(0).getId();
        ServiceJob job = serviceJobRepository.findById(jobId).orElseThrow();

        assignmentRepository.save(ServiceJobAssignment.builder()
                .serviceJob(job)
                .staff(lead)
                .role(AssignmentRole.LEAD)
                .status(AssignmentStatus.COMPLETED)
                .approvalStatus(AssignmentApprovalStatus.APPROVED)
                .assignedBy("it")
                .assignedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusMinutes(5))
                .completionNote("Done")
                .build());

        job.setAssignedStaff(lead);
        job.setStatus(ServiceJobStatus.COMPLETED);
        job.setLeadFinalCheckStatus(true);
        job.setLeadFinalCheckedAt(LocalDateTime.now());
        job.setLeadFinalCheckedBy("it-lead");
        job.setFinalApprovalStatus(true);
        job.setFinalApprovedAt(LocalDateTime.now());
        job.setFinalApprovedBy("it-supervisor");
        job.setEstimatedCost(new BigDecimal("25000.00"));
        serviceJobRepository.saveAndFlush(job);

        SettleDTO settle = new SettleDTO();
        settle.setFoc(true);
        settle.setFinalCost(BigDecimal.ZERO);
        settle.setPaidAmount(BigDecimal.ZERO);
        ServiceJobDTO settled = serviceJobService.settle(jobId, settle);
        assertEquals(ServiceJobStatus.COMPLETED, settled.getStatus());
        assertNotNull(settled.getPaymentStatus());
        assertTrue(settled.getJobNo() != null && settled.getJobNo().startsWith("SJ-"));

        IllegalStateException second = assertThrows(IllegalStateException.class,
                () -> serviceJobService.settle(jobId, settle));
        assertEquals("This service job has already been settled", second.getMessage());

        ServiceJobDTO delivered = serviceJobService.deliver(jobId);
        assertEquals(ServiceJobStatus.DELIVERED, delivered.getStatus());
        assertNotNull(delivered.getDeliveredDate());
    }
}
