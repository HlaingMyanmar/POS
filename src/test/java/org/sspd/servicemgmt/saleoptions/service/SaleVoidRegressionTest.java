package org.sspd.servicemgmt.saleoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleVoidRegressionTest {

    @Test
    void repeatedPaymentsAndAdjustmentsHaveDistinctBalancedJournals() throws Exception {
        SaleService service = construct(mock(SaleRepository.class), mock(SaleReturnRepository.class),
                mock(CustomerPaymentService.class), mock(PaymentTransactionRepository.class),
                mock(CashDrawerService.class), mock(AccountResolver.class));
        var journals = mock(org.sspd.servicemgmt.journaloption.entry.service.JournalWriter.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "journalWriter", journals);
        Sale sale = Sale.builder().id(11).saleCode("INV-11").build();
        for (int i = 0; i < 2; i++) {
            invoke(service, "createSaleJournalForPayment",
                    new Class<?>[]{Sale.class, BigDecimal.class, Integer.class, Integer.class, Integer.class, List.class},
                    sale, new BigDecimal("20"), 10, 20, null, List.of());
            invoke(service, "createPaymentAdjustmentJournal",
                    new Class<?>[]{Sale.class, BigDecimal.class, Integer.class, Integer.class, Integer.class},
                    sale, new BigDecimal("-5"), 10, 20, null);
        }
        var captor = org.mockito.ArgumentCaptor.forClass(org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO.class);
        verify(journals, org.mockito.Mockito.times(4)).write(captor.capture());
        assertEquals(4, captor.getAllValues().stream().map(j -> j.getReferenceNo()).distinct().count());
        for (var journal : captor.getAllValues()) {
            assertEquals(journal.getDetails().stream().map(d -> d.getDebit()).reduce(BigDecimal.ZERO, BigDecimal::add),
                    journal.getDetails().stream().map(d -> d.getCredit()).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    @Test
    void voidSaleWithReturnDoesNotRestockOriginalQuantity() throws Exception {
        SaleRepository sales = mock(SaleRepository.class);
        SaleReturnRepository returns = mock(SaleReturnRepository.class);
        CustomerPaymentService payments = mock(CustomerPaymentService.class);
        Product product = Product.builder().id(3).stockQty(2).build();
        Sale sale = Sale.builder()
                .id(11)
                .saleCode("INV-11")
                .voided(false)
                .details(List.of(SaleDetail.builder().product(product).qty(5).build()))
                .build();
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(sale));
        when(returns.findAllBySaleIdAndDeletedFalse(11)).thenReturn(List.of(
                SaleReturn.builder().id(4).returnCode("SR-4").deleted(false).build()));

        SaleService service = construct(sales, returns, payments, mock(PaymentTransactionRepository.class),
                mock(CashDrawerService.class), mock(AccountResolver.class));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidSale(11, "customer cancelled"));
        assertTrue(thrown.getMessage().toLowerCase().contains("return"));
        assertEquals(2, product.getStockQty());
        verify(payments, never()).reverseAccountingForVoidedSale(any());
        verify(sales).findLockedWithDetails(11);
        verify(sales, never()).findById(11);
    }

    @Test
    void voidedCashRefundIgnoresAlreadyReversedTransactions() throws Exception {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        AccountResolver accounts = mock(AccountResolver.class);
        ChartOfAccount cash = new ChartOfAccount();
        cash.setId(41);
        when(accounts.cash()).thenReturn(cash);
        PaymentMethod cashMethod = PaymentMethod.builder().id(7).account(cash).methodName("Cash").build();
        PaymentTransaction live = new PaymentTransaction();
        live.setPaymentMethod(cashMethod);
        live.setAmount(new BigDecimal("50"));
        live.setReversed(false);
        PaymentTransaction alreadyVoided = new PaymentTransaction();
        alreadyVoided.setPaymentMethod(cashMethod);
        alreadyVoided.setAmount(new BigDecimal("80"));
        alreadyVoided.setReversed(true);
        when(transactions.findByReferenceIdAndReferenceType(11, ReferenceType.Sale))
                .thenReturn(List.of(live, alreadyVoided));

        SaleService service = construct(mock(SaleRepository.class), mock(SaleReturnRepository.class),
                mock(CustomerPaymentService.class), transactions, drawer, accounts);
        invoke(service, "recordVoidedCashRefund", new Class<?>[]{Sale.class, String.class},
                Sale.builder().id(11).saleCode("INV-11").build(), "void");

        verify(drawer).recordCashRefund(new BigDecimal("50"), ReferenceType.Sale.name(), 11);
    }

    @Test
    void voidSaleReversesAllocatedCustomerPaymentJournals() throws Exception {
        SaleRepository sales = mock(SaleRepository.class);
        SaleReturnRepository returns = mock(SaleReturnRepository.class);
        CustomerPaymentService payments = mock(CustomerPaymentService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService> portal =
                mock(ObjectProvider.class);
        when(portal.getIfAvailable()).thenReturn(null);
        Sale sale = Sale.builder().id(11).saleCode("INV-11").voided(false).details(List.of()).build();
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(sale));
        when(sales.save(any())).thenReturn(sale);
        when(returns.findAllBySaleIdAndDeletedFalse(11)).thenReturn(List.of());

        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        when(transactions.findByReferenceIdAndReferenceType(11, ReferenceType.Sale)).thenReturn(List.of());
        SaleService service = construct(sales, returns, payments,
                transactions, mock(CashDrawerService.class),
                mock(AccountResolver.class), portal);
        service.voidSale(11, "wrong invoice");

        verify(payments).reverseAccountingForVoidedSale(11);
        verify(transactions, never()).deleteByReferenceIdAndReferenceType(11, ReferenceType.Sale);
        var journalWriter = (org.sspd.servicemgmt.journaloption.entry.service.JournalWriter)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "journalWriter");
        verify(journalWriter).reverseByReferenceNo("INV-11-PAY");
        verify(journalWriter).reverseByReferenceNo("INV-11-ADJ");
        verify(journalWriter).reverseByReferencePrefix("INV-11-PAY-", "system", "wrong invoice");
        verify(journalWriter).reverseByReferencePrefix("INV-11-ADJ-", "system", "wrong invoice");
    }

    private static SaleService construct(SaleRepository sales, SaleReturnRepository returns,
                                         CustomerPaymentService payments,
                                         PaymentTransactionRepository transactions,
                                         CashDrawerService drawer, AccountResolver accounts) throws Exception {
        return construct(sales, returns, payments, transactions, drawer, accounts, null);
    }

    private static SaleService construct(SaleRepository sales, SaleReturnRepository returns,
                                         CustomerPaymentService payments,
                                         PaymentTransactionRepository transactions,
                                         CashDrawerService drawer, AccountResolver accounts,
                                         ObjectProvider<?> portal) throws Exception {
        Constructor<?> constructor = Arrays.stream(SaleService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == SaleRepository.class) return sales;
                    if (type == SaleReturnRepository.class) return returns;
                    if (type == CustomerPaymentService.class) return payments;
                    if (type == PaymentTransactionRepository.class) return transactions;
                    if (type == CashDrawerService.class) return drawer;
                    if (type == AccountResolver.class) return accounts;
                    if (type == ObjectProvider.class) return portal != null ? portal : mock(ObjectProvider.class);
                    return mock(type);
                })
                .toArray();
        return (SaleService) constructor.newInstance(args);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, args);
    }
}
