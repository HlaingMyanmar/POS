package org.sspd.servicemgmt.stockoptions.lotoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.stockoptions.lotoptions.model.StockLot;
import org.sspd.servicemgmt.stockoptions.lotoptions.repository.SaleLotAllocationRepository;
import org.sspd.servicemgmt.stockoptions.lotoptions.repository.SaleReturnLotAllocationRepository;
import org.sspd.servicemgmt.stockoptions.lotoptions.repository.StockLotRepository;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.service.WarehouseResolver;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockLotServicePurchaseReturnTest {

    @Test
    void consumePurchaseReturnDepletesOriginalPurchaseLotsFirst() {
        StockLotRepository lots = mock(StockLotRepository.class);
        StockLotService service = new StockLotService(
                lots, mock(SaleLotAllocationRepository.class), mock(SaleReturnLotAllocationRepository.class),
                mock(WarehouseResolver.class), mock(InventoryStockService.class));
        Purchase purchase = Purchase.builder().id(10).build();
        Product product = Product.builder().id(20).name("Widget").hasSerial(false).stockQty(2).build();
        StockLot first = StockLot.builder().id(1).product(product).receivedQty(3).remainingQty(3).status("AVAILABLE").build();
        StockLot second = StockLot.builder().id(2).product(product).receivedQty(4).remainingQty(4).status("AVAILABLE").build();
        when(lots.findByPurchaseDetailPurchaseId(10)).thenReturn(List.of(first, second));

        service.consumePurchaseReturn(purchase, product, 5);

        assertEquals(0, first.getRemainingQty());
        assertEquals("DEPLETED", first.getStatus());
        assertEquals(2, second.getRemainingQty());
        verify(lots).save(first);
        verify(lots).save(second);
    }

    @Test
    void restorePurchaseReturnRestoresOriginalLotCapacity() {
        StockLotRepository lots = mock(StockLotRepository.class);
        StockLotService service = new StockLotService(
                lots, mock(SaleLotAllocationRepository.class), mock(SaleReturnLotAllocationRepository.class),
                mock(WarehouseResolver.class), mock(InventoryStockService.class));
        Purchase purchase = Purchase.builder().id(10).build();
        Product product = Product.builder().id(20).name("Widget").hasSerial(false).build();
        StockLot lot = StockLot.builder().id(1).product(product).receivedQty(5).remainingQty(2).status("AVAILABLE").build();
        when(lots.findByPurchaseDetailPurchaseId(10)).thenReturn(List.of(lot));

        service.restorePurchaseReturn(purchase, product, 3);

        assertEquals(5, lot.getRemainingQty());
        assertEquals("AVAILABLE", lot.getStatus());
        verify(lots).save(lot);
    }

    @Test
    void consumePurchaseReturnNeverBorrowsAnotherPurchaseLot() {
        StockLotRepository lots = mock(StockLotRepository.class);
        StockLotService service = new StockLotService(
                lots, mock(SaleLotAllocationRepository.class), mock(SaleReturnLotAllocationRepository.class),
                mock(WarehouseResolver.class), mock(InventoryStockService.class));
        Purchase purchase = Purchase.builder().id(10).build();
        Product product = Product.builder().id(20).name("Widget").hasSerial(false).build();
        StockLot original = StockLot.builder().id(1).product(product).receivedQty(1).remainingQty(1).status("AVAILABLE").build();
        when(lots.findByPurchaseDetailPurchaseId(10)).thenReturn(List.of(original));

        assertThrows(RuntimeException.class, () -> service.consumePurchaseReturn(purchase, product, 2));

        verify(lots, never()).findSellableFefo(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }
}
