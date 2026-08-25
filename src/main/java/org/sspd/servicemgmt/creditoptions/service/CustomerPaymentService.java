package org.sspd.servicemgmt.creditoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.creditoptions.dto.CustomerCreditApplyRequest;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentRequest;
import org.sspd.servicemgmt.creditoptions.mapper.CreditMapper;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditApplication;
import org.sspd.servicemgmt.creditoptions.model.CustomerPayment;
import org.sspd.servicemgmt.creditoptions.model.CustomerPaymentAllocation;
import org.sspd.servicemgmt.creditoptions.repository.CustomerCreditApplicationRepository;
import org.sspd.servicemgmt.creditoptions.repository.CustomerPaymentRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.saleoptions.model.CreditStatus;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CustomerPaymentRepository repository;
    private final CustomerCreditApplicationRepository creditApplicationRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StaffRepository staffRepository;
    private final CreditMapper mapper;
    private final CreditAlertService creditAlertService;
    private final PaymentBalanceValidator paymentBalanceValidator;
    private final CashDrawerService cashDrawerService;
    private final JournalWriter journalWriter;
    private final AccountResolver accounts;
    private final AccountingPeriodGuard periodGuard;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_CREATE','CAN_ACCESS_BOOKING_CREATE','CAN_ACCESS_CUSTOMER_PAYMENT_CREATE')")
    @Transactional
    public CustomerPaymentDTO createAdvancePayment(CustomerPaymentDTO dto) {
        if (dto.getSaleId() != null) {
            throw new RuntimeException("Use sale payment API for settling invoices");
        }
        CustomerPayment payment = toEntity(dto, null);
        payment.setAdvanceAmount(safe(dto.getAmount()));
        payment.setAllocatedAmount(BigDecimal.ZERO);
        CustomerPayment saved = repository.save(payment);
        saved.setPaymentNo(formatPaymentNo(saved.getId()));
        Customer customer = saved.getCustomer();
        customer.setAdvanceBalance(safe(customer.getAdvanceBalance()).add(safe(dto.getAmount())));
        customerRepository.save(customer);
        postAdvanceJournal(saved, dto.getStaffId());
        if (isCash(saved.getPaymentMethod()))
            cashDrawerService.recordCashSale(saved.getAmount());
        return toDto(repository.save(saved));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_PAYMENT_CREATE','CAN_ACCESS_SALE_UPDATE','CAN_ACCESS_SALE_CREATE')")
    @Transactional
    public CustomerPaymentDTO allocate(CustomerPaymentRequest request) {
        periodGuard.assertOpen(LocalDateTime.now(), "record customer payment");
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Payment amount must be greater than zero.");
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        PaymentMethod method = paymentMethodRepository.findById(request.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        if (method.getAccount() == null) throw new RuntimeException("Payment method must have a linked account.");
        if (request.getStaffId() == null || !staffRepository.existsById(request.getStaffId()))
            throw new RuntimeException("Valid staff is required for customer payment.");
        paymentBalanceValidator.validateSufficientBalance(method, request.getAmount());

        List<AllocationWork> work = resolveAllocations(request, customer);
        BigDecimal allocated = work.stream().map(AllocationWork::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(request.getAmount()) > 0)
            throw new RuntimeException("Allocated amount exceeds total payment.");
        BigDecimal advance = request.getAmount().subtract(allocated);

        CustomerPayment payment = CustomerPayment.builder()
                .paymentNo("PENDING")
                .customer(customer)
                .paymentMethod(method)
                .amount(request.getAmount())
                .allocatedAmount(allocated)
                .advanceAmount(advance)
                .paymentDate(LocalDateTime.now())
                .transactionNo(blank(request.getTransactionNo()) ? nextTransactionNo() : request.getTransactionNo().trim())
                .note(request.getRemark())
                .staff(staffRepository.findById(request.getStaffId()).orElseThrow())
                .voided(false)
                .build();
        List<CustomerPaymentAllocation> entities = new ArrayList<>();
        for (AllocationWork item : work) {
            Sale sale = item.sale();
            applyToSale(sale, item.amount());
            entities.add(CustomerPaymentAllocation.builder()
                    .customerPayment(payment).sale(sale).amount(item.amount()).build());
            PaymentTransaction tx = new PaymentTransaction();
            tx.setReferenceId(sale.getId());
            tx.setReferenceType(ReferenceType.Sale);
            tx.setPaymentMethod(method);
            tx.setAmount(item.amount());
            tx.setPaymentDate(LocalDateTime.now());
            tx.setTransactionNo(payment.getTransactionNo());
            paymentTransactionRepository.save(tx);
        }
        payment.setAllocations(entities);
        payment = repository.save(payment);
        payment.setPaymentNo(formatPaymentNo(payment.getId()));
        payment = repository.save(payment);

        if (advance.compareTo(BigDecimal.ZERO) > 0) {
            customer.setAdvanceBalance(safe(customer.getAdvanceBalance()).add(advance));
            customerRepository.save(customer);
        }
        postAllocateJournal(payment, request.getStaffId());
        if (isCash(method))
            cashDrawerService.recordCashSale(request.getAmount());
        return toDto(payment);
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_PAYMENT_CREATE','CAN_ACCESS_SALE_UPDATE')")
    @Transactional
    public CustomerPaymentDTO voidPayment(Integer id, String reason, Integer staffId) {
        periodGuard.assertOpen(LocalDateTime.now(), "void customer payment");
        CustomerPayment payment = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer payment not found"));
        if (Boolean.TRUE.equals(payment.getVoided()))
            throw new IllegalStateException("Payment is already voided.");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Void reason is required.");
        if (staffId == null || !staffRepository.existsById(staffId))
            throw new IllegalArgumentException("Valid staff is required.");
        if (payment.getAllocations() == null || payment.getAllocations().isEmpty())
            throw new IllegalStateException("Only allocated customer payments can be voided here.");

        for (CustomerPaymentAllocation alloc : payment.getAllocations()) {
            reverseFromSale(alloc.getSale(), safe(alloc.getAmount()));
            paymentTransactionRepository.findByReferenceIdAndReferenceType(alloc.getSale().getId(), ReferenceType.Sale).stream()
                    .filter(tx -> payment.getTransactionNo() != null && payment.getTransactionNo().equals(tx.getTransactionNo()))
                    .filter(tx -> !Boolean.TRUE.equals(tx.getReversed()))
                    .forEach(tx -> {
                        tx.setReversed(true);
                        tx.setReversedAt(LocalDateTime.now());
                        tx.setReversedBy(currentUsername());
                        tx.setReversalReason(reason.trim());
                        paymentTransactionRepository.save(tx);
                    });
        }
        if (safe(payment.getAdvanceAmount()).signum() > 0) {
            Customer customer = payment.getCustomer();
            customer.setAdvanceBalance(safe(customer.getAdvanceBalance()).subtract(safe(payment.getAdvanceAmount())).max(BigDecimal.ZERO));
            customerRepository.save(customer);
        }
        journalWriter.reverseByReferenceNo(payment.getPaymentNo());
        if (isCash(payment.getPaymentMethod()))
            cashDrawerService.recordCashRefund(payment.getAmount());

        payment.setVoided(true);
        payment.setVoidedAt(LocalDateTime.now());
        payment.setVoidedBy(currentUsername());
        payment.setVoidReason(reason.trim());
        return toDto(repository.save(payment));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_PAYMENT_CREATE','CAN_ACCESS_SALE_UPDATE','CAN_ACCESS_SERVICE_JOB_SETTLE')")
    @Transactional
    public Map<String, Object> applyCredit(CustomerCreditApplyRequest request) {
        periodGuard.assertOpen(LocalDateTime.now(), "apply customer credit");
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (request.getStaffId() == null || !staffRepository.existsById(request.getStaffId()))
            throw new RuntimeException("Valid staff is required.");
        BigDecimal amount = safe(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Credit amount must be greater than zero.");
        if (amount.compareTo(safe(customer.getAdvanceBalance())) > 0)
            throw new RuntimeException("Insufficient customer credit.");
        if (request.getServiceJobId() != null) {
            return applyCreditToJob(customer, request, amount);
        }
        if (request.getSaleId() == null)
            throw new RuntimeException("Sale or service job is required.");
        Sale target = saleRepository.findById(request.getSaleId())
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));
        if (!customer.getId().equals(target.getCustomer().getId()))
            throw new RuntimeException("Target invoice belongs to another customer.");
        if (Boolean.TRUE.equals(target.getVoided()))
            throw new RuntimeException("Cannot apply credit to a voided sale.");
        if (amount.compareTo(safe(target.getDueAmount())) > 0)
            throw new RuntimeException("Credit amount must be within the invoice due amount.");

        customer.setAdvanceBalance(safe(customer.getAdvanceBalance()).subtract(amount));
        customerRepository.save(customer);
        applyToSale(target, amount);

        CustomerCreditApplication application = CustomerCreditApplication.builder()
                .applicationNo("PENDING").customer(customer).sale(target).amount(amount)
                .appliedAt(LocalDateTime.now()).appliedBy(currentUsername()).reason(request.getReason())
                .build();
        application = creditApplicationRepository.save(application);
        application.setApplicationNo(String.format("CCA-%06d", application.getId()));
        application = creditApplicationRepository.save(application);

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(application.getApplicationNo());
        entry.setEntryDate(application.getAppliedAt());
        entry.setDescription("Apply customer credit to " + target.getSaleCode());
        entry.setStaffId(request.getStaffId());
        entry.setDetails(List.of(
                line(accounts.custAdvance().getId(), amount, BigDecimal.ZERO),
                line(accounts.receivable().getId(), BigDecimal.ZERO, amount)
        ));
        journalWriter.write(entry);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationNo", application.getApplicationNo());
        result.put("amount", amount);
        result.put("remainingDue", target.getDueAmount());
        result.put("advanceBalance", customer.getAdvanceBalance());
        return result;
    }

    private Map<String, Object> applyCreditToJob(Customer customer, CustomerCreditApplyRequest request, BigDecimal amount) {
        ServiceJob job = serviceJobRepository.findById(request.getServiceJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found"));
        if (!customer.getId().equals(job.getCustomer().getId()))
            throw new RuntimeException("Target job belongs to another customer.");
        if (Boolean.TRUE.equals(job.getVoided()))
            throw new RuntimeException("Cannot apply credit to a voided job.");
        if (amount.compareTo(safe(job.getDueAmount())) > 0)
            throw new RuntimeException("Credit amount must be within the job due amount.");

        customer.setAdvanceBalance(safe(customer.getAdvanceBalance()).subtract(amount));
        customerRepository.save(customer);
        applyToServiceJob(job, amount);

        CustomerCreditApplication application = CustomerCreditApplication.builder()
                .applicationNo("PENDING").customer(customer).serviceJobId(job.getId()).amount(amount)
                .appliedAt(LocalDateTime.now()).appliedBy(currentUsername()).reason(request.getReason())
                .build();
        application = creditApplicationRepository.save(application);
        application.setApplicationNo(String.format("CCA-%06d", application.getId()));
        application = creditApplicationRepository.save(application);

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(application.getApplicationNo());
        entry.setEntryDate(application.getAppliedAt());
        entry.setDescription("Apply customer credit to " + job.getJobNo());
        entry.setStaffId(request.getStaffId());
        entry.setDetails(List.of(
                line(accounts.custAdvance().getId(), amount, BigDecimal.ZERO),
                line(accounts.receivable().getId(), BigDecimal.ZERO, amount)
        ));
        journalWriter.write(entry);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationNo", application.getApplicationNo());
        result.put("amount", amount);
        result.put("remainingDue", job.getDueAmount());
        result.put("advanceBalance", customer.getAdvanceBalance());
        result.put("serviceJobId", job.getId());
        return result;
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_PAYMENT_READ','CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> receivables(Integer customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        List<Map<String, Object>> rows = saleRepository.findCustomerReceivablesFifo(customerId).stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("saleId", s.getId());
            row.put("saleCode", s.getSaleCode());
            row.put("saleDate", s.getSaleDate());
            row.put("dueDate", s.getDueDate());
            row.put("netAmount", s.getNetAmount());
            row.put("paidAmount", s.getPaidAmount());
            row.put("dueAmount", s.getDueAmount());
            row.put("type", "SALE");
            return row;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (ServiceJob job : serviceJobRepository.findByCustomerId(customerId)) {
            if (safe(job.getDueAmount()).signum() <= 0) continue;
            if (Boolean.TRUE.equals(job.getVoided())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("serviceJobId", job.getId());
            row.put("saleCode", job.getJobNo());
            row.put("saleDate", job.getReceivedDate());
            row.put("dueDate", job.getDueDate());
            row.put("netAmount", job.getNetAmount());
            row.put("paidAmount", job.getPaidAmount());
            row.put("dueAmount", job.getDueAmount());
            row.put("type", "SERVICE_JOB");
            rows.add(row);
        }
        return rows;
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_PAYMENT_READ','CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public Map<String, Object> creditSummary(Integer customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advanceBalance", safe(customer.getAdvanceBalance()));
        result.put("availableCredit", safe(customer.getAdvanceBalance()));
        return result;
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public List<CustomerPaymentDTO> findByCustomer(Integer customerId) {
        return repository.findByCustomerIdOrderByIdDesc(customerId).stream().map(this::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public List<CustomerPaymentDTO> findBySale(Integer saleId) {
        return repository.findBySaleId(saleId).stream().map(this::toDto).toList();
    }

    @Transactional
    public void recordSalePayment(Sale sale, CustomerPaymentDTO dto) {
        CustomerPayment payment = toEntity(dto, sale);
        payment.setAllocatedAmount(safe(dto.getAmount()));
        CustomerPayment saved = repository.save(payment);
        if (saved.getPaymentNo() == null || saved.getPaymentNo().isBlank()) {
            saved.setPaymentNo(formatPaymentNo(saved.getId()));
            repository.save(saved);
        }
    }

    public void addAdvanceBalance(Customer customer, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        customer.setAdvanceBalance(safe(customer.getAdvanceBalance()).add(amount));
        customerRepository.save(customer);
    }

    public void reduceAdvanceBalance(Customer customer, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        customer.setAdvanceBalance(safe(customer.getAdvanceBalance()).subtract(amount).max(BigDecimal.ZERO));
        customerRepository.save(customer);
    }

    private List<AllocationWork> resolveAllocations(CustomerPaymentRequest request, Customer customer) {
        List<AllocationWork> result = new ArrayList<>();
        if (request.getAllocations() != null && !request.getAllocations().isEmpty()) {
            Set<Integer> seen = new HashSet<>();
            for (var requested : request.getAllocations()) {
                if (requested.getSaleId() == null || !seen.add(requested.getSaleId()))
                    throw new RuntimeException("Duplicate or missing sale allocation.");
                Sale sale = saleRepository.findById(requested.getSaleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));
                if (!customer.getId().equals(sale.getCustomer().getId()))
                    throw new RuntimeException("Allocated sale belongs to another customer.");
                if (Boolean.TRUE.equals(sale.getVoided()))
                    throw new RuntimeException("Cannot allocate to a voided sale.");
                BigDecimal amount = safe(requested.getAmount());
                if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(safe(sale.getDueAmount())) > 0)
                    throw new RuntimeException("Invalid allocation for " + sale.getSaleCode());
                result.add(new AllocationWork(sale, amount));
            }
            return result;
        }
        BigDecimal remaining = request.getAmount();
        for (Sale sale : saleRepository.findCustomerReceivablesFifo(customer.getId())) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal amount = remaining.min(safe(sale.getDueAmount()));
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                result.add(new AllocationWork(sale, amount));
                remaining = remaining.subtract(amount);
            }
        }
        return result;
    }

    private void applyToSale(Sale sale, BigDecimal amount) {
        BigDecimal newPaid = safe(sale.getPaidAmount()).add(amount);
        BigDecimal newDue = safe(sale.getDueAmount()).subtract(amount).max(BigDecimal.ZERO);
        sale.setPaidAmount(newPaid);
        sale.setDueAmount(newDue);
        sale.setPaymentStatus(newDue.signum() <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial);
        sale.setCreditStatus(creditStatus(newDue, sale.getDueDate()));
        if (newDue.signum() <= 0) sale.setDueDate(null);
        saleRepository.save(sale);
        creditAlertService.evaluateDueAlerts(sale);
    }

    private void applyToServiceJob(ServiceJob job, BigDecimal amount) {
        BigDecimal newPaid = safe(job.getPaidAmount()).add(amount);
        BigDecimal newDue = safe(job.getDueAmount()).subtract(amount).max(BigDecimal.ZERO);
        job.setPaidAmount(newPaid);
        job.setDueAmount(newDue);
        job.setPaymentStatus(newDue.signum() <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial);
        job.setCreditStatus(creditStatus(newDue, job.getDueDate()));
        if (newDue.signum() <= 0) job.setDueDate(null);
        serviceJobRepository.save(job);
    }

    private void reverseFromSale(Sale sale, BigDecimal amount) {
        BigDecimal newPaid = safe(sale.getPaidAmount()).subtract(amount).max(BigDecimal.ZERO);
        BigDecimal newDue = safe(sale.getDueAmount()).add(amount);
        sale.setPaidAmount(newPaid);
        sale.setDueAmount(newDue);
        sale.setPaymentStatus(newPaid.signum() <= 0 ? PaymentStatus.Pending
                : (newDue.signum() <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial));
        sale.setCreditStatus(creditStatus(newDue, sale.getDueDate()));
        saleRepository.save(sale);
        creditAlertService.evaluateDueAlerts(sale);
    }

    private CreditStatus creditStatus(BigDecimal due, LocalDate dueDate) {
        if (due == null || due.signum() <= 0) return dueDate == null ? CreditStatus.Not_Credit : CreditStatus.Paid;
        if (dueDate == null) return CreditStatus.Active;
        return dueDate.isBefore(LocalDate.now()) ? CreditStatus.Overdue : CreditStatus.Active;
    }

    private void postAllocateJournal(CustomerPayment payment, Integer staffId) {
        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(payment.getPaymentNo());
        entry.setEntryDate(payment.getPaymentDate());
        entry.setDescription("Customer payment: " + payment.getCustomer().getName());
        entry.setStaffId(staffId);
        List<JournalDetailDTO> lines = new ArrayList<>();
        lines.add(line(payment.getPaymentMethod().getAccount().getId(), payment.getAmount(), BigDecimal.ZERO));
        if (safe(payment.getAllocatedAmount()).signum() > 0)
            lines.add(line(accounts.receivable().getId(), BigDecimal.ZERO, payment.getAllocatedAmount()));
        if (safe(payment.getAdvanceAmount()).signum() > 0)
            lines.add(line(accounts.custAdvance().getId(), BigDecimal.ZERO, payment.getAdvanceAmount()));
        entry.setDetails(lines);
        journalWriter.write(entry);
    }

    private void postAdvanceJournal(CustomerPayment payment, Integer staffId) {
        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(payment.getPaymentNo());
        entry.setEntryDate(payment.getPaymentDate());
        entry.setDescription("Customer advance: " + payment.getCustomer().getName());
        entry.setStaffId(staffId);
        entry.setDetails(List.of(
                line(payment.getPaymentMethod().getAccount().getId(), payment.getAmount(), BigDecimal.ZERO),
                line(accounts.custAdvance().getId(), BigDecimal.ZERO, payment.getAmount())
        ));
        journalWriter.write(entry);
    }

    private JournalDetailDTO line(Integer accountId, BigDecimal debit, BigDecimal credit) {
        JournalDetailDTO line = new JournalDetailDTO();
        line.setAccountId(accountId);
        line.setDebit(debit);
        line.setCredit(credit);
        return line;
    }

    private CustomerPayment toEntity(CustomerPaymentDTO dto, Sale sale) {
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));

        Staff staff = null;
        if (dto.getStaffId() != null) {
            staff = staffRepository.findById(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        } else if (sale != null && sale.getStaff() != null) {
            staff = sale.getStaff();
        } else {
            throw new RuntimeException("Staff is required for payment");
        }

        CustomerPayment payment = mapper.toEntity(dto);
        payment.setCustomer(customer);
        payment.setSale(sale);
        payment.setPaymentMethod(method);
        payment.setStaff(staff);
        payment.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDateTime.now());
        payment.setVoided(false);
        return payment;
    }

    private CustomerPaymentDTO toDto(CustomerPayment payment) {
        CustomerPaymentDTO dto = mapper.toDto(payment);
        if (payment.getAllocations() != null && !payment.getAllocations().isEmpty()) {
            dto.setAllocations(payment.getAllocations().stream().map(a -> {
                CustomerPaymentDTO.Allocation row = new CustomerPaymentDTO.Allocation();
                row.setSaleId(a.getSale().getId());
                row.setSaleCode(a.getSale().getSaleCode());
                row.setAmount(a.getAmount());
                row.setRemainingDue(a.getSale().getDueAmount());
                return row;
            }).toList());
        }
        return dto;
    }

    private boolean isCash(PaymentMethod method) {
        String name = method.getMethodName() == null ? "" : method.getMethodName().toLowerCase();
        return name.contains("cash") || name.contains("ငွေသား");
    }

    private String currentUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private String formatPaymentNo(Integer id) { return String.format("CP-%06d", id); }
    private String nextTransactionNo() { return "CPTX-" + System.currentTimeMillis(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private record AllocationWork(Sale sale, BigDecimal amount) {}
}
