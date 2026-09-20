package org.sspd.servicemgmt.accountingoptions.periodlock;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.dto.SaleReturnDTO;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.service.SaleReturnService;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.servicejoboptions.dto.SettleDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.service.ServiceJobTeamService;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClosedPeriodRevenueFlowTest {

    private final LocalDateTime closed = LocalDateTime.of(2024, 3, 1, 9, 0);

    @Test
    void saleCreateUpdateAndVoidHonorPeriodLock() throws Exception {
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        SaleRepository sales = mock(SaleRepository.class);
        SaleService service = construct(SaleService.class, Map.of(
                AccountingPeriodGuard.class, periodGuard,
                CustomerRepository.class, customers,
                SaleRepository.class, sales));

        SaleDTO dto = new SaleDTO();
        dto.setCustomerId(7);
        dto.setSaleDate(closed);
        dto.setDetails(List.of(new SaleDetailDTO()));
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(closed, "create sale");
        assertThrows(IllegalStateException.class, () -> service.save(dto));
        verify(customers, never()).findById(any());

        org.sspd.servicemgmt.saleoptions.model.Sale existing = org.sspd.servicemgmt.saleoptions.model.Sale.builder()
                .id(11).saleDate(closed).voided(false).build();
        when(sales.findById(11)).thenReturn(Optional.of(existing));
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(existing));
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(closed, "update sale");
        assertThrows(IllegalStateException.class, () -> service.update(11, new SaleDTO()));
        verify(sales, never()).save(any());

        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(eq(closed), eq("void sale"));
        assertThrows(IllegalStateException.class, () -> service.voidSale(11, "void"));
        verify(sales, never()).save(any());
    }

    @Test
    void saleReturnCreateAndVoidHonorPeriodLock() throws Exception {
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        SaleReturnRepository returns = mock(SaleReturnRepository.class);
        SaleReturnService service = construct(SaleReturnService.class, Map.of(
                AccountingPeriodGuard.class, periodGuard,
                SaleReturnRepository.class, returns));

        SaleReturnDTO dto = new SaleReturnDTO();
        dto.setSaleId(3);
        dto.setReturnDate(closed);
        dto.setDetails(List.of(new org.sspd.servicemgmt.saleoptions.salereturndetails.dto.SaleReturnDetailDTO()));
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(closed, "create sale return");
        assertThrows(IllegalStateException.class, () -> service.save(dto));

        org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn existing =
                org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn.builder()
                        .id(4).returnDate(closed).deleted(false).status("COMPLETED").build();
        when(returns.findByIdForUpdate(4)).thenReturn(Optional.of(existing));
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(closed, "void sale return");
        assertThrows(IllegalStateException.class, () -> service.voidReturn(4, "void"));
    }

    @Test
    void serviceJobSettleAndVoidHonorPeriodLock() throws Exception {
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        ServiceJobRepository jobs = mock(ServiceJobRepository.class);
        ServiceJobTeamService team = mock(ServiceJobTeamService.class);
        ServiceJobService service = construct(ServiceJobService.class, Map.of(
                AccountingPeriodGuard.class, periodGuard,
                ServiceJobRepository.class, jobs,
                ServiceJobTeamService.class, team));
        ServiceJob job = ServiceJob.builder().id(21).jobNo("SJ-21").completedDate(closed).build();
        when(jobs.findByIdForUpdate(21)).thenReturn(Optional.of(job));

        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(any(LocalDateTime.class), eq("settle service job"));
        assertThrows(IllegalStateException.class, () -> service.settle(21, new SettleDTO()));
        verify(team, never()).assertCanComplete(anyInt());

        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(eq(closed), contains("void service job settlement"));
        assertThrows(IllegalStateException.class, () -> service.voidSettlement(21, "void"));
        verify(jobs, never()).save(any());
    }

    private static <T> T construct(Class<T> type, Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(type.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(parameter -> overrides.containsKey(parameter) ? overrides.get(parameter) : mock(parameter))
                .toArray();
        return type.cast(constructor.newInstance(args));
    }
}
