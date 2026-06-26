package org.sspd.servicemgmt.accountingoptions.expenseoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.dto.ExpenseDTO;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.mapper.ExpenseMapper;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.model.Expense;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.repository.ExpenseRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ChartOfAccountRepository coaRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final StaffRepository staffRepository;
    private final JournalWriter journalWriter;
    private final ExpenseMapper mapper;
    private final AccountResolver accounts;
    private final PaymentBalanceValidator paymentBalanceValidator;

    @PreAuthorize("hasAuthority('CAN_ACCESS_EXPENSE_CREATE')")
    @Transactional
    public ExpenseDTO save(ExpenseDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero.");
        }

        ChartOfAccount account = coaRepository.findById(dto.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getAccountType() != AccountType.Expense && account.getAccountType() != AccountType.Asset) {
            throw new RuntimeException("Account '" + account.getAccountName() + "' must be an Expense or Asset account.");
        }
        if (isSystemOnlyAccount(account.getCode())) {
            throw new RuntimeException("Account '" + account.getCode() + "' is managed automatically. Cannot use for manual expense.");
        }

        PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
        if (method.getAccount() == null) {
            throw new RuntimeException("Payment method has no linked account.");
        }

        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        validateSufficientBalance(method, dto.getAmount());

        Expense entity = mapper.toEntity(dto);
        entity.setAccount(account);
        entity.setPaymentMethod(method);
        entity.setStaff(staff);
        entity.setExpenseDate(dto.getExpenseDate() != null ? dto.getExpenseDate() : LocalDateTime.now());
        entity.setExpenseCode("PENDING");

        Expense saved = expenseRepository.save(entity);
        saved.setExpenseCode(generateExpenseCode(saved.getId()));
        saved = expenseRepository.save(saved);

        createExpenseJournal(saved, account, method, staff);

        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_EXPENSE_READ')")
    @Transactional(readOnly = true)
    public List<ExpenseDTO> findAll() {
        return expenseRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_EXPENSE_READ')")
    @Transactional(readOnly = true)
    public ExpenseDTO findById(Integer id) {
        return mapper.toDto(expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id)));
    }

    private void createExpenseJournal(Expense expense, ChartOfAccount account,
                                      PaymentMethod method, Staff staff) {
        List<JournalDetailDTO> details = new ArrayList<>();

        JournalDetailDTO dr = new JournalDetailDTO();
        dr.setAccountId(account.getId());
        dr.setDebit(expense.getAmount());
        dr.setCredit(BigDecimal.ZERO);
        details.add(dr);

        JournalDetailDTO cr = new JournalDetailDTO();
        cr.setAccountId(method.getAccount().getId());
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(expense.getAmount());
        details.add(cr);

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(expense.getExpenseCode());
        journalDTO.setEntryDate(expense.getExpenseDate());
        journalDTO.setDescription(account.getAccountName() +
                (expense.getDescription() != null ? " - " + expense.getDescription() : ""));
        journalDTO.setStaffId(staff.getId());
        journalDTO.setDetails(details);

        journalWriter.write(journalDTO);
    }

    private String generateExpenseCode(Integer id) {
        return String.format("EXP-%05d", id);
    }

    private boolean isSystemOnlyAccount(String code) {
        return List.of(
                "EXP-006", // COGS — auto
                "EXP-007", // Purchases — purchase module
                "EXP-010", // Sales Returns — sale return
                "EXP-011", // Inventory Loss — stock adjustment
                "EXP-012", // Inventory Short — stock adjustment
                "EXP-013"  // Opening Stock — period entry
        ).contains(code);
    }

    private void validateSufficientBalance(PaymentMethod method, BigDecimal amount) {
        paymentBalanceValidator.validateSufficientBalance(method, amount);
    }
}
