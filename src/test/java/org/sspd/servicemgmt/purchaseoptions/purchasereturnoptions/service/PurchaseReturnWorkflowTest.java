package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper.PurchaseReturnMapper;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PurchaseReturnWorkflowTest {
    @Test
    void approvalQuarantinesWithoutConsumingStock() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        PurchaseReturnMapper mapper = mock(PurchaseReturnMapper.class);
        Product product = Product.builder().id(4).name("Part").stockQty(10).quarantinedQty(0).hasSerial(false).build();
        PurchaseReturnDetail detail = PurchaseReturnDetail.builder().product(product).qty(3).quarantinedQty(0).build();
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("PENDING_APPROVAL")
                .purchase(Purchase.builder().id(2).build()).details(List.of(detail)).build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        when(returns.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(PurchaseReturn.class))).thenReturn(new PurchaseReturnDTO());
        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                ProductRepository.class, products,
                PurchaseReturnMapper.class, mapper));

        service.approve(8, new PurchaseReturnDTO());

        assertEquals("APPROVED", entity.getStatus());
        assertEquals(10, product.getStockQty());
        assertEquals(3, product.getQuarantinedQty());
        assertEquals(3, detail.getQuarantinedQty());
        verify(products).save(product);
    }

    @Test
    void dispatchRejectsUnapprovedReturn() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("PENDING_APPROVAL").build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        PurchaseReturnService service = construct(Map.of(PurchaseReturnRepository.class, returns));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setCarrier("Carrier");
        request.setTrackingNo("TRACK");

        assertThrows(IllegalStateException.class, () -> service.dispatch(8, request));
    }

    private PurchaseReturnService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseReturnService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseReturnService) constructor.newInstance(args);
    }
}
