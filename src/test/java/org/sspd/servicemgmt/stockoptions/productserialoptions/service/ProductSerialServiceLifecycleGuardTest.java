package org.sspd.servicemgmt.stockoptions.productserialoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.repository.PurchaseDetailWarrantyRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.dto.ProductSerialDTO;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.mapper.ProductSerialMapper;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSerialServiceLifecycleGuardTest {

    @Test
    void updateRejectsSoldStatusFlipAndKeepsIdentity() {
        Product product = product(3);
        ProductSerial existing = serial(9, "SN-SOLD", SerialStatus.Sold, product, 12);
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        SerialDocumentHistory history = mock(SerialDocumentHistory.class);
        when(serials.findById(9)).thenReturn(Optional.of(existing));
        when(history.exists("SN-SOLD")).thenReturn(true);

        ProductSerialService service = service(serials, history);
        ProductSerialDTO dto = new ProductSerialDTO();
        dto.setSerialNumber("SN-NEW");
        dto.setProductId(99);
        dto.setStatus(SerialStatus.Available);
        dto.setWarrantyMonths(24);

        assertThrows(IllegalStateException.class, () -> service.update(9, dto));
        verify(serials, never()).save(any());
        assertEquals(SerialStatus.Sold, existing.getStatus());
        assertEquals("SN-SOLD", existing.getSerialNumber());
        assertEquals(3, existing.getProduct().getId());
        assertEquals(12, existing.getWarrantyMonths());
    }

    @Test
    void updateAllowsConditionOnSoldSerialWithoutRewritingMasterFields() {
        Product product = product(3);
        ProductSerial existing = serial(9, "SN-SOLD", SerialStatus.Sold, product, 12);
        existing.setCondition("scratched");
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        SerialDocumentHistory history = mock(SerialDocumentHistory.class);
        PurchaseDetailWarrantyRepository warranties = mock(PurchaseDetailWarrantyRepository.class);
        when(serials.findById(9)).thenReturn(Optional.of(existing));
        when(serials.save(any(ProductSerial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(history.exists("SN-SOLD")).thenReturn(true);
        when(warranties.findTopBySerialNumberOrderByIdDesc("SN-SOLD")).thenReturn(Optional.empty());

        ProductSerialService service = service(serials, history, warranties);
        ProductSerialDTO dto = new ProductSerialDTO();
        dto.setSerialNumber("SN-SOLD");
        dto.setProductId(3);
        dto.setStatus(SerialStatus.Sold);
        dto.setWarrantyMonths(12);
        dto.setCondition("cleaned");

        ProductSerialDTO saved = service.update(9, dto);

        assertEquals("cleaned", saved.getCondition());
        assertEquals(SerialStatus.Sold, existing.getStatus());
        assertEquals("SN-SOLD", existing.getSerialNumber());
        assertEquals(12, existing.getWarrantyMonths());
    }

    @Test
    void deleteRejectsSoldAndHistoricalAvailableSerials() {
        Product product = product(3);
        ProductSerial sold = serial(9, "SN-SOLD", SerialStatus.Sold, product, 0);
        ProductSerial returned = serial(10, "SN-RET", SerialStatus.Available, product, 0);
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        SerialDocumentHistory history = mock(SerialDocumentHistory.class);
        when(serials.findById(9)).thenReturn(Optional.of(sold));
        when(serials.findById(10)).thenReturn(Optional.of(returned));
        when(history.exists("SN-SOLD")).thenReturn(true);
        when(history.exists("SN-RET")).thenReturn(true);

        ProductSerialService service = service(serials, history);
        assertThrows(IllegalStateException.class, () -> service.delete(9));
        assertThrows(IllegalStateException.class, () -> service.delete(10));
        verify(serials, never()).delete(any());
    }

    @Test
    void deleteAllowsUnusedAvailableSerial() {
        Product product = product(3);
        ProductSerial unused = serial(11, "SN-NEW", SerialStatus.Available, product, 0);
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        SerialDocumentHistory history = mock(SerialDocumentHistory.class);
        when(serials.findById(11)).thenReturn(Optional.of(unused));
        when(history.exists("SN-NEW")).thenReturn(false);

        ProductSerialService service = service(serials, history);
        service.delete(11);
        verify(serials).delete(unused);
    }

    @Test
    void saveRejectsNonAvailableStatus() {
        ProductSerialService service = service(mock(ProductSerialRepository.class), mock(SerialDocumentHistory.class));
        ProductSerialDTO dto = new ProductSerialDTO();
        dto.setSerialNumber("SN-1");
        dto.setProductId(7);
        dto.setStatus(SerialStatus.Sold);
        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
    }

    private static ProductSerialService service(ProductSerialRepository serials, SerialDocumentHistory history) {
        return service(serials, history, mock(PurchaseDetailWarrantyRepository.class));
    }

    private static ProductSerialService service(
            ProductSerialRepository serials,
            SerialDocumentHistory history,
            PurchaseDetailWarrantyRepository warranties
    ) {
        return new ProductSerialService(
                mock(SimpMessagingTemplate.class),
                serials,
                ProductSerialMapper.INSTANCE,
                mock(ProductRepository.class),
                warranties,
                history
        );
    }

    private static Product product(int id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Widget");
        return product;
    }

    private static ProductSerial serial(int id, String number, SerialStatus status, Product product, int months) {
        return ProductSerial.builder()
                .id(id)
                .serialNumber(number)
                .status(status)
                .product(product)
                .warrantyMonths(months)
                .warrantyStartDate(LocalDate.of(2026, 1, 1))
                .warrantyEndDate(LocalDate.of(2026, 1, 1).plusMonths(Math.max(months, 0)))
                .build();
    }
}
