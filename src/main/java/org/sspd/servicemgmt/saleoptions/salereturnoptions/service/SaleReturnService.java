package org.sspd.servicemgmt.saleoptions.salereturnoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.creditoptions.service.CreditAlertService;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.saleoptions.model.CreditStatus;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.saleoptions.saledetails.repository.SaleDetailRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.dto.SaleReturnDTO;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.mapper.SaleReturnMapper;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.saleoptions.salereturndetails.dto.SaleReturnDetailDTO;
import org.sspd.servicemgmt.saleoptions.salereturndetails.model.SaleReturnDetail;
import org.sspd.servicemgmt.saleoptions.salereturndetails.repository.SaleReturnDetailRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
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
import org.sspd.servicemgmt.api.PageResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class SaleReturnService {

    private final SaleReturnRepository saleReturnRepository;
    private final SaleReturnDetailRepository saleReturnDetailRepository;
    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final StaffRepository staffRepository;
    private final ProductRepository productRepository;
    private final ProductSerialRepository productSerialRepository;
    private final StockMovementService stockMovementService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final JournalWriter journalWriter;
    private final SaleReturnMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final CreditAlertService creditAlertService;
    private final AccountResolver accountResolver;
    private final PaymentBalanceValidator paymentBalanceValidator;
    private final CashDrawerService cashDrawerService;

    private static final String SALE_RETURN_TOPIC = "/topic/sale-return";

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_CREATE')")
    @Transactional
    public SaleReturnDTO save(SaleReturnDTO dto) {
        if (dto.getDetails() == null || dto.getDetails().isEmpty()) {
            throw new RuntimeException("Sale return details are required");
        }
        if (dto.getSaleId() == null) {
            throw new RuntimeException("Sale reference is required for sale return");
        }

        Sale sale = saleRepository.findById(dto.getSaleId())
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));

        List<SaleReturn> existingReturns = saleReturnRepository.findAllBySaleIdAndDeletedFalse(sale.getId());

        Map<Integer, Integer> soldQtyByProduct = buildSoldQtyMap(sale.getId());
        Map<String, Integer> saleSerials = buildSaleSerialMap(sale.getId());
        Map<Integer, Integer> returnedQtyByProduct = buildReturnedQtyMap(existingReturns);
        Set<String> returnedSerials = buildReturnedSerials(existingReturns);

        // Qty-based full-return guard: block only when every sold item has already been returned
        boolean anyReturnable = soldQtyByProduct.entrySet().stream()
                .anyMatch(e -> returnedQtyByProduct.getOrDefault(e.getKey(), 0) < e.getValue());
        boolean anySerialsReturnable = saleSerials.keySet().stream()
                .anyMatch(sn -> !returnedSerials.contains(sn));
        if (!anyReturnable && !anySerialsReturnable && !soldQtyByProduct.isEmpty()) {
            throw new RuntimeException("Sale " + sale.getSaleCode() + " မှာ ပြန်လာနိုင်သော ပစ္စည်းအားလုံး return ပြန်ပြီးသားဖြစ်သည်");
        }

        SaleReturn entity = mapper.toEntity(dto);
        entity.setSale(sale);
        entity.setReturnCode("PENDING");
        if (entity.getReturnDate() == null) {
            entity.setReturnDate(LocalDateTime.now());
        }
        if (dto.getStaffId() != null) {
            Staff staff = staffRepository.findById(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
            entity.setStaff(staff);
        } else if (sale.getStaff() != null) {
            entity.setStaff(sale.getStaff());
        }

        BigDecimal total = BigDecimal.ZERO;
        List<SaleReturnDetail> detailEntities = new ArrayList<>();

        for (SaleReturnDetailDTO dDto : dto.getDetails()) {
            Product product = productRepository.findById(dDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            List<String> serials = dDto.getSerialNumbers() == null ? List.of() : dDto.getSerialNumbers();
            int qty = dDto.getQty() != null ? dDto.getQty() : 0;

            if (!serials.isEmpty()) {
                if (qty != serials.size()) {
                    throw new RuntimeException("Serial count must match qty for product: " + product.getName());
                }
                validateSerialsBelongToSale(serials, product, saleSerials);
                for (String sn : serials) {
                    if (returnedSerials.contains(sn)) {
                        throw new RuntimeException("Serial number '" + sn + "' was already returned");
                    }
                }
                for (String sn : serials) {
                    ProductSerial serial = productSerialRepository.findBySerialNumber(sn)
                            .orElseThrow(() -> new RuntimeException("Serial number '" + sn + "' not found"));
                    if (!serial.getProduct().getId().equals(product.getId())) {
                        throw new RuntimeException("Serial number '" + sn + "' does not belong to product: " + product.getName());
                    }
                    if (serial.getStatus() != SerialStatus.Sold) {
                        throw new RuntimeException("Serial number '" + sn + "' is not marked as Sold");
                    }
                    serial.setStatus(SerialStatus.Available);
                    productSerialRepository.save(serial);
                }
            } else {
                if (qty <= 0) {
                    throw new RuntimeException("Quantity must be greater than zero for product: " + product.getName());
                }
                int soldQty = soldQtyByProduct.getOrDefault(product.getId(), 0);
                int alreadyReturnedQty = returnedQtyByProduct.getOrDefault(product.getId(), 0);
                if (qty + alreadyReturnedQty > soldQty) {
                    throw new RuntimeException("Return quantity exceeds sold quantity for product: " + product.getName());
                }
                int currentStock = product.getStockQty() != null ? product.getStockQty() : 0;
                product.setStockQty(currentStock + qty);
                productRepository.save(product);
            }

            BigDecimal returnUnitPrice = discountedUnitPrice(sale, product.getId(), serials);
            if (returnUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Return unit price cannot be resolved for product: " + product.getName());
            }
            BigDecimal subtotal = returnUnitPrice.multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);

            SaleReturnDetail detail = SaleReturnDetail.builder()
                    .saleReturn(entity)
                    .product(product)
                    .qty(qty)
                    .unitPrice(returnUnitPrice)
                    .subtotal(subtotal)
                    .serialNumber(joinSerials(serials))
                    .build();
            detailEntities.add(detail);
        }

        entity.setDetails(detailEntities);
        BigDecimal returnedTax = calculateReturnedTax(sale, total, existingReturns);
        total = total.add(returnedTax);
        entity.setTotalReturnAmount(total);

        BigDecimal refund = paymentTotal(dto.getPayments(), dto.getRefundAmount() != null ? dto.getRefundAmount() : total);
        if (refund.compareTo(total) > 0) {
            throw new RuntimeException("Refund amount cannot exceed total return amount");
        }
        entity.setRefundAmount(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            if (dto.getPaymentMethodId() == null && (dto.getPayments() == null || dto.getPayments().isEmpty())) {
                throw new RuntimeException("Payment Method is required when refund amount is greater than zero");
            }
            Integer firstMethodId = dto.getPaymentMethodId() != null ? dto.getPaymentMethodId() : dto.getPayments().get(0).getPaymentMethodId();
            PaymentMethod method = paymentMethodRepository.findById(firstMethodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            for (PaymentLine line : resolvePaymentLines(dto.getPayments(), refund, method)) {
                paymentBalanceValidator.validateSufficientBalance(line.method(), line.amount());
            }
            entity.setPaymentMethod(method);
            String txnNo = (dto.getTransactionNo() == null || dto.getTransactionNo().isBlank())
                    ? generateTransactionNo()
                    : dto.getTransactionNo();
            entity.setTransactionNo(txnNo);
        }

        SaleReturn saved = saleReturnRepository.save(entity);
        saved.setReturnCode(generateReturnCode(saved.getId()));
        saved = saleReturnRepository.save(saved);

        applySaleAdjustments(sale, total, refund);
        recordStockMovements(detailEntities, saved.getId());
        createReturnJournal(saved, refund, dto.getPayments());
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            recordPaymentTransactions(saved, refund, dto.getPayments());
        }

        messagingTemplate.convertAndSend(SALE_RETURN_TOPIC, "SALE_RETURN_CREATED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_READ')")
    @Transactional(readOnly = true)
    public SaleReturnDTO findById(Integer id) {
        SaleReturn entity = saleReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale return not found with id: " + id));
        if (Boolean.TRUE.equals(entity.getDeleted())) {
            throw new ResourceNotFoundException("Sale return not found with id: " + id);
        }
        return mapper.toDto(entity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_READ')")
    @Transactional(readOnly = true)
    public PageResponse<SaleReturnDTO> findAll(String search, int page, int size) {
        return PageResponse.of(
                saleReturnRepository.findBySearch(search, PageRequest.of(page, size, Sort.by("id").descending()))
                        .map(mapper::toDto)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_READ')")
    @Transactional(readOnly = true)
    public List<SaleReturnDTO> findBySaleId(Integer saleId) {
        return saleReturnRepository.findAllBySaleIdAndDeletedFalse(saleId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_UPDATE')")
    @Transactional
    public SaleReturnDTO update(Integer id, SaleReturnDTO dto) {
        SaleReturn existing = saleReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale return not found with id: " + id));
        if (Boolean.TRUE.equals(existing.getDeleted())) {
            throw new ResourceNotFoundException("Sale return not found with id: " + id);
        }
        if (dto.getReturnDate() != null) {
            existing.setReturnDate(dto.getReturnDate());
        }
        if (dto.getReason() != null) {
            existing.setReason(dto.getReason());
        }

        SaleReturn saved = saleReturnRepository.save(existing);
        messagingTemplate.convertAndSend(SALE_RETURN_TOPIC, "SALE_RETURN_UPDATED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_DELETE')")
    @Transactional
    public void delete(Integer id) {
        SaleReturn existing = saleReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale return not found with id: " + id));
        if (Boolean.TRUE.equals(existing.getDeleted())) {
            throw new ResourceNotFoundException("Sale return not found with id: " + id);
        }

        List<SaleReturnDetail> details = saleReturnDetailRepository.findAllBySaleReturnIn(List.of(existing));

        // Reverse stock
        for (SaleReturnDetail detail : details) {
            Product product = detail.getProduct();
            List<String> serials = detail.getSerialNumber() != null
                    ? Arrays.stream(detail.getSerialNumber().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                    : List.of();
            if (!serials.isEmpty()) {
                for (String sn : serials) {
                    productSerialRepository.findBySerialNumber(sn).ifPresent(serial -> {
                        serial.setStatus(SerialStatus.Sold);
                        productSerialRepository.save(serial);
                    });
                }
            } else {
                int qty = detail.getQty() != null ? detail.getQty() : 0;
                int current = product.getStockQty() != null ? product.getStockQty() : 0;
                product.setStockQty(Math.max(0, current - qty));
                productRepository.save(product);
                stockMovementService.recordMovement(StockMovement.builder()
                        .product(product)
                        .movementType(MovementType.OUT)
                        .qty(qty)
                        .referenceType("SaleReturnVoid")
                        .referenceId(existing.getId())
                        .build());
            }
        }

        // Reverse sale amounts
        reverseSaleAdjustments(existing.getSale(), existing.getTotalReturnAmount(), existing.getRefundAmount());

        // Reverse journal entries and account balances
        journalWriter.reverseByReferenceNo(existing.getReturnCode());

        // Remove payment transactions (account balance already reversed via journal)
        paymentTransactionRepository.findByReferenceIdAndReferenceType(existing.getId(), ReferenceType.Sale_Return)
                .forEach(paymentTransactionRepository::delete);

        existing.setDeleted(Boolean.TRUE);
        saleReturnRepository.save(existing);
        messagingTemplate.convertAndSend(SALE_RETURN_TOPIC, "SALE_RETURN_DELETED");
    }

    private void reverseSaleAdjustments(Sale sale, BigDecimal returnAmount, BigDecimal refundAmount) {
        BigDecimal total  = returnAmount  != null ? returnAmount  : BigDecimal.ZERO;
        BigDecimal refund = refundAmount  != null ? refundAmount  : BigDecimal.ZERO;

        BigDecimal newTotal = (sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO).add(total);
        BigDecimal newNet   = (sale.getNetAmount()   != null ? sale.getNetAmount()   : BigDecimal.ZERO).add(total);

        boolean isCreditSale = sale.getDueDate() != null || sale.getCreditStatus() != CreditStatus.Not_Credit;
        BigDecimal newPaid;
        BigDecimal newDue;

        if (isCreditSale) {
            newPaid = sale.getPaidAmount() != null ? sale.getPaidAmount() : BigDecimal.ZERO;
            newDue  = (sale.getDueAmount() != null ? sale.getDueAmount() : BigDecimal.ZERO).add(total);
        } else {
            newPaid = (sale.getPaidAmount() != null ? sale.getPaidAmount() : BigDecimal.ZERO).add(refund);
            newDue  = newNet.subtract(newPaid);
            if (newDue.compareTo(BigDecimal.ZERO) < 0) newDue = BigDecimal.ZERO;
        }

        sale.setTotalAmount(newTotal);
        sale.setNetAmount(newNet);
        sale.setPaidAmount(newPaid);
        sale.setDueAmount(newDue);
        sale.setPaymentStatus(calculateStatus(newNet, newPaid));
        sale.setCreditStatus(calculateCreditStatus(newDue, sale.getDueDate()));
        saleRepository.save(sale);
        creditAlertService.evaluateDueAlerts(sale);
    }

    private void applySaleAdjustments(Sale sale, BigDecimal returnAmount, BigDecimal refundAmount) {
        BigDecimal oldTotal = sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal oldNet = sale.getNetAmount() != null ? sale.getNetAmount() : BigDecimal.ZERO;
        BigDecimal oldPaid = sale.getPaidAmount() != null ? sale.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal oldDue = sale.getDueAmount() != null ? sale.getDueAmount() : BigDecimal.ZERO;

        BigDecimal newTotal = oldTotal.subtract(returnAmount);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) newTotal = BigDecimal.ZERO;

        BigDecimal newNet = oldNet.subtract(returnAmount);
        if (newNet.compareTo(BigDecimal.ZERO) < 0) newNet = BigDecimal.ZERO;

        boolean isCreditSale = sale.getDueDate() != null || sale.getCreditStatus() != CreditStatus.Not_Credit;
        BigDecimal newPaid = oldPaid;
        BigDecimal newDue;

        if (isCreditSale) {
            // Credit sale: reduce outstanding due by the return amount, keep paid as-is.
            newDue = oldDue.subtract(returnAmount);
            if (newDue.compareTo(BigDecimal.ZERO) < 0) newDue = BigDecimal.ZERO;
        } else {
            // Cash/partial cash sale: reduce paid by the refund amount.
            newPaid = oldPaid.subtract(refundAmount);
            if (newPaid.compareTo(BigDecimal.ZERO) < 0) newPaid = BigDecimal.ZERO;
            if (newPaid.compareTo(newNet) > 0) newPaid = newNet;
            newDue = newNet.subtract(newPaid);
            if (newDue.compareTo(BigDecimal.ZERO) < 0) newDue = BigDecimal.ZERO;
        }

        sale.setTotalAmount(newTotal);
        sale.setNetAmount(newNet);
        sale.setPaidAmount(newPaid);
        sale.setDueAmount(newDue);
        if (newDue.compareTo(BigDecimal.ZERO) <= 0) {
            sale.setDueDate(null);
        }
        sale.setPaymentStatus(calculateStatus(newNet, newPaid));
        sale.setCreditStatus(calculateCreditStatus(newDue, sale.getDueDate()));
        saleRepository.save(sale);
        creditAlertService.evaluateDueAlerts(sale);
    }

    private void recordStockMovements(List<SaleReturnDetail> details, Integer returnId) {
        for (SaleReturnDetail detail : details) {
            stockMovementService.recordMovement(StockMovement.builder()
                    .product(detail.getProduct())
                    .movementType(MovementType.IN)
                    .qty(detail.getQty())
                    .referenceType("SaleReturn")
                    .referenceId(returnId)
                    .build());
        }
    }

    private void recordPaymentTransactions(SaleReturn saleReturn, BigDecimal refund, List<PaymentTransactionDTO> payments) {
        for (PaymentLine line : resolvePaymentLines(payments, refund, saleReturn.getPaymentMethod())) {
            PaymentTransaction paymentTx = new PaymentTransaction();
            paymentTx.setReferenceId(saleReturn.getId());
            paymentTx.setReferenceType(ReferenceType.Sale_Return);
            paymentTx.setPaymentMethod(line.method());
            paymentTx.setAmount(line.amount());
            paymentTx.setPaymentDate(LocalDateTime.now());
            paymentTx.setTransactionNo(line.transactionNo() != null && !line.transactionNo().isBlank()
                    ? line.transactionNo()
                    : saleReturn.getTransactionNo());
            paymentTransactionRepository.save(paymentTx);
            if (line.method().getAccount() != null
                    && line.method().getAccount().getId().equals(accountResolver.cash().getId())) {
                cashDrawerService.recordCashRefund(line.amount());
            }
        }
    }

    private void createReturnJournal(SaleReturn saleReturn, BigDecimal refund, List<PaymentTransactionDTO> payments) {
        BigDecimal total = saleReturn.getTotalReturnAmount() != null ? saleReturn.getTotalReturnAmount() : BigDecimal.ZERO;
        BigDecimal creditPortion = total.subtract(refund);
        BigDecimal preTaxReturn = saleReturn.getDetails() == null ? BigDecimal.ZERO : saleReturn.getDetails().stream()
                .map(d -> d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedTax = total.subtract(preTaxReturn).max(BigDecimal.ZERO);

        List<JournalDetailDTO> details = new ArrayList<>();

        JournalDetailDTO drSalesReturn = new JournalDetailDTO();
        drSalesReturn.setAccountId(accountResolver.salesRtn().getId());
        drSalesReturn.setDebit(preTaxReturn);
        drSalesReturn.setCredit(BigDecimal.ZERO);
        details.add(drSalesReturn);

        if (returnedTax.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO drTax = new JournalDetailDTO();
            drTax.setAccountId(accountResolver.taxPayable().getId());
            drTax.setDebit(returnedTax);
            drTax.setCredit(BigDecimal.ZERO);
            details.add(drTax);
        }

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            for (PaymentLine line : resolvePaymentLines(payments, refund, saleReturn.getPaymentMethod())) {
                JournalDetailDTO crCash = new JournalDetailDTO();
                crCash.setAccountId(line.method().getAccount().getId());
                crCash.setDebit(BigDecimal.ZERO);
                crCash.setCredit(line.amount());
                details.add(crCash);
            }
        }

        if (creditPortion.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO crAr = new JournalDetailDTO();
            crAr.setAccountId(accountResolver.receivable().getId());
            crAr.setDebit(BigDecimal.ZERO);
            crAr.setCredit(creditPortion);
            details.add(crAr);
        }

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(saleReturn.getReturnCode());
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Sale Return - " + saleReturn.getReturnCode());
        journalDTO.setStaffId(saleReturn.getStaff() != null ? saleReturn.getStaff().getId() : null);
        journalDTO.setDetails(details);

        journalWriter.write(journalDTO);
    }

    private BigDecimal calculateReturnedTax(Sale sale, BigDecimal preTaxReturn, List<SaleReturn> existingReturns) {
        BigDecimal saleTax = sale.getTaxAmount() != null ? sale.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal taxableBase = (sale.getNetAmount() != null ? sale.getNetAmount() : BigDecimal.ZERO).subtract(saleTax);
        if (saleTax.compareTo(BigDecimal.ZERO) <= 0 || taxableBase.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal alreadyReturnedTax = existingReturns.stream().map(existing -> {
            BigDecimal returnTotal = existing.getTotalReturnAmount() != null ? existing.getTotalReturnAmount() : BigDecimal.ZERO;
            BigDecimal returnBase = existing.getDetails() == null ? BigDecimal.ZERO : existing.getDetails().stream()
                    .map(d -> d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return returnTotal.subtract(returnBase).max(BigDecimal.ZERO);
        }).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal proportional = preTaxReturn.multiply(saleTax).divide(taxableBase, 2, RoundingMode.HALF_UP);
        return proportional.min(saleTax.subtract(alreadyReturnedTax).max(BigDecimal.ZERO));
    }

    private Integer resolveCashAccount(PaymentMethod method) {
        if (method == null || method.getAccount() == null || method.getAccount().getId() == null) {
            throw new RuntimeException("Payment Method must be linked to an account");
        }
        return method.getAccount().getId();
    }

    private Map<Integer, Integer> buildSoldQtyMap(Integer saleId) {
        List<SaleDetail> saleDetails = saleDetailRepository.findAllBySaleId(saleId);
        Map<Integer, Integer> map = new HashMap<>();
        for (SaleDetail d : saleDetails) {
            map.merge(d.getProduct().getId(), d.getQty() != null ? d.getQty() : 0, Integer::sum);
        }
        return map;
    }

    private BigDecimal discountedUnitPrice(Sale sale, Integer productId, List<String> serials) {
        List<SaleDetail> allSaleDetails = saleDetailRepository.findAllBySaleId(sale.getId());
        List<SaleDetail> saleDetails = allSaleDetails.stream()
                .filter(d -> d.getProduct() != null && productId.equals(d.getProduct().getId()))
                .toList();
        if (saleDetails.isEmpty()) return BigDecimal.ZERO;

        Set<String> requestedSerials = new HashSet<>();
        if (serials != null) {
            for (String serial : serials) {
                if (serial != null && !serial.isBlank()) requestedSerials.add(serial.trim().toUpperCase());
            }
        }

        List<SaleDetail> pricedLines = requestedSerials.isEmpty()
                ? saleDetails
                : saleDetails.stream()
                    .filter(d -> {
                        if (d.getSerialNumber() == null) return false;
                        return Arrays.stream(d.getSerialNumber().split(","))
                                .map(s -> s.trim().toUpperCase())
                                .anyMatch(requestedSerials::contains);
                    })
                    .toList();
        if (pricedLines.isEmpty()) pricedLines = saleDetails;

        int qty = pricedLines.stream().mapToInt(d -> d.getQty() != null ? d.getQty() : 0).sum();
        if (qty <= 0) return BigDecimal.ZERO;

        BigDecimal allLineNet = allSaleDetails.stream()
                .map(d -> d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lineNet = pricedLines.stream()
                .map(d -> d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overallDiscount = sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO;
        if (overallDiscount.compareTo(BigDecimal.ZERO) < 0) overallDiscount = BigDecimal.ZERO;

        BigDecimal allocatedOverallDiscount = BigDecimal.ZERO;
        if (allLineNet.compareTo(BigDecimal.ZERO) > 0 && overallDiscount.compareTo(BigDecimal.ZERO) > 0) {
            allocatedOverallDiscount = lineNet.multiply(overallDiscount)
                    .divide(allLineNet, 2, RoundingMode.HALF_UP);
        }

        BigDecimal returnableNet = lineNet.subtract(allocatedOverallDiscount);
        if (returnableNet.compareTo(BigDecimal.ZERO) < 0) returnableNet = BigDecimal.ZERO;
        return returnableNet.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Integer> buildSaleSerialMap(Integer saleId) {
        List<SaleDetail> saleDetails = saleDetailRepository.findAllBySaleId(saleId);
        Map<String, Integer> serials = new HashMap<>();
        for (SaleDetail d : saleDetails) {
            if (d.getSerialNumber() != null) {
                String[] tokens = d.getSerialNumber().split(",");
                for (String t : tokens) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) {
                        serials.put(trimmed, d.getProduct().getId());
                    }
                }
            }
        }
        return serials;
    }

    private Map<Integer, Integer> buildReturnedQtyMap(List<SaleReturn> returns) {
        if (returns == null || returns.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> map = new HashMap<>();
        List<SaleReturnDetail> details = saleReturnDetailRepository.findAllBySaleReturnIn(returns);
        for (SaleReturnDetail d : details) {
            map.merge(d.getProduct().getId(), d.getQty() != null ? d.getQty() : 0, Integer::sum);
        }
        return map;
    }

    private Set<String> buildReturnedSerials(List<SaleReturn> returns) {
        if (returns == null || returns.isEmpty()) {
            return Set.of();
        }
        Set<String> serials = new HashSet<>();
        List<SaleReturnDetail> details = saleReturnDetailRepository.findAllBySaleReturnIn(returns);
        for (SaleReturnDetail d : details) {
            if (d.getSerialNumber() != null) {
                String[] tokens = d.getSerialNumber().split(",");
                for (String t : tokens) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) {
                        serials.add(trimmed);
                    }
                }
            }
        }
        return serials;
    }

    private void validateSerialsBelongToSale(List<String> serials, Product product, Map<String, Integer> saleSerials) {
        for (String sn : serials) {
            Integer prodId = saleSerials.get(sn);
            if (prodId == null || !prodId.equals(product.getId())) {
                throw new RuntimeException("Serial number '" + sn + "' does not belong to this sale for product: " + product.getName());
            }
        }
    }

    private String joinSerials(List<String> serials) {
        if (serials == null || serials.isEmpty()) {
            return null;
        }
        return String.join(",", serials);
    }

    private String generateReturnCode(Integer id) {
        return String.format("SRN-%05d", id);
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
            throw new RuntimeException("Split payment total must equal refund amount.");
        }
        List<PaymentLine> lines = new ArrayList<>();
        for (PaymentTransactionDTO payment : payments) {
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            PaymentMethod method = paymentMethodRepository.findById(payment.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            if (method.getAccount() == null) throw new RuntimeException("Payment Method must have linked account.");
            lines.add(new PaymentLine(method, amount, payment.getTransactionNo()));
        }
        return lines;
    }

    private record PaymentLine(PaymentMethod method, BigDecimal amount, String transactionNo) {}

    private String generateTransactionNo() {
        Integer lastId = paymentTransactionRepository.findTopByOrderByIdDesc()
                .map(PaymentTransaction::getId)
                .orElse(0);
        return String.format("TXN-%06d", lastId + 1);
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
            return CreditStatus.Active;
        }
        LocalDate today = LocalDate.now();
        if (dueDate.isBefore(today)) {
            return CreditStatus.Overdue;
        }
        return CreditStatus.Active;
    }
}
