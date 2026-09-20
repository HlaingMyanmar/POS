package org.sspd.servicemgmt.creditoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditApplication;
import org.sspd.servicemgmt.creditoptions.model.CustomerPayment;
import org.sspd.servicemgmt.creditoptions.model.CustomerPaymentAllocation;
import org.sspd.servicemgmt.creditoptions.repository.CustomerCreditApplicationRepository;
import org.sspd.servicemgmt.creditoptions.repository.CustomerPaymentAllocationRepository;
import org.sspd.servicemgmt.creditoptions.repository.CustomerPaymentRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.saleoptions.model.Sale;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerPaymentSaleVoidJournalTest {

    @Test
    void voidSaleClosesDirectReceiptWithoutReversingAnUnrelatedPaymentJournal() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        CustomerPayment direct = CustomerPayment.builder().id(9).paymentNo("CP-9")
                .sale(Sale.builder().id(11).build()).voided(false).build();
        when(payments.findBySaleId(11)).thenReturn(List.of(direct));
        when(payments.findByIdForUpdate(9)).thenReturn(Optional.of(direct));
        CustomerPaymentService service = construct(payments, mock(CustomerPaymentAllocationRepository.class),
                mock(CustomerCreditApplicationRepository.class), journals, mock(CustomerRepository.class));
        service.reverseAccountingForVoidedSale(11);
        assertTrue(direct.getVoided());
        assertEquals("Sale voided", direct.getVoidReason());
        verify(payments).save(direct);
        verify(journals, never()).reverseByReferenceNo("CP-9");
        service.reverseAccountingForVoidedSale(11);
        verify(payments, org.mockito.Mockito.times(1)).save(direct);
    }

    @Test
    void reverseAccountingReversesPaymentNumberJournal() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        CustomerPaymentAllocationRepository allocations = mock(CustomerPaymentAllocationRepository.class);
        CustomerCreditApplicationRepository credits = mock(CustomerCreditApplicationRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        Sale sale = Sale.builder().id(11).saleCode("INV-11").build();
        CustomerPayment payment = CustomerPayment.builder()
                .id(8).paymentNo("CP-000008").advanceAmount(BigDecimal.ZERO).voided(false).build();
        payment.setAllocations(List.of(CustomerPaymentAllocation.builder()
                .customerPayment(payment).sale(sale).amount(new BigDecimal("80")).build()));
        when(allocations.findBySaleId(11)).thenReturn(payment.getAllocations());
        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));
        when(credits.findBySaleIdOrderByIdDesc(11)).thenReturn(List.of());
        when(journals.hasActiveReference("CP-000008")).thenReturn(true);

        CustomerPaymentService service = construct(payments, allocations, credits, journals, mock(CustomerRepository.class));
        service.reverseAccountingForVoidedSale(11);

        verify(journals).reverseByReferenceNo("CP-000008");
        assertTrue(Boolean.TRUE.equals(payment.getVoided()));
    }

    @Test
    void reverseAccountingRejectsPaymentSharedWithAnotherSale() throws Exception {
        CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
        CustomerPaymentAllocationRepository allocations = mock(CustomerPaymentAllocationRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        Sale thisSale = Sale.builder().id(11).voided(false).build();
        Sale otherSale = Sale.builder().id(12).voided(false).build();
        CustomerPayment payment = CustomerPayment.builder()
                .id(8).paymentNo("CP-000008").advanceAmount(BigDecimal.ZERO).voided(false).build();
        CustomerPaymentAllocation thisAlloc = CustomerPaymentAllocation.builder()
                .customerPayment(payment).sale(thisSale).amount(new BigDecimal("40")).build();
        payment.setAllocations(List.of(thisAlloc,
                CustomerPaymentAllocation.builder()
                        .customerPayment(payment).sale(otherSale).amount(new BigDecimal("40")).build()));
        when(allocations.findBySaleId(11)).thenReturn(List.of(thisAlloc));
        when(payments.findByIdForUpdate(8)).thenReturn(Optional.of(payment));

        CustomerPaymentService service = construct(payments, allocations,
                mock(CustomerCreditApplicationRepository.class), journals, mock(CustomerRepository.class));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.reverseAccountingForVoidedSale(11));
        assertTrue(thrown.getMessage().toLowerCase().contains("other"));
        verify(journals, never()).reverseByReferenceNo("CP-000008");
    }

    @Test
    void reverseAccountingRestoresCreditApplicationAdvance() throws Exception {
        CustomerPaymentAllocationRepository allocations = mock(CustomerPaymentAllocationRepository.class);
        CustomerCreditApplicationRepository credits = mock(CustomerCreditApplicationRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        Customer customer = Customer.builder().id(4).advanceBalance(BigDecimal.ZERO).build();
        Sale sale = Sale.builder().id(11).customer(customer).build();
        when(allocations.findBySaleId(11)).thenReturn(List.of());
        when(credits.findBySaleIdOrderByIdDesc(11)).thenReturn(List.of(CustomerCreditApplication.builder()
                .applicationNo("CCA-000001").customer(customer).sale(sale).amount(new BigDecimal("25")).build()));
        when(journals.hasActiveReference("CCA-000001")).thenReturn(true);
        when(customers.findByIdForUpdate(4)).thenReturn(Optional.of(customer));

        CustomerPaymentService service = construct(mock(CustomerPaymentRepository.class), allocations,
                credits, journals, customers);
        service.reverseAccountingForVoidedSale(11);

        verify(journals).reverseByReferenceNo("CCA-000001");
        assertEquals(new BigDecimal("25"), customer.getAdvanceBalance());
    }

    @Test
    void reverseAccountingRestoresServiceJobCreditApplicationAdvance() throws Exception {
        CustomerCreditApplicationRepository credits = mock(CustomerCreditApplicationRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        Customer customer = Customer.builder().id(4).advanceBalance(BigDecimal.ZERO).build();
        when(credits.findByServiceJobIdOrderByIdDesc(21)).thenReturn(List.of(CustomerCreditApplication.builder()
                .applicationNo("CCA-000021").customer(customer).serviceJobId(21).amount(new BigDecimal("60")).build()));
        when(journals.hasActiveReference("CCA-000021")).thenReturn(true);
        when(customers.findByIdForUpdate(4)).thenReturn(Optional.of(customer));

        CustomerPaymentService service = construct(mock(CustomerPaymentRepository.class),
                mock(CustomerPaymentAllocationRepository.class), credits, journals, customers);
        service.reverseAccountingForVoidedServiceJob(21);

        verify(journals).reverseByReferenceNo("CCA-000021");
        assertEquals(new BigDecimal("60"), customer.getAdvanceBalance());
    }

    @Test
    void reverseAccountingSkipsServiceJobCreditWhenJournalAlreadyReversed() throws Exception {
        CustomerCreditApplicationRepository credits = mock(CustomerCreditApplicationRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        Customer customer = Customer.builder().id(4).advanceBalance(BigDecimal.ZERO).build();
        when(credits.findByServiceJobIdOrderByIdDesc(21)).thenReturn(List.of(CustomerCreditApplication.builder()
                .applicationNo("CCA-000021").customer(customer).serviceJobId(21).amount(new BigDecimal("60")).build()));
        when(journals.hasActiveReference("CCA-000021")).thenReturn(false);

        CustomerPaymentService service = construct(mock(CustomerPaymentRepository.class),
                mock(CustomerPaymentAllocationRepository.class), credits, journals, customers);
        service.reverseAccountingForVoidedServiceJob(21);

        verify(journals, never()).reverseByReferenceNo("CCA-000021");
        verify(customers, never()).findByIdForUpdate(4);
        assertEquals(BigDecimal.ZERO, customer.getAdvanceBalance());
    }

    private static CustomerPaymentService construct(CustomerPaymentRepository payments,
                                                    CustomerPaymentAllocationRepository allocations,
                                                    CustomerCreditApplicationRepository credits,
                                                    JournalWriter journals,
                                                    CustomerRepository customers) throws Exception {
        Constructor<?> constructor = Arrays.stream(CustomerPaymentService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == CustomerPaymentRepository.class) return payments;
                    if (type == CustomerPaymentAllocationRepository.class) return allocations;
                    if (type == CustomerCreditApplicationRepository.class) return credits;
                    if (type == JournalWriter.class) return journals;
                    if (type == CustomerRepository.class) return customers;
                    return mock(type);
                })
                .toArray();
        return (CustomerPaymentService) constructor.newInstance(args);
    }
}
