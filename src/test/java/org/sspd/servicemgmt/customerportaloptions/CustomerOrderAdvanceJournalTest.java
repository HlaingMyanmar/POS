package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderPaymentService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerStockReservationService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.service.SaleService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CustomerOrderAdvanceJournalTest {

    @Test
    void verifiedDepositDebitsPaymentAccountAndCreditsCustomerAdvance() {
        CustomerOrderPaymentService service = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        AccountResolver accounts = mock(AccountResolver.class);
        JournalWriter writer = mock(JournalWriter.class);
        ReflectionTestUtils.setField(service, "methods", methods);
        ReflectionTestUtils.setField(service, "accounts", accounts);
        ReflectionTestUtils.setField(service, "journalWriter", writer);

        ChartOfAccount wallet = account(101);
        ChartOfAccount advance = account(202);
        PaymentMethod method = new PaymentMethod();
        method.setId(3);
        method.setAccount(wallet);
        when(methods.findById(3)).thenReturn(Optional.of(method));
        when(accounts.custAdvance()).thenReturn(advance);
        when(writer.hasActiveReferencePrefix(anyString())).thenReturn(false);

        CustomerOrder order = CustomerOrder.builder().id(42).orderNo("CA-000042").build();
        ReflectionTestUtils.invokeMethod(service, "postAdvanceReceiptJournal", order,
                new BigDecimal("30000.00"), 3, "DEPOSIT");

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(writer).write(captor.capture());
        JournalEntryDTO journal = captor.getValue();
        assertEquals("CUSTOMER-ORDER-42-ADV-DEPOSIT", journal.getReferenceNo());
        assertEquals(new BigDecimal("30000.00"), journal.getDetails().get(0).getDebit());
        assertEquals(Integer.valueOf(101), journal.getDetails().get(0).getAccountId());
        assertEquals(new BigDecimal("30000.00"), journal.getDetails().get(1).getCredit());
        assertEquals(Integer.valueOf(202), journal.getDetails().get(1).getAccountId());
    }

    @Test
    void prepaidSaleDebitsAdvanceAndDoesNotDebitCashAgain() {
        SaleService service = mock(SaleService.class, CALLS_REAL_METHODS);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        AccountResolver accounts = mock(AccountResolver.class);
        JournalWriter writer = mock(JournalWriter.class);
        ReflectionTestUtils.setField(service, "paymentMethodRepository", methods);
        ReflectionTestUtils.setField(service, "accountResolver", accounts);
        ReflectionTestUtils.setField(service, "journalWriter", writer);
        when(accounts.custAdvance()).thenReturn(account(202));
        when(accounts.sales()).thenReturn(account(303));

        Sale sale = new Sale();
        sale.setSaleCode("SAL-000001");
        sale.setNetAmount(new BigDecimal("100000.00"));
        sale.setPaidAmount(new BigDecimal("100000.00"));
        sale.setDueAmount(BigDecimal.ZERO);
        sale.setTaxAmount(BigDecimal.ZERO);
        PaymentTransactionDTO payment = new PaymentTransactionDTO();
        payment.setPaymentMethodId(3);
        payment.setAmount(new BigDecimal("100000.00"));

        ReflectionTestUtils.invokeMethod(service, "createSaleJournal", sale,
                null, null, null, List.of(payment), new BigDecimal("100000.00"));

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(writer).write(captor.capture());
        JournalEntryDTO journal = captor.getValue();
        assertEquals(2, journal.getDetails().size());
        assertEquals(Integer.valueOf(202), journal.getDetails().get(0).getAccountId());
        assertEquals(new BigDecimal("100000.00"), journal.getDetails().get(0).getDebit());
        assertEquals(Integer.valueOf(303), journal.getDetails().get(1).getAccountId());
        assertEquals(new BigDecimal("100000.00"), journal.getDetails().get(1).getCredit());
        verify(methods, never()).findById(anyInt());
    }

    @Test
    void saleCreditsDeliveryChargeToDeliveryIncomeInsteadOfProductSales() {
        SaleService service = mock(SaleService.class, CALLS_REAL_METHODS);
        AccountResolver accounts = mock(AccountResolver.class);
        JournalWriter writer = mock(JournalWriter.class);
        ReflectionTestUtils.setField(service, "accountResolver", accounts);
        ReflectionTestUtils.setField(service, "journalWriter", writer);
        when(accounts.custAdvance()).thenReturn(account(202));
        when(accounts.sales()).thenReturn(account(303));
        when(accounts.deliveryIncome()).thenReturn(account(304));
        when(accounts.taxPayable()).thenReturn(account(305));

        Sale sale = new Sale();
        sale.setSaleCode("SAL-000002");
        sale.setNetAmount(new BigDecimal("100000.00"));
        sale.setPaidAmount(new BigDecimal("100000.00"));
        sale.setDueAmount(BigDecimal.ZERO);
        sale.setTaxAmount(new BigDecimal("2000.00"));
        sale.setDeliveryCharge(new BigDecimal("3000.00"));

        ReflectionTestUtils.invokeMethod(service, "createSaleJournal", sale,
                null, null, null, List.of(), new BigDecimal("100000.00"));

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(writer).write(captor.capture());
        JournalEntryDTO journal = captor.getValue();
        assertEquals(4, journal.getDetails().size());
        assertEquals(new BigDecimal("100000.00"), journal.getDetails().get(0).getDebit());
        assertEquals(Integer.valueOf(303), journal.getDetails().get(1).getAccountId());
        assertEquals(new BigDecimal("95000.00"), journal.getDetails().get(1).getCredit());
        assertEquals(Integer.valueOf(304), journal.getDetails().get(2).getAccountId());
        assertEquals(new BigDecimal("3000.00"), journal.getDetails().get(2).getCredit());
        assertEquals(Integer.valueOf(305), journal.getDetails().get(3).getAccountId());
        assertEquals(new BigDecimal("2000.00"), journal.getDetails().get(3).getCredit());
    }

    @Test
    void cashAdvanceReceiptUpdatesCustomerCardAndDrawer() {
        CustomerOrderPaymentService service = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        AccountResolver accounts = mock(AccountResolver.class);
        JournalWriter writer = mock(JournalWriter.class);
        CustomerPaymentService credit = mock(CustomerPaymentService.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        ReflectionTestUtils.setField(service, "methods", methods);
        ReflectionTestUtils.setField(service, "accounts", accounts);
        ReflectionTestUtils.setField(service, "journalWriter", writer);
        ReflectionTestUtils.setField(service, "customerCredit", credit);
        ReflectionTestUtils.setField(service, "cashDrawer", drawer);

        ChartOfAccount cash = account(101);
        PaymentMethod method = new PaymentMethod();
        method.setId(8);
        method.setMethodName("CASH");
        method.setAccount(cash);
        when(methods.findById(8)).thenReturn(Optional.of(method));
        when(accounts.custAdvance()).thenReturn(account(202));
        when(writer.hasActiveReferencePrefix(anyString())).thenReturn(false);

        Customer customer = new Customer();
        customer.setId(7);
        CustomerOrder order = CustomerOrder.builder().id(42).orderNo("CA-000042").customer(customer).build();
        ReflectionTestUtils.invokeMethod(service, "postAdvanceReceiptJournal", order,
                new BigDecimal("70000.00"), 8, "REMAINDER");

        verify(credit).addAdvanceBalance(customer, new BigDecimal("70000.00"));
        verify(drawer).recordCashSale(new BigDecimal("70000.00"), "Customer_Order_REMAINDER", 42);
    }

    @Test
    void restoreAfterVoidedSaleReopensPaidOrderAndRestoresAdvance() {
        CustomerOrderPaymentService service = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        CustomerPaymentService credit = mock(CustomerPaymentService.class);
        JournalWriter writer = mock(JournalWriter.class);
        PaymentTransactionRepository paymentTransactions = mock(PaymentTransactionRepository.class);
        ReflectionTestUtils.setField(service, "orders", orders);
        ReflectionTestUtils.setField(service, "stock", stock);
        ReflectionTestUtils.setField(service, "events", events);
        ReflectionTestUtils.setField(service, "customerCredit", credit);
        ReflectionTestUtils.setField(service, "journalWriter", writer);
        ReflectionTestUtils.setField(service, "paymentTransactions", paymentTransactions);

        Customer customer = new Customer();
        customer.setId(7);
        CustomerOrderLine line = CustomerOrderLine.builder()
                .productName("Laptop").qty(1)
                .unitPrice(new BigDecimal("100000.00"))
                .subtotal(new BigDecimal("100000.00"))
                .build();
        CustomerOrder order = CustomerOrder.builder()
                .id(42).orderNo("CA-000042").customer(customer)
                .status(CustomerOrderStatus.CONFIRMED)
                .paymentState("FULFILLED")
                .completedSaleId(99)
                .itemsTotal(new BigDecimal("100000.00"))
                .deliveryCharge(BigDecimal.ZERO)
                .reservationActive(false)
                .lines(List.of(line))
                .build();
        line.setOrder(order);
        PaymentTransaction orderTx = new PaymentTransaction();
        orderTx.setId(15);
        orderTx.setReversed(true);
        orderTx.setTransactionNo("KBZ-DEP-MIGRATED-15");
        orderTx.setReversalReason("Migrated to sale #99 | KBZ-DEP");
        PaymentTransaction saleTx = new PaymentTransaction();
        saleTx.setId(21);
        saleTx.setReversed(true);
        saleTx.setTransactionNo("KBZ-DEP");
        when(orders.findFirstByCompletedSaleId(99)).thenReturn(Optional.of(order));
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        when(writer.hasActiveReferencePrefix("CUSTOMER-ORDER-42-ADV-FULL")).thenReturn(true);
        when(paymentTransactions.findByReferenceIdAndReferenceType(99, ReferenceType.Sale)).thenReturn(List.of(saleTx));
        when(paymentTransactions.findByReferenceIdAndReferenceType(42, ReferenceType.Customer_Order)).thenReturn(List.of(orderTx));

        assertTrue(service.restoreAfterVoidedSale(99, "wrong invoice"));
        assertNull(order.getCompletedSaleId());
        assertEquals("PAID", order.getPaymentState());
        verify(stock).reserve(order);
        verify(credit).addAdvanceBalance(customer, new BigDecimal("100000.00"));
        assertFalse(Boolean.TRUE.equals(orderTx.getReversed()));
        assertEquals("KBZ-DEP", orderTx.getTransactionNo());
        assertNull(orderTx.getReversalReason());
        assertTrue(saleTx.getTransactionNo().contains("-VOID-21"));
        verify(paymentTransactions, never()).deleteByReferenceIdAndReferenceType(any(), any());
    }

    @Test
    void restoreAfterVoidedSaleDoesNotSwallowReservationFailure() {
        CustomerOrderPaymentService service = mock(CustomerOrderPaymentService.class, CALLS_REAL_METHODS);
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        CustomerStockReservationService stock = mock(CustomerStockReservationService.class);
        ReflectionTestUtils.setField(service, "orders", orders);
        ReflectionTestUtils.setField(service, "stock", stock);
        ReflectionTestUtils.setField(service, "events", mock(DataEventPublisher.class));
        CustomerOrder order = CustomerOrder.builder()
                .id(42).orderNo("CA-000042")
                .status(CustomerOrderStatus.CONFIRMED)
                .paymentState("FULFILLED")
                .completedSaleId(99)
                .itemsTotal(new BigDecimal("100000.00"))
                .deliveryCharge(BigDecimal.ZERO)
                .reservationActive(false)
                .lines(List.of())
                .build();
        when(orders.findFirstByCompletedSaleId(99)).thenReturn(Optional.of(order));
        when(orders.findLocked(42)).thenReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(new IllegalStateException("stock unavailable")).when(stock).reserve(order);

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> service.restoreAfterVoidedSale(99, "wrong invoice"));
        org.junit.jupiter.api.Assertions.assertEquals("stock unavailable", thrown.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(order.getCompletedSaleId());
    }

    private ChartOfAccount account(int id) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        return account;
    }
}
