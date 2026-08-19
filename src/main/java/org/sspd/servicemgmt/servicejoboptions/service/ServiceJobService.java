package org.sspd.servicemgmt.servicejoboptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.creditoptions.service.CreditService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemRepository;
import org.sspd.servicemgmt.servicejoboptions.dto.ReworkRequestDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobLineDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobPartDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.SettleDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkType;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkResolutionMode;
import org.sspd.servicemgmt.servicejoboptions.model.OldPartDisposition;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkPartResolution;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ReworkPartResolutionRepository;
import org.sspd.servicemgmt.shelflocationoptions.repository.ShelfLocationRepository;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;

@Service
@RequiredArgsConstructor
public class ServiceJobService {

    private final ServiceJobRepository repo;
    private final CustomerRepository customerRepo;
    private final StaffRepository staffRepo;
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
    private final SimpMessagingTemplate messagingTemplate;
    private final BookingRepository bookingRepo;
    private final ReworkPartResolutionRepository reworkResolutionRepo;
    private final StockMovementService stockMovementService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional(readOnly = true)
    public Page<ServiceJobDTO> findAll(String search, String dateFrom, String dateTo, int page, int size) {
        LocalDateTime from = parseDateStart(dateFrom);
        LocalDateTime to   = parseDateEnd(dateTo);
        return repo.findBySearchAndDate(search, from, to,
                        PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::toDto);
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
    public ServiceJobDTO findById(Integer id) {
        return toDto(repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<ServiceJobDTO> findByBookingId(Integer bookingId) {
        List<ServiceJobDTO> jobs = repo.findAllByBookingIdOrderByIdAsc(bookingId).stream()
            .map(this::toDto)
            .toList();
        if (jobs.isEmpty())
            throw new ResourceNotFoundException("No service job for booking: " + bookingId);
        return jobs;
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
        return repo.findByStatus(status).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceJobDTO> findUnpaid() {
        return repo.findByStatusAndPaymentStatusIsNullOrderByReceivedDateDesc(ServiceJobStatus.COMPLETED)
                   .stream().map(this::toDto).toList();
    }

    @Transactional
    public ServiceJobDTO create(ServiceJobDTO dto) {
        ServiceJob job = ServiceJob.builder()
            .jobNo(generateJobNo())
            .customer(customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")))
            .itemName(dto.getItemName())
            .serialNo(dto.getSerialNo())
            .color(dto.getColor())
            .itemCondition(dto.getItemCondition())
            .deviceConditions(dto.getDeviceConditions())
            .accessories(dto.getAccessories())
            .problemDesc(dto.getProblemDesc())
            .diagnosisNotes(dto.getDiagnosisNotes())
            .estimatedCost(dto.getEstimatedCost() != null ? dto.getEstimatedCost() : BigDecimal.ZERO)
            .finalCost(BigDecimal.ZERO)
            .status(ServiceJobStatus.RECEIVED)
            .remark(dto.getRemark())
            .lines(new ArrayList<>())
            .productParts(new ArrayList<>())
            .build();

        if (dto.getAssignedStaffId() != null)
            job.setAssignedStaff(staffRepo.findById(dto.getAssignedStaffId()).orElse(null));
        if (dto.getShelfLocationId() != null)
            job.setShelfLocation(shelfLocationRepo.findById(dto.getShelfLocationId()).orElse(null));
        if (dto.getEstimatedCompletion() != null && !dto.getEstimatedCompletion().isBlank())
            job.setEstimatedCompletion(LocalDateTime.parse(dto.getEstimatedCompletion(), FMT));

        buildLines(job, dto);
        buildProductParts(job, dto);
        ServiceJobDTO result = toDto(repo.save(job));
        messagingTemplate.convertAndSend("/topic/service-jobs", "JOB_CREATED");
        return result;
    }

    @Transactional
    public ServiceJobDTO update(Integer id, ServiceJobDTO dto) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));

        if (job.getStatus() == ServiceJobStatus.DELIVERED || job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Delivered or cancelled service jobs cannot be edited");

        if (dto.getCustomerId() != null)
            job.setCustomer(customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
        if (dto.getAssignedStaffId() != null)
            job.setAssignedStaff(staffRepo.findById(dto.getAssignedStaffId()).orElse(null));
        job.setShelfLocation(dto.getShelfLocationId() != null
            ? shelfLocationRepo.findById(dto.getShelfLocationId()).orElse(null)
            : null);
        if (dto.getItemName() != null)          job.setItemName(dto.getItemName());
        if (dto.getSerialNo() != null)          job.setSerialNo(dto.getSerialNo());
        if (dto.getColor() != null)             job.setColor(dto.getColor());
        if (dto.getItemCondition() != null)     job.setItemCondition(dto.getItemCondition());
        if (dto.getDeviceConditions() != null)  job.setDeviceConditions(dto.getDeviceConditions());
        if (dto.getAccessories() != null)       job.setAccessories(dto.getAccessories());
        if (dto.getProblemDesc() != null)       job.setProblemDesc(dto.getProblemDesc());
        if (dto.getDiagnosisNotes() != null)    job.setDiagnosisNotes(dto.getDiagnosisNotes());
        if (dto.getEstimatedCost() != null)     job.setEstimatedCost(dto.getEstimatedCost());
        if (dto.getRemark() != null)           job.setRemark(dto.getRemark());
        if (dto.getEstimatedCompletion() != null && !dto.getEstimatedCompletion().isBlank())
            job.setEstimatedCompletion(LocalDateTime.parse(dto.getEstimatedCompletion(), FMT));

        if (dto.getLines() != null) {
            job.getLines().clear();
            buildLines(job, dto);
        }
        if (dto.getProductParts() != null) {
            reverseProductParts(job);
            job.getProductParts().clear();
            buildProductParts(job, dto);
        } else {
            recalculateEstimatedCost(job);
        }
        ServiceJobDTO updated = toDto(repo.save(job));
        messagingTemplate.convertAndSend("/topic/service-jobs", "JOB_UPDATED");
        return updated;
    }

    @Transactional
    public ServiceJobDTO updateStatus(Integer id, ServiceJobStatus status) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));

        if (job.getStatus() == ServiceJobStatus.DELIVERED)
            throw new IllegalStateException("Delivered jobs cannot change status.");
        if (job.getStatus() == ServiceJobStatus.CANCELLED)
            throw new IllegalStateException("Cancelled jobs cannot change status.");
        if (job.getPaymentStatus() != null)
            throw new IllegalStateException("This job has already been settled. Status cannot be changed after payment is recorded.");

        job.setStatus(status);
        if (status == ServiceJobStatus.COMPLETED)
            job.setCompletedDate(LocalDateTime.now());
        ServiceJobDTO result = toDto(repo.save(job));
        messagingTemplate.convertAndSend("/topic/service-jobs", "JOB_STATUS_CHANGED");
        return result;
    }

    @Transactional
    public ServiceJobDTO settle(Integer id, SettleDTO dto) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));

        BigDecimal grossAmount = dto.getFinalCost() != null ? dto.getFinalCost() : job.getEstimatedCost();
        BigDecimal discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        boolean isFoc = Boolean.TRUE.equals(dto.getFoc());
        BigDecimal netAmt = isFoc ? BigDecimal.ZERO : grossAmount.subtract(discount).max(BigDecimal.ZERO);
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

        PaymentMethod pm = null;
        if (dto.getPaymentMethodId() != null)
            pm = paymentMethodRepo.findById(dto.getPaymentMethodId()).orElse(null);
        job.setPaymentMethod(pm);

        boolean hasProducts = job.getProductParts() != null && !job.getProductParts().isEmpty();

        if (hasProducts) {
            SaleDTO saleDto = createSaleFromServiceJob(job, dto, netAmt);
            SaleDTO createdSale = saleService.save(saleDto);
            job.setSaleId(createdSale.getId());
        }

        ServiceJob saved = repo.save(job);

        // Journal covers both cash and credit portions (revenue recognised at settlement)
        if (!isFoc && netAmt.compareTo(BigDecimal.ZERO) > 0) {
            createJournalEntry(saved, dto.getPaymentAccountId(), pm, paid, due, dto.getPayments());
        }
        // Payment transaction only for money actually received
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            createPaymentTransactions(saved, pm, paid, dto.getTransactionNo(), dto.getPayments());
        }
        return toDto(saved);
    }

    @Transactional
    public ServiceJobDTO payDue(Integer id, SettleDTO dto) {
        ServiceJob job = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));

        BigDecimal currentDue = job.getDueAmount() != null ? job.getDueAmount() : BigDecimal.ZERO;
        if (currentDue.compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("No outstanding due for this service job.");

        BigDecimal incoming = paymentTotal(dto.getPayments(), dto.getPaidAmount());
        if (incoming.compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Paid amount must be greater than zero.");
        if (dto.getPaymentMethodId() == null && (dto.getPayments() == null || dto.getPayments().isEmpty()))
            throw new RuntimeException("Payment Method ရွေးပါ");

        BigDecimal applied = incoming.min(currentDue);
        BigDecimal newPaid = (job.getPaidAmount() != null ? job.getPaidAmount() : BigDecimal.ZERO).add(applied);
        BigDecimal newDue  = currentDue.subtract(applied);

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
        createPayDueJournal(saved, dto.getPaymentAccountId(), pm, applied, dto.getPayments());
        createPaymentTransactions(saved, pm, applied, dto.getTransactionNo(), dto.getPayments());

        messagingTemplate.convertAndSend("/topic/service-jobs", "JOB_PAY_DUE");
        return toDto(saved);
    }

    private void createPayDueJournal(ServiceJob job, Integer paymentAccountId,
                                     PaymentMethod pm, BigDecimal applied, List<PaymentTransactionDTO> payments) {
        if (applied.compareTo(BigDecimal.ZERO) <= 0) return;

        List<JournalDetailDTO> details = new ArrayList<>();

        for (PaymentLine line : resolvePaymentLines(payments, applied, pm)) {
            JournalDetailDTO drCash = new JournalDetailDTO();
            drCash.setAccountId(line.method().getAccount().getId());
            drCash.setDebit(line.amount());
            drCash.setCredit(BigDecimal.ZERO);
            details.add(drCash);
        }

        // CR Accounts Receivable
        JournalDetailDTO crAr = new JournalDetailDTO();
        crAr.setAccountId(accountResolver.receivable().getId());
        crAr.setDebit(BigDecimal.ZERO);
        crAr.setCredit(applied);
        details.add(crAr);

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(job.getJobNo() + "-PAY");
        entry.setEntryDate(LocalDateTime.now());
        entry.setDescription("AR collection for service job " + job.getJobNo());
        entry.setStaffId(job.getAssignedStaff() != null ? job.getAssignedStaff().getId() : null);
        entry.setDetails(details);

        journalWriter.write(entry);
    }

    @Transactional
    public ServiceJobDTO deliver(Integer id) {
        ServiceJob job = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found: " + id));
        if (job.getStatus() != ServiceJobStatus.COMPLETED)
            throw new IllegalStateException("Only completed jobs can be marked as delivered");
        job.setStatus(ServiceJobStatus.DELIVERED);
        job.setDeliveredDate(LocalDateTime.now());
        ServiceJobDTO result = toDto(repo.save(job));
        messagingTemplate.convertAndSend("/topic/service-jobs", "JOB_DELIVERED");
        return result;
    }

    private SaleDTO createSaleFromServiceJob(ServiceJob job, SettleDTO dto, BigDecimal totalAmount) {
        SaleDTO saleDto = new SaleDTO();
        saleDto.setCustomerId(job.getCustomer().getId());
        saleDto.setStaffId(job.getAssignedStaff() != null ? job.getAssignedStaff().getId() : null);
        saleDto.setSaleDate(LocalDateTime.now());

        List<SaleDetailDTO> details = new ArrayList<>();
        BigDecimal productTotal = BigDecimal.ZERO;
        for (ServiceJobPart part : job.getProductParts()) {
            SaleDetailDTO detail = new SaleDetailDTO();
            detail.setProductId(part.getProduct().getId());
            detail.setProductName(part.getProduct().getName());
            detail.setQty(part.getQty());
            boolean covered = Boolean.TRUE.equals(part.getWarrantyCovered());
            detail.setUnitPrice(covered ? BigDecimal.ZERO : part.getUnitPrice());
            detail.setDiscountAmount(covered ? BigDecimal.ZERO : (part.getDiscountAmount() != null ? part.getDiscountAmount() : BigDecimal.ZERO));
            detail.setSubtotal(part.getSubtotal());
            List<String> serialNumbers = splitSerials(part.getSerialNumbers());
            if (Boolean.TRUE.equals(part.getProduct().getHasSerial()) && serialNumbers.isEmpty()) {
                throw new RuntimeException("Serial numbers are required for product: " + part.getProduct().getName());
            }
            detail.setSerialNumbers(serialNumbers);
            details.add(detail);
            productTotal = productTotal.add(part.getSubtotal());
        }

        saleDto.setDetails(details);
        saleDto.setPaidAmount(BigDecimal.ZERO);
        saleDto.setPaymentMethodId(dto.getPaymentMethodId());
        saleDto.setPaymentAccountId(dto.getPaymentAccountId());
        saleDto.setRemark("Service Job: " + job.getJobNo());
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
            .jobNo(generateJobNo()).customer(original.getCustomer())
            .assignedStaff(req.getAssignedStaffId() != null
                ? staffRepo.findById(req.getAssignedStaffId()).orElse(original.getAssignedStaff()) : original.getAssignedStaff())
            .itemName(original.getItemName()).itemCondition(original.getItemCondition())
            .serialNo(original.getSerialNo()).color(original.getColor()).accessories(original.getAccessories())
            .shelfLocation(original.getShelfLocation())
            .problemDesc(req.getProblemDesc() != null ? req.getProblemDesc() : original.getProblemDesc())
            .estimatedCost(customerCharge).finalCost(BigDecimal.ZERO).status(ServiceJobStatus.RECEIVED)
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
        messagingTemplate.convertAndSend("/topic/service-jobs", "REWORK_CREATED");
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
        repo.deleteById(id);
    }

    private void buildLines(ServiceJob job, ServiceJobDTO dto) {
        if (dto.getLines() == null || dto.getLines().isEmpty()) return;
        if (job.getLines() == null) job.setLines(new ArrayList<>());
        BigDecimal total = BigDecimal.ZERO;
        for (ServiceJobLineDTO l : dto.getLines()) {
            var svc = serviceItemRepo.findById(l.getServiceItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Service item not found: " + l.getServiceItemId()));
            int qty = l.getQty() != null ? l.getQty() : 1;
            BigDecimal price = svc.getPrice();
            boolean covered = Boolean.TRUE.equals(l.getWarrantyCovered());
            BigDecimal sub = covered ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(qty));
            job.getLines().add(ServiceJobLine.builder()
                .serviceJob(job)
                .serviceItem(svc)
                .qty(qty)
                .price(price)
                .subtotal(sub)
                .warrantyMonths(l.getWarrantyMonths() != null ? l.getWarrantyMonths() : 0)
                .warrantyCovered(covered)
                .build());
            total = total.add(sub);
        }
        job.setEstimatedCost(total.add(productPartsTotal(job)));
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

    private void reverseProductParts(ServiceJob job) {
        if (job.getProductParts() == null) return;
        for (ServiceJobPart part : job.getProductParts()) {
            Product product = part.getProduct();
            if (Boolean.TRUE.equals(product.getHasSerial())) {
                for (String sn : splitSerials(part.getSerialNumbers())) {
                    serialRepo.findBySerialNumber(sn).ifPresent(serial -> {
                        if (serial.getStatus() == SerialStatus.Used_In_Service) {
                            serial.setStatus(SerialStatus.Available);
                            serialRepo.save(serial);
                        }
                    });
                }
            } else {
                int currentQty = product.getStockQty() != null ? product.getStockQty() : 0;
                product.setStockQty(currentQty + (part.getQty() != null ? part.getQty() : 0));
                productRepo.save(product);
            }
        }
    }

    private void recalculateEstimatedCost(ServiceJob job) {
        BigDecimal services = job.getLines() == null ? BigDecimal.ZERO : job.getLines().stream()
            .map(ServiceJobLine::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        job.setEstimatedCost(services.add(productPartsTotal(job)));
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
                                    PaymentMethod pm, BigDecimal paid, BigDecimal due, List<PaymentTransactionDTO> payments) {
        // Revenue = full net amount (labor + products); sale record is inventory-only
        BigDecimal revenueAmt = paid.add(due);
        if (revenueAmt.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal cashPortion = paid;
        BigDecimal arPortion  = due;

        List<JournalDetailDTO> details = new ArrayList<>();

        // DR Cash / Bank (only when money was actually received)
        if (cashPortion.compareTo(BigDecimal.ZERO) > 0) {
            for (PaymentLine line : resolvePaymentLines(payments, cashPortion, pm)) {
                JournalDetailDTO drCash = new JournalDetailDTO();
                drCash.setAccountId(line.method().getAccount().getId());
                drCash.setDebit(line.amount());
                drCash.setCredit(BigDecimal.ZERO);
                details.add(drCash);
            }
        }

        // DR Accounts Receivable (credit portion not yet paid)
        if (arPortion.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO drAr = new JournalDetailDTO();
            drAr.setAccountId(accountResolver.receivable().getId());
            drAr.setDebit(arPortion);
            drAr.setCredit(BigDecimal.ZERO);
            details.add(drAr);
        }

        // CR Service Revenue
        JournalDetailDTO cr = new JournalDetailDTO();
        cr.setAccountId(accountResolver.serviceRevenue().getId());
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(revenueAmt);
        details.add(cr);

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(job.getJobNo());
        entry.setEntryDate(LocalDateTime.now());
        entry.setDescription("Service Job Settlement - " + job.getJobNo()
            + (job.getSaleId() != null ? " [Sale: " + job.getSaleId() + "]" : ""));
        entry.setStaffId(job.getAssignedStaff() != null ? job.getAssignedStaff().getId() : null);
        entry.setDetails(details);

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
            if (method.getAccount() == null) throw new RuntimeException("Payment Method must have linked account.");
            lines.add(new PaymentLine(method, amount, payment.getTransactionNo()));
        }
        return lines;
    }

    private record PaymentLine(PaymentMethod method, BigDecimal amount, String transactionNo) {}

    private Integer resolveCashAccount(PaymentMethod pm, Integer overrideAccountId) {
        if (overrideAccountId != null) return overrideAccountId;
        if (pm != null && pm.getAccount() != null) return pm.getAccount().getId();
        return accountResolver.cash().getId();
    }

    private String generateJobNo() {
        int next = repo.findTopByOrderByIdDesc().map(j -> j.getId() + 1).orElse(1);
        return String.format("SJ-%06d", next);
    }

    private String generateTxnNo() {
        long count = paymentTransactionRepo.count();
        return String.format("TXN-%06d", count + 1);
    }

    private ServiceJobDTO toDto(ServiceJob j) {
        ServiceJobDTO dto = new ServiceJobDTO();
        dto.setId(j.getId());
        dto.setJobNo(j.getJobNo());
        dto.setCustomerId(j.getCustomer().getId());
        dto.setCustomerName(j.getCustomer().getName());
        if (j.getAssignedStaff() != null) {
            dto.setAssignedStaffId(j.getAssignedStaff().getId());
            dto.setAssignedStaffName(j.getAssignedStaff().getName());
        }
        dto.setItemName(j.getItemName());
        dto.setSerialNo(j.getSerialNo());
        dto.setColor(j.getColor());
        dto.setItemCondition(j.getItemCondition());
        dto.setDeviceConditions(j.getDeviceConditions());
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
        dto.setPaidAmount(j.getPaidAmount());
        dto.setDueAmount(j.getDueAmount());
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
            bookingRepo.findById(j.getBookingId()).ifPresent(b -> {
                dto.setBookingNo(b.getInvoiceNo());
                if (dto.getColor() == null || dto.getColor().isBlank())
                    dto.setColor(b.getColor());
                if (dto.getSerialNo() == null || dto.getSerialNo().isBlank())
                    dto.setSerialNo(b.getSerialNumber());
                // Only fall back to booking accessories if the job has none of its own
                if (dto.getAccessories() == null || dto.getAccessories().isBlank())
                    dto.setAccessories(b.getAccessories());
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
            ld.setPrice(l.getPrice());
            ld.setSubtotal(l.getSubtotal());
            ld.setWarrantyMonths(l.getWarrantyMonths());
            ld.setWarrantyCovered(Boolean.TRUE.equals(l.getWarrantyCovered()));
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
            return pd;
        }).toList());
        return dto;
    }
}
