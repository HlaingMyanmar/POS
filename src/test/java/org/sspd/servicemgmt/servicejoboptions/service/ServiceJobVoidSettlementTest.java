package org.sspd.servicemgmt.servicejoboptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobActivityRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceJobVoidSettlementTest {

    @Test
    void voidSettlementStopsWhenLinkedSaleVoidFails() throws Exception {
        ServiceJobRepository jobs = mock(ServiceJobRepository.class);
        SaleService sales = mock(SaleService.class);
        JournalWriter journals = mock(JournalWriter.class);
        CustomerPaymentService payments = mock(CustomerPaymentService.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        ServiceJobActivityRepository activities = mock(ServiceJobActivityRepository.class);
        ServiceJob job = ServiceJob.builder()
                .id(21)
                .jobNo("JOB-21")
                .saleId(11)
                .voided(false)
                .status(ServiceJobStatus.COMPLETED)
                .paymentStatus(PaymentStatus.Paid)
                .build();
        when(jobs.findByIdForUpdate(21)).thenReturn(Optional.of(job));
        when(sales.voidSale(11, "wrong settlement")).thenThrow(new IllegalStateException("Cannot void sale with returns"));

        ServiceJobService service = construct(jobs, sales, journals, payments, drawer, activities);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidSettlement(21, "wrong settlement"));

        assertEquals("Cannot void sale with returns", thrown.getMessage());
        assertEquals(11, job.getSaleId());
        assertEquals(false, job.getVoided());
        verify(sales).voidSale(11, "wrong settlement");
        org.mockito.Mockito.verifyNoInteractions(journals, payments, drawer);
        verify(jobs, never()).save(any());
        verify(activities, never()).save(any());
    }

    @Test
    void approveEstimateDoesNotChangeSettledJobLines() throws Exception {
        ServiceJobRepository jobs = mock(ServiceJobRepository.class);
        org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine line =
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine.builder()
                        .confirmationStatus(org.sspd.servicemgmt.servicejoboptions.model.ServiceLineConfirmationStatus.RECOMMENDED)
                        .build();
        ServiceJob job = ServiceJob.builder()
                .id(21)
                .status(ServiceJobStatus.COMPLETED)
                .paymentStatus(PaymentStatus.Paid)
                .voided(false)
                .estimateApproved(false)
                .lines(java.util.List.of(line))
                .build();
        when(jobs.findById(21)).thenReturn(Optional.of(job));
        ServiceJobService service = construct(jobs, mock(SaleService.class), mock(JournalWriter.class),
                mock(CustomerPaymentService.class), mock(CashDrawerService.class), mock(ServiceJobActivityRepository.class));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.approveEstimate(21));
        assertTrue(thrown.getMessage().toLowerCase().contains("settled"));
        assertEquals(false, job.getEstimateApproved());
        assertEquals(org.sspd.servicemgmt.servicejoboptions.model.ServiceLineConfirmationStatus.RECOMMENDED,
                line.getConfirmationStatus());
        verify(jobs, never()).save(any());
    }

    @Test
    void holdEstimateRejectsSettledJob() throws Exception {
        ServiceJobRepository jobs = mock(ServiceJobRepository.class);
        ServiceJob job = ServiceJob.builder()
                .id(21)
                .status(ServiceJobStatus.COMPLETED)
                .paymentStatus(PaymentStatus.Paid)
                .voided(false)
                .estimateApproved(true)
                .build();
        when(jobs.findById(21)).thenReturn(Optional.of(job));
        ServiceJobService service = construct(jobs, mock(SaleService.class), mock(JournalWriter.class),
                mock(CustomerPaymentService.class), mock(CashDrawerService.class), mock(ServiceJobActivityRepository.class));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.holdEstimate(21, "wait"));
        assertTrue(thrown.getMessage().toLowerCase().contains("settled"));
        assertEquals(true, job.getEstimateApproved());
        verify(jobs, never()).save(any());
    }

    private ServiceJobService construct(ServiceJobRepository jobs, SaleService sales, JournalWriter journals,
            CustomerPaymentService payments, CashDrawerService drawer, ServiceJobActivityRepository activities)
            throws Exception {
        Constructor<?> constructor = Arrays.stream(ServiceJobService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == ServiceJobRepository.class) return jobs;
                    if (type == SaleService.class) return sales;
                    if (type == JournalWriter.class) return journals;
                    if (type == CustomerPaymentService.class) return payments;
                    if (type == CashDrawerService.class) return drawer;
                    if (type == ServiceJobActivityRepository.class) return activities;
                    return mock(type);
                })
                .toArray();
        return (ServiceJobService) constructor.newInstance(args);
    }
}
