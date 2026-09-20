package org.sspd.servicemgmt.purchaseoptions.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service.PaymentTransactionService;
import org.sspd.servicemgmt.purchaseoptions.mapper.PurchaseMapper;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseDebtPaymentLockTest {

    @Test
    void payPurchaseDebtBatchLocksSupplierThenPurchase() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentTransactionService paymentTransactions = mock(PaymentTransactionService.class);
        PurchaseMapper mapper = mock(PurchaseMapper.class);
        AccountResolver accounts = mock(AccountResolver.class);

        Supplier supplier = Supplier.builder().id(3).name("ACME")
                .openingBalance(BigDecimal.ZERO).advanceBalance(BigDecimal.ZERO).build();
        Purchase purchase = Purchase.builder()
                .id(11)
                .purchaseCode("PO-11")
                .supplier(supplier)
                .staff(Staff.builder().id(9).build())
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(BigDecimal.ZERO)
                .dueAmount(new BigDecimal("100"))
                .build();
        PaymentMethod bank = PaymentMethod.builder()
                .id(4).methodName("KBZPay")
                .account(ChartOfAccount.builder().id(31).build())
                .build();
        when(accounts.cash()).thenReturn(ChartOfAccount.builder().id(21).build());
        when(accounts.payable()).thenReturn(ChartOfAccount.builder().id(8).build());
        when(purchases.findSupplierIdById(11)).thenReturn(Optional.of(3));
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(purchase));
        when(purchases.save(purchase)).thenReturn(purchase);
        when(purchases.sumDueAmountBySupplierId(3)).thenReturn(new BigDecimal("50"));
        when(purchases.sumSupplierCreditAmountBySupplierId(3)).thenReturn(BigDecimal.ZERO);
        when(methods.findById(4)).thenReturn(Optional.of(bank));
        AtomicInteger ids = new AtomicInteger(40);
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) tx.setId(ids.getAndIncrement());
            if (tx.getTransactionNo() == null) tx.setTransactionNo("TXN-" + tx.getId());
            return tx;
        });
        when(mapper.toDto(any(PaymentTransaction.class))).thenReturn(new PaymentTransactionDTO());

        PurchaseService service = construct(Map.of(
                PurchaseRepository.class, purchases,
                SupplierRepository.class, suppliers,
                EntityManager.class, entityManager,
                PaymentMethodRepository.class, methods,
                PaymentTransactionRepository.class, transactions,
                PaymentTransactionService.class, paymentTransactions,
                PurchaseMapper.class, mapper,
                AccountResolver.class, accounts));

        PaymentTransactionDTO line = new PaymentTransactionDTO();
        line.setReferenceId(11);
        line.setPaymentMethodId(4);
        line.setAmount(new BigDecimal("50"));
        service.payPurchaseDebtBatch(List.of(line));

        org.mockito.InOrder order = inOrder(purchases, suppliers, entityManager);
        order.verify(purchases).findSupplierIdById(11);
        order.verify(suppliers).findByIdForUpdate(3);
        order.verify(purchases).findByIdForUpdate(11);
        order.verify(entityManager).refresh(purchase, LockModeType.PESSIMISTIC_WRITE);
        verify(purchases, never()).findById(11);
        assertEquals(new BigDecimal("50"), purchase.getPaidAmount());
        assertEquals(new BigDecimal("50"), purchase.getDueAmount());
        assertEquals(new BigDecimal("50"), supplier.getCurrentBalance());
        verify(transactions).save(any(PaymentTransaction.class));
        verify(suppliers).save(supplier);
    }

    private PurchaseService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseService) constructor.newInstance(args);
    }
}
