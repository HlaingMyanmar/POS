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
import org.sspd.servicemgmt.accountingoptions.coaoptions.service.ChartOfAccountService;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.dto.PaymentMethodDTO;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentMethodService;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service.PaymentTransactionService;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AccountBalanceService {

    private final AccountBalanceRepository repository;
    private final AccountBalanceMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final JournalWriter journalWriter;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentMethodService paymentMethodService;


    private static final String BALANCE_TOPIC = "/topic/account-balance";

    /**
     * ၁။ အကောင့်အားလုံး၏ လက်ကျန်ငွေစာရင်းကို ကြည့်ရှုခြင်း (Trial Balance အတွက်)
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @Transactional(readOnly = true)
    public List<AccountBalanceDTO> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * ၂။ အကောင့်တစ်ခုချင်းစီအလိုက် လက်ကျန်ငွေကို ရှာဖွေခြင်း
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @Transactional(readOnly = true)
    public AccountBalanceDTO findByAccountId(Integer accountId) {
        AccountBalance balance = repository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Balance record not found for Account ID: " + accountId));
        return mapper.toDto(balance);
    }

    /**
     * ၃။ သတ်မှတ်ထားသော ဘဏ္ဍာရေးနှစ်အလိုက် လက်ကျန်ငွေကို ရှာဖွေခြင်း
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @Transactional(readOnly = true)
    public AccountBalanceDTO findByAccountAndYear(Integer accountId, String fiscalYear) {
        AccountBalance balance = repository.findByAccountIdAndFiscalYear(accountId, fiscalYear)
                .orElseThrow(() -> new ResourceNotFoundException("Balance record not found for Account ID: " + accountId + " and Year: " + fiscalYear));
        return mapper.toDto(balance);
    }

    /**
     * ၄။ လက်ကျန်ငွေ ပြောင်းလဲသွားတိုင်း WebSocket မှတစ်ဆင့် အသိပေးရန် (Internal Use)
     */
    public void notifyBalanceUpdate() {
        messagingTemplate.convertAndSend(BALANCE_TOPIC, "BALANCE_UPDATED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_UPDATE')")
    @Transactional
    public AccountBalanceDTO setOpeningBalance(Integer accountId, BigDecimal amount, Integer staffId, Integer paymentMethodId) {

        String refNo = "OPN-" + System.currentTimeMillis() / 1000;

        // ၁။ Journal Entry တစ်ခု အလိုအလျောက် တည်ဆောက်မယ်
        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(refNo);
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Opening Balance Initialization");
        journalDTO.setStaffId(staffId);

        PaymentMethodDTO method = paymentMethodService.findAll()
                .stream()
                .filter(pm -> Objects.equals(pm.getId(), paymentMethodId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Payment Method not found"));

        // ၂။ Double Entry စာရင်းဝင်မယ်
        // DR - ပစ်မှတ်အကောင့် (ဥပမာ- Cash Account ID 5)
        JournalDetailDTO drDetail = new JournalDetailDTO();
        drDetail.setAccountId(method.getAccountId());
        drDetail.setDebit(amount);
        drDetail.setCredit(BigDecimal.ZERO);


        // CR - Opening Balance Equity (သင်သတ်မှတ်ထားသော ID 15)
        JournalDetailDTO crDetail = new JournalDetailDTO();
        crDetail.setAccountId(accountId);
        crDetail.setDebit(BigDecimal.ZERO);
        crDetail.setCredit(amount);

        journalDTO.setDetails(List.of(drDetail, crDetail));
        journalWriter.write(journalDTO); // Journal သိမ်းလိုက်တာနဲ့ AccountBalance ပါ auto update ဖြစ်သွားမယ်

        // ၃။ Payment Transaction မှတ်တမ်းသွင်းမယ် (Report မှာ ပေါ်လာအောင်)
        PaymentTransactionDTO payDto = new PaymentTransactionDTO();
        payDto.setReferenceId(0); // 0 သည် အဖွင့်လက်ကျန်ဖြစ်ကြောင်း အမှတ်အသားပြုခြင်း
        payDto.setReferenceType("Opening_Balance"); // သို့မဟုတ် Enum တွင် 'Opening' ထပ်တိုးနိုင်ပါသည်
        payDto.setPaymentMethodId(paymentMethodId); // Cash သို့မဟုတ် KBZ Pay ID
        payDto.setAmount(amount);
        payDto.setTransactionNo(refNo);

        paymentTransactionService.saveInternalTransaction(payDto); // Payment Report ထဲ data ရောက်သွားမယ်

        // ၄။ Update ဖြစ်သွားတဲ့ Balance ကို ပြန်ရှာပြီး DTO အနေနဲ့ Return ပြန်ပေးမယ်
        AccountBalance updatedBalance = repository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Balance not found after update"));

        messagingTemplate.convertAndSend(BALANCE_TOPIC, "BALANCE_INITIALIZED");
        return mapper.toDto(updatedBalance);
    }



}