package org.sspd.servicemgmt.saleoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.mapper.SaleMapper;
import org.sspd.servicemgmt.saleoptions.dto.SalePaymentDTO;
import org.sspd.servicemgmt.saleoptions.model.CreditStatus;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
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
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO;
import org.sspd.servicemgmt.creditoptions.model.AlertType;
import org.sspd.servicemgmt.creditoptions.model.CreditOverrideLog;
import org.sspd.servicemgmt.creditoptions.service.CreditAlertService;
import org.sspd.servicemgmt.creditoptions.service.CreditService;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.creditoptions.repository.CreditOverrideLogRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.sspd.servicemgmt.api.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final AccountResolver accountResolver;
    @Value("${credit.large-alert-threshold:1000000}")
    private BigDecimal largeCreditAlertThreshold;

    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductSerialRepository serialRepository;
    private final StockMovementService stockMovementService;
    private final JournalWriter journalWriter;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SaleMapper mapper;
    private final CreditService creditService;
    private final CreditAlertService creditAlertService;
    private final CustomerPaymentService customerPaymentService;
    private final CreditOverrideLogRepository creditOverrideLogRepository;
    private final PaymentBalanceValidator paymentBalanceValidator;
    private final CompanySettingsService companySettingsService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CashDrawerService cashDrawerService;

    private static final BigDecimal CASHIER_DISCOUNT_PERCENT = new BigDecimal("5");
    private static final BigDecimal MANAGER_DISCOUNT_PERCENT = new BigDecimal("20");

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_UPDATE')")
    @Transactional
    public SaleDTO payDue(Integer saleId, SalePaymentDTO dto) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + saleId));
        if (Boolean.TRUE.equals(sale.getVoided())) {
            throw new RuntimeException("Cannot record payment for a voided sale.");
        }

        BigDecimal currentDue = sale.getDueAmount() != null ? sale.getDueAmount() : BigDecimal.ZERO;
        if (currentDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("No due amount to pay for this sale.");
        }

        BigDecimal incomingPaid = paymentTotal(dto.getPayments(), dto.getPaidAmount());
        if (incomingPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Paid amount must be greater than zero.");
        }
        if (incomingPaid.compareTo(currentDue) > 0) {
            throw new RuntimeException("Paid amount cannot exceed the outstanding balance.");
        }

        BigDecimal applied = incomingPaid;

        BigDecimal newPaid = sale.getPaidAmount().add(applied);
        BigDecimal newDue = currentDue.subtract(applied);
        sale.setPaidAmount(newPaid);
        sale.setDueAmount(newDue);
        sale.setPaymentStatus(calculateStatus(sale.getNetAmount(), newPaid));
        sale.setCreditStatus(calculateCreditStatus(newDue, sale.getDueDate()));

        Sale saved = saleRepository.save(sale);

        SaleDTO paymentDto = toSaleDtoForPayment(dto, applied);
        createPaymentTransaction(saved, paymentDto);
        createSaleJournalForPayment(saved, applied, dto.getPaymentAccountId(), dto.getArAccountId(), dto.getPaymentMethodId(), paymentDto.getPayments());
        recordCustomerPayment(saved, dto, applied);
        creditAlertService.evaluateDueAlerts(saved);

        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @Transactional
    public SaleDTO save(SaleDTO dto) {
        if (dto.getDetails() == null || dto.getDetails().isEmpty()) {
            throw new RuntimeException("Sale details are required");
        }
        if (dto.getCustomerId() == null) {
            throw new RuntimeException("Customer is required");
        }
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Staff staff = dto.getStaffId() != null
                ? staffRepository.findById(dto.getStaffId()).orElseThrow(() -> new ResourceNotFoundException("Staff not found"))
                : null;
        validateStaffSelection(staff);

        Sale sale = new Sale();
        sale.setVoided(Boolean.FALSE);
        sale.setCustomer(customer);
        sale.setStaff(staff);
        sale.setSaleDate(resolveSaleDateWithPermission(dto.getSaleDate()));
        sale.setRemark(dto.getRemark());
        sale.setFoc(Boolean.TRUE.equals(dto.getFoc()));
        sale.setSaleCode("PENDING"); // temporary to satisfy not-null, will overwrite after save

        List<SaleDetail> details = buildDetails(dto.getDetails(), sale, dto.isServiceJobSale());
        sale.setDetails(details);

        BigDecimal discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal tax = dto.getTaxAmount() != null ? dto.getTaxAmount() : BigDecimal.ZERO;
        if (tax.signum() < 0) throw new RuntimeException("Tax amount cannot be negative");
        sale.setTaxAmount(tax);
        BigDecimal totalPreview = calculateTotal(details);
        validateDiscountLimit(totalPreview, discount);
        BigDecimal netPreview = totalPreview.subtract(discount).max(BigDecimal.ZERO).add(tax);

        // Internal service-job sales: treat as fully paid (inventory-only, payment tracked at job level)
        boolean isServiceJobSale = dto.isServiceJobSale();
        BigDecimal paidPreview = isServiceJobSale ? netPreview
                : paymentTotal(dto.getPayments(), dto.getPaidAmount());
        if (!isServiceJobSale && paidPreview.compareTo(netPreview) > 0) {
            throw new RuntimeException("Paid amount cannot exceed the sale net amount.");
        }
        BigDecimal duePreview = netPreview.subtract(paidPreview).max(BigDecimal.ZERO);

        if (!isServiceJobSale) {
            if (Boolean.TRUE.equals(customer.getCreditHold()) && duePreview.compareTo(BigDecimal.ZERO) > 0)
                throw new RuntimeException("Customer credit is on hold");
            if (Boolean.TRUE.equals(customer.getBlacklisted()) && duePreview.compareTo(BigDecimal.ZERO) > 0)
                throw new RuntimeException("Customer is blacklisted; cash sale only");
        }

        LocalDate resolvedDueDate = dto.getDueDate();
        if (!isServiceJobSale && duePreview.compareTo(BigDecimal.ZERO) > 0) {
            resolvedDueDate = creditService.resolveDueDate(customer.getId(), sale.getSaleDate(), resolvedDueDate, duePreview);
        }

        applyTotals(sale, details, discount, paidPreview, resolvedDueDate);
        if (!isServiceJobSale) {
            enforceCreditLimitWithOverride(sale, sale.getDueAmount(), dto.getManagerOverride(), dto.getManagerId(), dto.getOverrideNote());
        }

        Sale saved = saleRepository.save(sale);
        saved.setSaleCode(generateSaleCode(saved.getId()));
        saved = saleRepository.save(saved);
        recordStockMovements(saved); // always: reduce inventory stock
        createInventoryValuationJournal(saved); // perpetual inventory: DR COGS / CR Inventory

        // Payment tracking: skip for internal service-job sales (handled at ServiceJob level)
        if (!isServiceJobSale) {
            createPaymentTransaction(saved, dto);
            createSaleJournal(saved, dto.getPaymentAccountId(), dto.getArAccountId(), dto.getPaymentMethodId(), dto.getPayments());
            recordCustomerPayment(saved, dto, saved.getPaidAmount());
            creditAlertService.evaluateDueAlerts(saved);
            checkLargeCreditAlert(saved);
        }

        SaleDTO result = mapper.toDto(saved);
        messagingTemplate.convertAndSend("/topic/sales", "SALE_CREATED");
        return result;
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public PageResponse<SaleDTO> findAll(String search, String dateFrom, String dateTo, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        LocalDateTime from = parseDateStart(dateFrom);
        LocalDateTime to   = parseDateEnd(dateTo);
        return PageResponse.of(saleRepository.findBySearch(search, from, to, pageable).map(mapper::toDto));
    }

    private LocalDateTime parseDateStart(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay();
    }

    private LocalDateTime parseDateEnd(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay().plusDays(1);
    }

    private void validateStaffSelection(Staff selectedStaff) {
        if (hasCurrentAuthority("CAN_ACCESS_SALE_STAFF_OVERRIDE")) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        User user = username == null ? null : userRepository.findByUsernameOrEmail(username, username).orElse(null);
        boolean matches = user != null && user.getStaff() != null && selectedStaff != null
                && user.getStaff().getId().equals(selectedStaff.getId());
        if (!matches) {
            throw new AccessDeniedException("Sale အတွက် သင့် Staff ကိုသာ ရွေးချယ်နိုင်ပါသည်။");
        }
    }

    private LocalDateTime resolveSaleDateWithPermission(LocalDateTime requestedSaleDate) {
        LocalDateTime resolved = requestedSaleDate != null ? requestedSaleDate : LocalDateTime.now();
        LocalDate today = LocalDate.now();
        if (resolved.toLocalDate().isBefore(today) && !hasCurrentAuthority("CAN_ACCESS_SALE_BACKDATE")) {
            throw new AccessDeniedException("Sale ကို အတိတ်ရက်စွဲဖြင့် ထည့်သွင်းရန် သင့်မှာ ခွင့်ပြုချက်မရှိပါ။");
        }
        if (resolved.toLocalDate().isAfter(today) && !hasCurrentAuthority("CAN_ACCESS_SALE_FUTUREDATE")) {
            throw new AccessDeniedException("Sale ကို အနာဂတ်ရက်စွဲဖြင့် ထည့်သွင်းရန် သင့်မှာ ခွင့်ပြုချက်မရှိပါ။");
        }
        return resolved;
    }

    private boolean hasCurrentAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_CUSTOMER_READ')")
    @Transactional(readOnly = true)
    public java.util.List<SaleDTO> findByCustomerId(Integer customerId) {
        return saleRepository.findByCustomerIdOrderBySaleDateDescIdDesc(customerId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleDTO findById(Integer id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));
        return mapper.toDto(sale);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_UPDATE')")
    @Transactional
    public SaleDTO update(Integer id, SaleDTO dto) {
        Sale existing = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));

        if (dto.getCustomerId() != null) {
            Customer customer = customerRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            existing.setCustomer(customer);
        }

        if (dto.getStaffId() != null) {
            Staff staff = staffRepository.findById(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
            existing.setStaff(staff);
        }

        if (dto.getSaleDate() != null) existing.setSaleDate(resolveSaleDateWithPermission(dto.getSaleDate()));
        if (dto.getRemark() != null) existing.setRemark(dto.getRemark());

        if (dto.getDetails() != null) {
            throw new RuntimeException("Sale detail updates are not supported.");
        }

        List<SaleDetail> details = existing.getDetails();
        BigDecimal discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : existing.getDiscountAmount();
        BigDecimal tax = dto.getTaxAmount() != null ? dto.getTaxAmount() : existing.getTaxAmount();
        if (tax != null && tax.signum() < 0) throw new RuntimeException("Tax amount cannot be negative");
        existing.setTaxAmount(tax != null ? tax : BigDecimal.ZERO);
        BigDecimal paid = dto.getPaidAmount() != null ? dto.getPaidAmount() : existing.getPaidAmount();
        LocalDate dueDate = dto.getDueDate() != null ? dto.getDueDate() : existing.getDueDate();

        BigDecimal totalPreview = calculateTotal(details);
        validateDiscountLimit(totalPreview, discount);
        BigDecimal netPreview = totalPreview.subtract(discount != null ? discount : BigDecimal.ZERO)
                .add(existing.getTaxAmount() != null ? existing.getTaxAmount() : BigDecimal.ZERO);
        if (netPreview.compareTo(BigDecimal.ZERO) < 0) netPreview = BigDecimal.ZERO;
        BigDecimal paidPreview = paid != null ? paid : BigDecimal.ZERO;
        if (paidPreview.compareTo(netPreview) > 0) paidPreview = netPreview;
        BigDecimal duePreview = netPreview.subtract(paidPreview);

        if (duePreview.compareTo(BigDecimal.ZERO) > 0 && dueDate == null) {
            dueDate = creditService.resolveDueDate(
                    existing.getCustomer().getId(), existing.getSaleDate(), null, duePreview);
        }
        if (Boolean.TRUE.equals(existing.getCustomer().getCreditHold()) && duePreview.compareTo(BigDecimal.ZERO) > 0)
            throw new RuntimeException("Customer credit is on hold");
        if (Boolean.TRUE.equals(existing.getCustomer().getBlacklisted()) && duePreview.compareTo(BigDecimal.ZERO) > 0)
            throw new RuntimeException("Customer is blacklisted; cash sale only");

        // ✅ applyTotals မခေါ်မီ oldPaid သိမ်းထား
        BigDecimal oldPaid = existing.getPaidAmount() != null ? existing.getPaidAmount() : BigDecimal.ZERO;

        applyTotals(existing, details, discount, paidPreview, dueDate);
        enforceCreditLimitWithOverride(existing, existing.getDueAmount(),
                dto.getManagerOverride(), dto.getManagerId(), dto.getOverrideNote());

        Sale saved = saleRepository.save(existing);

        BigDecimal newPaid = saved.getPaidAmount() != null ? saved.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal diffPaid = newPaid.subtract(oldPaid);

        if (diffPaid.compareTo(BigDecimal.ZERO) > 0) {
            createPaymentTransaction(saved, dtoWithPaid(saved, dto, diffPaid));
            createPaymentAdjustmentJournal(saved, diffPaid,
                    dto.getPaymentAccountId(), dto.getArAccountId(), dto.getPaymentMethodId());
        } else if (diffPaid.compareTo(BigDecimal.ZERO) < 0) {
            if (dto.getPaymentMethodId() != null) {
                PaymentMethod refundMethod = paymentMethodRepository.findById(dto.getPaymentMethodId())
                        .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
                paymentBalanceValidator.validateSufficientBalance(refundMethod, diffPaid.abs());
            }
            createPaymentAdjustmentJournal(saved, diffPaid,
                    dto.getPaymentAccountId(), dto.getArAccountId(), dto.getPaymentMethodId());
        }

        creditAlertService.evaluateDueAlerts(saved);
        checkLargeCreditAlert(saved);
        messagingTemplate.convertAndSend("/topic/sales", "SALE_UPDATED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_VOID','CAN_ACCESS_SALE_DELETE')")
    @Transactional
    public SaleDTO voidSale(Integer id, String reason) {
        Sale existing = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));
        if (Boolean.TRUE.equals(existing.getVoided())) {
            throw new RuntimeException("Voided sale cannot be updated.");
        }
        if (Boolean.TRUE.equals(existing.getVoided())) {
            throw new RuntimeException("Sale is already voided");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("Void reason is required");
        }

        reverseStock(existing);
        recordVoidedCashRefund(existing);
        paymentTransactionRepository.deleteByReferenceIdAndReferenceType(existing.getId(), ReferenceType.Sale);
        journalWriter.reverseByReferenceNo(existing.getSaleCode());
        journalWriter.reverseByReferenceNo(existing.getSaleCode() + "-COGS");
        journalWriter.reverseByReferenceNo(existing.getSaleCode() + "-PAY");
        journalWriter.reverseByReferenceNo(existing.getSaleCode() + "-ADJ");

        existing.setVoided(Boolean.TRUE);
        existing.setVoidReason(reason.trim());
        existing.setVoidedBy(currentUsername());
        existing.setVoidedAt(LocalDateTime.now());
        Sale saved = saleRepository.save(existing);
        messagingTemplate.convertAndSend("/topic/sales", "SALE_VOIDED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public byte[] exportExcel(String dateFrom, String dateTo) throws java.io.IOException {
        var sales = saleRepository.findBySearch("", parseDateStart(dateFrom), parseDateEnd(dateTo),
                PageRequest.of(0, Integer.MAX_VALUE, Sort.by("id").descending())).getContent();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var output = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sales");
            String[] headers = {"Invoice", "Date", "Customer", "Staff", "Total", "Discount", "Tax / VAT", "Net", "Paid", "Due", "Payment Status", "Credit Status", "Voided"};
            var style = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.INDIGO.getIndex());
            style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 4800);
            }
            int rowIndex = 1;
            for (Sale sale : sales) {
                var row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(sale.getSaleCode() != null ? sale.getSaleCode() : "#" + sale.getId());
                row.createCell(1).setCellValue(sale.getSaleDate() != null ? sale.getSaleDate().toString() : "");
                row.createCell(2).setCellValue(sale.getCustomer() != null ? sale.getCustomer().getName() : "");
                row.createCell(3).setCellValue(sale.getStaff() != null ? sale.getStaff().getName() : "");
                row.createCell(4).setCellValue(sale.getTotalAmount() != null ? sale.getTotalAmount().doubleValue() : 0);
                row.createCell(5).setCellValue(sale.getDiscountAmount() != null ? sale.getDiscountAmount().doubleValue() : 0);
                row.createCell(6).setCellValue(sale.getTaxAmount() != null ? sale.getTaxAmount().doubleValue() : 0);
                row.createCell(7).setCellValue(sale.getNetAmount() != null ? sale.getNetAmount().doubleValue() : 0);
                row.createCell(8).setCellValue(sale.getPaidAmount() != null ? sale.getPaidAmount().doubleValue() : 0);
                row.createCell(9).setCellValue(sale.getDueAmount() != null ? sale.getDueAmount().doubleValue() : 0);
                row.createCell(10).setCellValue(sale.getPaymentStatus() != null ? sale.getPaymentStatus().name() : "");
                row.createCell(11).setCellValue(sale.getCreditStatus() != null ? sale.getCreditStatus().name() : "");
                row.createCell(12).setCellValue(Boolean.TRUE.equals(sale.getVoided()));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private List<SaleDetail> buildDetails(List<SaleDetailDTO> detailDTOs, Sale parent, boolean isServiceJobSale) {
        List<SaleDetail> detailEntities = new ArrayList<>();
        for (SaleDetailDTO d : detailDTOs) {
            Product product = productRepository.findById(d.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            if (!isServiceJobSale && (product.getSellingPrice() == null || product.getSellingPrice().compareTo(BigDecimal.ZERO) <= 0)) {
                throw new RuntimeException("Product '" + product.getName() + "' တွင် selling price မသတ်မှတ်ရသေးပါ။ ရောင်းချမည့်အချိန် selling price သတ်မှတ်ထားရပါမည်။");
            }

            if (d.getUnitPrice() == null) {
                throw new RuntimeException("Unit price is required");
            }
            if (!isServiceJobSale && d.getUnitPrice().compareTo(product.getSellingPrice()) != 0
                    && !hasCurrentAuthority("CAN_ACCESS_SALE_PRICE_EDIT")) {
                throw new AccessDeniedException("Unit price editing requires CAN_ACCESS_SALE_PRICE_EDIT");
            }

            int requestedWarrantyMonths = d.getWarrantyMonths() != null ? d.getWarrantyMonths()
                    : (product.getWarrantyMonths() != null ? product.getWarrantyMonths() : 0);
            java.time.LocalDate saleLocalDate = parent.getSaleDate() != null
                    ? parent.getSaleDate().toLocalDate() : java.time.LocalDate.now();
            BigDecimal lineDiscount = d.getDiscountAmount() != null ? d.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal customVoucherPrice = d.getCustomVoucherPrice();
            boolean isFoc = Boolean.TRUE.equals(d.getFoc());
            if (lineDiscount.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Line discount cannot be negative");
            }
            if (customVoucherPrice != null && customVoucherPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Custom voucher price cannot be negative");
            }

            List<String> serials = d.getSerialNumbers() == null ? List.of() : d.getSerialNumbers();

            if (!serials.isEmpty()) {
                // serial-mode: one row per serial, qty forced to 1 each
                if (d.getQty() == null || d.getQty() <= 0) {
                    throw new RuntimeException("Quantity must be greater than zero");
                }
                if (!serials.isEmpty() && d.getQty().intValue() != serials.size()) {
                    throw new RuntimeException("Serial count must match qty for product: " + product.getName());
                }
                BigDecimal perSerialDiscount = serials.size() > 0
                        ? lineDiscount.divide(BigDecimal.valueOf(serials.size()), 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                if (!isFoc && perSerialDiscount.compareTo(d.getUnitPrice()) > 0) {
                    throw new RuntimeException("Line discount cannot exceed line amount for product: " + product.getName());
                }

                for (String sn : serials) {
                    ProductSerial serial = serialRepository.findBySerialNumber(sn)
                            .orElseThrow(() -> new RuntimeException("Serial number '" + sn + "' not found"));
                    if (!serial.getProduct().getId().equals(product.getId())) {
                        throw new RuntimeException("Serial number '" + sn + "' does not belong to product: " + product.getName());
                    }
                    if (serial.getStatus() != SerialStatus.Available) {
                        throw new RuntimeException("Serial number '" + sn + "' is not available for sale");
                    }
                    int serialWarrantyMonths = serial.getWarrantyMonths() != null
                            ? serial.getWarrantyMonths()
                            : requestedWarrantyMonths;
                    java.time.LocalDate serialWarrantyExpiry = serial.getWarrantyEndDate() != null
                            ? serial.getWarrantyEndDate()
                            : (serialWarrantyMonths > 0 ? saleLocalDate.plusMonths(serialWarrantyMonths) : null);
                    serial.setStatus(isServiceJobSale ? SerialStatus.Used_In_Service : SerialStatus.Sold);
                    serialRepository.save(serial);

                    BigDecimal gross = d.getUnitPrice(); // qty 1 per serial
                    BigDecimal subtotal = isFoc ? BigDecimal.ZERO : gross.subtract(perSerialDiscount);
                    if (subtotal.compareTo(BigDecimal.ZERO) < 0) subtotal = BigDecimal.ZERO;
                    SaleDetail detail = SaleDetail.builder()
                            .sale(parent)
                            .product(product)
                            .qty(1)
                            .unitPrice(d.getUnitPrice())
                            .customVoucherPrice(customVoucherPrice)
                            .customerMargin(BigDecimal.ZERO)
                            .subtotal(subtotal)
                            .serialNumber(sn)
                            .costPriceSnapshot(product.getCostPrice())
                            .discountAmount(perSerialDiscount)
                            .foc(isFoc)
                            .warrantyMonths(serialWarrantyMonths)
                            .warrantyExpiryDate(serialWarrantyExpiry)
                            .build();
                    detailEntities.add(detail);
                }
            } else {
                if (Boolean.TRUE.equals(product.getHasSerial())) {
                    throw new RuntimeException("Serial numbers are required for product: " + product.getName());
                }
                // non-serial mode: single row
                if (d.getQty() == null || d.getQty() <= 0) {
                    throw new RuntimeException("Quantity must be greater than zero");
                }
                int currentQty = product.getStockQty() != null ? product.getStockQty() : 0;
                int availableQty = currentQty - (product.getQuarantinedQty() == null ? 0 : product.getQuarantinedQty());
                if (availableQty < d.getQty()) {
                    throw new RuntimeException("Insufficient stock for: " + product.getName()
                            + ". Available: " + availableQty);
                }
                product.setStockQty(currentQty - d.getQty());
                productRepository.save(product);
                BigDecimal gross = d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQty()));
                if (!isFoc && lineDiscount.compareTo(gross) > 0) {
                    throw new RuntimeException("Line discount cannot exceed line amount for product: " + product.getName());
                }
                BigDecimal subtotal = isFoc ? BigDecimal.ZERO : gross.subtract(lineDiscount);
                if (subtotal.compareTo(BigDecimal.ZERO) < 0) subtotal = BigDecimal.ZERO;
                java.time.LocalDate warrantyExpiry = requestedWarrantyMonths > 0
                        ? saleLocalDate.plusMonths(requestedWarrantyMonths)
                        : null;

                SaleDetail detail = SaleDetail.builder()
                        .sale(parent)
                        .product(product)
                        .qty(d.getQty())
                        .unitPrice(d.getUnitPrice())
                        .customVoucherPrice(customVoucherPrice)
                        .customerMargin(BigDecimal.ZERO)
                        .subtotal(subtotal)
                        .serialNumber(null)
                        .costPriceSnapshot(product.getCostPrice())
                        .discountAmount(lineDiscount)
                        .foc(isFoc)
                        .warrantyMonths(requestedWarrantyMonths)
                        .warrantyExpiryDate(warrantyExpiry)
                        .build();
                detailEntities.add(detail);
            }
        }
        return detailEntities;
    }

    private BigDecimal calculateTotal(List<SaleDetail> details) {
        return details.stream()
                .map(SaleDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateDiscountLimit(BigDecimal total, BigDecimal discount) {
        BigDecimal safeDiscount = discount != null ? discount : BigDecimal.ZERO;
        if (safeDiscount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Discount cannot be negative");
        }
        if (safeDiscount.compareTo(total) > 0) {
            throw new RuntimeException("Discount cannot exceed sale total");
        }
        if (safeDiscount.signum() == 0 || hasCurrentAuthority("CAN_ACCESS_SALE_DISCOUNT_OVERRIDE")
                || hasCurrentAuthority("ROLE_ADMINISTRATOR")) return;

        BigDecimal limit = hasCurrentAuthority("ROLE_MANAGER") || hasCurrentAuthority("ROLE_ADMIN")
                ? MANAGER_DISCOUNT_PERCENT : CASHIER_DISCOUNT_PERCENT;
        BigDecimal percent = total.signum() == 0 ? BigDecimal.ZERO
                : safeDiscount.multiply(new BigDecimal("100")).divide(total, 4, java.math.RoundingMode.HALF_UP);
        if (percent.compareTo(limit) > 0) {
            throw new AccessDeniedException("Discount exceeds role limit of " + limit.stripTrailingZeros().toPlainString() + "%");
        }
    }

    private void reverseStock(Sale sale) {
        if (sale.getDetails() == null) return;
        for (SaleDetail detail : sale.getDetails()) {
            Product product = detail.getProduct();
            int qty = detail.getQty() != null ? detail.getQty() : 0;
            if (detail.getSerialNumber() != null && !detail.getSerialNumber().isBlank()) {
                serialRepository.findBySerialNumber(detail.getSerialNumber()).ifPresent(serial -> {
                    serial.setStatus(SerialStatus.Available);
                    serialRepository.save(serial);
                });
            } else {
                product.setStockQty((product.getStockQty() != null ? product.getStockQty() : 0) + qty);
                productRepository.save(product);
            }
            stockMovementService.recordMovement(StockMovement.builder()
                    .product(product).movementType(MovementType.RETURN).qty(qty)
                    .referenceType("SaleVoid").referenceId(sale.getId()).build());
        }
    }

    private void recordVoidedCashRefund(Sale sale) {
        BigDecimal cashPaid = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(sale.getId(), ReferenceType.Sale).stream()
                .filter(tx -> tx.getPaymentMethod() != null && tx.getPaymentMethod().getAccount() != null)
                .filter(tx -> tx.getPaymentMethod().getAccount().getId().equals(accountResolver.cash().getId()))
                .map(PaymentTransaction::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cashDrawerService.recordCashRefund(cashPaid);
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private void applyTotals(Sale sale, List<SaleDetail> details, BigDecimal discount, BigDecimal paid, LocalDate dueDate) {
        BigDecimal total = details.stream()
                .map(SaleDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal safeDiscount = discount != null ? discount : BigDecimal.ZERO;
        if (safeDiscount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Discount cannot be negative");
        }
        BigDecimal tax = sale.getTaxAmount() != null ? sale.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal net = total.subtract(safeDiscount).add(tax);
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            net = BigDecimal.ZERO;
        }

        BigDecimal incomingPaid = paid != null ? paid : BigDecimal.ZERO;
        BigDecimal safePaid = incomingPaid.min(net); // prevent overpayment beyond net
        BigDecimal due = net.subtract(safePaid);

        if (due.compareTo(BigDecimal.ZERO) > 0 && dueDate == null) {
            throw new RuntimeException("Due date is required for credit/partial sale.");
        }

        sale.setTotalAmount(total);
        sale.setDiscountAmount(safeDiscount);
        sale.setNetAmount(net);
        sale.setPaidAmount(safePaid);
        sale.setDueAmount(due);
        sale.setPaymentStatus(calculateStatus(net, safePaid));
        sale.setDueDate(due.compareTo(BigDecimal.ZERO) > 0 ? dueDate : null);
        sale.setCreditStatus(calculateCreditStatus(due, sale.getDueDate()));

        // Allocate any sale-level discount across lines before saving the voucher-only margin.
        // Neither this value nor customVoucherPrice changes subtotal, netAmount, payments, or profit.
        BigDecimal effectiveDiscount = safeDiscount.min(total);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < details.size(); index++) {
            SaleDetail detail = details.get(index);
            BigDecimal lineSubtotal = detail.getSubtotal() != null ? detail.getSubtotal() : BigDecimal.ZERO;
            BigDecimal lineDiscount = index == details.size() - 1
                    ? effectiveDiscount.subtract(allocated)
                    : (total.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : effectiveDiscount.multiply(lineSubtotal).divide(total, 2, java.math.RoundingMode.HALF_UP));
            allocated = allocated.add(lineDiscount);
            if (detail.getCustomVoucherPrice() == null) {
                detail.setCustomerMargin(BigDecimal.ZERO);
                continue;
            }
            BigDecimal voucherAmount = detail.getCustomVoucherPrice()
                    .multiply(BigDecimal.valueOf(detail.getQty() != null ? detail.getQty() : 0));
            detail.setCustomerMargin(voucherAmount.subtract(lineSubtotal.subtract(lineDiscount)));
        }
    }

    private PaymentStatus calculateStatus(BigDecimal net, BigDecimal paid) {
        if (paid == null || paid.compareTo(BigDecimal.ZERO) == 0) {
            return PaymentStatus.Pending;
        }
        int cmp = paid.compareTo(net);
        if (cmp >= 0) {
            return PaymentStatus.Paid;
        }
        return PaymentStatus.Partial;
    }

    private CreditStatus calculateCreditStatus(BigDecimal due, LocalDate dueDate) {
        if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
            return dueDate == null ? CreditStatus.Not_Credit : CreditStatus.Paid;
        }
        if (dueDate == null) {
            throw new RuntimeException("Due date is required when there is outstanding amount.");
        }
        LocalDate today = LocalDate.now();
        if (dueDate.isBefore(today)) {
            return CreditStatus.Overdue;
        }
        return CreditStatus.Active;
    }

    private String generateSaleCode(Integer id) {
        var cfg = companySettingsService.getSettings();
        String prefix = cfg.getSalePrefix() != null && !cfg.getSalePrefix().isBlank() ? cfg.getSalePrefix() : "INV";
        int digits = cfg.getSaleDigits() != null ? cfg.getSaleDigits() : 5;
        return String.format("%s-%0" + digits + "d", prefix, id);
    }

    private String generateTransactionNo() {
        Long count = paymentTransactionRepository.count();
        return String.format("TXN-%06d", count + 1);
    }

    private SaleDTO toSaleDtoForPayment(SalePaymentDTO payDto, BigDecimal appliedAmount) {
        SaleDTO dto = new SaleDTO();
        dto.setPaidAmount(appliedAmount);
        dto.setPaymentMethodId(payDto.getPaymentMethodId());
        dto.setPaymentAccountId(payDto.getPaymentAccountId());
        dto.setTransactionNo(payDto.getTransactionNo());
        dto.setArAccountId(payDto.getArAccountId());
        dto.setPayments(scalePaymentsToApplied(payDto.getPayments(), appliedAmount));
        return dto;
    }

    private SaleDTO dtoWithPaid(Sale sale, SaleDTO source, BigDecimal paidDiff) {
        SaleDTO dto = new SaleDTO();
        dto.setPaidAmount(paidDiff);
        dto.setPaymentMethodId(source.getPaymentMethodId());
        dto.setPaymentAccountId(source.getPaymentAccountId());
        dto.setTransactionNo(source.getTransactionNo());
        dto.setArAccountId(source.getArAccountId());
        dto.setPayments(scalePaymentsToApplied(source.getPayments(), paidDiff));
        dto.setStaffId(source.getStaffId() != null ? source.getStaffId()
                : (sale.getStaff() != null ? sale.getStaff().getId() : null));
        dto.setRemark(source.getRemark());
        return dto;
    }

    private void recordStockMovements(Sale sale) {
        if (sale.getDetails() == null) return;
        for (SaleDetail detail : sale.getDetails()) {
            stockMovementService.recordMovement(StockMovement.builder()
                    .product(detail.getProduct())
                    .movementType(MovementType.OUT)
                    .qty(detail.getQty())
                    .referenceType("Sale")
                    .referenceId(sale.getId())
                    .build());
        }
    }

    private void createInventoryValuationJournal(Sale sale) {
        if (sale.getDetails() == null || sale.getDetails().isEmpty()) return;
        BigDecimal totalCost = sale.getDetails().stream()
                .map(detail -> (detail.getCostPriceSnapshot() != null ? detail.getCostPriceSnapshot() : BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(detail.getQty() != null ? detail.getQty() : 0)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.compareTo(BigDecimal.ZERO) <= 0) return;

        JournalDetailDTO drCogs = new JournalDetailDTO();
        drCogs.setAccountId(accountResolver.cogs().getId());
        drCogs.setDebit(totalCost);
        drCogs.setCredit(BigDecimal.ZERO);
        JournalDetailDTO crInventory = new JournalDetailDTO();
        crInventory.setAccountId(accountResolver.inventory().getId());
        crInventory.setDebit(BigDecimal.ZERO);
        crInventory.setCredit(totalCost);

        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(sale.getSaleCode() + "-COGS");
        entry.setEntryDate(LocalDateTime.now());
        entry.setDescription("Inventory cost recognition - " + sale.getSaleCode());
        entry.setStaffId(sale.getStaff() != null ? sale.getStaff().getId() : null);
        entry.setDetails(List.of(drCogs, crInventory));
        journalWriter.write(entry);
    }

    private void createSaleJournal(Sale sale, Integer paymentAccountId, Integer arAccountId, Integer paymentMethodId, List<PaymentTransactionDTO> payments) {
        if (sale.getNetAmount() == null) return;
        BigDecimal net = sale.getNetAmount();
        BigDecimal paid = sale.getPaidAmount() != null ? sale.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal due = sale.getDueAmount() != null ? sale.getDueAmount() : BigDecimal.ZERO;

        if (paid.compareTo(BigDecimal.ZERO) <= 0 && due.compareTo(BigDecimal.ZERO) <= 0) {
            return; // nothing to post
        }

        List<JournalDetailDTO> details = new ArrayList<>();

        List<PaymentLine> paymentLines = resolvePaymentLines(payments, paid, paymentMethodId, paymentAccountId);
        if (!paymentLines.isEmpty()) {
            for (PaymentLine line : paymentLines) {
                JournalDetailDTO drCash = new JournalDetailDTO();
                drCash.setAccountId(line.method().getAccount().getId());
                drCash.setDebit(line.amount());
                drCash.setCredit(BigDecimal.ZERO);
                details.add(drCash);
            }
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            Integer cashOrBankAccount = resolveCashAccount(paymentMethodId, paymentAccountId);
            JournalDetailDTO drCash = new JournalDetailDTO();
            drCash.setAccountId(cashOrBankAccount);
            drCash.setDebit(paid);
            drCash.setCredit(BigDecimal.ZERO);
            details.add(drCash);
        }

        if (due.compareTo(BigDecimal.ZERO) > 0) {
            if (arAccountId == null) {
                arAccountId = accountResolver.receivable().getId(); // default AR
            }
            JournalDetailDTO drAR = new JournalDetailDTO();
            drAR.setAccountId(arAccountId);
            drAR.setDebit(due);
            drAR.setCredit(BigDecimal.ZERO);
            details.add(drAR);
        }

        JournalDetailDTO crSales = new JournalDetailDTO();
        crSales.setAccountId(accountResolver.sales().getId()); // Product Sale income
        crSales.setDebit(BigDecimal.ZERO);
        BigDecimal tax = sale.getTaxAmount() != null ? sale.getTaxAmount() : BigDecimal.ZERO;
        crSales.setCredit(net.subtract(tax));
        details.add(crSales);
        if (tax.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO crTax = new JournalDetailDTO();
            crTax.setAccountId(accountResolver.taxPayable().getId());
            crTax.setDebit(BigDecimal.ZERO);
            crTax.setCredit(tax);
            details.add(crTax);
        }

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(sale.getSaleCode());
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Product Sale - " + sale.getSaleCode());
        journalDTO.setStaffId(sale.getStaff() != null ? sale.getStaff().getId() : null);
        journalDTO.setDetails(details);

        journalWriter.write(journalDTO);
    }

    private void createSaleJournalForPayment(Sale sale, BigDecimal appliedPaid, Integer paymentAccountId, Integer arAccountId, Integer paymentMethodId, List<PaymentTransactionDTO> payments) {
        if (appliedPaid.compareTo(BigDecimal.ZERO) <= 0) return;
        Integer arId = arAccountId != null ? arAccountId
                : accountResolver.receivable().getId();

        List<JournalDetailDTO> details = new ArrayList<>();

        List<PaymentLine> paymentLines = resolvePaymentLines(payments, appliedPaid, paymentMethodId, paymentAccountId);
        if (!paymentLines.isEmpty()) {
            for (PaymentLine line : paymentLines) {
                JournalDetailDTO drCash = new JournalDetailDTO();
                drCash.setAccountId(line.method().getAccount().getId());
                drCash.setDebit(line.amount());
                drCash.setCredit(BigDecimal.ZERO);
                details.add(drCash);
            }
        } else {
            Integer cashOrBankAccount = resolveCashAccount(paymentMethodId, paymentAccountId);
            JournalDetailDTO drCash = new JournalDetailDTO();
            drCash.setAccountId(cashOrBankAccount);
            drCash.setDebit(appliedPaid);
            drCash.setCredit(BigDecimal.ZERO);
            details.add(drCash);
        }

        JournalDetailDTO crAR = new JournalDetailDTO();
        crAR.setAccountId(arId);
        crAR.setDebit(BigDecimal.ZERO);
        crAR.setCredit(appliedPaid);
        details.add(crAR);

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(sale.getSaleCode() + "-PAY");
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("AR collection for sale " + sale.getSaleCode());
        journalDTO.setStaffId(sale.getStaff() != null ? sale.getStaff().getId() : null);
        journalDTO.setDetails(details);

        journalWriter.write(journalDTO);
    }

    private void createPaymentAdjustmentJournal(Sale sale, BigDecimal diffPaid, Integer paymentAccountId, Integer arAccountId, Integer paymentMethodId) {
        if (diffPaid == null || diffPaid.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal amount = diffPaid.abs();
        Integer cashOrBankAccount = resolveCashAccount(paymentMethodId, paymentAccountId);
        Integer arId = arAccountId != null ? arAccountId : accountResolver.receivable().getId();

        List<JournalDetailDTO> details = new ArrayList<>();
        if (diffPaid.compareTo(BigDecimal.ZERO) > 0) {
            // additional payment: DR Cash, CR AR
            JournalDetailDTO drCash = new JournalDetailDTO();
            drCash.setAccountId(cashOrBankAccount);
            drCash.setDebit(amount);
            drCash.setCredit(BigDecimal.ZERO);
            details.add(drCash);

            JournalDetailDTO crAR = new JournalDetailDTO();
            crAR.setAccountId(arId);
            crAR.setDebit(BigDecimal.ZERO);
            crAR.setCredit(amount);
            details.add(crAR);
        } else {
            // refund/rollback: DR AR, CR Cash
            JournalDetailDTO drAR = new JournalDetailDTO();
            drAR.setAccountId(arId);
            drAR.setDebit(amount);
            drAR.setCredit(BigDecimal.ZERO);
            details.add(drAR);

            JournalDetailDTO crCash = new JournalDetailDTO();
            crCash.setAccountId(cashOrBankAccount);
            crCash.setDebit(BigDecimal.ZERO);
            crCash.setCredit(amount);
            details.add(crCash);
        }

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(sale.getSaleCode() + "-ADJ");
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Payment adjustment for sale " + sale.getSaleCode());
        journalDTO.setStaffId(sale.getStaff() != null ? sale.getStaff().getId() : null);
        journalDTO.setDetails(details);
        journalWriter.write(journalDTO);
    }
    private void createPaymentTransaction(Sale sale, SaleDTO dto) {
        BigDecimal paid = dto.getPaidAmount() != null ? dto.getPaidAmount() : BigDecimal.ZERO;
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return;
        List<PaymentLine> lines = resolvePaymentLines(dto.getPayments(), paid, dto.getPaymentMethodId(), dto.getPaymentAccountId());
        if (!lines.isEmpty()) {
            for (PaymentLine line : lines) {
                PaymentTransaction paymentTx = new PaymentTransaction();
                paymentTx.setReferenceId(sale.getId());
                paymentTx.setReferenceType(ReferenceType.Sale);
                paymentTx.setPaymentMethod(line.method());
                paymentTx.setAmount(line.amount());
                paymentTx.setPaymentDate(LocalDateTime.now());
                paymentTx.setTransactionNo(line.transactionNo() == null || line.transactionNo().isBlank()
                        ? generateTransactionNo()
                        : line.transactionNo());
                paymentTransactionRepository.save(paymentTx);
                recordDrawerCashSale(line.method(), line.amount());
            }
            return;
        }

        PaymentMethod method = resolvePaymentMethod(dto);

        PaymentTransaction paymentTx = new PaymentTransaction();
        paymentTx.setReferenceId(sale.getId());
        paymentTx.setReferenceType(ReferenceType.Sale);
        paymentTx.setPaymentMethod(method);
        paymentTx.setAmount(paid);
        paymentTx.setPaymentDate(LocalDateTime.now());
        String txnNo = (dto.getTransactionNo() == null || dto.getTransactionNo().isEmpty())
                ? generateTransactionNo()
                : dto.getTransactionNo();
        paymentTx.setTransactionNo(txnNo);
        paymentTransactionRepository.save(paymentTx);
        recordDrawerCashSale(method, paid);
    }

    private void recordDrawerCashSale(PaymentMethod method, BigDecimal amount) {
        if (method != null && method.getAccount() != null
                && method.getAccount().getId().equals(accountResolver.cash().getId())) {
            cashDrawerService.recordCashSale(amount);
        }
    }

    private BigDecimal paymentTotal(List<PaymentTransactionDTO> payments, BigDecimal fallback) {
        if (payments == null || payments.isEmpty()) return fallback != null ? fallback : BigDecimal.ZERO;
        return payments.stream()
                .map(PaymentTransactionDTO::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PaymentLine> resolvePaymentLines(List<PaymentTransactionDTO> payments, BigDecimal expectedTotal,
                                                  Integer fallbackMethodId, Integer fallbackAccountId) {
        if (payments == null || payments.isEmpty()) return List.of();
        BigDecimal total = paymentTotal(payments, BigDecimal.ZERO);
        if (expectedTotal != null && total.compareTo(expectedTotal) != 0) {
            throw new RuntimeException("Split payment total must equal paid amount.");
        }
        List<PaymentLine> lines = new ArrayList<>();
        for (PaymentTransactionDTO payment : payments) {
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            Integer methodId = payment.getPaymentMethodId() != null ? payment.getPaymentMethodId() : fallbackMethodId;
            if (methodId == null) throw new RuntimeException("Payment Method is required for each split payment line.");
            PaymentMethod method = paymentMethodRepository.findById(methodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            ensureCashBankAccount(method.getAccount());
            lines.add(new PaymentLine(method, amount, payment.getTransactionNo()));
        }
        if (lines.isEmpty() && expectedTotal != null && expectedTotal.compareTo(BigDecimal.ZERO) > 0 && fallbackMethodId == null && fallbackAccountId == null) {
            throw new RuntimeException("Payment Method is required when paidAmount > 0");
        }
        return lines;
    }

    private List<PaymentTransactionDTO> scalePaymentsToApplied(List<PaymentTransactionDTO> payments, BigDecimal appliedAmount) {
        if (payments == null || payments.isEmpty()) return null;
        BigDecimal total = paymentTotal(payments, BigDecimal.ZERO);
        if (total.compareTo(BigDecimal.ZERO) <= 0) return payments;
        if (total.compareTo(appliedAmount) == 0) return payments;
        List<PaymentTransactionDTO> scaled = new ArrayList<>();
        BigDecimal remaining = appliedAmount;
        for (int i = 0; i < payments.size(); i++) {
            PaymentTransactionDTO source = payments.get(i);
            BigDecimal sourceAmount = source.getAmount() != null ? source.getAmount() : BigDecimal.ZERO;
            BigDecimal amount = i == payments.size() - 1
                    ? remaining
                    : sourceAmount.multiply(appliedAmount).divide(total, 2, java.math.RoundingMode.HALF_UP);
            remaining = remaining.subtract(amount);
            PaymentTransactionDTO target = new PaymentTransactionDTO();
            target.setPaymentMethodId(source.getPaymentMethodId());
            target.setPaymentMethodName(source.getPaymentMethodName());
            target.setAmount(amount);
            target.setTransactionNo(source.getTransactionNo());
            scaled.add(target);
        }
        return scaled;
    }

    private record PaymentLine(PaymentMethod method, BigDecimal amount, String transactionNo) {}

    private PaymentMethod resolvePaymentMethod(SaleDTO dto) {
        if (dto.getPaymentMethodId() != null) {
            PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            ensureCashBankAccount(method.getAccount());
            return method;
        }
        throw new RuntimeException("Payment Method is required when paidAmount > 0");
    }

    private Integer resolveCashAccount(Integer paymentMethodId, Integer paymentAccountId) {
        if (paymentMethodId != null) {
            PaymentMethod method = paymentMethodRepository.findById(paymentMethodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            ensureCashBankAccount(method.getAccount());
            return method.getAccount().getId();
        }
        if (paymentAccountId != null) {
            return paymentAccountId; // explicit Cash=5 or Bank=6
        }
        throw new RuntimeException("Payment account is required (cash=5 or bank=6)");
    }

    private void ensureCashBankAccount(org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount account) {
        if (account == null || account.getId() == null) {
            throw new RuntimeException("Payment Method does not have linked cash/bank account");
        }
        Integer id = account.getId();
        Integer cashId = accountResolver.cash().getId();
        Integer bankId = accountResolver.bankKbz().getId();
        Integer kpayId = accountResolver.kpay().getId();
        Integer waveId = accountResolver.wavePay().getId();
        if (!id.equals(cashId) && !id.equals(bankId) && !id.equals(kpayId) && !id.equals(waveId)) {
            throw new RuntimeException("Payment Method must link to a cash/bank equivalent account; found account id " + id);
        }
    }

    private void enforceCreditLimitWithOverride(Sale sale, BigDecimal newDue, Boolean managerOverride, Integer managerId, String overrideNote) {
        try {
            creditService.enforceCreditLimit(sale.getCustomer().getId(), newDue, sale);
        } catch (RuntimeException ex) {
            if (isLimitExceeded(ex) && Boolean.TRUE.equals(managerOverride)) {
                Staff manager = resolveStaff(managerId, sale.getStaff());
                CreditOverrideLog log = CreditOverrideLog.builder()
                        .sale(sale.getId() != null ? sale : null)
                        .customer(sale.getCustomer())
                        .staff(manager)
                        .note(overrideNote)
                        .reason(ex.getMessage())
                        .build();
                creditOverrideLogRepository.save(log);
                creditAlertService.createAlert(AlertType.Credit_Limit_Exceeded, sale.getCustomer(), sale);
                return;
            }
            throw ex;
        }
    }

    private boolean isLimitExceeded(RuntimeException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("credit limit exceeded");
    }

    private Staff resolveStaff(Integer managerId, Staff fallback) {
        if (managerId != null) {
            return staffRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found with id: " + managerId));
        }
        if (fallback != null) {
            return fallback;
        }
        throw new RuntimeException("Manager approval required");
    }

    private void checkLargeCreditAlert(Sale sale) {
        if (sale.getDueAmount() != null &&
                sale.getDueAmount().compareTo(largeCreditAlertThreshold) >= 0) {
            creditAlertService.createAlert(AlertType.Large_Credit_Sale, sale.getCustomer(), sale);
        }
    }

    private void recordCustomerPayment(Sale sale, SaleDTO dto, BigDecimal appliedPaid) {
        if (appliedPaid == null || appliedPaid.compareTo(BigDecimal.ZERO) <= 0) return;
        Integer methodId = dto.getPaymentMethodId();
        String txnNo = dto.getTransactionNo();
        if ((methodId == null || methodId == 0) && dto.getPayments() != null && !dto.getPayments().isEmpty()) {
            methodId = dto.getPayments().get(0).getPaymentMethodId();
            txnNo = dto.getPayments().get(0).getTransactionNo();
        }
        if (methodId == null) {
            throw new RuntimeException("Payment Method is required when recording received payment");
        }
        CustomerPaymentDTO paymentDTO = new CustomerPaymentDTO();
        paymentDTO.setCustomerId(sale.getCustomer().getId());
        paymentDTO.setSaleId(sale.getId());
        paymentDTO.setAmount(appliedPaid);
        paymentDTO.setPaymentMethodId(methodId);
        paymentDTO.setTransactionNo(txnNo);
        paymentDTO.setNote(dto.getRemark());
        Integer staffId = dto.getStaffId() != null ? dto.getStaffId()
                : (sale.getStaff() != null ? sale.getStaff().getId() : null);
        paymentDTO.setStaffId(staffId);
        paymentDTO.setPaymentDate(LocalDateTime.now());
        customerPaymentService.recordSalePayment(sale, paymentDTO);
    }

    private void recordCustomerPayment(Sale sale, SalePaymentDTO dto, BigDecimal appliedPaid) {
        if (appliedPaid == null || appliedPaid.compareTo(BigDecimal.ZERO) <= 0) return;
        Integer methodId = dto.getPaymentMethodId();
        String txnNo = dto.getTransactionNo();
        if ((methodId == null || methodId == 0) && dto.getPayments() != null && !dto.getPayments().isEmpty()) {
            methodId = dto.getPayments().get(0).getPaymentMethodId();
            txnNo = dto.getPayments().get(0).getTransactionNo();
        }
        if (methodId == null) {
            throw new RuntimeException("Payment Method is required when recording received payment");
        }
        CustomerPaymentDTO paymentDTO = new CustomerPaymentDTO();
        paymentDTO.setCustomerId(sale.getCustomer().getId());
        paymentDTO.setSaleId(sale.getId());
        paymentDTO.setAmount(appliedPaid);
        paymentDTO.setPaymentMethodId(methodId);
        paymentDTO.setTransactionNo(txnNo);
        paymentDTO.setNote(dto.getNote());
        Integer staffId = dto.getStaffId() != null ? dto.getStaffId()
                : (sale.getStaff() != null ? sale.getStaff().getId() : null);
        paymentDTO.setStaffId(staffId);
        paymentDTO.setPaymentDate(LocalDateTime.now());
        customerPaymentService.recordSalePayment(sale, paymentDTO);
    }
}
