package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.dto.AccountBalanceDTO;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.mapper.AccountBalanceMapper;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository.AccountBalanceRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service.PaymentTransactionService;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountBalanceService {

    private final AccountBalanceRepository repository;
    private final AccountBalanceMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final JournalWriter journalWriter;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ChartOfAccountRepository coaRepository;
    private final AccountingPeriodGuard periodGuard;

    private static final String BALANCE_TOPIC = "/topic/account-balance";

    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @Transactional(readOnly = true)
    public List<AccountBalanceDTO> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @Transactional(readOnly = true)
    public AccountBalanceDTO findByAccountId(Integer accountId) {
        AccountBalance balance = repository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Balance record not found for Account ID: " + accountId));
        return mapper.toDto(balance);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @Transactional(readOnly = true)
    public AccountBalanceDTO findByAccountAndYear(Integer accountId, String fiscalYear) {
        AccountBalance balance = repository.findByAccountIdAndFiscalYear(accountId, fiscalYear)
                .orElseThrow(() -> new ResourceNotFoundException("Balance record not found for Account ID: " + accountId + " and Year: " + fiscalYear));
        return mapper.toDto(balance);
    }

    public void notifyBalanceUpdate() {
        messagingTemplate.convertAndSend(BALANCE_TOPIC, "BALANCE_UPDATED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_UPDATE')")
    @Transactional
    public AccountBalanceDTO setOpeningBalance(Integer accountId, BigDecimal amount, Integer staffId, Integer paymentMethodId) {
        LocalDateTime effectiveDate = LocalDateTime.now();
        periodGuard.assertOpen(effectiveDate, "set opening balance");

        // ၁။ Target account (Cash / KPay / etc.) ရှာမယ်
        ChartOfAccount targetAccount = coaRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        // ၂။ Counter-entry account: Share Capital (EQU-001) auto-ရှာမယ်
        ChartOfAccount equityAccount = coaRepository.findByCode(AccountCode.SHARE_CAPITAL)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Opening Balance Equity account (EQU-001 / Share Capital) not found. Please seed the Chart of Accounts."));

        // ၃။ ဟောင်းသည့် opening balance journal / payment row ရှိရင် reverse လုပ်မယ် (re-set case)
        String baseRefNo = "OPN-ACCT-" + accountId;
        journalWriter.reverseByReferenceNo(baseRefNo, "system", "Reset opening balance");
        journalWriter.reverseByReferencePrefix(baseRefNo + "-", "system", "Reset opening balance");
        reverseOpeningTransactions(baseRefNo);

        String openingRefNo = baseRefNo + "-" + Long.toUnsignedString(System.nanoTime(), 36);

        // ၄။ Double-entry direction: Asset/Expense → DR target, CR equity
        //                            Liability/Equity/Income → DR equity, CR target
        boolean isDebitNormal = targetAccount.getAccountType() == AccountType.Asset
                             || targetAccount.getAccountType() == AccountType.Expense;

        JournalDetailDTO drDetail = new JournalDetailDTO();
        JournalDetailDTO crDetail = new JournalDetailDTO();

        if (isDebitNormal) {
            drDetail.setAccountId(targetAccount.getId());
            drDetail.setDebit(amount);
            drDetail.setCredit(BigDecimal.ZERO);
            crDetail.setAccountId(equityAccount.getId());
            crDetail.setDebit(BigDecimal.ZERO);
            crDetail.setCredit(amount);
        } else {
            drDetail.setAccountId(equityAccount.getId());
            drDetail.setDebit(amount);
            drDetail.setCredit(BigDecimal.ZERO);
            crDetail.setAccountId(targetAccount.getId());
            crDetail.setDebit(BigDecimal.ZERO);
            crDetail.setCredit(amount);
        }

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(openingRefNo);
        journalDTO.setEntryDate(effectiveDate);
        journalDTO.setDescription("Opening Balance: " + targetAccount.getAccountName());
        journalDTO.setStaffId(staffId);
        journalDTO.setDetails(List.of(drDetail, crDetail));

        journalWriter.write(journalDTO);

        // ၅။ AccountBalance.openingBalance field ကို update လုပ်မယ်
        //    (currentBalance ကို JournalWriter က auto update ဖြစ်ပြီးသား)
        String year = String.valueOf(effectiveDate.getYear());
        AccountBalance balance = repository.findByAccountIdAndFiscalYear(targetAccount.getId(), year)
                .orElseGet(() -> {
                    AccountBalance b = new AccountBalance();
                    b.setAccount(targetAccount);
                    b.setFiscalYear(year);
                    b.setOpeningBalance(BigDecimal.ZERO);
                    b.setCurrentBalance(BigDecimal.ZERO);
                    b.setLastUpdated(LocalDateTime.now());
                    return b;
                });
        balance.setOpeningBalance(amount);
        repository.save(balance);

        // ၆။ Payment Transaction မှတ်တမ်း (report အတွက်)
        if (paymentMethodId != null) {
            PaymentTransactionDTO payDto = new PaymentTransactionDTO();
            payDto.setReferenceId(accountId);
            payDto.setReferenceType("Opening_Balance");
            payDto.setPaymentMethodId(paymentMethodId);
            payDto.setAmount(amount);
            payDto.setPaymentDate(effectiveDate);
            payDto.setTransactionNo(openingRefNo);
            paymentTransactionService.saveOpeningBalanceTransaction(payDto);
        }

        messagingTemplate.convertAndSend(BALANCE_TOPIC, "BALANCE_INITIALIZED");

        // ၇။ Target account ၏ updated balance ကို return ပြန်ပေးမယ်
        return mapper.toDto(
                repository.findByAccountId(accountId)
                        .orElseThrow(() -> new ResourceNotFoundException("Balance not found after update: " + accountId))
        );
    }

    private void reverseOpeningTransactions(String baseRefNo) {
        List<PaymentTransaction> linked = new ArrayList<>();
        paymentTransactionRepository.findByTransactionNo(baseRefNo).ifPresent(linked::add);
        linked.addAll(paymentTransactionRepository.findByTransactionNoStartingWith(baseRefNo + "-"));
        LocalDateTime now = LocalDateTime.now();
        for (PaymentTransaction tx : linked) {
            if (tx == null || Boolean.TRUE.equals(tx.getReversed())) continue;
            if (tx.getReferenceType() != null && !ReferenceType.Opening_Balance.equals(tx.getReferenceType())) continue;
            tx.setReversed(true);
            tx.setReversedAt(now);
            tx.setReversedBy("system");
            tx.setReversalReason("Reset opening balance");
            paymentTransactionRepository.save(tx);
        }
    }
}
