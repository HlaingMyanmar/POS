package org.sspd.servicemgmt.servicejoboptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.creditoptions.dto.CustomerCreditApplyRequest;
import org.sspd.servicemgmt.creditoptions.repository.CustomerCreditApplicationRepository;
import org.sspd.servicemgmt.creditoptions.service.CreditService;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemRepository;
import org.sspd.servicemgmt.servicejoboptions.dto.ReworkRequestDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobActivityDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobAttachmentDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobLineDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobNotificationDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobPartDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.SettleDTO;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobAssignment;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository.ServiceJobAssignmentRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository.ServiceJobHandoverRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobHandover;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.service.ServiceJobTeamService;
import org.sspd.servicemgmt.servicejoboptions.model.DiscountAllocationMethod;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkType;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkResolutionMode;
import org.sspd.servicemgmt.servicejoboptions.model.OldPartDisposition;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkPartResolution;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobActivity;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobAttachment;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceLineConfirmationStatus;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobNotification;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceMode;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianVisitRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobActivityRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobAttachmentRepository;
import org.sspd.servicemgmt.servicejoboptions.support.CustomerNotifier;
import org.sspd.servicemgmt.servicejoboptions.support.ServiceJobSettlementBreakdown;
import org.sspd.servicemgmt.servicejoboptions.support.ServiceJobSettlementCalculator;
import org.sspd.servicemgmt.servicejoboptions.support.ServiceJobSettlementJournalBuilder;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobNotificationRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ReworkPartResolutionRepository;
import org.sspd.servicemgmt.shelflocationoptions.repository.ShelfLocationRepository;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceJobService {
    private final org.sspd.servicemgmt.bookingoptions.repository.BookingRepository bookingRepository;

    private final ServiceJobRepository repo;
    private final TechnicianVisitRepository technicianVisitRepo;
    private final CustomerRepository customerRepo;
    private final StaffRepository staffRepo;
    private final UserRepository userRepository;
    private final ServiceItemRepository serviceItemRepo;
    private final ProductRepository productRepo;
    private final ProductSerialRepository serialRepo;
    private final ShelfLocationRepository shelfLocationRepo;
    private final PaymentMethodRepository paymentMethodRepo;
    private final JournalWriter journalWriter;
    private final PaymentTransactionRepository paymentTransactionRepo;
    private final AccountResolver accountResolver;
    private final SaleService saleService;
    private final CreditService creditService;
    private final CustomerPaymentService customerPaymentService;
    private final CustomerCreditApplicationRepository creditApplicationRepository;
    private final DataEventPublisher dataEventPublisher;
    private final ReworkPartResolutionRepository reworkResolutionRepo;
    private final StockMovementService stockMovementService;
    private final ServiceJobActivityRepository activityRepo;
    private final ServiceJobAttachmentRepository attachmentRepo;
    private final ServiceJobNotificationRepository notificationRepo;
    private final CashDrawerService cashDrawerService;
    private final ServiceJobTeamService teamService;
    private final ServiceJobAssignmentRepository assignmentRepository;
    private final ServiceJobHandoverRepository handoverRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final CustomerNotifier customerNotifier;

    private static final Collection<AssignmentStatus> VISIBLE_ASSIGNMENT_STATUSES = EnumSet.of(
            AssignmentStatus.PENDING,
            AssignmentStatus.ACTIVE,
            AssignmentStatus.PAUSED,
            AssignmentStatus.COMPLETED
    );

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional(readOnly = true)
    public Page<ServiceJobDTO> findAll(String search, String dateFrom, String dateTo, int page, int size, Integer staffId) {
        LocalDateTime from = parseDateStart(dateFrom);
        LocalDateTime to   = parseDateEnd(dateTo);
        Integer scopedStaffId = resolveStaffScope(staffId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id").descending());
        if (scopedStaffId != null) {
            Page<ServiceJobDTO> jobPage = repo.findBySearchAndDateForStaff(search, from, to, scopedStaffId, VISIBLE_ASSIGNMENT_STATUSES, pageable)
                    .map(this::toDto);
            enrichPendingHandovers(jobPage.getContent(), scopedStaffId);
            enrichTeamFlags(jobPage.getContent(), scopedStaffId);
            return jobPage;
        }
        return repo.findBySearchAndDate(search, from, to, pageable).map(this::toDto);
    }

    private LocalDateTime parseDateStart(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay();
    }

    private LocalDateTime parseDateEnd(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay().plusDays(1);
    }

    @Transactional(readOnly = true)
    public List<ServiceJobDTO> findByCustomerId(Integer customerId) {
        return repo.findByCustomerId(customerId).stream()
                .sorted(java.util.Comparator.comparing(ServiceJob::getId).reversed())
                .filter(this::canReadJob)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceJobDTO findById(Integer id) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        assertCanRead(job);
        return toDto(job, true);
    }

    @Transactional(readOnly = true)
    public Set<String> getUsedSerialNumbers(Integer excludeJobId) {
        List<String> raw = repo.findUsedSerialNumberStrings(excludeJobId);
        Set<String> result = new HashSet<>();
        for (String csv : raw) {
            if (csv == null || csv.isBlank()) continue;
            Arrays.stream(csv.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .forEach(result::add);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ServiceJobDTO> findByStatus(ServiceJobStatus status) {
        Integer scopedStaffId = resolveStaffScope(null);
        if (scopedStaffId != null) {
            return repo.findByStatusForStaff(status, scopedStaffId, VISIBLE_ASSIGNMENT_STATUSES).stream()
                    .map(this::toDto).toList();
        }
        return repo.findByStatus(status).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceJobDTO> findUnpaid() {
        Integer scopedStaffId = resolveStaffScope(null);
        if (scopedStaffId != null) {
            return repo.findUnpaidForStaff(scopedStaffId, VISIBLE_ASSIGNMENT_STATUSES).stream()
                    .map(this::toDto).toList();
        }
        return repo.findByStatusAndPaymentStatusIsNullOrderByReceivedDateDesc(ServiceJobStatus.COMPLETED)
                   .stream().map(this::toDto).toList();
    }

    @Transactional
    public ServiceJobDTO create(ServiceJobDTO dto) {
        ServiceJob job = ServiceJob.builder()
            .jobNo(temporaryJobNo())
            .customer(customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")))
            .itemName(dto.getItemName())
            .deviceType(dto.getDeviceType())
            .serialNo(dto.getSerialNo())
            .color(dto.getColor())
            .itemCondition(dto.getItemCondition())
            .deviceConditions(dto.getDeviceConditions())
            .partRequests(dto.getPartRequests())
            .accessories(dto.getAccessories())
            .problemDesc(dto.getProblemDesc())
            .diagnosisNotes(dto.getDiagnosisNotes())
            .estimatedCost(dto.getEstimatedCost() != null ? dto.getEstimatedCost() : BigDecimal.ZERO)
            .finalCost(BigDecimal.ZERO)
            .status(ServiceJobStatus.RECEIVED)
            .serviceMode(dto.getServiceMode() == null ? ServiceMode.INDOOR : dto.getServiceMode())
            .bookingId(dto.getBookingId())
            .remark(dto.getRemark())
                .priority(hasAuthority("CAN_ACCESS_SERVICE_JOB_PRIORITY_UPDATE")
                    && dto.getPriority() != null && !dto.getPriority().isBlank()
                    ? dto.getPriority() : "NORMAL")
            .holdReason(dto.getHoldReason())
            .lines(new ArrayList<>())
            .productParts(new ArrayList<>())
            .build();

        job.setAssignedStaff(resolveAssignedTechnician(dto.getAssignedStaffId(), null));
        if (dto.getHelperStaffId() != null)
            job.setHelperStaff(staffRepo.findById(dto.getHelperStaffId()).orElse(null));
        if (dto.getShelfLocationId() != null)
            job.setShelfLocation(shelfLocationRepo.findById(dto.getShelfLocationId()).orElse(null));
        if (dto.getEstimatedCompletion() != null && !dto.getEstimatedCompletion().isBlank())
            job.setEstimatedCompletion(LocalDateTime.parse(dto.getEstimatedCompletion(), FMT));

        buildLines(job, dto);
        buildProductParts(job, dto);
        refreshEstimateApproval(job);
        job = repo.saveAndFlush(job);
        job.setJobNo(generateJobNo(job.getId()));
        job = repo.save(job);
        teamService.syncFromJob(job.getId());
        ServiceJobDTO result = toDto(job);
        recordActivity(job, "CREATED", null, job.getStatus() != null ? job.getStatus().name() : "RECEIVED", "Job created");
        broadcastJobEvent( "JOB_CREATED");
        return result;
    }

    @Transactional(readOnly = true)
    public List<ServiceJobDTO> findByBookingId(Integer bookingId) {
        return repo.findAllByBookingIdOrderByIdAsc(bookingId).stream()
                .filter(this::canReadJob)
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ServiceJobDTO update(Integer id, ServiceJobDTO dto) {
        ServiceJob job = repo.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        BigDecimal previousEstimate = job.getEstimatedCost() != null ? job.getEstimatedCost() : BigDecimal.ZERO;

        assertEditable(job);
        assertAssignmentAcceptedForEdit(job.getId());

        if (dto.getCustomerId() != null)
            job.setCustomer(customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
        if (dto.getServiceMode() != null && dto.getServiceMode() != job.getServiceMode()) {
            if (technicianVisitRepo.existsByServiceJobId(id)) {
                throw new IllegalStateException("Visit history ရှိသော Job ကို Indoor/Outdoor Type ပြောင်းမရပါ");
            }
            job.setServiceMode(dto.getServiceMode());
        }
        // Only managers with assign permission may change primary lead via job form.
        // Technicians must use Hand Over; otherwise Member save was overwriting Lead and hiding the job.
        if (hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")) {
            job.setAssignedStaff(resolveAssignedTechnician(dto.getAssignedStaffId(), job.getAssignedStaff()));
            if (dto.getHelperStaffId() != null)
                job.setHelperStaff(staffRepo.findById(dto.getHelperStaffId()).orElse(null));
        }
        job.setShelfLocation(dto.getShelfLocationId() != null
            ? shelfLocationRepo.findById(dto.getShelfLocationId()).orElse(null)
            : null);
        if (dto.getItemName() != null)          job.setItemName(dto.getItemName());
        if (dto.getDeviceType() != null)        job.setDeviceType(dto.getDeviceType());
        if (dto.getSerialNo() != null)          job.setSerialNo(dto.getSerialNo());
        if (dto.getColor() != null)             job.setColor(dto.getColor());
        if (dto.getItemCondition() != null)     job.setItemCondition(dto.getItemCondition());
        if (dto.getDeviceConditions() != null)  job.setDeviceConditions(dto.getDeviceConditions());
        if (dto.getPartRequests() != null)       job.setPartRequests(dto.getPartRequests());
        if (dto.getAccessories() != null)       job.setAccessories(dto.getAccessories());
        if (dto.getProblemDesc() != null)       job.setProblemDesc(dto.getProblemDesc());
        if (dto.getDiagnosisNotes() != null)    job.setDiagnosisNotes(dto.getDiagnosisNotes());
        if (dto.getEstimatedCost() != null)     job.setEstimatedCost(dto.getEstimatedCost());
        if (dto.getRemark() != null)           job.setRemark(dto.getRemark());
        if (hasAuthority("CAN_ACCESS_SERVICE_JOB_PRIORITY_UPDATE")
                && dto.getPriority() != null && !dto.getPriority().isBlank()) {
            job.setPriority(dto.getPriority());
        }
        if (dto.getHoldReason() != null)       job.setHoldReason(dto.getHoldReason());
        if (dto.getEstimatedCompletion() != null && !dto.getEstimatedCompletion().isBlank())
            job.setEstimatedCompletion(LocalDateTime.parse(dto.getEstimatedCompletion(), FMT));

        if (dto.getLines() != null) {
            job.getLines().clear();
            buildLines(job, dto);
        }
        if (dto.getProductParts() != null) {
            // Job parts are only issued from inventory when settlement creates the
            // internal sale. Before settlement there is nothing to put back into stock.
            job.getProductParts().clear();
            buildProductParts(job, dto);
        } else {
            recalculateEstimatedCost(job);
        }
        BigDecimal currentEstimate = job.getEstimatedCost() != null ? job.getEstimatedCost() : BigDecimal.ZERO;
        if (previousEstimate.compareTo(currentEstimate) != 0) {
            job.setEstimateApproved(Boolean.FALSE);
            job.setEstimateApprovedAt(null);
            job.setEstimateApprovedBy(null);
            recordActivity(job, "ESTIMATE_REVISED", null, job.getStatus() != null ? job.getStatus().name() : null,
                    previousEstimate + " → " + currentEstimate);
        }
        refreshEstimateApproval(job);
        job.setModifiedBy(currentUsername());
        job.setModifiedAt(LocalDateTime.now());
        job = repo.save(job);
        teamService.syncFromJob(job.getId());
        ServiceJobDTO updated = toDto(job);
        recordActivity(job, "UPDATED", null, job.getStatus() != null ? job.getStatus().name() : null, "Job updated");
        broadcastJobEvent( "JOB_UPDATED");
        return updated;
    }

    @Transactional
    public ServiceJobDTO updateStatus(Integer id, ServiceJobStatus status) {
        return updateStatus(id, status, null);
    }

    @Transactional
    public ServiceJobDTO updateStatus(Integer id, ServiceJobStatus status, String holdReason) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));

        if (job.getStatus() == ServiceJobStatus.DELIVERED)
            throw new IllegalStateException("Delivered jobs cannot change status.");
        if (job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Cancelled jobs cannot change status.");
        if (job.getPaymentStatus() != null && !Boolean.TRUE.equals(job.getVoided()))
            throw new IllegalStateException("This job has already been settled. Status cannot be changed after payment is recorded.");

        assertAssignmentAcceptedForEdit(id);

        ServiceJobStatus from = job.getStatus();
        validateStatusTransition(from, status);
        if (status == ServiceJobStatus.COMPLETED)
            teamService.assertCanComplete(id);
        if (status == ServiceJobStatus.COMPLETED && !Boolean.TRUE.equals(job.getFinalApprovalStatus()))
            throw new IllegalStateException("Supervisor final approval is required before completing this job");
        if (status == ServiceJobStatus.WAITING_PARTS && (holdReason == null || holdReason.isBlank()))
            throw new IllegalArgumentException("ပစ္စည်းစောင့်ရသည့်အကြောင်းရင်း ဖြည့်ပါ။");
        if (status == ServiceJobStatus.IN_PROGRESS && job.getLines() != null
                && job.getLines().stream().anyMatch(l -> l.getConfirmationStatus() == ServiceLineConfirmationStatus.CUSTOMER_HOLD))
            throw new IllegalStateException("Customer estimate hold — hold ဖြေရှင်းပြီးမှ ပြင်ဆင်မှုစတင်နိုင်ပါသည်။");
        if (status == ServiceJobStatus.IN_PROGRESS && job.getEstimatedCost() != null
                && job.getEstimatedCost().compareTo(BigDecimal.ZERO) > 0
                && !Boolean.TRUE.equals(job.getEstimateApproved()))
            throw new IllegalStateException("Estimate ကို customer အတည်ပြုပြီးမှ ပြင်ဆင်မှုစတင်နိုင်ပါသည်။");
        job.setStatus(status);
        if (status == ServiceJobStatus.WAITING_PARTS)
            job.setHoldReason(holdReason);
        else if (status != ServiceJobStatus.WAITING_PARTS)
            job.setHoldReason(null);
        if (status == ServiceJobStatus.IN_PROGRESS && job.getWorkStartedAt() == null)
            job.setWorkStartedAt(LocalDateTime.now());
        if (status == ServiceJobStatus.COMPLETED)
            job.setCompletedDate(LocalDateTime.now());
        syncLineStatuses(job, status);
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "STATUS", from != null ? from.name() : null, status.name(),
                status == ServiceJobStatus.WAITING_PARTS ? holdReason : null);
        broadcastJobEvent( "JOB_STATUS_CHANGED");
        return result;
    }

    private void validateStatusTransition(ServiceJobStatus from, ServiceJobStatus to) {
        if (from == null || from == to) return;
        boolean allowed = switch (from) {
            case RECEIVED -> to == ServiceJobStatus.ASSIGNED || to == ServiceJobStatus.INSPECTING || to == ServiceJobStatus.CANCELLED;
            case ASSIGNED -> to == ServiceJobStatus.INSPECTING || to == ServiceJobStatus.IN_PROGRESS || to == ServiceJobStatus.CANCELLED;
            case INSPECTING -> to == ServiceJobStatus.IN_PROGRESS || to == ServiceJobStatus.WAITING_PARTS || to == ServiceJobStatus.CANCELLED;
            case WAITING_PARTS -> to == ServiceJobStatus.IN_PROGRESS || to == ServiceJobStatus.CANCELLED;
            case IN_PROGRESS -> to == ServiceJobStatus.WAITING_PARTS || to == ServiceJobStatus.COMPLETED || to == ServiceJobStatus.CANCELLED;
            case COMPLETED -> to == ServiceJobStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid service job status transition: " + from + " → " + to);
    }

    @Transactional
    public ServiceJobDTO approveFinalCompletion(Integer id) {
        if (!hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN"))
            throw new AccessDeniedException("Supervisor approval is required");
        ServiceJob job = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getStatus() == ServiceJobStatus.DELIVERED || job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Closed jobs cannot be approved");
        teamService.assertCanComplete(id);
        if (!Boolean.TRUE.equals(job.getLeadFinalCheckStatus()))
            throw new IllegalStateException("Lead Technician final check is required before supervisor approval");
        if (job.getLeadFinalCheckedAt() == null || assignmentRepository
                .findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(id, EnumSet.of(
                        AssignmentStatus.PENDING, AssignmentStatus.ACTIVE,
                        AssignmentStatus.PAUSED, AssignmentStatus.COMPLETED)).stream()
                .anyMatch(a -> a.getLastActionAt() != null && a.getLastActionAt().isAfter(job.getLeadFinalCheckedAt())))
            throw new IllegalStateException("Work changed after Lead final check; submit Lead final check again");
        job.setFinalApprovalStatus(true);
        job.setFinalApprovedBy(currentUsername());
        job.setFinalApprovedAt(LocalDateTime.now());
        if (job.getStatus() != ServiceJobStatus.COMPLETED) {
            ServiceJobStatus currentStatus = job.getStatus();
            job.setStatus(ServiceJobStatus.COMPLETED);
            job.setCompletedDate(LocalDateTime.now());
            syncLineStatuses(job, ServiceJobStatus.COMPLETED);
            recordActivity(job, "FINAL_APPROVAL", currentStatus != null ? currentStatus.name() : null, ServiceJobStatus.COMPLETED.name(), null);
        }
        ServiceJobDTO result = toDto(repo.save(job));
        broadcastJobEvent( "JOB_FINAL_APPROVED");
        return result;
    }

    @Transactional
    public ServiceJobDTO submitLeadFinalCheck(Integer id, String note) {
        ServiceJob job = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getStatus() == ServiceJobStatus.DELIVERED || job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Closed jobs cannot be final checked");
        teamService.assertLeadCanFinalCheck(id);
        LocalDateTime now = LocalDateTime.now();
        job.setLeadFinalCheckStatus(true);
        job.setLeadFinalCheckedBy(currentUsername());
        job.setLeadFinalCheckedAt(now);
        job.setLeadFinalCheckNote(trimToNull(note));
        job.setFinalReturnReason(null);
        if (!supervisorApprovalRequired()) {
            ServiceJobStatus from = job.getStatus();
            job.setFinalApprovalStatus(true);
            job.setFinalApprovedBy("AUTO_AFTER_LEAD_CHECK");
            job.setFinalApprovedAt(now);
            job.setStatus(ServiceJobStatus.COMPLETED);
            job.setCompletedDate(now);
            syncLineStatuses(job, ServiceJobStatus.COMPLETED);
            recordActivity(job, "LEAD_FINAL_CHECK_AUTO_APPROVED", from == null ? null : from.name(),
                    ServiceJobStatus.COMPLETED.name(), trimToNull(note));
        } else {
            recordActivity(job, "LEAD_FINAL_CHECK", job.getStatus().name(), job.getStatus().name(), trimToNull(note));
        }
        ServiceJobDTO result = toDto(repo.save(job));
        broadcastJobEvent( "JOB_LEAD_FINAL_CHECKED");
        return result;
    }

    @Transactional
    public ServiceJobDTO returnFinalCheck(Integer id, String reason) {
        if (!hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN"))
            throw new AccessDeniedException("Supervisor permission is required");
        if (trimToNull(reason) == null)
            throw new IllegalArgumentException("Return reason is required");
        ServiceJob job = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (!Boolean.TRUE.equals(job.getLeadFinalCheckStatus()))
            throw new IllegalStateException("Lead final check has not been submitted");
        if (job.getStatus() == ServiceJobStatus.COMPLETED || job.getStatus() == ServiceJobStatus.DELIVERED
                || job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Completed or closed jobs cannot be returned from final check");
        teamService.reopenLeadForRework(id, reason.trim());
        job.setLeadFinalCheckStatus(false);
        job.setLeadFinalCheckedBy(null);
        job.setLeadFinalCheckedAt(null);
        job.setLeadFinalCheckNote(null);
        job.setFinalApprovalStatus(false);
        job.setFinalApprovedBy(null);
        job.setFinalApprovedAt(null);
        job.setFinalReturnReason(reason.trim());
        job.setStatus(ServiceJobStatus.IN_PROGRESS);
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "SUPERVISOR_RETURNED", ServiceJobStatus.IN_PROGRESS.name(),
                ServiceJobStatus.IN_PROGRESS.name(), reason.trim());
        broadcastJobEvent( "JOB_FINAL_CHECK_RETURNED");
        return result;
    }

    private boolean supervisorApprovalRequired() {
        return companySettingsRepository.findAll().stream().findFirst()
                .map(s -> !Boolean.FALSE.equals(s.getServiceSupervisorApprovalRequired())).orElse(true);
    }

    private boolean serviceAllowDeliveryWithDue() {
        return companySettingsRepository.findAll().stream().findFirst()
                .map(s -> Boolean.TRUE.equals(s.getServiceAllowDeliveryWithDue())).orElse(false);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Staff resolveAssignedTechnician(Integer requestedStaffId, Staff fallback) {
        if (hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")) {
            if (requestedStaffId != null) return staffRepo.findById(requestedStaffId).orElse(fallback);
            return fallback;
        }
        Staff mine = currentUserStaff();
        if (mine == null) {
            throw new AccessDeniedException("ဝန်ဆောင်မှုအတွက် သင့်ကျွမ်းကျင်သူ Staff ကိုသာ သတ်မှတ်နိုင်ပါသည်။");
        }
        if (requestedStaffId != null && !mine.getId().equals(requestedStaffId)) {
            throw new AccessDeniedException("ဝန်ဆောင်မှုအတွက် သင့်ကျွမ်းကျင်သူ Staff ကိုသာ သတ်မှတ်နိုင်ပါသည်။");
        }
        return mine;
    }

    private Staff currentUserStaff() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        User user = username == null ? null : userRepository.findByUsernameOrEmail(username, username).orElse(null);
        return user != null ? user.getStaff() : null;
    }

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private Integer resolveStaffScope(Integer requestedStaffId) {
        if (hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")) {
            return requestedStaffId;
        }
        Staff mine = currentUserStaff();
        return mine != null ? mine.getId() : -1;
    }

    private void assertCanRead(ServiceJob job) {
        if (!canReadJob(job)) {
            throw new AccessDeniedException("You can only view service jobs assigned to your staff account");
        }
    }

    private boolean canReadJob(ServiceJob job) {
        if (hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")) return true;
        Staff mine = currentUserStaff();
        if (mine == null || job == null || job.getId() == null) return false;
        return canReadJobAsStaff(job, mine.getId());
    }

    private boolean canReadJobAsStaff(ServiceJob job, Integer staffId) {
        if (staffId == null || staffId <= 0) return false;
        Integer jobId = job.getId();
        if (handoverRepository.existsByServiceJobIdAndToStaffIdAndStatus(
                jobId, staffId, HandoverStatus.PENDING)) return true;
        if (handoverRepository.existsByServiceJobIdAndFromStaffId(jobId, staffId)) return true;
        if (assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(
                jobId, staffId, VISIBLE_ASSIGNMENT_STATUSES)) return true;
        if (job.getAssignedStaff() != null && staffId.equals(job.getAssignedStaff().getId())) return true;
        if (job.getHelperStaff() != null && staffId.equals(job.getHelperStaff().getId())) return true;
        return false;
    }

    private boolean isJobVisibleToStaff(ServiceJob job, Integer staffId) {
        if (staffId == null || staffId <= 0) return false;
        Integer jobId = job.getId();
        if (handoverRepository.existsByServiceJobIdAndToStaffIdAndStatus(
                jobId, staffId, HandoverStatus.PENDING)) return true;
        if (handoverRepository.existsByServiceJobIdAndFromStaffIdAndStatus(
                jobId, staffId, HandoverStatus.PENDING)) return false;
        if (assignmentRepository.existsByServiceJobIdAndStaffIdAndStatusIn(
                jobId, staffId, VISIBLE_ASSIGNMENT_STATUSES)) return true;
        if (job.getAssignedStaff() != null && staffId.equals(job.getAssignedStaff().getId())) return true;
        if (job.getHelperStaff() != null && staffId.equals(job.getHelperStaff().getId())) return true;
        return false;
    }

    private void enrichPendingHandovers(List<ServiceJobDTO> dtos, Integer staffId) {
        if (dtos.isEmpty() || staffId == null || staffId <= 0) return;
        List<Integer> jobIds = dtos.stream().map(ServiceJobDTO::getId).toList();
        Map<Integer, ServiceJobHandover> pendingByJob = handoverRepository
                .findAllByToStaffIdAndStatusAndServiceJobIdIn(staffId, HandoverStatus.PENDING, jobIds)
                .stream()
                .collect(Collectors.toMap(h -> h.getServiceJob().getId(), h -> h, (left, right) -> left));
        for (ServiceJobDTO dto : dtos) {
            ServiceJobHandover handover = pendingByJob.get(dto.getId());
            if (handover == null) continue;
            dto.setPendingHandoverForMe(true);
            dto.setPendingHandoverId(handover.getId());
            dto.setPendingHandoverFromStaffName(handover.getFromAssignment().getStaff().getName());
            dto.setPendingHandoverRemainingWork(handover.getRemainingWork());
        }
    }

    private void enrichTeamFlags(List<ServiceJobDTO> dtos, Integer staffId) {
        if (dtos.isEmpty() || staffId == null || staffId <= 0) return;
        boolean canAssign = hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN");
        List<Integer> jobIds = dtos.stream().map(ServiceJobDTO::getId).filter(java.util.Objects::nonNull).toList();
        if (jobIds.isEmpty()) return;
        Map<Integer, ServiceJobAssignment> byJob = assignmentRepository
                .findAllByStaffIdAndServiceJobIdInAndStatusIn(staffId, jobIds, VISIBLE_ASSIGNMENT_STATUSES)
                .stream()
                .collect(Collectors.toMap(a -> a.getServiceJob().getId(), a -> a, (left, right) -> left));
        for (ServiceJobDTO dto : dtos) {
            ServiceJobAssignment assignment = byJob.get(dto.getId());
            if (assignment == null) {
                if (staffId.equals(dto.getAssignedStaffId()) || staffId.equals(dto.getHelperStaffId())) {
                    dto.setOnTeamForMe(true);
                    dto.setMyAssignmentRole(staffId.equals(dto.getAssignedStaffId()) ? "LEAD" : "HELPER");
                }
                // No PENDING assignment row → already allowed (legacy sync creates ACTIVE).
                dto.setCanEditJob(true);
                continue;
            }
            dto.setOnTeamForMe(true);
            dto.setMyAssignmentRole(assignment.getRole() != null ? assignment.getRole().name() : null);
            dto.setMyAssignmentStatus(assignment.getStatus() != null ? assignment.getStatus().name() : null);
            dto.setCanEditJob(canAssign || assignment.getStatus() != AssignmentStatus.PENDING);
        }
    }

    /**
     * Technicians must accept their PENDING assignment before editing the job form/status.
     * Managers with technician-assign permission are exempt.
     */
    private void assertAssignmentAcceptedForEdit(Integer jobId) {
        if (hasAuthority("CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN")) return;
        Staff mine = currentUserStaff();
        if (mine == null || jobId == null) return;
        boolean pending = assignmentRepository
                .findAllByStaffIdAndServiceJobIdInAndStatusIn(
                        mine.getId(), List.of(jobId), EnumSet.of(AssignmentStatus.PENDING))
                .stream()
                .anyMatch(a -> a.getStatus() == AssignmentStatus.PENDING);
        if (pending) {
            throw new IllegalStateException(
                    "Technician Assignment ကို လက်ခံပြီးမှသာ ဝန်ဆောင်မှု ပြင်ဆင်နိုင်ပါသည်။");
        }
    }

    @Transactional
    public ServiceJobDTO settle(Integer id, SettleDTO dto) {
        ServiceJob job = repo.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));

        teamService.assertCanComplete(id);
        assertReadyForSettlement(job);

        boolean isFoc = Boolean.TRUE.equals(dto.getFoc());
        stampBilledPrices(job);

        DiscountAllocationMethod allocationMethod = DiscountAllocationMethod.from(dto.getDiscountAllocationMethod());
        BigDecimal discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        ServiceJobSettlementBreakdown breakdown = ServiceJobSettlementCalculator.compute(job, discount, allocationMethod, isFoc);

        BigDecimal grossAmount = breakdown.gross();
        BigDecimal laborNet = breakdown.laborNet();
        BigDecimal partsNet = breakdown.partsNet();
        BigDecimal netAmt = breakdown.net();

        if (grossAmount.compareTo(BigDecimal.ZERO) <= 0 && !isFoc) {
            BigDecimal fallback = dto.getFinalCost() != null ? dto.getFinalCost() : nz(job.getEstimatedCost());
            if (fallback.signum() > 0) {
                grossAmount = fallback;
                netAmt = grossAmount.subtract(discount).max(BigDecimal.ZERO);
                boolean hasLabor = job.getLines() != null && job.getLines().stream()
                        .anyMatch(org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine::isBillable);
                boolean hasParts = job.getProductParts() != null && !job.getProductParts().isEmpty();
                if (hasParts && !hasLabor) {
                    partsNet = netAmt;
                    laborNet = BigDecimal.ZERO;
                    breakdown = new ServiceJobSettlementBreakdown(
                            BigDecimal.ZERO, grossAmount, discount, laborNet, partsNet, grossAmount, netAmt);
                } else {
                    laborNet = netAmt;
                    partsNet = BigDecimal.ZERO;
                    breakdown = new ServiceJobSettlementBreakdown(
                            grossAmount, BigDecimal.ZERO, discount, laborNet, partsNet, grossAmount, netAmt);
                }
            }
        }

        BigDecimal paid = paymentTotal(dto.getPayments(), dto.getPaidAmount() != null ? dto.getPaidAmount() : netAmt).min(netAmt);
        BigDecimal due = netAmt.subtract(paid);

        // Credit checks when there is outstanding due
        if (due.compareTo(BigDecimal.ZERO) > 0) {
            Customer customer = job.getCustomer();
            if (Boolean.TRUE.equals(customer.getBlacklisted()))
                throw new RuntimeException("Customer is blacklisted; cash settlement only");
            if (Boolean.TRUE.equals(customer.getCreditHold()))
                throw new RuntimeException("Customer credit is on hold");
            creditService.enforceCreditLimitForServiceJob(customer.getId(), due, customer, job.getId());
        }

        job.setFinalCost(grossAmount);
        job.setDiscountAmount(discount);
        job.setFoc(isFoc);
        job.setNetAmount(netAmt);
        job.setLaborNetAmount(laborNet);
        job.setPartsNetAmount(partsNet);
        job.setDiscountAllocationMethod(isFoc ? null : allocationMethod);
        job.setPaidAmount(paid);
        job.setDueAmount(due);
        job.setDueDate(dto.getDueDate());
        job.setPaymentStatus(due.compareTo(BigDecimal.ZERO) <= 0
                ? org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Paid
                : (paid.compareTo(BigDecimal.ZERO) > 0
                        ? org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Partial
                        : org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Pending));
        job.setCreditStatus(due.compareTo(BigDecimal.ZERO) > 0
                ? org.sspd.servicemgmt.saleoptions.model.CreditStatus.Active
                : org.sspd.servicemgmt.saleoptions.model.CreditStatus.Not_Credit);
        job.setStatus(ServiceJobStatus.COMPLETED);
        if (job.getCompletedDate() == null) job.setCompletedDate(LocalDateTime.now());
        job.setVoided(Boolean.FALSE);
        job.setVoidReason(null);
        job.setVoidedBy(null);
        job.setVoidedAt(null);
        job.setSettledBy(currentActorDisplayName());

        PaymentMethod pm = null;
        if (dto.getPaymentMethodId() != null)
            pm = paymentMethodRepo.findById(dto.getPaymentMethodId()).orElse(null);
        job.setPaymentMethod(pm);

        boolean hasProducts = job.getProductParts() != null && !job.getProductParts().isEmpty();

        if (hasProducts) {
            SaleDTO saleDto = createSaleFromServiceJob(job, dto);
            SaleDTO createdSale = saleService.save(saleDto);
            job.setSaleId(createdSale.getId());
        }

        ServiceJob saved = repo.save(job);

        // Journal covers both cash and credit portions (revenue recognised at settlement)
        if (!isFoc && netAmt.compareTo(BigDecimal.ZERO) > 0) {
            createJournalEntry(saved, dto.getPaymentAccountId(), pm, paid, due, breakdown, dto.getPayments());
        }
        // Payment transaction only for money actually received
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            if (pm == null && dto.getPaymentMethodId() != null)
                pm = paymentMethodRepo.findById(dto.getPaymentMethodId()).orElse(null);
            createPaymentTransactions(saved, pm, paid, dto.getTransactionNo(), dto.getPayments());
        }
        recordActivity(saved, "SETTLED", saved.getStatus().name(), ServiceJobStatus.COMPLETED.name(),
                "Settled " + saved.getNetAmount());
        broadcastJobEvent( "JOB_SETTLED");
        return toDto(repo.findById(saved.getId()).orElse(saved));
    }

    @Transactional
    public ServiceJobDTO payDue(Integer id, SettleDTO dto) {
        ServiceJob job = repo.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));

        BigDecimal currentDue = job.getDueAmount() != null ? job.getDueAmount() : BigDecimal.ZERO;
        if (currentDue.compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("No outstanding due for this service job.");

        BigDecimal incoming = paymentTotal(dto.getPayments(), dto.getPaidAmount());
        BigDecimal paymentDiscount = nz(dto.getPaymentDiscountAmount());
        if (incoming.compareTo(BigDecimal.ZERO) < 0 || paymentDiscount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Payment and discount amounts cannot be negative.");
        if (incoming.add(paymentDiscount).compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Payment or approved payment discount must be greater than zero.");
        if (incoming.compareTo(BigDecimal.ZERO) > 0 && dto.getPaymentMethodId() == null && (dto.getPayments() == null || dto.getPayments().isEmpty()))
            throw new RuntimeException("Payment Method ရွေးပါ");
        if (incoming.add(paymentDiscount).compareTo(currentDue) > 0)
            throw new IllegalArgumentException("Payment plus discount cannot exceed outstanding due.");
        if (paymentDiscount.compareTo(BigDecimal.ZERO) > 0) {
            if (!hasAuthority("CAN_ACCESS_SERVICE_JOB_PAYMENT_DISCOUNT_APPROVE")
                    && !hasAuthority("ROLE_ADMINISTRATOR") && !hasAuthority("ROLE_MANAGER"))
                throw new org.springframework.security.access.AccessDeniedException("Payment discount approval permission is required.");
            if (dto.getPaymentDiscountApprovalNote() == null || dto.getPaymentDiscountApprovalNote().isBlank())
                throw new IllegalArgumentException("Payment discount approval note is required.");
            job.setPaymentDiscountApprovedBy(currentUsername());
            job.setPaymentDiscountApprovedAt(LocalDateTime.now());
            job.setPaymentDiscountApprovalNote(dto.getPaymentDiscountApprovalNote().trim());
            job.setPaymentDiscountAmount(nz(job.getPaymentDiscountAmount()).add(paymentDiscount));
        }

        BigDecimal applied = incoming;
        BigDecimal receivableReduction = incoming.add(paymentDiscount);
        BigDecimal newPaid = nz(job.getPaidAmount()).add(applied);
        BigDecimal newDue  = currentDue.subtract(receivableReduction);

        job.setPaidAmount(newPaid);
        job.setDueAmount(newDue);
        job.setPaymentStatus(newDue.compareTo(BigDecimal.ZERO) <= 0
                ? org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Paid
                : org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Partial);
        job.setCreditStatus(newDue.compareTo(BigDecimal.ZERO) > 0
                ? org.sspd.servicemgmt.saleoptions.model.CreditStatus.Active
                : org.sspd.servicemgmt.saleoptions.model.CreditStatus.Paid);

        PaymentMethod pm = null;
        if (dto.getPaymentMethodId() != null)
            pm = paymentMethodRepo.findById(dto.getPaymentMethodId()).orElse(null);

        ServiceJob saved = repo.save(job);

        // Journal: DR Cash/Bank, CR Accounts Receivable
        createPayDueJournal(saved, dto.getPaymentAccountId(), pm, applied, paymentDiscount, receivableReduction, dto.getPayments());
        if (applied.compareTo(BigDecimal.ZERO) > 0)
            createPaymentTransactions(saved, pm, applied, dto.getTransactionNo(), dto.getPayments());

        recordActivity(saved, "DUE_COLLECTED", null, saved.getPaymentStatus().name(),
                "Cash/Bank " + applied + ", payment discount " + paymentDiscount + ", AR reduced " + receivableReduction);
        broadcastJobEvent( paymentDiscount.signum() > 0 ? "JOB_DUE_PAID_WITH_DISCOUNT" : "JOB_PAY_DUE");
        return toDto(saved);
    }

    private void createPayDueJournal(ServiceJob job, Integer paymentAccountId,
                                     PaymentMethod pm, BigDecimal applied, BigDecimal paymentDiscount,
                                     BigDecimal receivableReduction, List<PaymentTransactionDTO> payments) {
        if (receivableReduction.compareTo(BigDecimal.ZERO) <= 0) return;

        List<JournalDetailDTO> details = new ArrayList<>();

        for (PaymentLine line : applied.signum() > 0 ? resolvePaymentLines(payments, applied, pm) : List.<PaymentLine>of()) {
            JournalDetailDTO drCash = new JournalDetailDTO();
            drCash.setAccountId(resolveCashAccount(line.method(), paymentAccountId));
            drCash.setDebit(line.amount());
            drCash.setCredit(BigDecimal.ZERO);
            details.add(drCash);
        }

        if (paymentDiscount.signum() > 0) {
            JournalDetailDTO drDiscount = new JournalDetailDTO();
            drDiscount.setAccountId(accountResolver.paymentDiscount().getId());
            drDiscount.setDebit(paymentDiscount);
            drDiscount.setCredit(BigDecimal.ZERO);
            details.add(drDiscount);
        }

        // CR Accounts Receivable
        JournalDetailDTO crAr = new JournalDetailDTO();
        crAr.setAccountId(accountResolver.receivable().getId());
        crAr.setDebit(BigDecimal.ZERO);
        crAr.setCredit(receivableReduction);
        details.add(crAr);

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(job.getJobNo() + "-PAY-" + System.nanoTime());
        entry.setEntryDate(LocalDateTime.now());
        entry.setDescription("AR collection for service job " + job.getJobNo());
        entry.setStaffId(job.getAssignedStaff() != null ? job.getAssignedStaff().getId() : null);
        entry.setDetails(details);

        journalWriter.write(entry);
    }

    @Transactional
    public ServiceJobDTO deliver(Integer id) {
        ServiceJob job = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getStatus() != ServiceJobStatus.COMPLETED)
            throw new IllegalStateException("Only completed jobs can be marked as delivered");
        BigDecimal due = job.getDueAmount() != null ? job.getDueAmount() : BigDecimal.ZERO;
        boolean settledOrFree = Boolean.TRUE.equals(job.getFoc())
                || (job.getPaymentStatus() != null && due.compareTo(BigDecimal.ZERO) <= 0);
        boolean dueDeliveryApproved = due.compareTo(BigDecimal.ZERO) > 0
                && serviceAllowDeliveryWithDue()
                && job.getDueDeliveryApprovedAt() != null;
        if (!settledOrFree && !dueDeliveryApproved)
            throw new IllegalStateException("ငွေရှင်းပြီး သို့မဟုတ် FOC အတည်ပြုပြီးမှ ပစ္စည်းပေးအပ်နိုင်ပါသည်။");
        job.setStatus(ServiceJobStatus.DELIVERED);
        job.setDeliveredDate(LocalDateTime.now());
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "DELIVERED", ServiceJobStatus.COMPLETED.name(), ServiceJobStatus.DELIVERED.name(), null);
        broadcastJobEvent( "JOB_DELIVERED");
        return result;
    }

    @Transactional
    public ServiceJobDTO approveDueDelivery(Integer id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Due delivery approval reason is required.");
        ServiceJob job = repo.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getStatus() != ServiceJobStatus.COMPLETED || nz(job.getDueAmount()).signum() <= 0)
            throw new IllegalStateException("Only a completed job with outstanding due can be approved.");
        boolean enabled = serviceAllowDeliveryWithDue();
        if (!enabled) throw new IllegalStateException("Delivery with outstanding due is disabled in Company Settings.");
        job.setDueDeliveryApprovedBy(currentUsername());
        job.setDueDeliveryApprovedAt(LocalDateTime.now());
        job.setDueDeliveryApprovalReason(reason.trim());
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "DUE_DELIVERY_APPROVED", null, job.getStatus().name(), reason.trim());
        broadcastJobEvent( "JOB_DUE_DELIVERY_APPROVED");
        return result;
    }

    /**
     * Internal inventory sale for job parts. Selling totals use part subtots (after line discounts);
     * overall settlement discount allocated to parts (pro-rata / parts-first / labor-first share)
     * is applied as the sale header discount so Sale net = partsNetAmount.
     */
    private SaleDTO createSaleFromServiceJob(ServiceJob job, SettleDTO dto) {
        SaleDTO saleDto = new SaleDTO();
        saleDto.setCustomerId(job.getCustomer().getId());
        saleDto.setStaffId(job.getAssignedStaff() != null ? job.getAssignedStaff().getId() : null);
        saleDto.setSaleDate(LocalDateTime.now());

        List<SaleDetailDTO> details = new ArrayList<>();
        BigDecimal partsBalance = BigDecimal.ZERO;
        for (ServiceJobPart part : job.getProductParts()) {
            SaleDetailDTO detail = new SaleDetailDTO();
            detail.setProductId(part.getProduct().getId());
            detail.setProductName(part.getProduct().getName());
            detail.setQty(part.getQty());
            boolean covered = Boolean.TRUE.equals(part.getWarrantyCovered());
            BigDecimal lineDiscount = covered ? BigDecimal.ZERO
                    : (part.getDiscountAmount() != null ? part.getDiscountAmount() : BigDecimal.ZERO);
            BigDecimal subtotal = part.getSubtotal() != null
                    ? part.getSubtotal().max(BigDecimal.ZERO)
                    : ServiceJobSettlementCalculator.partBalance(part);
            detail.setUnitPrice(covered ? BigDecimal.ZERO : part.getUnitPrice());
            detail.setDiscountAmount(lineDiscount);
            detail.setSubtotal(covered ? BigDecimal.ZERO : subtotal);
            List<String> serialNumbers = splitSerials(part.getSerialNumbers());
            if (Boolean.TRUE.equals(part.getProduct().getHasSerial()) && serialNumbers.isEmpty()) {
                throw new RuntimeException("Serial numbers are required for product: " + part.getProduct().getName());
            }
            detail.setSerialNumbers(serialNumbers);
            details.add(detail);
            if (!covered) partsBalance = partsBalance.add(nz(detail.getSubtotal()));
        }

        BigDecimal partsNet = job.getPartsNetAmount() != null ? job.getPartsNetAmount() : partsBalance;
        BigDecimal overallPartsDiscount = partsBalance.subtract(partsNet).max(BigDecimal.ZERO);

        saleDto.setDetails(details);
        saleDto.setDiscountAmount(overallPartsDiscount);
        saleDto.setPaidAmount(BigDecimal.ZERO);
        saleDto.setPaymentMethodId(dto.getPaymentMethodId());
        saleDto.setPaymentAccountId(dto.getPaymentAccountId());
        saleDto.setRemark("Service Job: " + job.getJobNo()
                + (overallPartsDiscount.signum() > 0
                ? " | Parts overall discount " + overallPartsDiscount.toPlainString()
                : ""));
        saleDto.setServiceJobSale(true); // inventory-only; payment & journals handled at job level

        return saleDto;
    }

    private BigDecimal calculateLaborCost(ServiceJob job) {
        return job.getLines() == null ? BigDecimal.ZERO : job.getLines().stream()
            .map(ServiceJobLine::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public ServiceJobDTO createRework(Integer originalJobId, ReworkRequestDTO req) {
        ServiceJob original = repo.findById(originalJobId)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + originalJobId));
        if (original.getStatus() != ServiceJobStatus.DELIVERED)
            throw new IllegalStateException("Rework can only be created for DELIVERED jobs");
        if (req.getReworkType() == null)
            throw new IllegalArgumentException("Return type is required");

        ReworkResolutionMode mode = req.getResolutionMode() != null
            ? req.getResolutionMode() : ReworkResolutionMode.SERVICE_ONLY;
        ServiceJobPart originalPart = null;
        Product replacementProduct = null;
        int replacementQty = req.getReplacementQty() != null ? req.getReplacementQty() : 1;
        BigDecimal originalCredit = BigDecimal.ZERO;
        BigDecimal replacementPrice = BigDecimal.ZERO;
        BigDecimal customerCharge = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        List<String> replacementSerials = req.getReplacementSerialNumbers() != null
            ? req.getReplacementSerialNumbers().stream().map(String::trim).filter(v -> !v.isBlank()).distinct().toList()
            : List.of();

        if (mode != ReworkResolutionMode.SERVICE_ONLY) {
            if (req.getOriginalPartId() == null)
                throw new IllegalArgumentException("Original job part is required");
            originalPart = original.getProductParts().stream()
                .filter(p -> p.getId().equals(req.getOriginalPartId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected part does not belong to the original job"));
            if (req.getOldPartDisposition() == null)
                throw new IllegalArgumentException("Old part disposition is required");
            if (reworkResolutionRepo.existsByOriginalPartId(originalPart.getId()))
                throw new IllegalStateException("This original job part has already been returned/disposed in another rework");

            BigDecimal lineValue = originalPart.getUnitPrice().multiply(BigDecimal.valueOf(originalPart.getQty()))
                .subtract(originalPart.getDiscountAmount() != null ? originalPart.getDiscountAmount() : BigDecimal.ZERO)
                .max(BigDecimal.ZERO);
            originalCredit = req.getWarrantyCredit() != null
                ? req.getWarrantyCredit().max(BigDecimal.ZERO).min(lineValue) : lineValue;

            if (mode == ReworkResolutionMode.REPLACE_SAME || mode == ReworkResolutionMode.UPGRADE) {
                if (req.getReplacementProductId() == null)
                    throw new IllegalArgumentException("Replacement product is required");
                if (replacementQty <= 0)
                    throw new IllegalArgumentException("Replacement quantity must be greater than zero");
                replacementProduct = productRepo.findById(req.getReplacementProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Replacement product not found"));
                validateReplacementAvailability(replacementProduct, replacementQty, replacementSerials);
                BigDecimal unitPrice = replacementProduct.getSellingPrice() != null
                    ? replacementProduct.getSellingPrice() : BigDecimal.ZERO;
                replacementPrice = unitPrice.multiply(BigDecimal.valueOf(replacementQty));
                customerCharge = mode == ReworkResolutionMode.UPGRADE
                    ? replacementPrice.subtract(originalCredit).max(BigDecimal.ZERO) : BigDecimal.ZERO;
            } else if (mode == ReworkResolutionMode.REFUND) {
                if (req.getRefundPaymentMethodId() == null)
                    throw new IllegalArgumentException("Refund payment method is required");
                refundAmount = req.getRefundAmount() != null ? req.getRefundAmount() : originalCredit;
                if (refundAmount.compareTo(BigDecimal.ZERO) <= 0 || refundAmount.compareTo(originalCredit) > 0)
                    throw new IllegalArgumentException("Refund must be greater than zero and cannot exceed warranty credit");
            }
        }

        ServiceJob rework = ServiceJob.builder()
            .jobNo(temporaryJobNo()).customer(original.getCustomer())
            .assignedStaff(resolveAssignedTechnician(req.getAssignedStaffId(), original.getAssignedStaff()))
            .itemName(original.getItemName()).deviceType(original.getDeviceType()).itemCondition(original.getItemCondition())
            .deviceConditions(original.getDeviceConditions()).partRequests(original.getPartRequests())
            .serialNo(original.getSerialNo()).color(original.getColor()).accessories(original.getAccessories())
            .shelfLocation(original.getShelfLocation())
            .problemDesc(req.getProblemDesc() != null ? req.getProblemDesc() : original.getProblemDesc())
            .estimatedCost(customerCharge).finalCost(BigDecimal.ZERO).status(ServiceJobStatus.RECEIVED)
            .serviceMode(original.getServiceMode() == null ? ServiceMode.INDOOR : original.getServiceMode())
            .rework(true).reworkType(req.getReworkType()).parentJobId(originalJobId)
            .replacementItemName(replacementProduct != null ? replacementProduct.getName() : req.getReplacementItemName())
            .replacementSerialNo(!replacementSerials.isEmpty() ? replacementSerials.get(0) : req.getReplacementSerialNo())
            .replacementReason(req.getReplacementReason()).bookingId(original.getBookingId())
            .lines(new ArrayList<>()).productParts(new ArrayList<>()).build();

        if (replacementProduct != null) {
            BigDecimal unitPrice = replacementProduct.getSellingPrice() != null
                ? replacementProduct.getSellingPrice() : BigDecimal.ZERO;
            rework.getProductParts().add(ServiceJobPart.builder()
                .serviceJob(rework).product(replacementProduct).qty(replacementQty).unitPrice(unitPrice)
                .discountAmount(replacementPrice.subtract(customerCharge)).subtotal(customerCharge)
                .serialNumbers(String.join(",", replacementSerials))
                .warrantyCovered(customerCharge.compareTo(BigDecimal.ZERO) == 0).build());
        }

        rework = repo.saveAndFlush(rework);
        rework.setJobNo(generateJobNo(rework.getId()));
        rework = repo.save(rework);
        if (mode != ReworkResolutionMode.SERVICE_ONLY) {
            updateOldPartDisposition(originalPart, req.getOldPartDisposition());
            ReworkPartResolution resolution = reworkResolutionRepo.save(ReworkPartResolution.builder()
                .reworkJob(rework).originalPart(originalPart).replacementProduct(replacementProduct)
                .resolutionMode(mode).oldPartDisposition(req.getOldPartDisposition())
                .oldSerialNumbers(originalPart.getSerialNumbers())
                .replacementSerialNumbers(String.join(",", replacementSerials)).replacementQty(replacementProduct != null ? replacementQty : null)
                .originalCredit(originalCredit).replacementPrice(replacementPrice).customerCharge(customerCharge)
                .refundAmount(refundAmount).reason(req.getReplacementReason()).build());
            stockMovementService.recordMovement(StockMovement.builder()
                .product(originalPart.getProduct())
                .movementType(req.getOldPartDisposition() == OldPartDisposition.REUSE ? MovementType.RETURN : MovementType.ADJUST)
                .qty(originalPart.getQty()).referenceId(resolution.getId())
                .referenceType("ReworkOldPart:" + req.getOldPartDisposition().name()).build());
            if (req.getOldPartDisposition() == OldPartDisposition.REUSE) {
                createReturnedPartValuationJournal(rework, originalPart);
            }
        }
        if (mode == ReworkResolutionMode.REFUND) {
            recordReworkRefund(rework, refundAmount, req.getRefundPaymentMethodId(), req.getRefundTransactionNo());
        }
        broadcastJobEvent( "REWORK_CREATED");
        return toDto(rework);
    }

    private void createReturnedPartValuationJournal(ServiceJob rework, ServiceJobPart originalPart) {
        BigDecimal unitCost = originalPart.getProduct().getCostPrice() != null
            ? originalPart.getProduct().getCostPrice() : BigDecimal.ZERO;
        BigDecimal returnedCost = unitCost.multiply(BigDecimal.valueOf(originalPart.getQty()));
        if (returnedCost.compareTo(BigDecimal.ZERO) <= 0) return;

        JournalDetailDTO drInventory = new JournalDetailDTO();
        drInventory.setAccountId(accountResolver.inventory().getId());
        drInventory.setDebit(returnedCost);
        drInventory.setCredit(BigDecimal.ZERO);
        JournalDetailDTO crCogs = new JournalDetailDTO();
        crCogs.setAccountId(accountResolver.cogs().getId());
        crCogs.setDebit(BigDecimal.ZERO);
        crCogs.setCredit(returnedCost);

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(rework.getJobNo() + "-RETURN-COST");
        entry.setEntryDate(LocalDateTime.now());
        entry.setDescription("Reusable rework part returned to inventory - " + rework.getJobNo());
        entry.setStaffId(rework.getAssignedStaff() != null ? rework.getAssignedStaff().getId() : null);
        entry.setDetails(List.of(drInventory, crCogs));
        journalWriter.write(entry);
    }

    private void recordReworkRefund(ServiceJob rework, BigDecimal amount, Integer paymentMethodId, String transactionNo) {
        PaymentMethod method = paymentMethodRepo.findById(paymentMethodId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund payment method not found"));
        if (method.getAccount() == null)
            throw new IllegalArgumentException("Refund payment method must have a linked account");

        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceId(rework.getId());
        tx.setReferenceType(ReferenceType.Service);
        tx.setPaymentMethod(method);
        tx.setAmount(amount.negate());
        tx.setPaymentDate(LocalDateTime.now());
        tx.setTransactionNo(transactionNo != null && !transactionNo.isBlank() ? transactionNo.trim() : generateTxnNo());
        paymentTransactionRepo.save(tx);

        JournalDetailDTO drReturn = new JournalDetailDTO();
        drReturn.setAccountId(accountResolver.salesRtn().getId());
        drReturn.setDebit(amount);
        drReturn.setCredit(BigDecimal.ZERO);
        JournalDetailDTO crPayment = new JournalDetailDTO();
        crPayment.setAccountId(method.getAccount().getId());
        crPayment.setDebit(BigDecimal.ZERO);
        crPayment.setCredit(amount);
        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(rework.getJobNo() + "-REFUND");
        entry.setEntryDate(LocalDateTime.now());
        entry.setDescription("Rework part refund - " + rework.getJobNo());
        entry.setStaffId(rework.getAssignedStaff() != null ? rework.getAssignedStaff().getId() : null);
        entry.setDetails(List.of(drReturn, crPayment));
        journalWriter.write(entry);
    }
    private void validateReplacementAvailability(Product product, int qty, List<String> serials) {
        if (Boolean.TRUE.equals(product.getHasSerial())) {
            if (serials.size() != qty)
                throw new IllegalArgumentException("Replacement serial count must match quantity");
            for (String sn : serials) {
                ProductSerial serial = serialRepo.findBySerialNumber(sn)
                    .orElseThrow(() -> new ResourceNotFoundException("Serial number not found: " + sn));
                if (!serial.getProduct().getId().equals(product.getId()) || serial.getStatus() != SerialStatus.Available)
                    throw new IllegalArgumentException("Replacement serial is unavailable or belongs to another product: " + sn);
            }
        } else if (product.getStockQty() == null || product.getStockQty() < qty) {
            throw new IllegalArgumentException("Insufficient replacement stock for " + product.getName());
        }
    }

    private void updateOldPartDisposition(ServiceJobPart part, OldPartDisposition disposition) {
        Product product = part.getProduct();
        if (Boolean.TRUE.equals(product.getHasSerial())) {
            SerialStatus target = switch (disposition) {
                case REUSE -> SerialStatus.Available;
                case QUARANTINE -> SerialStatus.Quarantined;
                case DAMAGED -> SerialStatus.Damaged;
                case SUPPLIER_RETURN -> SerialStatus.Returned_To_Supplier;
            };
            for (String sn : splitSerials(part.getSerialNumbers())) {
                ProductSerial serial = serialRepo.findBySerialNumber(sn)
                    .orElseThrow(() -> new ResourceNotFoundException("Original part serial not found: " + sn));
                if (serial.getStatus() != SerialStatus.Used_In_Service
                    && serial.getStatus() != SerialStatus.Sold
                    && serial.getStatus() != SerialStatus.Quarantined)
                    throw new IllegalStateException("Original part serial is not installed/quarantined: " + sn);
                serial.setStatus(target);
                serialRepo.save(serial);
            }
        } else if (disposition == OldPartDisposition.REUSE) {
            int currentQty = product.getStockQty() != null ? product.getStockQty() : 0;
            product.setStockQty(currentQty + part.getQty());
            productRepo.save(product);
        }
    }
    @Transactional
    public void delete(Integer id) {
        ServiceJob job = repo.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getPaymentStatus() != null && !Boolean.TRUE.equals(job.getVoided()))
            throw new IllegalStateException("Settled job ကို ဖျက်မရပါ။ Settlement void လုပ်ပါ။");
        if (job.getStatus() == ServiceJobStatus.DELIVERED)
            throw new IllegalStateException("ပေးအပ်ပြီးသော Job ကို ဖျက်မရပါ။");
        repo.deleteById(id);
        broadcastJobEvent( "JOB_DELETED");
    }

    @Transactional
    public ServiceJobDTO voidSettlement(Integer id, String reason) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Void reason is required");
        ServiceJob job = repo.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (Boolean.TRUE.equals(job.getVoided()))
            throw new IllegalStateException("Settlement is already voided");
        if (job.getPaymentStatus() == null)
            throw new IllegalStateException("Job has not been settled");
        if (job.getStatus() == ServiceJobStatus.DELIVERED)
            throw new IllegalStateException("ပေးအပ်ပြီးသော Job ကို void မလုပ်နိုင်ပါ");

        if (job.getSaleId() != null) {
            Integer saleId = job.getSaleId();
            try {
                saleService.voidSale(saleId, reason);
                recordActivity(job, "INVENTORY_REVERSED", null, job.getStatus().name(),
                        "Linked sale #" + saleId + " voided — inventory/COGS reversed");
            }
            catch (Exception ex) {
                recordActivity(job, "INVENTORY_REVERSE_FAILED", null, job.getStatus().name(),
                        "Linked sale #" + saleId + " void failed: " + ex.getMessage());
            }
            job.setSaleId(null);
        }

        BigDecimal cashPaid = paymentTransactionRepo
                .findByReferenceIdAndReferenceType(job.getId(), ReferenceType.Service).stream()
                .filter(tx -> !Boolean.TRUE.equals(tx.getReversed()))
                .filter(tx -> tx.getPaymentMethod() != null && tx.getPaymentMethod().getAccount() != null)
                .filter(tx -> tx.getPaymentMethod().getAccount().getId().equals(accountResolver.cash().getId()))
                .map(PaymentTransaction::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (cashPaid.compareTo(BigDecimal.ZERO) > 0)
            cashDrawerService.recordCashRefund(cashPaid);

        paymentTransactionRepo.findByReferenceIdAndReferenceType(job.getId(), ReferenceType.Service).forEach(tx -> {
            tx.setReversed(true);
            tx.setReversedAt(LocalDateTime.now());
            tx.setReversedBy(currentUsername());
            tx.setReversalReason(reason.trim());
            paymentTransactionRepo.save(tx);
        });
        String actor = currentUsername();
        // Legacy settlements used bare jobNo; current settlements use jobNo-SETTLE-*
        journalWriter.reverseByReferenceNo(job.getJobNo(), actor, reason.trim());
        journalWriter.reverseByReferencePrefix(
                ServiceJobSettlementJournalBuilder.settlementReferencePrefix(job.getJobNo()), actor, reason.trim());
        journalWriter.reverseByReferencePrefix(job.getJobNo() + "-PAY", actor, reason.trim());
        journalWriter.reverseByReferenceNo(job.getJobNo() + "-RETURN-COST", actor, reason.trim());

        job.setVoided(true);
        job.setVoidReason(reason.trim());
        job.setVoidedBy(currentUsername());
        job.setVoidedAt(LocalDateTime.now());
        job.setSettledBy(null);
        job.setPaidAmount(BigDecimal.ZERO);
        job.setDueAmount(BigDecimal.ZERO);
        job.setNetAmount(BigDecimal.ZERO);
        job.setLaborNetAmount(null);
        job.setPartsNetAmount(null);
        job.setDiscountAllocationMethod(null);
        job.setPaymentStatus(null);
        job.setCreditStatus(org.sspd.servicemgmt.saleoptions.model.CreditStatus.Not_Credit);
        job.setStatus(ServiceJobStatus.IN_PROGRESS);
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "VOIDED", ServiceJobStatus.COMPLETED.name(), ServiceJobStatus.IN_PROGRESS.name(), reason);
        broadcastJobEvent( "JOB_VOIDED");
        return result;
    }

    @Transactional
    public ServiceJobDTO approveEstimate(Integer id) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        job.setEstimateApproved(true);
        job.setEstimateApprovedAt(LocalDateTime.now());
        job.setEstimateApprovedBy(currentUsername());
        if (job.getLines() != null) {
            for (ServiceJobLine line : job.getLines()) {
                ServiceLineConfirmationStatus status = line.getConfirmationStatus();
                if (status == ServiceLineConfirmationStatus.RECOMMENDED
                        || status == ServiceLineConfirmationStatus.INSPECTING
                        || status == ServiceLineConfirmationStatus.CUSTOMER_HOLD) {
                    line.setConfirmationStatus(ServiceLineConfirmationStatus.CUSTOMER_APPROVED);
                    if (line.getApprovedPrice() == null) {
                        line.setApprovedPrice(line.estimateUnitPrice());
                    }
                    line.refreshCharge();
                }
            }
        }
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "ESTIMATE_APPROVED", null, job.getStatus() != null ? job.getStatus().name() : null,
                "Estimate " + job.getEstimatedCost());
        broadcastJobEvent( "JOB_ESTIMATE_APPROVED");
        return result;
    }

    @Transactional
    public ServiceJobDTO holdEstimate(Integer id, String reason) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getStatus() == ServiceJobStatus.DELIVERED || job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Closed jobs cannot be put on estimate hold");
        job.setEstimateApproved(false);
        job.setEstimateApprovedAt(null);
        job.setEstimateApprovedBy(null);
        if (job.getLines() != null) {
            for (ServiceJobLine line : job.getLines()) {
                ServiceLineConfirmationStatus status = line.getConfirmationStatus();
                if (status == ServiceLineConfirmationStatus.RECOMMENDED
                        || status == ServiceLineConfirmationStatus.INSPECTING
                        || status == ServiceLineConfirmationStatus.CUSTOMER_APPROVED) {
                    line.setConfirmationStatus(ServiceLineConfirmationStatus.CUSTOMER_HOLD);
                }
            }
        }
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "ESTIMATE_HOLD", null, job.getStatus() != null ? job.getStatus().name() : null,
                trimToNull(reason));
        broadcastJobEvent( "JOB_ESTIMATE_HOLD");
        return result;
    }

    @Transactional
    public ServiceJobDTO rejectEstimate(Integer id, String reason) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getStatus() == ServiceJobStatus.DELIVERED)
            throw new IllegalStateException("Delivered jobs cannot reject estimate");
        if (job.getPaymentStatus() != null && !Boolean.TRUE.equals(job.getVoided()))
            throw new IllegalStateException("Settled jobs cannot reject estimate");
        ServiceJobStatus from = job.getStatus();
        if (job.getLines() != null) {
            for (ServiceJobLine line : job.getLines()) {
                ServiceLineConfirmationStatus status = line.getConfirmationStatus();
                if (status != ServiceLineConfirmationStatus.CUSTOMER_REJECTED
                        && status != ServiceLineConfirmationStatus.COMPLETED) {
                    line.setConfirmationStatus(ServiceLineConfirmationStatus.CUSTOMER_REJECTED);
                    line.refreshCharge();
                }
            }
        }
        job.setEstimateApproved(false);
        job.setEstimateApprovedAt(null);
        job.setEstimateApprovedBy(null);
        job.setStatus(ServiceJobStatus.CANCELLED);
        ServiceJobDTO result = toDto(repo.save(job));
        recordActivity(job, "ESTIMATE_REJECTED", from != null ? from.name() : null,
                ServiceJobStatus.CANCELLED.name(), trimToNull(reason));
        broadcastJobEvent( "JOB_ESTIMATE_REJECTED");
        return result;
    }

    static void assertEditable(ServiceJob job) {
        if (job.getPaymentStatus() != null && !Boolean.TRUE.equals(job.getVoided()))
            throw new IllegalStateException("Settled service jobs cannot be edited. Void the settlement first.");
        if (job.getStatus() == ServiceJobStatus.COMPLETED)
            throw new IllegalStateException("Completed service jobs cannot be edited. Return the final check for rework first.");
        if (job.getStatus() == ServiceJobStatus.DELIVERED || job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Delivered or cancelled service jobs cannot be edited");
    }

    static void assertReadyForSettlement(ServiceJob job) {
        if (job.getPaymentStatus() != null && !Boolean.TRUE.equals(job.getVoided()))
            throw new IllegalStateException("This service job has already been settled");
        if (job.getStatus() != ServiceJobStatus.COMPLETED)
            throw new IllegalStateException("Job must be completed before settlement");
        if (!Boolean.TRUE.equals(job.getLeadFinalCheckStatus()))
            throw new IllegalStateException("Lead Technician final check is required before settlement");
        if (!Boolean.TRUE.equals(job.getFinalApprovalStatus()))
            throw new IllegalStateException("Supervisor final approval is required before settlement");
    }

    /**
     * Records a customer notification in history. Delivery is delegated to
     * {@link CustomerNotifier} (default: log-only; no SMS/Viber/Telegram provider yet).
     */
    @Transactional
    public ServiceJobNotificationDTO notifyCustomer(Integer id, ServiceJobNotificationDTO dto) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        String channel = dto.getChannel() != null ? dto.getChannel().toUpperCase() : "NOTE";
        ServiceJobNotification saved = notificationRepo.save(ServiceJobNotification.builder()
            .serviceJob(job)
            .channel(channel)
            .note(dto.getNote())
            .actor(currentUsername())
            .notifiedAt(LocalDateTime.now())
            .build());
        job.setLastNotifiedAt(saved.getNotifiedAt());
        repo.save(job);
        customerNotifier.dispatch(job, channel, dto.getNote(), currentUsername());
        recordActivity(job, "NOTIFY", null, job.getStatus() != null ? job.getStatus().name() : null,
                channel + (dto.getNote() != null ? ": " + dto.getNote() : ""));
        ServiceJobNotificationDTO out = new ServiceJobNotificationDTO();
        out.setId(saved.getId());
        out.setChannel(saved.getChannel());
        out.setNote(saved.getNote());
        out.setActor(saved.getActor());
        out.setNotifiedAt(saved.getNotifiedAt());
        broadcastJobEvent( "JOB_NOTIFICATION_ADDED");
        return out;
    }

    @Transactional
    public ServiceJobAttachmentDTO addAttachment(Integer jobId, ServiceJobAttachmentDTO dto) {
        ServiceJob job = repo.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + jobId));
        if (dto.getDataUrl() == null || dto.getDataUrl().isBlank())
            throw new IllegalArgumentException("Attachment data is required");
        if (dto.getDataUrl().length() > 4_500_000)
            throw new IllegalArgumentException("Attachment is too large");
        ServiceJobAttachment saved = attachmentRepo.save(ServiceJobAttachment.builder()
            .serviceJob(job)
            .attachmentType(dto.getAttachmentType() != null ? dto.getAttachmentType() : "PHOTO")
            .fileName(dto.getFileName())
            .contentType(dto.getContentType())
            .dataUrl(dto.getDataUrl())
            .uploadedBy(currentUsername())
            .uploadedAt(LocalDateTime.now())
            .build());
        recordActivity(job, "ATTACHMENT", null, job.getStatus() != null ? job.getStatus().name() : null, saved.getFileName());
        broadcastJobEvent( "JOB_ATTACHMENT_ADDED");
        return toAttachmentDto(saved);
    }

    @Transactional
    public void deleteAttachment(Integer jobId, Integer attachmentId) {
        ServiceJobAttachment attachment = attachmentRepo.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        if (!attachment.getServiceJob().getId().equals(jobId))
            throw new IllegalArgumentException("Attachment does not belong to this job");
        attachmentRepo.delete(attachment);
        broadcastJobEvent( "JOB_ATTACHMENT_DELETED");
    }

    @Transactional(readOnly = true)
    public List<ServiceJobDTO> findOverdue() {
        Integer scopedStaffId = resolveStaffScope(null);
        if (scopedStaffId != null) {
            return repo.findOverdueForStaff(LocalDateTime.now(), scopedStaffId, VISIBLE_ASSIGNMENT_STATUSES).stream()
                    .map(this::toDto).toList();
        }
        return repo.findOverdue(LocalDateTime.now()).stream().map(this::toDto).toList();
    }

    private void recordActivity(ServiceJob job, String type, String from, String to, String note) {
        if (job.getId() == null) return;
        activityRepo.save(ServiceJobActivity.builder()
            .serviceJob(job)
            .eventType(type)
            .fromStatus(from)
            .toStatus(to)
            .note(note)
            .actor(currentUsername())
            .occurredAt(LocalDateTime.now())
            .build());
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    /** Prefer staff name, then user name, then username — stored on settle for voucher. */
    private String currentActorDisplayName() {
        String username = currentUsername();
        if (username == null || username.isBlank() || "system".equals(username)) return username;
        return userRepository.findByUsernameOrEmail(username, username)
                .map(user -> {
                    if (user.getStaff() != null && user.getStaff().getName() != null
                            && !user.getStaff().getName().isBlank()) {
                        return user.getStaff().getName();
                    }
                    if (user.getName() != null && !user.getName().isBlank()) return user.getName();
                    return user.getUsername() != null ? user.getUsername() : username;
                })
                .orElse(username);
    }

    private ServiceJobAttachmentDTO toAttachmentDto(ServiceJobAttachment a) {
        ServiceJobAttachmentDTO dto = new ServiceJobAttachmentDTO();
        dto.setId(a.getId());
        dto.setAttachmentType(a.getAttachmentType());
        dto.setFileName(a.getFileName());
        dto.setContentType(a.getContentType());
        dto.setDataUrl(a.getDataUrl());
        dto.setUploadedBy(a.getUploadedBy());
        dto.setUploadedAt(a.getUploadedAt());
        return dto;
    }

    private void buildLines(ServiceJob job, ServiceJobDTO dto) {
        if (dto.getLines() == null || dto.getLines().isEmpty()) return;
        if (job.getLines() == null) job.setLines(new ArrayList<>());
        int minutes = 0;
        for (ServiceJobLineDTO l : dto.getLines()) {
            var svc = serviceItemRepo.findById(l.getServiceItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Service item not found: " + l.getServiceItemId()));
            int qty = l.getQty() != null ? l.getQty() : 1;
            boolean covered = l.getWarrantyCovered() != null
                    ? Boolean.TRUE.equals(l.getWarrantyCovered())
                    : Boolean.TRUE.equals(svc.getFocDefault());
            int warranty = l.getWarrantyMonths() != null ? l.getWarrantyMonths()
                    : (svc.getWarrantyMonths() != null ? svc.getWarrantyMonths() : 0);
            ServiceLineConfirmationStatus confirmation = ServiceLineConfirmationStatus.from(l.getConfirmationStatus());
            ServiceJobLine line = ServiceJobLine.builder()
                .serviceJob(job)
                .serviceItem(svc)
                .qty(qty)
                .warrantyMonths(warranty)
                .warrantyCovered(covered)
                .confirmationStatus(confirmation)
                .discountAmount(l.getDiscountAmount() != null ? l.getDiscountAmount() : BigDecimal.ZERO)
                .build();
            applyLinePricing(line, svc, l, confirmation);
            job.getLines().add(line);
            minutes += (svc.getDurationMinutes() != null ? svc.getDurationMinutes() : 0) * qty;
        }
        recalculateEstimatedCost(job);
        if (job.getEstimatedCompletion() == null && minutes > 0)
            job.setEstimatedCompletion(LocalDateTime.now().plusMinutes(minutes));
    }

    private void buildProductParts(ServiceJob job, ServiceJobDTO dto) {
        if (dto.getProductParts() == null || dto.getProductParts().isEmpty()) {
            recalculateEstimatedCost(job);
            return;
        }
        if (job.getProductParts() == null) job.setProductParts(new ArrayList<>());
        for (ServiceJobPartDTO p : dto.getProductParts()) {
            Product product = productRepo.findById(p.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + p.getProductId()));
            int qty = p.getQty() != null ? p.getQty() : 1;
            if (qty <= 0) throw new RuntimeException("Part quantity must be greater than zero");

            List<String> serials = p.getSerialNumbers() == null ? List.of()
                : p.getSerialNumbers().stream().filter(sn -> sn != null && !sn.isBlank()).map(String::trim).toList();
            if (Boolean.TRUE.equals(product.getHasSerial())) {
                if (serials.size() != qty) {
                    throw new RuntimeException("Serial count must match qty for product: " + product.getName());
                }
                for (String sn : serials) {
                    ProductSerial serial = serialRepo.findBySerialNumber(sn)
                        .orElseThrow(() -> new RuntimeException("Serial number '" + sn + "' not found"));
                    if (!serial.getProduct().getId().equals(product.getId())) {
                        throw new RuntimeException("Serial number '" + sn + "' does not belong to product: " + product.getName());
                    }
                    if (serial.getStatus() != SerialStatus.Available) {
                        throw new RuntimeException("Serial number '" + sn + "' is not available");
                    }
                }
            }

            BigDecimal unitPrice = p.getUnitPrice() != null ? p.getUnitPrice()
                : (product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO);
            BigDecimal discount = p.getDiscountAmount() != null ? p.getDiscountAmount() : BigDecimal.ZERO;
            boolean covered = Boolean.TRUE.equals(p.getWarrantyCovered());
            BigDecimal subtotal = covered ? BigDecimal.ZERO
                : unitPrice.multiply(BigDecimal.valueOf(qty)).subtract(discount).max(BigDecimal.ZERO);
            job.getProductParts().add(ServiceJobPart.builder()
                .serviceJob(job)
                .product(product)
                .qty(qty)
                .unitPrice(unitPrice)
                .discountAmount(discount)
                .subtotal(subtotal)
                .serialNumbers(String.join(",", serials))
                .warrantyCovered(covered)
                .build());
        }
        recalculateEstimatedCost(job);
    }

    private void recalculateEstimatedCost(ServiceJob job) {
        BigDecimal services = job.getLines() == null ? BigDecimal.ZERO : job.getLines().stream()
            .filter(ServiceJobLine::isBillable)
            .map(line -> line.getSubtotal() != null ? line.getSubtotal() : ServiceJobSettlementCalculator.lineBalance(line))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        job.setEstimatedCost(services.add(productPartsTotal(job)));
    }

    private void refreshEstimateApproval(ServiceJob job) {
        if (job.getLines() == null || job.getLines().isEmpty()) return;
        var pending = job.getLines().stream()
                .filter(ServiceJobLine::isBillable)
                .toList();
        if (pending.isEmpty()) return;
        boolean allConfirmed = pending.stream()
                .allMatch(line -> line.getConfirmationStatus().isCustomerConfirmed());
        if (allConfirmed && !Boolean.TRUE.equals(job.getEstimateApproved())) {
            job.setEstimateApproved(true);
            job.setEstimateApprovedAt(LocalDateTime.now());
            job.setEstimateApprovedBy(currentUsername());
        }
    }

    private void syncLineStatuses(ServiceJob job, ServiceJobStatus to) {
        if (job.getLines() == null) return;
        for (ServiceJobLine line : job.getLines()) {
            ServiceLineConfirmationStatus status = line.getConfirmationStatus();
            if (status == ServiceLineConfirmationStatus.CUSTOMER_REJECTED
                    || status == ServiceLineConfirmationStatus.COMPLETED) continue;
            if (to == ServiceJobStatus.INSPECTING && status == ServiceLineConfirmationStatus.RECOMMENDED) {
                line.setConfirmationStatus(ServiceLineConfirmationStatus.INSPECTING);
            } else if (to == ServiceJobStatus.IN_PROGRESS && status == ServiceLineConfirmationStatus.CUSTOMER_APPROVED) {
                line.setConfirmationStatus(ServiceLineConfirmationStatus.IN_PROGRESS);
            } else if (to == ServiceJobStatus.COMPLETED
                    && (status == ServiceLineConfirmationStatus.IN_PROGRESS
                    || status == ServiceLineConfirmationStatus.CUSTOMER_APPROVED)) {
                line.setConfirmationStatus(ServiceLineConfirmationStatus.COMPLETED);
                if (line.getBilledPrice() == null) {
                    line.setBilledPrice(line.getApprovedPrice() != null ? line.getApprovedPrice() : line.estimateUnitPrice());
                }
                line.refreshCharge();
            }
        }
    }

    private BigDecimal productPartsTotal(ServiceJob job) {
        return job.getProductParts() == null ? BigDecimal.ZERO : job.getProductParts().stream()
            .map(ServiceJobPart::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<String> splitSerials(String serialNumbers) {
        if (serialNumbers == null || serialNumbers.isBlank()) return List.of();
        return java.util.Arrays.stream(serialNumbers.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }

    private void createJournalEntry(ServiceJob job, Integer paymentAccountId,
                                    PaymentMethod pm, BigDecimal paid, BigDecimal due,
                                    ServiceJobSettlementBreakdown breakdown,
                                    List<PaymentTransactionDTO> payments) {
        if (breakdown == null || nz(breakdown.net()).signum() <= 0) return;

        String settlePrefix = ServiceJobSettlementJournalBuilder.settlementReferencePrefix(job.getJobNo());
        if (journalWriter.hasActiveReferencePrefix(settlePrefix)) {
            throw new IllegalStateException("Settlement journal already exists for " + job.getJobNo());
        }

        List<ServiceJobSettlementJournalBuilder.PaymentSlice> slices = new ArrayList<>();
        if (nz(paid).signum() > 0) {
            for (PaymentLine line : resolvePaymentLines(payments, paid, pm)) {
                slices.add(new ServiceJobSettlementJournalBuilder.PaymentSlice(
                        resolveCashAccount(line.method(), paymentAccountId), line.amount()));
            }
        }

        ServiceJobSettlementJournalBuilder.AccountIds accounts = new ServiceJobSettlementJournalBuilder.AccountIds(
                accountResolver.serviceRevenue().getId(),
                accountResolver.sales().getId(),
                accountResolver.laborDiscount().getId(),
                accountResolver.partsDiscount().getId(),
                accountResolver.receivable().getId()
        );

        ServiceJobSettlementJournalBuilder.BuiltJournal built =
                ServiceJobSettlementJournalBuilder.build(breakdown, slices, paid, due, accounts);
        if (built.details().isEmpty()) return;
        if (!built.isBalanced()) {
            throw new IllegalStateException("Settlement journal is not balanced for " + job.getJobNo()
                    + " (DR " + built.totalDebit() + " / CR " + built.totalCredit() + ")");
        }

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(ServiceJobSettlementJournalBuilder.settlementReference(job.getJobNo()));
        entry.setEntryDate(LocalDateTime.now());
        entry.setDescription("Service Job Settlement - " + job.getJobNo()
            + (job.getSaleId() != null ? " [Sale: " + job.getSaleId() + "]" : ""));
        entry.setStaffId(job.getAssignedStaff() != null ? job.getAssignedStaff().getId() : null);
        entry.setDetails(built.details());

        journalWriter.write(entry);
    }

    private void createPaymentTransactions(ServiceJob job, PaymentMethod pm, BigDecimal amount, String userTransactionNo,
                                           List<PaymentTransactionDTO> payments) {
        for (PaymentLine line : resolvePaymentLines(payments, amount, pm)) {
            PaymentTransaction tx = new PaymentTransaction();
            tx.setReferenceId(job.getId());
            tx.setReferenceType(ReferenceType.Service);
            tx.setPaymentMethod(line.method());
            tx.setAmount(line.amount());
            tx.setPaymentDate(LocalDateTime.now());
            tx.setTransactionNo(line.transactionNo() != null && !line.transactionNo().isBlank()
                    ? line.transactionNo()
                    : (userTransactionNo != null && !userTransactionNo.isBlank() ? userTransactionNo : generateTxnNo()));
            paymentTransactionRepo.save(tx);
        }
    }

    private BigDecimal paymentTotal(List<PaymentTransactionDTO> payments, BigDecimal fallback) {
        if (payments == null || payments.isEmpty()) return fallback != null ? fallback : BigDecimal.ZERO;
        return payments.stream()
                .map(PaymentTransactionDTO::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PaymentLine> resolvePaymentLines(List<PaymentTransactionDTO> payments, BigDecimal expectedTotal, PaymentMethod fallbackMethod) {
        if (payments == null || payments.isEmpty()) {
            if (fallbackMethod == null) throw new RuntimeException("Payment Method is required.");
            return List.of(new PaymentLine(fallbackMethod, expectedTotal, null));
        }
        BigDecimal total = paymentTotal(payments, BigDecimal.ZERO);
        if (expectedTotal != null && total.compareTo(expectedTotal) != 0) {
            throw new RuntimeException("Split payment total must equal paid amount.");
        }
        List<PaymentLine> lines = new ArrayList<>();
        for (PaymentTransactionDTO payment : payments) {
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            PaymentMethod method = paymentMethodRepo.findById(payment.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            lines.add(new PaymentLine(method, amount, payment.getTransactionNo()));
        }
        if (lines.isEmpty() && expectedTotal != null && expectedTotal.compareTo(BigDecimal.ZERO) > 0) {
            if (fallbackMethod == null) throw new RuntimeException("Payment Method is required.");
            return List.of(new PaymentLine(fallbackMethod, expectedTotal, null));
        }
        return lines;
    }

    private record PaymentLine(PaymentMethod method, BigDecimal amount, String transactionNo) {}

    private Integer resolveCashAccount(PaymentMethod pm, Integer overrideAccountId) {
        if (overrideAccountId != null) return overrideAccountId;
        if (pm != null && pm.getAccount() != null) return pm.getAccount().getId();
        return accountResolver.cash().getId();
    }

    private void applyLinePricing(ServiceJobLine line, ServiceItem svc, ServiceJobLineDTO dto,
                                  ServiceLineConfirmationStatus confirmation) {
        BigDecimal catalog = dto.getCatalogPrice() != null ? dto.getCatalogPrice() : nz(svc.getPrice());
        BigDecimal estimated = firstPrice(dto.getEstimatedPrice(), dto.getPrice(), catalog);
        BigDecimal approved = dto.getApprovedPrice();
        BigDecimal billed = dto.getBilledPrice();
        if (confirmation.isCustomerConfirmed() && approved == null) {
            approved = estimated;
        }
        if (confirmation == ServiceLineConfirmationStatus.COMPLETED && billed == null) {
            billed = approved != null ? approved : estimated;
        }

        boolean changed = differs(estimated, catalog)
                || (approved != null && differs(approved, catalog))
                || (billed != null && differs(billed, catalog));
        String reason = dto.getPriceChangeReason() != null ? dto.getPriceChangeReason().trim() : "";
        if (changed && reason.isEmpty()) {
            throw new IllegalArgumentException("စျေးပြောင်းရသည့်အကြောင်းပြချက် ဖြည့်ပါ: " + svc.getItem());
        }

        BigDecimal min = svc.getMinPrice();
        BigDecimal max = svc.getMaxPrice();
        boolean outOfRange = outsideRange(estimated, min, max)
                || (approved != null && outsideRange(approved, min, max))
                || (billed != null && outsideRange(billed, min, max));
        boolean overrideOk = Boolean.TRUE.equals(dto.getPriceOverrideApproved())
                || hasAuthority("CAN_ACCESS_SERVICE_JOB_PRICE_OVERRIDE")
                || isAdministrator();
        if (outOfRange && !overrideOk) {
            throw new AccessDeniedException("Min/Max စျေးကျော်နေသည်။ Manager approval လိုအပ်သည်: " + svc.getItem());
        }

        line.setCatalogPrice(catalog);
        line.setEstimatedPrice(estimated);
        line.setApprovedPrice(approved);
        line.setBilledPrice(billed);
        line.setMinPrice(min);
        line.setMaxPrice(max);
        line.setPriceChangeReason(changed ? reason : null);
        line.setPriceOverrideApproved(outOfRange && overrideOk);
        line.setPriceOverrideApprovedBy(outOfRange && overrideOk ? currentUsername() : null);
        line.refreshCharge();
    }

    private void stampBilledPrices(ServiceJob job) {
        if (job.getLines() == null) return;
        for (ServiceJobLine line : job.getLines()) {
            if (line.getBilledPrice() == null) {
                line.setBilledPrice(line.getApprovedPrice() != null ? line.getApprovedPrice() : line.estimateUnitPrice());
            }
            if (line.getConfirmationStatus() != ServiceLineConfirmationStatus.CUSTOMER_REJECTED
                    && line.getConfirmationStatus() != ServiceLineConfirmationStatus.COMPLETED) {
                line.setConfirmationStatus(ServiceLineConfirmationStatus.COMPLETED);
            }
            line.refreshCharge();
        }
    }

    private boolean isAdministrator() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> "ROLE_ADMINISTRATOR".equals(granted.getAuthority())
                        || "ADMINISTRATOR".equals(granted.getAuthority()));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal firstPrice(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) return value;
        }
        return BigDecimal.ZERO;
    }

    private static boolean differs(BigDecimal left, BigDecimal right) {
        return nz(left).compareTo(nz(right)) != 0;
    }

    private static boolean outsideRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) return false;
        if (min != null && value.compareTo(min) < 0) return true;
        return max != null && value.compareTo(max) > 0;
    }

    private String temporaryJobNo() {
        return "TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Stable job number derived from the persisted PK (same pattern as booking numbers). */
    static String generateJobNo(Integer id) {
        return String.format("SJ-%06d", id);
    }

    private String generateTxnNo() {
        long count = paymentTransactionRepo.count();
        return String.format("TXN-%06d", count + 1);
    }

    private ServiceJobDTO toDto(ServiceJob j) {
        return toDto(j, false);
    }

    private ServiceJobDTO toDto(ServiceJob j, boolean detail) {
        ServiceJobDTO dto = new ServiceJobDTO();
        dto.setId(j.getId());
        dto.setJobNo(j.getJobNo());
        dto.setCustomerId(j.getCustomer().getId());
        dto.setCustomerName(j.getCustomer().getName());
        dto.setServiceMode(j.getServiceMode() == null ? ServiceMode.INDOOR : j.getServiceMode());
        dto.setCustomerLatitude(j.getCustomer().getLatitude());
        dto.setCustomerLongitude(j.getCustomer().getLongitude());
        if (j.getAssignedStaff() != null) {
            dto.setAssignedStaffId(j.getAssignedStaff().getId());
            dto.setAssignedStaffName(j.getAssignedStaff().getName());
        }
        dto.setItemName(j.getItemName());
        dto.setDeviceType(j.getDeviceType());
        dto.setSerialNo(j.getSerialNo());
        dto.setColor(j.getColor());
        dto.setItemCondition(j.getItemCondition());
        dto.setDeviceConditions(j.getDeviceConditions());
        dto.setPartRequests(j.getPartRequests());
        dto.setProblemDesc(j.getProblemDesc());
        dto.setDiagnosisNotes(j.getDiagnosisNotes());
        dto.setEstimatedCost(j.getEstimatedCost());
        dto.setFinalCost(j.getFinalCost());
        dto.setReceivedDate(j.getReceivedDate() != null ? j.getReceivedDate().toString() : null);
        dto.setEstimatedCompletion(j.getEstimatedCompletion() != null ? j.getEstimatedCompletion().toString() : null);
        dto.setCompletedDate(j.getCompletedDate() != null ? j.getCompletedDate().toString() : null);
        dto.setDeliveredDate(j.getDeliveredDate() != null ? j.getDeliveredDate().toString() : null);
        dto.setStatus(j.getStatus());
        if (j.getPaymentMethod() != null) {
            dto.setPaymentMethodId(j.getPaymentMethod().getId());
            dto.setPaymentMethodName(j.getPaymentMethod().getMethodName());
        }
        dto.setDiscountAmount(j.getDiscountAmount());
        dto.setFoc(j.getFoc());
        dto.setNetAmount(j.getNetAmount());
        dto.setLaborNetAmount(j.getLaborNetAmount());
        dto.setPartsNetAmount(j.getPartsNetAmount());
        dto.setDiscountAllocationMethod(j.getDiscountAllocationMethod() != null ? j.getDiscountAllocationMethod().name() : null);
        dto.setPaidAmount(j.getPaidAmount());
        dto.setDueAmount(j.getDueAmount());
        dto.setPaymentDiscountAmount(j.getPaymentDiscountAmount());
        dto.setPaymentDiscountApprovedBy(j.getPaymentDiscountApprovedBy());
        dto.setPaymentDiscountApprovedAt(j.getPaymentDiscountApprovedAt());
        dto.setPaymentDiscountApprovalNote(j.getPaymentDiscountApprovalNote());
        dto.setDueDeliveryApprovedBy(j.getDueDeliveryApprovedBy());
        dto.setDueDeliveryApprovedAt(j.getDueDeliveryApprovedAt());
        dto.setDueDeliveryApprovalReason(j.getDueDeliveryApprovalReason());
        dto.setDueDate(j.getDueDate());
        dto.setPaymentStatus(j.getPaymentStatus() != null ? j.getPaymentStatus().name() : null);
        dto.setCreditStatus(j.getCreditStatus() != null ? j.getCreditStatus().name() : null);
        dto.setCustomerPhone(j.getCustomer().getPhone());
        dto.setAccessories(j.getAccessories());
        if (j.getShelfLocation() != null) {
            dto.setShelfLocationId(j.getShelfLocation().getId());
            dto.setShelfLocationCode(j.getShelfLocation().getCode());
            dto.setShelfLocationLabel(j.getShelfLocation().getLabel());
        }
        dto.setBookingId(j.getBookingId());
        if (j.getBookingId() != null) {
            bookingRepository.findById(j.getBookingId()).ifPresent(booking -> {
                dto.setBookingNo(booking.getBookingNo());
                dto.setAppointmentDate(booking.getAppointmentDate());
            });
        }
        dto.setSaleId(j.getSaleId());
        dto.setRework(Boolean.TRUE.equals(j.getRework()));
        dto.setParentJobId(j.getParentJobId());
        dto.setReworkType(j.getReworkType());
        dto.setReplacementItemName(j.getReplacementItemName());
        dto.setReplacementSerialNo(j.getReplacementSerialNo());
        dto.setReplacementReason(j.getReplacementReason());
        if (Boolean.TRUE.equals(j.getRework())) {
            reworkResolutionRepo.findByReworkJobIdOrderByIdAsc(j.getId()).stream().findFirst().ifPresent(r -> {
                dto.setResolutionMode(r.getResolutionMode());
                dto.setOldPartDisposition(r.getOldPartDisposition());
                dto.setOriginalPartName(r.getOriginalPart() != null ? r.getOriginalPart().getProduct().getName() : null);
                dto.setOriginalPartCode(r.getOriginalPart() != null ? r.getOriginalPart().getProduct().getProductCode() : null);
                dto.setOriginalPartSerialNumbers(splitSerials(r.getOldSerialNumbers()));
                dto.setReplacementProductName(r.getReplacementProduct() != null ? r.getReplacementProduct().getName() : null);
                dto.setReplacementProductCode(r.getReplacementProduct() != null ? r.getReplacementProduct().getProductCode() : null);
                dto.setReplacementPartSerialNumbers(splitSerials(r.getReplacementSerialNumbers()));
                dto.setReplacementQty(r.getReplacementQty());
                dto.setWarrantyCredit(r.getOriginalCredit());
                dto.setReplacementPrice(r.getReplacementPrice());
                dto.setCustomerCharge(r.getCustomerCharge());
                dto.setRefundAmount(r.getRefundAmount());
            });
        }
        if (dto.getResolutionMode() == ReworkResolutionMode.REFUND) {
            paymentTransactionRepo.findByReferenceIdAndReferenceType(j.getId(), ReferenceType.Service).stream()
                .filter(tx -> tx.getAmount() != null && tx.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .findFirst().ifPresent(tx -> {
                    dto.setRefundPaymentMethodName(tx.getPaymentMethod() != null ? tx.getPaymentMethod().getMethodName() : null);
                    dto.setRefundTransactionNo(tx.getTransactionNo());
                    dto.setRefundDate(tx.getPaymentDate() != null ? tx.getPaymentDate().toString() : null);
                });
        }
        if (j.getParentJobId() != null) {
            repo.findById(j.getParentJobId()).ifPresent(p -> dto.setParentJobNo(p.getJobNo()));
        }
        dto.setRemark(j.getRemark());
        dto.setLines(j.getLines() == null ? List.of() : j.getLines().stream().map(l -> {
            ServiceJobLineDTO ld = new ServiceJobLineDTO();
            ld.setId(l.getId());
            ld.setServiceItemId(l.getServiceItem().getId());
            ld.setServiceItemName(l.getServiceItem().getItem());
            ld.setQty(l.getQty());
            ld.setCatalogPrice(l.getCatalogPrice());
            ld.setEstimatedPrice(l.getEstimatedPrice());
            ld.setApprovedPrice(l.getApprovedPrice());
            ld.setBilledPrice(l.getBilledPrice());
            ld.setPrice(l.getPrice());
            ld.setSubtotal(l.getSubtotal());
            ld.setDiscountAmount(l.getDiscountAmount());
            ld.setMinPrice(l.getMinPrice());
            ld.setMaxPrice(l.getMaxPrice());
            ld.setPriceChangeReason(l.getPriceChangeReason());
            ld.setPriceOverrideApproved(Boolean.TRUE.equals(l.getPriceOverrideApproved()));
            ld.setPriceOverrideApprovedBy(l.getPriceOverrideApprovedBy());
            ld.setWarrantyMonths(l.getWarrantyMonths());
            ld.setWarrantyCovered(Boolean.TRUE.equals(l.getWarrantyCovered()));
            ld.setConfirmationStatus(l.getConfirmationStatus().name());
            return ld;
        }).toList());
        dto.setProductParts(j.getProductParts() == null ? List.of() : j.getProductParts().stream().map(p -> {
            ServiceJobPartDTO pd = new ServiceJobPartDTO();
            pd.setId(p.getId());
            pd.setProductId(p.getProduct().getId());
            pd.setProductName(p.getProduct().getName());
            pd.setProductCode(p.getProduct().getProductCode());
            pd.setProductType(p.getProduct().getProductType() != null ? p.getProduct().getProductType().name() : null);
            pd.setQty(p.getQty());
            pd.setUnitPrice(p.getUnitPrice());
            pd.setDiscountAmount(p.getDiscountAmount());
            pd.setSubtotal(p.getSubtotal());
            pd.setSerialNumbers(splitSerials(p.getSerialNumbers()));
            pd.setWarrantyCovered(Boolean.TRUE.equals(p.getWarrantyCovered()));
            pd.setWarrantyMonths(p.getProduct() != null ? p.getProduct().getWarrantyMonths() : null);
            return pd;
        }).toList());
        dto.setVoided(Boolean.TRUE.equals(j.getVoided()));
        dto.setVoidReason(j.getVoidReason());
        dto.setVoidedBy(j.getVoidedBy());
        dto.setVoidedAt(j.getVoidedAt() != null ? j.getVoidedAt().toString() : null);
        dto.setSettledBy(j.getSettledBy());
        dto.setEstimateApproved(Boolean.TRUE.equals(j.getEstimateApproved()));
        dto.setEstimateApprovedAt(j.getEstimateApprovedAt() != null ? j.getEstimateApprovedAt().toString() : null);
        dto.setEstimateApprovedBy(j.getEstimateApprovedBy());
        dto.setFinalApprovalStatus(Boolean.TRUE.equals(j.getFinalApprovalStatus()));
        dto.setFinalApprovedBy(j.getFinalApprovedBy());
        dto.setFinalApprovedAt(j.getFinalApprovedAt() != null ? j.getFinalApprovedAt().toString() : null);
        dto.setLeadFinalCheckStatus(Boolean.TRUE.equals(j.getLeadFinalCheckStatus()));
        dto.setLeadFinalCheckedBy(j.getLeadFinalCheckedBy());
        dto.setLeadFinalCheckedAt(j.getLeadFinalCheckedAt() != null ? j.getLeadFinalCheckedAt().toString() : null);
        dto.setLeadFinalCheckNote(j.getLeadFinalCheckNote());
        dto.setFinalReturnReason(j.getFinalReturnReason());
        dto.setSupervisorApprovalRequired(supervisorApprovalRequired());
        dto.setPriority(j.getPriority() != null ? j.getPriority() : "NORMAL");
        if (j.getHelperStaff() != null) {
            dto.setHelperStaffId(j.getHelperStaff().getId());
            dto.setHelperStaffName(j.getHelperStaff().getName());
        }
        dto.setHoldReason(j.getHoldReason());
        dto.setWorkStartedAt(j.getWorkStartedAt() != null ? j.getWorkStartedAt().toString() : null);
        dto.setLastNotifiedAt(j.getLastNotifiedAt() != null ? j.getLastNotifiedAt().toString() : null);
        dto.setModifiedBy(j.getModifiedBy());
        dto.setModifiedAt(j.getModifiedAt() != null ? j.getModifiedAt().toString() : null);
        if (j.getWorkStartedAt() != null) {
            LocalDateTime end = j.getCompletedDate() != null ? j.getCompletedDate() : LocalDateTime.now();
            dto.setTechnicianMinutes(java.time.Duration.between(j.getWorkStartedAt(), end).toMinutes());
        }
        dto.setOverdue(j.getEstimatedCompletion() != null
                && j.getEstimatedCompletion().isBefore(LocalDateTime.now())
                && j.getStatus() != ServiceJobStatus.COMPLETED
                && j.getStatus() != ServiceJobStatus.DELIVERED
                && j.getStatus() != ServiceJobStatus.CANCELLED);
        if (detail && j.getId() != null) {
            dto.setActivities(activityRepo.findByServiceJobIdOrderByOccurredAtAsc(j.getId()).stream().map(a -> {
                ServiceJobActivityDTO ad = new ServiceJobActivityDTO();
                ad.setId(a.getId());
                ad.setEventType(a.getEventType());
                ad.setFromStatus(a.getFromStatus());
                ad.setToStatus(a.getToStatus());
                ad.setNote(a.getNote());
                ad.setActor(a.getActor());
                ad.setOccurredAt(a.getOccurredAt());
                return ad;
            }).toList());
            dto.setAttachments(attachmentRepo.findByServiceJobIdOrderByUploadedAtDesc(j.getId()).stream()
                .map(this::toAttachmentDto).toList());
            dto.setNotifications(notificationRepo.findByServiceJobIdOrderByNotifiedAtDesc(j.getId()).stream().map(n -> {
                ServiceJobNotificationDTO nd = new ServiceJobNotificationDTO();
                nd.setId(n.getId());
                nd.setChannel(n.getChannel());
                nd.setNote(n.getNote());
                nd.setActor(n.getActor());
                nd.setNotifiedAt(n.getNotifiedAt());
                return nd;
            }).toList());
        }
        Integer mine = Optional.ofNullable(currentUserStaff()).map(Staff::getId).orElse(null);
        if (mine != null && j.getId() != null) {
            enrichTeamFlags(List.of(dto), mine);
            enrichPendingHandovers(List.of(dto), mine);
        }
        return dto;
    }

    private void broadcastJobEvent(Object event) {
        dataEventPublisher.publishTopic("/topic/service-jobs", event);
    }
}
