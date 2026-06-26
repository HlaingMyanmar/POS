package org.sspd.servicemgmt.accountingoptions.incomeoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.dto.IncomeDTO;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.mapper.IncomeMapper;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.model.Income;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.repository.IncomeRepository;
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
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final ChartOfAccountRepository coaRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final StaffRepository staffRepository;
    private final JournalWriter journalWriter;
    private final IncomeMapper mapper;

    @PreAuthorize("hasAuthority('CAN_ACCESS_INCOME_CREATE')")
    @Transactional
    public IncomeDTO save(IncomeDTO dto) {

        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero.");
        }

        ChartOfAccount account = coaRepository.findById(dto.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!"Income".equalsIgnoreCase(account.getAccountType().name())) {
            throw new RuntimeException("Account '" + account.getAccountName() + "' is not an Income account.");
        }
        if (isSystemOnlyAccount(account.getCode())) {
            throw new RuntimeException("Account '" + account.getCode() + "' is managed automatically.");
        }

        PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
        if (method.getAccount() == null) {
            throw new RuntimeException("Payment method has no linked account.");
        }

        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        Income entity = mapper.toEntity(dto);
        entity.setAccount(account);
        entity.setPaymentMethod(method);
        entity.setStaff(staff);
        entity.setIncomeDate(dto.getIncomeDate() != null ? dto.getIncomeDate() : LocalDateTime.now());
        entity.setIncomeCode("PENDING");

        Income saved = incomeRepository.save(entity);
        saved.setIncomeCode(generateIncomeCode(saved.getId()));
        saved = incomeRepository.save(saved);

        createIncomeJournal(saved, account, method, staff);

        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_INCOME_READ')")
    @Transactional(readOnly = true)
    public List<IncomeDTO> findAll() {
        return incomeRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_INCOME_READ')")
    @Transactional(readOnly = true)
    public IncomeDTO findById(Integer id) {
        return mapper.toDto(incomeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Income not found: " + id)));
    }

    private void createIncomeJournal(Income income, ChartOfAccount account,
                                     PaymentMethod method, Staff staff) {
        List<JournalDetailDTO> details = new ArrayList<>();

        JournalDetailDTO dr = new JournalDetailDTO();
        dr.setAccountId(method.getAccount().getId());
        dr.setDebit(income.getAmount());
        dr.setCredit(BigDecimal.ZERO);
        details.add(dr);

        JournalDetailDTO cr = new JournalDetailDTO();
        cr.setAccountId(account.getId());
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(income.getAmount());
        details.add(cr);

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(income.getIncomeCode());
        journalDTO.setEntryDate(income.getIncomeDate());
        journalDTO.setDescription(account.getAccountName() +
                (income.getDescription() != null ? " - " + income.getDescription() : ""));
        journalDTO.setStaffId(staff.getId());
        journalDTO.setDetails(details);

        journalWriter.write(journalDTO);
    }

    private String generateIncomeCode(Integer id) {
        return String.format("INC-%05d", id);
    }

    private boolean isSystemOnlyAccount(String code) {
        return List.of(
                "INC-002",  // Product Sales — sale module
                "INC-006",  // Inventory Gain — stock adjustment
                "INC-007",  // Purchase Returns — purchase return
                "INC-008"   // Inventory Over — stock adjustment
        ).contains(code);
    }
}
