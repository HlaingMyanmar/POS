package org.sspd.servicemgmt.saleoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.mapper.SaleMapper;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleUpdatePaymentLockTest {

    @Test
    void updateLocksSaleRowAndRejectsPaidAmountOverwrite() throws Exception {
        Sale existing = sale(11, "150.00");
        SaleRepository sales = mock(SaleRepository.class);
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(existing));

        SaleService service = construct(Map.of(SaleRepository.class, sales));
        SaleDTO dto = new SaleDTO();
        dto.setRemark("edit");
        dto.setPaidAmount(new BigDecimal("50.00"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.update(11, dto));
        assertEquals("Sale payment can only change through the due-payment workflow", thrown.getMessage());
        assertEquals(new BigDecimal("150.00"), existing.getPaidAmount());
        verify(sales).findLockedWithDetails(11);
        verify(sales, never()).findById(11);
        verify(sales, never()).save(any());
    }

    @Test
    void updateKeepsLockedPaidAmountWhenCashierAlreadyCollected() throws Exception {
        Sale existing = sale(11, "150.00");
        SaleRepository sales = mock(SaleRepository.class);
        SaleMapper mapper = mock(SaleMapper.class);
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(existing));
        when(sales.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(Sale.class))).thenReturn(new SaleDTO());

        SaleService service = construct(Map.of(
                SaleRepository.class, sales,
                SaleMapper.class, mapper));
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "largeCreditAlertThreshold", new BigDecimal("1000000"));
        SaleDTO dto = new SaleDTO();
        dto.setRemark("note after payment");
        dto.setPaidAmount(new BigDecimal("150.00"));

        service.update(11, dto);

        assertEquals(new BigDecimal("150.00"), existing.getPaidAmount());
        assertEquals(0, existing.getDueAmount().compareTo(new BigDecimal("50.00")));
        verify(sales).findLockedWithDetails(11);
        verify(sales, never()).findById(11);
    }

    private static Sale sale(int id, String paid) {
        Customer customer = Customer.builder().id(7).creditHold(false).blacklisted(false).build();
        SaleDetail detail = SaleDetail.builder().subtotal(new BigDecimal("200.00")).build();
        List<SaleDetail> details = new ArrayList<>();
        details.add(detail);
        return Sale.builder()
                .id(id)
                .saleDate(LocalDateTime.of(2026, 9, 1, 10, 0))
                .voided(false)
                .customer(customer)
                .details(details)
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .deliveryCharge(BigDecimal.ZERO)
                .paidAmount(new BigDecimal(paid))
                .dueAmount(new BigDecimal("50.00"))
                .dueDate(LocalDate.of(2026, 10, 1))
                .build();
    }

    private static SaleService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(SaleService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (SaleService) constructor.newInstance(args);
    }
}
