package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class PurchaseReturnAccountingRegressionTest {

    @Test
    void supplierCreditReturnIsFullyJournaled() throws Exception {
        JournalWriter writer = mock(JournalWriter.class);
        AccountResolver accounts = mock(AccountResolver.class);
        when(accounts.supplierAdvance()).thenReturn(account(11));
        when(accounts.purchaseRtn()).thenReturn(account(22));
        PurchaseReturnService service = construct(Map.of(
                JournalWriter.class, writer,
                AccountResolver.class, accounts));
        PurchaseReturn purchaseReturn = PurchaseReturn.builder()
                .id(7).returnNo("PRN-7").totalReturnAmount(new BigDecimal("100")).build();

        invoke(service, "createReturnJournal",
                new Class<?>[]{PurchaseReturn.class, PaymentMethod.class, BigDecimal.class,
                        BigDecimal.class, BigDecimal.class, Integer.class, String.class, List.class, BigDecimal.class},
                purchaseReturn, null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100"), 3, "Supplier", null, BigDecimal.ZERO);

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(writer).write(captor.capture());
        JournalEntryDTO entry = captor.getValue();
        assertEquals(new BigDecimal("100"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(11).equals(d.getAccountId()))
                .map(d -> d.getDebit()).findFirst().orElseThrow());
        assertEquals(new BigDecimal("100"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(22).equals(d.getAccountId()))
                .map(d -> d.getCredit()).findFirst().orElseThrow());
    }

    @Test
    void splitRefundVoidCreditsEveryOriginalMethodAndReversesCashLine() throws Exception {
        JournalWriter writer = mock(JournalWriter.class);
        AccountResolver accounts = mock(AccountResolver.class);
        CashDrawerService cashDrawer = mock(CashDrawerService.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        when(accounts.purchaseRtn()).thenReturn(account(22));
        when(accounts.cash()).thenReturn(account(31));
        PurchaseReturnService service = construct(Map.of(
                JournalWriter.class, writer,
                AccountResolver.class, accounts,
                CashDrawerService.class, cashDrawer,
                PaymentTransactionRepository.class, transactions));
        PurchaseReturn purchaseReturn = PurchaseReturn.builder()
                .id(8).returnNo("PRN-8").totalReturnAmount(new BigDecimal("100")).build();
        PaymentTransaction cash = transaction(1, 31, "40");
        PaymentTransaction bank = transaction(2, 32, "60");

        invoke(service, "createVoidJournal",
                new Class<?>[]{PurchaseReturn.class, Integer.class, String.class,
                        BigDecimal.class, BigDecimal.class, List.class},
                purchaseReturn, 3, "Supplier", BigDecimal.ZERO, BigDecimal.ZERO, List.of(cash, bank));
        invoke(service, "reverseRefundTransactions",
                new Class<?>[]{PurchaseReturn.class, List.class, String.class},
                purchaseReturn, List.of(cash, bank), "mistake");

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(writer).write(captor.capture());
        assertTrue(captor.getValue().getDetails().stream()
                .anyMatch(d -> Integer.valueOf(31).equals(d.getAccountId())
                        && new BigDecimal("40").compareTo(d.getCredit()) == 0));
        assertTrue(captor.getValue().getDetails().stream()
                .anyMatch(d -> Integer.valueOf(32).equals(d.getAccountId())
                        && new BigDecimal("60").compareTo(d.getCredit()) == 0));
        assertTrue(Boolean.TRUE.equals(cash.getReversed()));
        assertTrue(Boolean.TRUE.equals(bank.getReversed()));
        verify(cashDrawer).recordPurchaseCashOut(new BigDecimal("40"),
                "Void purchase return refund PRN-8");
    }

    @Test
    void supplierShippingReducesSettlementAndKeepsJournalBalanced() throws Exception {
        JournalWriter writer = mock(JournalWriter.class);
        AccountResolver accounts = mock(AccountResolver.class);
        when(accounts.supplierAdvance()).thenReturn(account(11));
        when(accounts.purchaseRtn()).thenReturn(account(22));
        when(accounts.transportation()).thenReturn(account(33));
        PurchaseReturnService service = construct(Map.of(JournalWriter.class, writer, AccountResolver.class, accounts));
        PurchaseReturn purchaseReturn = PurchaseReturn.builder().id(9).returnNo("PRN-9")
                .totalReturnAmount(new BigDecimal("100.00"))
                .supplierShippingPortion(new BigDecimal("10.00")).build();

        invoke(service, "createReturnJournal",
                new Class<?>[]{PurchaseReturn.class, PaymentMethod.class, BigDecimal.class,
                        BigDecimal.class, BigDecimal.class, Integer.class, String.class, List.class, BigDecimal.class},
                purchaseReturn, null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("90.00"), 3, "Supplier", null, new BigDecimal("10.00"));

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(writer).write(captor.capture());
        JournalEntryDTO entry = captor.getValue();
        assertEquals(new BigDecimal("10.00"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(33).equals(d.getAccountId()))
                .map(d -> d.getDebit()).findFirst().orElseThrow());
        assertEquals(new BigDecimal("100.00"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(22).equals(d.getAccountId()))
                .map(d -> d.getCredit()).findFirst().orElseThrow());
    }

    @Test
    void existingShippingTransactionMakesPostingIdempotent() throws Exception {
        JournalWriter writer = mock(JournalWriter.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PurchaseReturn purchaseReturn = PurchaseReturn.builder().id(10).returnNo("PRN-10")
                .companyShippingPortion(new BigDecimal("12.00")).build();
        when(transactions.findByReferenceIdAndReferenceType(10,
                org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType.Purchase_Return_Shipping))
                .thenReturn(List.of(new PaymentTransaction()));
        PurchaseReturnService service = construct(Map.of(
                JournalWriter.class, writer,
                PaymentTransactionRepository.class, transactions));

        invoke(service, "postCompanyShipping", new Class<?>[]{PurchaseReturn.class}, purchaseReturn);
        invoke(service, "postCompanyShipping", new Class<?>[]{PurchaseReturn.class}, purchaseReturn);

        verify(transactions, never()).save(any(PaymentTransaction.class));
        verify(writer, never()).write(any(JournalEntryDTO.class));
    }

    private PurchaseReturnService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseReturnService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseReturnService) constructor.newInstance(args);
    }

    private void invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private ChartOfAccount account(int id) {
        return ChartOfAccount.builder().id(id).build();
    }

    private PaymentTransaction transaction(int id, int accountId, String amount) {
        PaymentMethod method = PaymentMethod.builder().id(id).account(account(accountId)).build();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(id);
        tx.setPaymentMethod(method);
        tx.setAmount(new BigDecimal(amount));
        tx.setReversed(false);
        return tx;
    }
}
