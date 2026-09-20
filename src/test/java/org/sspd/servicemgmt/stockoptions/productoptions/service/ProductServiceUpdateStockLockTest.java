package org.sspd.servicemgmt.stockoptions.productoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.stockoptions.opening.service.OpeningStockService;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ProductDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.mapper.ProductMapper;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceUpdateStockLockTest {

    @Test
    void mapperUpdateIgnoresStockAndSerialMode() {
        Product existing = Product.builder()
                .id(7)
                .name("Widget")
                .hasSerial(Boolean.FALSE)
                .stockQty(12)
                .quarantinedQty(3)
                .customerReservedQty(2)
                .build();
        ProductDTO dto = new ProductDTO();
        dto.setName("Renamed");
        dto.setHasSerial(Boolean.TRUE);
        dto.setStockQty(99);
        dto.setQuarantinedQty(8);

        ProductMapper.INSTANCE.updateEntityFromDto(dto, existing);

        assertEquals("Renamed", existing.getName());
        assertEquals(Boolean.FALSE, existing.getHasSerial());
        assertEquals(12, existing.getStockQty());
        assertEquals(3, existing.getQuarantinedQty());
        assertEquals(2, existing.getCustomerReservedQty());
    }

    @Test
    void updateKeepsStockWhenDtoTriesToRewriteQuantity() {
        Product existing = Product.builder()
                .id(7)
                .name("Widget")
                .hasSerial(Boolean.FALSE)
                .stockQty(12)
                .quarantinedQty(3)
                .customerReservedQty(0)
                .archived(Boolean.FALSE)
                .reorderLevel(0)
                .warrantyMonths(0)
                .build();
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById(7)).thenReturn(Optional.of(existing));
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductService service = service(products);
        ProductDTO dto = new ProductDTO();
        dto.setName("Renamed");
        dto.setHasSerial(Boolean.FALSE);
        dto.setStockQty(99);
        dto.setQuarantinedQty(1);

        ProductDTO saved = service.update(7, dto);

        assertEquals("Renamed", saved.getName());
        assertEquals(12, existing.getStockQty());
        assertEquals(3, existing.getQuarantinedQty());
        assertEquals(Boolean.FALSE, existing.getHasSerial());
    }

    @Test
    void updateRejectsSerialModeChangeWithoutStockWorkflow() {
        Product existing = Product.builder()
                .id(7)
                .name("Widget")
                .hasSerial(Boolean.FALSE)
                .stockQty(12)
                .quarantinedQty(0)
                .build();
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById(7)).thenReturn(Optional.of(existing));

        ProductService service = service(products);
        ProductDTO dto = new ProductDTO();
        dto.setName("Widget");
        dto.setHasSerial(Boolean.TRUE);
        dto.setStockQty(0);

        assertThrows(IllegalArgumentException.class, () -> service.update(7, dto));
        verify(products, never()).save(any());
        assertEquals(12, existing.getStockQty());
        assertEquals(Boolean.FALSE, existing.getHasSerial());
    }

    private static ProductService service(ProductRepository products) {
        return new ProductService(
                mock(SimpMessagingTemplate.class),
                products,
                ProductMapper.INSTANCE,
                mock(CategoryRepository.class),
                mock(BrandRepository.class),
                mock(UnitRepository.class),
                mock(ProductSerialRepository.class),
                mock(PurchaseRepository.class),
                mock(OpeningStockService.class),
                mock(ProductPhotoStorageService.class)
        );
    }
}
