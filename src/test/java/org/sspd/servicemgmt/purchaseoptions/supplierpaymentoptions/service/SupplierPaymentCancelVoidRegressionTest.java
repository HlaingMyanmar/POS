package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto.SupplierPaymentRequest;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierPayment;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierPaymentAllocation;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierPaymentRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierPaymentCancelVoidRegressionTest {

    @Test
    void voidPaymentRejectsCancelledPurchase() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);

        Supplier supplier = Supplier.builder().id(3).advanceBalance(BigDecimal.ZERO).build();
        Purchase cancelled = Purchase.builder()
                .id(11)
                .purchaseCode("PO-11")
                .supplier(supplier)
                .status(PurchaseStatus.CANCELLED)
                .paidAmount(new BigDecimal("100"))
                .dueAmount(BigDecimal.ZERO)
                .returnAmount(BigDecimal.ZERO)
                .build();
        SupplierPayment payment = paymentFor(supplier, cancelled, new BigDecimal("100"));
        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(staff.existsById(9)).thenReturn(true);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(cancelled));

        SupplierPaymentService service = construct(purchases, payments, suppliers, staff, drawer);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidPayment(8, "undo"));
        assertTrue(thrown.getMessage().toLowerCase().contains("cancelled"));
        assertEquals(BigDecimal.ZERO, cancelled.getDueAmount());
        assertEquals(new BigDecimal("100"), cancelled.getPaidAmount());
        verify(drawer, never()).recordPurchaseCashIn(any(), any());
    }

    @Test
    void voidPaymentRejectsWhenPaidAmountAlreadyRefunded() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);

        Supplier supplier = Supplier.builder().id(3).advanceBalance(BigDecimal.ZERO).build();
        Purchase purchase = Purchase.builder()
                .id(11)
                .purchaseCode("PO-11")
                .supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(new BigDecimal("10"))
                .dueAmount(new BigDecimal("90"))
                .returnAmount(BigDecimal.ZERO)
                .build();
        SupplierPayment payment = paymentFor(supplier, purchase, new BigDecimal("100"));
        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(staff.existsById(9)).thenReturn(true);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(purchase));

        SupplierPaymentService service = construct(purchases, payments, suppliers, staff, drawer);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidPayment(8, "undo"));
        assertTrue(thrown.getMessage().toLowerCase().contains("paid amount"));
        assertEquals(new BigDecimal("90"), purchase.getDueAmount());
        verify(drawer, never()).recordPurchaseCashIn(any(), any());
    }

    @Test
    void voidPaymentReversesTransactionsBySourceIdEvenWhenNumbersDiffer() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        JournalWriter journals = mock(JournalWriter.class);

        Supplier supplier = Supplier.builder().id(3).name("ACME").advanceBalance(BigDecimal.ZERO).openingBalance(BigDecimal.ZERO).build();
        Purchase first = Purchase.builder()
                .id(11).purchaseCode("PO-11").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(new BigDecimal("40")).dueAmount(BigDecimal.ZERO)
                .returnAmount(BigDecimal.ZERO).build();
        Purchase second = Purchase.builder()
                .id(12).purchaseCode("PO-12").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(new BigDecimal("60")).dueAmount(BigDecimal.ZERO)
                .returnAmount(BigDecimal.ZERO).build();
        PaymentMethod bank = PaymentMethod.builder().id(4).methodName("KBZPay").build();
        SupplierPayment payment = SupplierPayment.builder()
                .id(8).paymentNo("SP-000008").supplier(supplier).paymentMethod(bank)
                .totalAmount(new BigDecimal("100")).allocatedAmount(new BigDecimal("100"))
                .advanceAmount(BigDecimal.ZERO).transactionNo("SPTX-000008").voided(false)
                .allocations(new java.util.ArrayList<>()).build();
        payment.setAllocations(List.of(
                SupplierPaymentAllocation.builder().supplierPayment(payment).purchase(first).amount(new BigDecimal("40")).build(),
                SupplierPaymentAllocation.builder().supplierPayment(payment).purchase(second).amount(new BigDecimal("60")).build()));
        PaymentTransaction tx1 = new PaymentTransaction();
        tx1.setId(31); tx1.setTransactionNo("TXN-000031"); tx1.setReversed(false);
        tx1.setSourceType(PaymentTransaction.SOURCE_SUPPLIER_PAYMENT); tx1.setSourceId(8);
        PaymentTransaction tx2 = new PaymentTransaction();
        tx2.setId(32); tx2.setTransactionNo("TXN-000032"); tx2.setReversed(false);
        tx2.setSourceType(PaymentTransaction.SOURCE_SUPPLIER_PAYMENT); tx2.setSourceId(8);

        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(payments.save(payment)).thenReturn(payment);
        when(staff.existsById(9)).thenReturn(true);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(first));
        when(purchases.findByIdForUpdate(12)).thenReturn(Optional.of(second));
        when(purchases.sumDueAmountBySupplierId(3)).thenReturn(new BigDecimal("100"));
        when(purchases.sumSupplierCreditAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(transactions.findBySourceTypeAndSourceId(PaymentTransaction.SOURCE_SUPPLIER_PAYMENT, 8))
                .thenReturn(List.of(tx1, tx2));

        SupplierPaymentService service = construct(purchases, payments, suppliers, staff, drawer, transactions, journals);
        service.voidPayment(8, "undo split");

        assertTrue(Boolean.TRUE.equals(tx1.getReversed()));
        assertTrue(Boolean.TRUE.equals(tx2.getReversed()));
        assertEquals("undo split", tx1.getReversalReason());
        assertEquals(new BigDecimal("40"), first.getDueAmount());
        assertEquals(new BigDecimal("60"), second.getDueAmount());
        verify(journals).reverseByReferenceNo("SP-000008");
        verify(transactions, never()).findByReferenceIdAndReferenceType(any(), any());
    }

    @Test
    void payRecordsAdvanceTransactionForUnallocatedAmount() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        AccountResolver accounts = mock(AccountResolver.class);
        PaymentBalanceValidator balances = mock(PaymentBalanceValidator.class);

        ChartOfAccount cash = ChartOfAccount.builder().id(21).build();
        when(accounts.payable()).thenReturn(ChartOfAccount.builder().id(22).build());
        when(accounts.supplierAdvance()).thenReturn(ChartOfAccount.builder().id(23).build());

        UserRepository users = mock(UserRepository.class);
        PaymentMethod cashMethod = PaymentMethod.builder().id(4).methodName("Cash").account(cash).build();
        Supplier supplier = Supplier.builder().id(3).name("ACME")
                .advanceBalance(BigDecimal.ZERO).openingBalance(BigDecimal.ZERO).build();
        Purchase purchase = Purchase.builder()
                .id(11).purchaseCode("PO-11").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(BigDecimal.ZERO).dueAmount(new BigDecimal("60000"))
                .returnAmount(BigDecimal.ZERO).build();

        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(suppliers.save(supplier)).thenReturn(supplier);
        when(methods.findById(4)).thenReturn(Optional.of(cashMethod));
        User actor = new User();
        actor.setUsername("cashier");
        actor.setStaff(Staff.builder().id(7).name("Cashier").role("Cashier").build());
        when(users.findByUsernameOrEmail("cashier", "cashier")).thenReturn(Optional.of(actor));
        when(staff.existsById(7)).thenReturn(true);
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(purchase));
        when(purchases.save(purchase)).thenReturn(purchase);
        when(purchases.sumDueAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(purchases.sumSupplierCreditAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(payments.save(any(SupplierPayment.class))).thenAnswer(inv -> {
            SupplierPayment payment = inv.getArgument(0);
            if (payment.getId() == null) payment.setId(8);
            return payment;
        });
        AtomicInteger ids = new AtomicInteger(40);
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) tx.setId(ids.getAndIncrement());
            return tx;
        });

        SupplierPaymentService service = construct(purchases, payments, suppliers, staff, drawer,
                transactions, journals, methods, accounts, balances, users);

        SupplierPaymentRequest request = new SupplierPaymentRequest();
        request.setSupplierId(3);
        request.setPaymentMethodId(4);
        request.setStaffId(9);
        request.setAmount(new BigDecimal("100000"));
        SupplierPaymentRequest.Allocation allocation = new SupplierPaymentRequest.Allocation();
        allocation.setPurchaseId(11);
        allocation.setAmount(new BigDecimal("60000"));
        request.setAllocations(List.of(allocation));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cashier", "n/a"));
        try {
            service.pay(request);
        } finally {
            SecurityContextHolder.clearContext();
        }

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions, atLeast(2)).save(captor.capture());
        Map<Integer, PaymentTransaction> byId = captor.getAllValues().stream()
                .collect(Collectors.toMap(PaymentTransaction::getId, Function.identity(), (a, b) -> b));
        List<PaymentTransaction> unique = new ArrayList<>(byId.values());
        PaymentTransaction purchaseTx = unique.stream()
                .filter(tx -> ReferenceType.Purchase.equals(tx.getReferenceType())).findFirst().orElseThrow();
        PaymentTransaction advanceTx = unique.stream()
                .filter(tx -> ReferenceType.Supplier_Advance.equals(tx.getReferenceType())).findFirst().orElseThrow();

        assertEquals(0, new BigDecimal("60000").compareTo(purchaseTx.getAmount()));
        assertEquals(Integer.valueOf(11), purchaseTx.getReferenceId());
        assertEquals(0, new BigDecimal("40000").compareTo(advanceTx.getAmount()));
        assertEquals(Integer.valueOf(3), advanceTx.getReferenceId());
        assertEquals(PaymentTransaction.SOURCE_SUPPLIER_PAYMENT, purchaseTx.getSourceType());
        assertEquals(PaymentTransaction.SOURCE_SUPPLIER_PAYMENT, advanceTx.getSourceType());
        assertEquals(Integer.valueOf(8), purchaseTx.getSourceId());
        assertEquals(Integer.valueOf(8), advanceTx.getSourceId());
        assertEquals(0, new BigDecimal("40000").compareTo(supplier.getAdvanceBalance()));
        verify(drawer).recordPurchaseCashOut(eq(new BigDecimal("100000")), any(), eq("Supplier_Payment"), eq(8));
    }

    private static SupplierPayment paymentFor(Supplier supplier, Purchase purchase, BigDecimal amount) {
        SupplierPayment payment = SupplierPayment.builder()
                .id(8)
                .paymentNo("SP-000008")
                .supplier(supplier)
                .totalAmount(amount)
                .allocatedAmount(amount)
                .advanceAmount(BigDecimal.ZERO)
                .voided(false)
                .build();
        payment.setAllocations(List.of(SupplierPaymentAllocation.builder()
                .supplierPayment(payment)
                .purchase(purchase)
                .amount(amount)
                .build()));
        return payment;
    }

    private static SupplierPaymentService construct(PurchaseRepository purchases,
                                                    SupplierPaymentRepository payments,
                                                    SupplierRepository suppliers,
                                                    StaffRepository staff,
                                                    CashDrawerService drawer) throws Exception {
        return construct(purchases, payments, suppliers, staff, drawer,
                mock(PaymentTransactionRepository.class), mock(JournalWriter.class),
                mock(PaymentMethodRepository.class), mock(AccountResolver.class),
                mock(PaymentBalanceValidator.class), mock(UserRepository.class));
    }

    private static SupplierPaymentService construct(PurchaseRepository purchases,
                                                    SupplierPaymentRepository payments,
                                                    SupplierRepository suppliers,
                                                    StaffRepository staff,
                                                    CashDrawerService drawer,
                                                    PaymentTransactionRepository transactions,
                                                    JournalWriter journals) throws Exception {
        return construct(purchases, payments, suppliers, staff, drawer, transactions, journals,
                mock(PaymentMethodRepository.class), mock(AccountResolver.class),
                mock(PaymentBalanceValidator.class), mock(UserRepository.class));
    }

    private static SupplierPaymentService construct(PurchaseRepository purchases,
                                                    SupplierPaymentRepository payments,
                                                    SupplierRepository suppliers,
                                                    StaffRepository staff,
                                                    CashDrawerService drawer,
                                                    PaymentTransactionRepository transactions,
                                                    JournalWriter journals,
                                                    PaymentMethodRepository methods,
                                                    AccountResolver accounts,
                                                    PaymentBalanceValidator balances,
                                                    UserRepository users) throws Exception {
        Constructor<?> constructor = Arrays.stream(SupplierPaymentService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == PurchaseRepository.class) return purchases;
                    if (type == SupplierPaymentRepository.class) return payments;
                    if (type == SupplierRepository.class) return suppliers;
                    if (type == StaffRepository.class) return staff;
                    if (type == CashDrawerService.class) return drawer;
                    if (type == PaymentTransactionRepository.class) return transactions;
                    if (type == JournalWriter.class) return journals;
                    if (type == PaymentMethodRepository.class) return methods;
                    if (type == AccountResolver.class) return accounts;
                    if (type == PaymentBalanceValidator.class) return balances;
                    if (type == UserRepository.class) return users;
                    return mock(type);
                })
                .toArray();
        return (SupplierPaymentService) constructor.newInstance(args);
    }
}
