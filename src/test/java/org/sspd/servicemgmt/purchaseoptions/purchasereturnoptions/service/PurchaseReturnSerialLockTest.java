package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper.PurchaseReturnMapper;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
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

class PurchaseReturnSerialLockTest {

    @Test
    void approveLocksSerialAndDoesNotOverwriteSoldStatus() throws Exception {
        Product product = Product.builder().id(4).name("Part").hasSerial(true).build();
        ProductSerial sold = ProductSerial.builder()
                .id(21).serialNumber("SN-1").status(SerialStatus.Sold).product(product).build();
        PurchaseReturnDetail detail = PurchaseReturnDetail.builder()
                .product(product).qty(1).serialNumber("SN-1").quarantinedQty(0).build();
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("PENDING_APPROVAL")
                .details(List.of(detail)).build();
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        when(serials.findLockedBySerialNumber("SN-1")).thenReturn(Optional.of(sold));

        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                ProductSerialRepository.class, serials));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.approve(8, new PurchaseReturnDTO()));
        assertEquals("Serial is not available to quarantine: SN-1", thrown.getMessage());
        assertEquals(SerialStatus.Sold, sold.getStatus());
        verify(serials, never()).findBySerialNumber(any());
        verify(serials, never()).save(any());
    }

    @Test
    void approveQuarantinesLockedAvailableSerial() throws Exception {
        Product product = Product.builder().id(4).name("Part").hasSerial(true).build();
        ProductSerial available = ProductSerial.builder()
                .id(21).serialNumber("SN-1").status(SerialStatus.Available).product(product).build();
        PurchaseReturnDetail detail = PurchaseReturnDetail.builder()
                .product(product).qty(1).serialNumber("SN-1").quarantinedQty(0).build();
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("PENDING_APPROVAL")
                .details(List.of(detail)).build();
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        PurchaseReturnMapper mapper = mock(PurchaseReturnMapper.class);
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        when(returns.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serials.findLockedBySerialNumber("SN-1")).thenReturn(Optional.of(available));
        when(mapper.toDto(any(PurchaseReturn.class))).thenReturn(new PurchaseReturnDTO());

        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                ProductSerialRepository.class, serials,
                PurchaseReturnMapper.class, mapper));

        service.approve(8, new PurchaseReturnDTO());

        assertEquals(SerialStatus.Quarantined, available.getStatus());
        verify(serials).findLockedBySerialNumber("SN-1");
        verify(serials).save(available);
        verify(serials, never()).findBySerialNumber(any());
    }

    @Test
    void dispatchLocksSerialAndDoesNotOverwriteNonQuarantinedStatus() throws Exception {
        Product product = Product.builder().id(4).name("Part").hasSerial(true).stockQty(1).build();
        ProductSerial sold = ProductSerial.builder()
                .id(21).serialNumber("SN-1").status(SerialStatus.Sold).product(product).build();
        PurchaseReturnDetail detail = PurchaseReturnDetail.builder()
                .product(product).qty(1).serialNumber("SN-1").quarantinedQty(1).build();
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("APPROVED")
                .returnDate(LocalDateTime.now().minusHours(2))
                .approvedAt(LocalDateTime.now().minusHours(1))
                .details(List.of(detail)).build();
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        when(serials.findLockedBySerialNumber("SN-1")).thenReturn(Optional.of(sold));

        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                ProductSerialRepository.class, serials));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setCarrier("DHL");
        request.setTrackingNo("TRK-1");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.dispatch(8, request));
        assertEquals("Serial is not quarantined: SN-1", thrown.getMessage());
        assertEquals(SerialStatus.Sold, sold.getStatus());
        verify(serials, never()).findBySerialNumber(any());
        verify(serials, never()).save(any());
    }

    private static PurchaseReturnService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseReturnService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseReturnService) constructor.newInstance(args);
    }
}
