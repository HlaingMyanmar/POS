package org.sspd.servicemgmt.creditoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.creditoptions.mapper.CreditMapper;
import org.sspd.servicemgmt.creditoptions.model.CustomerPayment;
import org.sspd.servicemgmt.creditoptions.model.CustomerPaymentAllocation;
import org.sspd.servicemgmt.creditoptions.repository.CustomerPaymentRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

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

class CustomerPaymentVoidGuardTest {

    @Test
    void voidPaymentRejectsAlreadyVoidedSale() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        SaleRepository sales = mock(SaleRepository.class);
        StaffRepository staff = mock(StaffRepository.class);

        Customer customer = Customer.builder().id(4).advanceBalance(BigDecimal.ZERO).build();
        Sale voidedSale = Sale.builder()
                .id(21)
                .saleCode("INV-21")
                .customer(customer)
                .voided(true)
                .paidAmount(BigDecimal.ZERO)
                .dueAmount(BigDecimal.ZERO)
                .build();
        CustomerPayment payment = paymentFor(customer, voidedSale, new BigDecimal("80"));

        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(staff.existsById(9)).thenReturn(true);
        when(customers.findByIdForUpdate(4)).thenReturn(Optional.of(customer));
        when(sales.findLockedWithDetails(21)).thenReturn(Optional.of(voidedSale));

        CustomerPaymentService service = construct(payments, customers, sales, staff);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidPayment(8, "mistake", 9));
        assertTrue(thrown.getMessage().toLowerCase().contains("voided"));
        assertEquals(BigDecimal.ZERO, voidedSale.getDueAmount());
    }

    @Test
    void voidPaymentRejectsWhenPaidAmountIsAlreadyGone() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        SaleRepository sales = mock(SaleRepository.class);
        StaffRepository staff = mock(StaffRepository.class);

        Customer customer = Customer.builder().id(4).advanceBalance(BigDecimal.ZERO).build();
        Sale sale = Sale.builder()
                .id(22)
                .saleCode("INV-22")
                .customer(customer)
                .voided(false)
                .paidAmount(new BigDecimal("10"))
                .dueAmount(new BigDecimal("90"))
                .build();
        CustomerPayment payment = paymentFor(customer, sale, new BigDecimal("80"));

        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(staff.existsById(9)).thenReturn(true);
        when(customers.findByIdForUpdate(4)).thenReturn(Optional.of(customer));
        when(sales.findLockedWithDetails(22)).thenReturn(Optional.of(sale));

        CustomerPaymentService service = construct(payments, customers, sales, staff);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidPayment(8, "mistake", 9));
        assertTrue(thrown.getMessage().toLowerCase().contains("paid amount"));
        assertEquals(new BigDecimal("90"), sale.getDueAmount());
        assertEquals(new BigDecimal("10"), sale.getPaidAmount());
    }

    @Test
    void voidPaymentVoidsUnusedAdvance() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        CreditMapper mapper = mock(CreditMapper.class);

        Customer customer = Customer.builder().id(4).advanceBalance(new BigDecimal("50")).build();
        CustomerPayment payment = unusedAdvance(customer, new BigDecimal("50"));
        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(staff.existsById(9)).thenReturn(true);
        when(customers.findByIdForUpdate(4)).thenReturn(Optional.of(customer));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toDto(any(CustomerPayment.class))).thenReturn(new org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO());

        CustomerPaymentService service = construct(payments, customers, mock(SaleRepository.class), staff, journals, drawer, mapper);
        service.voidPayment(8, "wrong deposit", 9);

        assertTrue(Boolean.TRUE.equals(payment.getVoided()));
        assertEquals(BigDecimal.ZERO, customer.getAdvanceBalance());
        verify(journals).reverseByReferenceNo("CP-000008");
        verify(drawer).recordCashRefund(new BigDecimal("50"), "Customer_Payment", 8);
        verify(customers).save(customer);
    }

    @Test
    void voidPaymentRejectsUsedAdvance() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        CashDrawerService drawer = mock(CashDrawerService.class);

        Customer customer = Customer.builder().id(4).advanceBalance(new BigDecimal("10")).build();
        CustomerPayment payment = unusedAdvance(customer, new BigDecimal("50"));
        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(staff.existsById(9)).thenReturn(true);
        when(customers.findByIdForUpdate(4)).thenReturn(Optional.of(customer));

        CustomerPaymentService service = construct(payments, customers, mock(SaleRepository.class), staff, journals, drawer, mock(CreditMapper.class));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidPayment(8, "wrong deposit", 9));
        assertTrue(thrown.getMessage().toLowerCase().contains("already been used"));
        assertEquals(new BigDecimal("10"), customer.getAdvanceBalance());
        verify(journals, never()).reverseByReferenceNo(any());
        verify(drawer, never()).recordCashRefund(any(), any(), any());
    }

    @Test
    void voidPaymentRejectsSaleLinkedReceiptWithoutAllocations() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        CashDrawerService drawer = mock(CashDrawerService.class);

        Customer customer = Customer.builder().id(4).advanceBalance(BigDecimal.ZERO).build();
        Sale sale = Sale.builder().id(22).saleCode("INV-22").customer(customer).build();
        CustomerPayment payment = CustomerPayment.builder()
                .id(8).paymentNo("CP-000008").customer(customer).sale(sale)
                .amount(new BigDecimal("80")).allocatedAmount(new BigDecimal("80"))
                .advanceAmount(BigDecimal.ZERO).voided(false)
                .paymentMethod(PaymentMethod.builder().methodName("Cash").build())
                .build();
        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(staff.existsById(9)).thenReturn(true);

        CustomerPaymentService service = construct(payments, customers, mock(SaleRepository.class), staff, journals, drawer, mock(CreditMapper.class));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidPayment(8, "mistake", 9));
        assertTrue(thrown.getMessage().toLowerCase().contains("unused advance"));
        verify(journals, never()).reverseByReferenceNo(any());
    }

    private static CustomerPayment unusedAdvance(Customer customer, BigDecimal amount) {
        return CustomerPayment.builder()
                .id(8)
                .paymentNo("CP-000008")
                .customer(customer)
                .amount(amount)
                .allocatedAmount(BigDecimal.ZERO)
                .advanceAmount(amount)
                .voided(false)
                .paymentMethod(PaymentMethod.builder().methodName("Cash").build())
                .build();
    }

    private static CustomerPayment paymentFor(Customer customer, Sale sale, BigDecimal amount) {
        CustomerPayment payment = CustomerPayment.builder()
                .id(8)
                .paymentNo("CP-000008")
                .customer(customer)
                .amount(amount)
                .allocatedAmount(amount)
                .advanceAmount(BigDecimal.ZERO)
                .voided(false)
                .build();
        payment.setAllocations(List.of(CustomerPaymentAllocation.builder()
                .customerPayment(payment)
                .sale(sale)
                .amount(amount)
                .build()));
        return payment;
    }

    private static CustomerPaymentService construct(CustomerPaymentRepository payments,
                                                    CustomerRepository customers,
                                                    SaleRepository sales,
                                                    StaffRepository staff) throws Exception {
        return construct(payments, customers, sales, staff, mock(JournalWriter.class), mock(CashDrawerService.class), mock(CreditMapper.class));
    }

    private static CustomerPaymentService construct(CustomerPaymentRepository payments,
                                                    CustomerRepository customers,
                                                    SaleRepository sales,
                                                    StaffRepository staff,
                                                    JournalWriter journals,
                                                    CashDrawerService drawer,
                                                    CreditMapper mapper) throws Exception {
        Constructor<?> constructor = Arrays.stream(CustomerPaymentService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == CustomerPaymentRepository.class) return payments;
                    if (type == CustomerRepository.class) return customers;
                    if (type == SaleRepository.class) return sales;
                    if (type == StaffRepository.class) return staff;
                    if (type == JournalWriter.class) return journals;
                    if (type == CashDrawerService.class) return drawer;
                    if (type == CreditMapper.class) return mapper;
                    return mock(type);
                })
                .toArray();
        return (CustomerPaymentService) constructor.newInstance(args);
    }
}
