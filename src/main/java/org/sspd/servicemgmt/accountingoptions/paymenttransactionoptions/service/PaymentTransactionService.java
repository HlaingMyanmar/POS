package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.AccountTransferDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper.PaymentTransactionMapper;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentTransactionRepository repository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final JournalWriter journalWriter;

    private static final String TRANSACTION_TOPIC = "/topic/payment-transaction";

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public PaymentTransactionDTO save(PaymentTransactionDTO dto) {
        PaymentTransaction entity = mapper.toEntity(dto);

        if (dto.getPaymentMethodId() != null) {
            PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            entity.setPaymentMethod(method);
        }

        if (dto.getTransactionNo() == null || dto.getTransactionNo().trim().isEmpty()) {
            entity.setTransactionNo(generateTransactionNo());
        }

        if ("Purchase".equalsIgnoreCase(dto.getReferenceType())) {
            throw new RuntimeException("Use /api/v1/payment-transactions/pay-purchase-debt for purchase debt payment.");
        }

        PaymentTransaction savedEntity = repository.save(entity);
        messagingTemplate.convertAndSend(TRANSACTION_TOPIC, "TRANSACTION_CREATED");
        return mapper.toDto(savedEntity);
    }

    @Transactional
    public PaymentTransactionDTO saveInternalTransaction(PaymentTransactionDTO dto) {
        PaymentTransaction entity = mapper.toEntity(dto);

        PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
        entity.setPaymentMethod(method);

        if (dto.getTransactionNo() == null || dto.getTransactionNo().trim().isEmpty()) {
            entity.setTransactionNo(generateTransactionNo());
        }

        if (entity.getPaymentDate() == null) {
            entity.setPaymentDate(java.time.LocalDateTime.now());
        }

        PaymentTransaction savedEntity = repository.save(entity);

        messagingTemplate.convertAndSend("/topic/payment-transaction", "TRANSACTION_CREATED");
        return mapper.toDto(savedEntity);
    }

    private String generateTransactionNo() {
        Integer lastId = repository.findTopByOrderByIdDesc()
                .map(PaymentTransaction::getId)
                .orElse(0);
        return String.format("TXN-%06d", lastId + 1);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public PaymentTransactionDTO transfer(AccountTransferDTO dto) {
        if (dto.getFromPaymentMethodId() == null || dto.getToPaymentMethodId() == null) {
            throw new RuntimeException("From and To payment methods are required.");
        }
        if (dto.getFromPaymentMethodId().equals(dto.getToPaymentMethodId())) {
            throw new RuntimeException("From and To payment methods must be different.");
        }
        BigDecimal amount = dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Transfer amount must be greater than zero.");
        }

        PaymentMethod from = paymentMethodRepository.findById(dto.getFromPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("From Payment Method not found"));
        PaymentMethod to = paymentMethodRepository.findById(dto.getToPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("To Payment Method not found"));
        if (from.getAccount() == null || to.getAccount() == null) {
            throw new RuntimeException("Payment methods must have linked cash/bank accounts.");
        }

        String txNo = dto.getTransactionNo() == null || dto.getTransactionNo().isBlank()
                ? generateTransactionNo()
                : dto.getTransactionNo();

        PaymentTransaction out = new PaymentTransaction();
        out.setReferenceId(0);
        out.setReferenceType(ReferenceType.Transfer);
        out.setPaymentMethod(from);
        out.setAmount(amount.negate());
        out.setPaymentDate(LocalDateTime.now());
        out.setTransactionNo(txNo + "-OUT");
        repository.save(out);

        PaymentTransaction in = new PaymentTransaction();
        in.setReferenceId(0);
        in.setReferenceType(ReferenceType.Transfer);
        in.setPaymentMethod(to);
        in.setAmount(amount);
        in.setPaymentDate(LocalDateTime.now());
        in.setTransactionNo(txNo + "-IN");
        PaymentTransaction saved = repository.save(in);

        JournalDetailDTO drTo = new JournalDetailDTO();
        drTo.setAccountId(to.getAccount().getId());
        drTo.setDebit(amount);
        drTo.setCredit(BigDecimal.ZERO);

        JournalDetailDTO crFrom = new JournalDetailDTO();
        crFrom.setAccountId(from.getAccount().getId());
        crFrom.setDebit(BigDecimal.ZERO);
        crFrom.setCredit(amount);

        JournalEntryDTO journal = new JournalEntryDTO();
        journal.setReferenceNo(txNo);
        journal.setEntryDate(LocalDateTime.now());
        journal.setDescription(dto.getDescription() != null && !dto.getDescription().isBlank()
                ? dto.getDescription()
                : "Money transfer: " + from.getMethodName() + " to " + to.getMethodName());
        journal.setStaffId(dto.getStaffId());
        journal.setDetails(List.of(drTo, crFrom));
        journalWriter.write(journal);

        messagingTemplate.convertAndSend(TRANSACTION_TOPIC, "TRANSFER_CREATED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @Transactional(readOnly = true)
    public List<PaymentTransactionDTO> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionDTO> findAllWithDetails() {
        List<PaymentTransaction> transactions = repository.findAll();

        List<Integer> purchaseIds = transactions.stream()
                .filter(tx -> ReferenceType.Purchase.equals(tx.getReferenceType()))
                .map(PaymentTransaction::getReferenceId)
                .distinct().toList();

        List<Integer> supplierIds = transactions.stream()
                .filter(tx -> ReferenceType.Debt_Payment.equals(tx.getReferenceType()))
                .map(PaymentTransaction::getReferenceId)
                .distinct().toList();

        List<Integer> saleIds = transactions.stream()
                .filter(tx -> ReferenceType.Sale.equals(tx.getReferenceType()))
                .map(PaymentTransaction::getReferenceId)
                .distinct().toList();

        List<Integer> saleReturnIds = transactions.stream()
                .filter(tx -> ReferenceType.Sale_Return.equals(tx.getReferenceType()))
                .map(PaymentTransaction::getReferenceId)
                .distinct().toList();

        List<Integer> serviceIds = transactions.stream()
                .filter(tx -> ReferenceType.Service.equals(tx.getReferenceType()))
                .map(PaymentTransaction::getReferenceId)
                .distinct().toList();

        var purchaseMap = purchaseRepository.findAllById(purchaseIds).stream()
                .collect(java.util.stream.Collectors.toMap(Purchase::getId, p -> p));

        var supplierMap = supplierRepository.findAllById(supplierIds).stream()
                .collect(java.util.stream.Collectors.toMap(Supplier::getId, s -> s));

        var saleMap = saleRepository.findAllById(saleIds).stream()
                .collect(java.util.stream.Collectors.toMap(Sale::getId, s -> s));

        var saleReturnMap = saleReturnRepository.findAllById(saleReturnIds).stream()
                .collect(java.util.stream.Collectors.toMap(SaleReturn::getId, sr -> sr));

        var serviceMap = serviceJobRepository.findAllById(serviceIds).stream()
                .collect(java.util.stream.Collectors.toMap(ServiceJob::getId, sj -> sj));

        return transactions.stream().map(tx -> {
            PaymentTransactionDTO dto = mapper.toDto(tx);

            if (dto.getPaymentDate() == null && tx.getPaymentDate() != null) {
                dto.setPaymentDate(tx.getPaymentDate());
            }

            if (ReferenceType.Purchase.equals(tx.getReferenceType())) {
                Purchase p = purchaseMap.get(tx.getReferenceId());
                if (p != null) {
                    dto.setReferenceCode(p.getPurchaseCode());
                    if (p.getSupplier() != null) dto.setEntityName(p.getSupplier().getName());
                }
            } else if (ReferenceType.Debt_Payment.equals(tx.getReferenceType())) {
                Supplier s = supplierMap.get(tx.getReferenceId());
                if (s != null) {
                    dto.setReferenceCode(s.getCode());
                    dto.setEntityName(s.getName());
                }
            } else if (ReferenceType.Sale.equals(tx.getReferenceType())) {
                Sale s = saleMap.get(tx.getReferenceId());
                if (s != null) {
                    dto.setReferenceCode(s.getSaleCode());
                    if (s.getCustomer() != null) dto.setEntityName(s.getCustomer().getName());
                }
            } else if (ReferenceType.Sale_Return.equals(tx.getReferenceType())) {
                SaleReturn sr = saleReturnMap.get(tx.getReferenceId());
                if (sr != null) {
                    dto.setReferenceCode(sr.getReturnCode());
                    if (sr.getSale() != null && sr.getSale().getCustomer() != null)
                        dto.setEntityName(sr.getSale().getCustomer().getName());
                }
            } else if (ReferenceType.Service.equals(tx.getReferenceType())) {
                ServiceJob sj = serviceMap.get(tx.getReferenceId());
                if (sj != null) {
                    dto.setReferenceCode(sj.getJobNo());
                    if (sj.getCustomer() != null) dto.setEntityName(sj.getCustomer().getName());
                }
            }

            return dto;
        }).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @Transactional(readOnly = true)
    public List<PaymentTransactionDTO> findByReference(Integer refId, String type) {
        ReferenceType refType = ReferenceType.valueOf(type);
        return repository.findByReferenceIdAndReferenceType(refId, refType).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @Transactional(readOnly = true)
    public PaymentTransactionDTO findById(Integer id) {
        PaymentTransaction entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction Not Found with id " + id));
        return mapper.toDto(entity);
    }
}
