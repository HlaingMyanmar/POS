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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceCreateArchivedTest {

    @Test
    void toEntityMapsNullArchivedToFalse() {
        ProductDTO dto = new ProductDTO();
        dto.setName("Widget");
        dto.setArchived(null);
        dto.setQuarantinedQty(null);

        Product entity = ProductMapper.INSTANCE.toEntity(dto);

        assertEquals(Boolean.FALSE, entity.getArchived());
        assertEquals(0, entity.getQuarantinedQty());
    }

    @Test
    void saveDefaultsNullArchivedToFalse() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId(1);
            }
            return product;
        });

        ProductService service = new ProductService(
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

        ProductDTO dto = new ProductDTO();
        dto.setName("Widget");
        dto.setArchived(null);
        dto.setQuarantinedQty(null);

        ProductDTO saved = service.save(dto);

        assertFalse(Boolean.TRUE.equals(saved.getArchived()));
        assertEquals(Boolean.FALSE, saved.getArchived());
        assertEquals(0, saved.getQuarantinedQty());
    }
}
