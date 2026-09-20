package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto.SupplierCreditApplyRequest;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto.SupplierPaymentRequest;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierPayment;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierCreditApplicationRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierPaymentRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
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

class SupplierPaymentActorIdentityTest {

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void payIgnoresRequestStaffIdAndJournalsAuthenticatedStaff() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        AccountResolver accounts = mock(AccountResolver.class);

        ChartOfAccount cash = ChartOfAccount.builder().id(21).build();
        when(accounts.payable()).thenReturn(ChartOfAccount.builder().id(22).build());
        when(accounts.supplierAdvance()).thenReturn(ChartOfAccount.builder().id(23).build());
        PaymentMethod cashMethod = PaymentMethod.builder().id(4).methodName("Cash").account(cash).build();
        Supplier supplier = Supplier.builder().id(3).name("ACME")
                .advanceBalance(BigDecimal.ZERO).openingBalance(BigDecimal.ZERO).build();
        Purchase purchase = Purchase.builder()
                .id(11).purchaseCode("PO-11").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(BigDecimal.ZERO).dueAmount(new BigDecimal("100"))
                .returnAmount(BigDecimal.ZERO).build();

        authenticate("cashier", 7, users, staff);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(suppliers.save(supplier)).thenReturn(supplier);
        when(methods.findById(4)).thenReturn(Optional.of(cashMethod));
        when(staff.existsById(9)).thenReturn(true);
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(purchase));
        when(purchases.save(purchase)).thenReturn(purchase);
        when(purchases.sumDueAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(purchases.sumSupplierCreditAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(payments.save(any(SupplierPayment.class))).thenAnswer(inv -> {
            SupplierPayment payment = inv.getArgument(0);
            if (payment.getId() == null) payment.setId(8);
            return payment;
        });
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) tx.setId(40);
            return tx;
        });

        SupplierPaymentService service = construct(purchases, payments, suppliers, staff, users,
                methods, transactions, journals, accounts);

        SupplierPaymentRequest request = new SupplierPaymentRequest();
        request.setSupplierId(3);
        request.setPaymentMethodId(4);
        request.setStaffId(9);
        request.setAmount(new BigDecimal("100"));
        SupplierPaymentRequest.Allocation allocation = new SupplierPaymentRequest.Allocation();
        allocation.setPurchaseId(11);
        allocation.setAmount(new BigDecimal("100"));
        request.setAllocations(List.of(allocation));

        service.pay(request);

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(journals).write(captor.capture());
        assertEquals(Integer.valueOf(7), captor.getValue().getStaffId());
    }

    @Test
    void applyCreditIgnoresRequestStaffIdAndJournalsAuthenticatedStaff() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        UserRepository users = mock(UserRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        AccountResolver accounts = mock(AccountResolver.class);
        SupplierCreditApplicationRepository applications = mock(SupplierCreditApplicationRepository.class);

        when(accounts.payable()).thenReturn(ChartOfAccount.builder().id(22).build());
        when(accounts.supplierAdvance()).thenReturn(ChartOfAccount.builder().id(23).build());
        Supplier supplier = Supplier.builder().id(3).name("ACME")
                .advanceBalance(new BigDecimal("50")).openingBalance(BigDecimal.ZERO).build();
        Purchase purchase = Purchase.builder()
                .id(11).purchaseCode("PO-11").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(BigDecimal.ZERO).dueAmount(new BigDecimal("50"))
                .returnAmount(BigDecimal.ZERO).build();

        authenticate("cashier", 7, users, staff);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(suppliers.save(supplier)).thenReturn(supplier);
        when(staff.existsById(9)).thenReturn(true);
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(purchase));
        when(purchases.save(purchase)).thenReturn(purchase);
        when(purchases.findSupplierCreditSourcesFifoForUpdate(3)).thenReturn(List.of());
        when(purchases.sumDueAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(purchases.sumSupplierCreditAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(applications.save(any(SupplierCreditApplication.class))).thenAnswer(inv -> {
            SupplierCreditApplication application = inv.getArgument(0);
            if (application.getId() == null) application.setId(21);
            return application;
        });

        SupplierPaymentService service = construct(purchases, mock(SupplierPaymentRepository.class),
                suppliers, staff, users, mock(PaymentMethodRepository.class),
                mock(PaymentTransactionRepository.class), journals, accounts, applications);

        SupplierCreditApplyRequest request = new SupplierCreditApplyRequest();
        request.setSupplierId(3);
        request.setPurchaseId(11);
        request.setStaffId(9);
        request.setAmount(new BigDecimal("50"));

        service.applyCredit(request);

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(journals).write(captor.capture());
        assertEquals(Integer.valueOf(7), captor.getValue().getStaffId());
    }

    @Test
    void payRejectsAuthenticatedUserWithoutStaffLink() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        UserRepository users = mock(UserRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        JournalWriter journals = mock(JournalWriter.class);

        PaymentMethod cashMethod = PaymentMethod.builder().id(4).methodName("Cash")
                .account(ChartOfAccount.builder().id(21).build()).build();
        Supplier supplier = Supplier.builder().id(3).name("ACME").advanceBalance(BigDecimal.ZERO).build();
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(methods.findById(4)).thenReturn(Optional.of(cashMethod));
        when(staff.existsById(9)).thenReturn(true);
        User actor = new User();
        actor.setUsername("cashier");
        when(users.findByUsernameOrEmail("cashier", "cashier")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cashier", "n/a"));

        SupplierPaymentService service = construct(purchases, mock(SupplierPaymentRepository.class),
                suppliers, staff, users, methods, mock(PaymentTransactionRepository.class),
                journals, mock(AccountResolver.class));

        SupplierPaymentRequest request = new SupplierPaymentRequest();
        request.setSupplierId(3);
        request.setPaymentMethodId(4);
        request.setStaffId(9);
        request.setAmount(new BigDecimal("100"));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> service.pay(request));
        assertTrue(thrown.getMessage().toLowerCase().contains("staff"));
        verify(journals, never()).write(any());
    }

    private static void authenticate(String username, Integer staffId, UserRepository users, StaffRepository staff) {
        User actor = new User();
        actor.setUsername(username);
        actor.setStaff(Staff.builder().id(staffId).name("Cashier").role("Cashier").build());
        when(users.findByUsernameOrEmail(username, username)).thenReturn(Optional.of(actor));
        when(staff.existsById(staffId)).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a"));
    }

    private static SupplierPaymentService construct(PurchaseRepository purchases,
                                                    SupplierPaymentRepository payments,
                                                    SupplierRepository suppliers,
                                                    StaffRepository staff,
                                                    UserRepository users,
                                                    PaymentMethodRepository methods,
                                                    PaymentTransactionRepository transactions,
                                                    JournalWriter journals,
                                                    AccountResolver accounts) throws Exception {
        return construct(purchases, payments, suppliers, staff, users, methods, transactions, journals, accounts,
                mock(SupplierCreditApplicationRepository.class));
    }

    private static SupplierPaymentService construct(PurchaseRepository purchases,
                                                    SupplierPaymentRepository payments,
                                                    SupplierRepository suppliers,
                                                    StaffRepository staff,
                                                    UserRepository users,
                                                    PaymentMethodRepository methods,
                                                    PaymentTransactionRepository transactions,
                                                    JournalWriter journals,
                                                    AccountResolver accounts,
                                                    SupplierCreditApplicationRepository applications) throws Exception {
        Constructor<?> constructor = Arrays.stream(SupplierPaymentService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == PurchaseRepository.class) return purchases;
                    if (type == SupplierPaymentRepository.class) return payments;
                    if (type == SupplierRepository.class) return suppliers;
                    if (type == StaffRepository.class) return staff;
                    if (type == UserRepository.class) return users;
                    if (type == PaymentMethodRepository.class) return methods;
                    if (type == PaymentTransactionRepository.class) return transactions;
                    if (type == JournalWriter.class) return journals;
                    if (type == AccountResolver.class) return accounts;
                    if (type == SupplierCreditApplicationRepository.class) return applications;
                    return mock(type);
                })
                .toArray();
        return (SupplierPaymentService) constructor.newInstance(args);
    }
}
