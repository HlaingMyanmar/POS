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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSerialServiceCreateStatusTest {

    @Test
    void toEntityMapsNullStatusToAvailable() {
        ProductSerialDTO dto = new ProductSerialDTO();
        dto.setSerialNumber("SN-1");
        dto.setStatus(null);

        ProductSerial entity = ProductSerialMapper.INSTANCE.toEntity(dto);

        assertEquals(SerialStatus.Available, entity.getStatus());
    }

    @Test
    void saveDefaultsNullStatusToAvailable() {
        ProductSerialRepository serials = mock(ProductSerialRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        PurchaseDetailWarrantyRepository warranties = mock(PurchaseDetailWarrantyRepository.class);
        Product product = new Product();
        product.setId(7);
        when(products.findById(7)).thenReturn(Optional.of(product));
        when(serials.existsBySerialNumber("SN-1")).thenReturn(false);
        when(warranties.findTopBySerialNumberOrderByIdDesc("SN-1")).thenReturn(Optional.empty());
        when(serials.save(any(ProductSerial.class))).thenAnswer(invocation -> {
            ProductSerial serial = invocation.getArgument(0);
            if (serial.getId() == null) {
                serial.setId(1);
            }
            return serial;
        });
        ProductSerialService service = new ProductSerialService(
                mock(SimpMessagingTemplate.class),
                serials,
                ProductSerialMapper.INSTANCE,
                products,
                warranties
        );

        ProductSerialDTO dto = new ProductSerialDTO();
        dto.setSerialNumber("SN-1");
        dto.setProductId(7);
        dto.setStatus(null);

        ProductSerialDTO saved = service.save(dto);

        assertEquals(SerialStatus.Available, saved.getStatus());
    }
}
