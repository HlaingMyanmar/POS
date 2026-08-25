package org.sspd.servicemgmt.stockoptions.lotoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;
import org.sspd.servicemgmt.stockoptions.lotoptions.dto.StockLotDTO;
import org.sspd.servicemgmt.stockoptions.lotoptions.model.*;
import org.sspd.servicemgmt.stockoptions.lotoptions.repository.*;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StockLotService {
    private final StockLotRepository lots;
    private final SaleLotAllocationRepository saleAllocations;
    private final SaleReturnLotAllocationRepository returnAllocations;

    @Transactional
    public void receivePurchase(Purchase purchase) {
        for (var d : purchase.getDetails()) {
            if (Boolean.TRUE.equals(d.getProduct().getHasSerial()) || lots.findByPurchaseDetailId(d.getId()).isPresent())
                continue;
            lots.save(StockLot.builder()
                    .product(d.getProduct()).purchaseDetail(d)
                    .batchNumber(d.getBatchNumber()).expiryDate(d.getExpiryDate())
                    .warehouseName(purchase.getWarehouseName())
                    .receivedQty(d.getQty()).remainingQty(d.getQty())
                    .receivedAt(purchase.getPurchaseDate() != null ? purchase.getPurchaseDate() : LocalDateTime.now())
                    .build());
        }
    }

    @Transactional
    public void allocateSale(Sale sale) {
        String warehouse = normalizeWh(sale.getWarehouseName());
        boolean warehouseScoped = sale.getWarehouseName() != null && !sale.getWarehouseName().isBlank();
        Map<Integer, Integer> sold = new HashMap<>();
        for (var d : sale.getDetails())
            if (d.getSerialNumber() == null || d.getSerialNumber().isBlank())
                sold.merge(d.getProduct().getId(), d.getQty() == null ? 0 : d.getQty(), Integer::sum);
        for (var e : sold.entrySet()) {
            var product = sale.getDetails().stream().filter(d -> d.getProduct().getId().equals(e.getKey())).findFirst().orElseThrow().getProduct();
            List<StockLot> sellableLots = lots.findSellableFefo(e.getKey(), LocalDate.now()).stream()
                    .filter(l -> !warehouseScoped || warehouse.equalsIgnoreCase(normalizeWh(l.getWarehouseName())))
                    .toList();
            long tracked = Optional.ofNullable(lots.sumTrackedRemaining(e.getKey())).orElse(0L);
            long sellable = sellableLots.stream().mapToLong(StockLot::getRemainingQty).sum();
            long stockBefore = (product.getStockQty() == null ? 0 : product.getStockQty()) + e.getValue();
            long legacy = warehouseScoped ? 0 : Math.max(0, stockBefore - tracked);
            if (e.getValue() > sellable + legacy)
                throw new RuntimeException("Insufficient non-expired stock for: " + product.getName()
                        + (warehouseScoped ? " in warehouse " + warehouse : "")
                        + ". Expired lots cannot be sold.");
        }
        for (var d : sale.getDetails()) {
            if (d.getSerialNumber() != null && !d.getSerialNumber().isBlank()) continue;
            int needed = d.getQty() == null ? 0 : d.getQty();
            for (var lot : lots.findSellableFefo(d.getProduct().getId(), LocalDate.now())) {
                if (warehouseScoped && !warehouse.equalsIgnoreCase(normalizeWh(lot.getWarehouseName()))) continue;
                if (needed <= 0) break;
                int take = Math.min(needed, lot.getRemainingQty());
                if (take <= 0) continue;
                lot.setRemainingQty(lot.getRemainingQty() - take);
                if (lot.getRemainingQty() == 0) lot.setStatus("DEPLETED");
                lots.save(lot);
                saleAllocations.save(SaleLotAllocation.builder().saleDetail(d).stockLot(lot).allocatedQty(take).returnedQty(0).build());
                needed -= take;
            }
        }
    }

    @Transactional
    public void restoreSaleVoid(Sale sale) {
        for (var a : saleAllocations.findBySaleDetailSaleId(sale.getId())) {
            int qty = a.getAllocatedQty() - a.getReturnedQty();
            if (qty > 0) {
                var lot = a.getStockLot();
                lot.setRemainingQty(lot.getRemainingQty() + qty);
                lot.setStatus("AVAILABLE");
                lots.save(lot);
            }
            a.setReturnedQty(a.getAllocatedQty());
            saleAllocations.save(a);
        }
    }

    @Transactional
    public void restoreSaleReturn(SaleReturn ret) {
        for (var d : ret.getDetails()) {
            if (Boolean.FALSE.equals(d.getRestock())) continue;
            if (d.getSerialNumber() != null && !d.getSerialNumber().isBlank()) continue;
            int needed = d.getQty() == null ? 0 : d.getQty();
            for (var a : saleAllocations.findReturnable(ret.getSale().getId(), d.getProduct().getId())) {
                if (needed <= 0) break;
                int qty = Math.min(needed, a.getAllocatedQty() - a.getReturnedQty());
                if (qty <= 0) continue;
                var lot = a.getStockLot();
                lot.setRemainingQty(lot.getRemainingQty() + qty);
                lot.setStatus("AVAILABLE");
                lots.save(lot);
                a.setReturnedQty(a.getReturnedQty() + qty);
                saleAllocations.save(a);
                returnAllocations.save(SaleReturnLotAllocation.builder().saleReturnDetail(d).saleLotAllocation(a).qty(qty).build());
                needed -= qty;
            }
        }
    }

    @Transactional
    public void reverseSaleReturn(SaleReturn ret) {
        for (var r : returnAllocations.findBySaleReturnDetailSaleReturnId(ret.getId())) {
            var a = r.getSaleLotAllocation();
            var lot = a.getStockLot();
            if (lot.getRemainingQty() < r.getQty())
                throw new RuntimeException("Cannot void return: restored lot stock has already been consumed.");
            lot.setRemainingQty(lot.getRemainingQty() - r.getQty());
            if (lot.getRemainingQty() == 0) lot.setStatus("DEPLETED");
            lots.save(lot);
            a.setReturnedQty(Math.max(0, a.getReturnedQty() - r.getQty()));
            saleAllocations.save(a);
        }
        returnAllocations.deleteAll(returnAllocations.findBySaleReturnDetailSaleReturnId(ret.getId()));
    }

    @Transactional
    public void cancelPurchase(Purchase p) {
        for (var lot : lots.findByPurchaseDetailPurchaseId(p.getId())) {
            if (!Objects.equals(lot.getRemainingQty(), lot.getReceivedQty()))
                throw new RuntimeException("Cannot cancel purchase: batch "
                        + (lot.getBatchNumber() == null ? lot.getId() : lot.getBatchNumber())
                        + " has already been sold or consumed.");
            lot.setRemainingQty(0);
            lot.setStatus("CANCELLED");
            lots.save(lot);
        }
    }

    /**
     * Reduce original purchase lots when goods are returned to supplier (non-serial).
     * Never consume another purchase's lot: void must be able to restore the exact
     * original lot without inflating received quantity.
     */
    @Transactional
    public void consumePurchaseReturn(Purchase purchase, Product product, int qty) {
        if (product == null || Boolean.TRUE.equals(product.getHasSerial()) || qty <= 0) return;
        int need = qty;
        List<StockLot> preferred = lots.findByPurchaseDetailPurchaseId(purchase.getId()).stream()
                .filter(l -> l.getProduct() != null && Objects.equals(l.getProduct().getId(), product.getId()))
                .filter(l -> l.getRemainingQty() != null && l.getRemainingQty() > 0)
                .filter(l -> !"CANCELLED".equalsIgnoreCase(l.getStatus()))
                .sorted(Comparator
                        .comparing((StockLot l) -> l.getExpiryDate() == null)
                        .thenComparing(StockLot::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StockLot::getId))
                .toList();
        need = depleteLots(preferred, need);
        if (need > 0 && !preferred.isEmpty())
            throw new RuntimeException("Insufficient stock in the original purchase lot for return: "
                    + product.getName() + ". Short by " + need + ".");
        // Legacy purchases may predate stock_lots; aggregate product stock remains authoritative for them.
    }

    /** Restore lot qty when a purchase return is voided (non-serial). */
    @Transactional
    public void restorePurchaseReturn(Purchase purchase, Product product, int qty) {
        if (product == null || Boolean.TRUE.equals(product.getHasSerial()) || qty <= 0) return;
        int need = qty;
        List<StockLot> preferred = lots.findByPurchaseDetailPurchaseId(purchase.getId()).stream()
                .filter(l -> l.getProduct() != null && Objects.equals(l.getProduct().getId(), product.getId()))
                .filter(l -> !"CANCELLED".equalsIgnoreCase(l.getStatus()))
                .sorted(Comparator.comparing(StockLot::getId))
                .toList();
        for (StockLot lot : preferred) {
            if (need <= 0) break;
            int rem = lot.getRemainingQty() == null ? 0 : lot.getRemainingQty();
            int received = lot.getReceivedQty() == null ? 0 : lot.getReceivedQty();
            int capacity = Math.max(0, received - rem);
            int add = Math.min(need, capacity);
            if (add <= 0) continue;
            lot.setRemainingQty(rem + add);
            lot.setStatus("AVAILABLE");
            lots.save(lot);
            need -= add;
        }
        if (need > 0 && !preferred.isEmpty())
            throw new RuntimeException("Cannot void return: original lot capacity is insufficient for " + product.getName());
        // Legacy purchases without stock_lots require no lot restoration.
    }

    private int depleteLots(List<StockLot> source, int need) {
        for (StockLot lot : source) {
            if (need <= 0) break;
            int rem = lot.getRemainingQty() == null ? 0 : lot.getRemainingQty();
            if (rem <= 0) continue;
            int take = Math.min(need, rem);
            lot.setRemainingQty(rem - take);
            if (lot.getRemainingQty() == 0) lot.setStatus("DEPLETED");
            lots.save(lot);
            need -= take;
        }
        return need;
    }

    @Transactional(readOnly = true)
    public List<StockLotDTO> expiring(int days) {
        LocalDate now = LocalDate.now();
        return lots.findExpiring(now.plusDays(Math.max(0, days))).stream().map(l -> {
            long d = ChronoUnit.DAYS.between(now, l.getExpiryDate());
            return StockLotDTO.builder().id(l.getId()).productId(l.getProduct().getId())
                    .productCode(l.getProduct().getProductCode()).productName(l.getProduct().getName())
                    .purchaseId(l.getPurchaseDetail().getPurchase().getId())
                    .purchaseCode(l.getPurchaseDetail().getPurchase().getPurchaseCode())
                    .batchNumber(l.getBatchNumber()).expiryDate(l.getExpiryDate()).warehouseName(l.getWarehouseName())
                    .receivedQty(l.getReceivedQty()).remainingQty(l.getRemainingQty()).daysToExpiry(d)
                    .alertLevel(d < 0 ? "EXPIRED" : d <= 7 ? "CRITICAL" : d <= 30 ? "WARNING" : "UPCOMING").build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<org.sspd.servicemgmt.stockoptions.lotoptions.dto.WarehouseBalanceDTO> warehouseBalances() {
        Map<String, org.sspd.servicemgmt.stockoptions.lotoptions.dto.WarehouseBalanceDTO> grouped = new LinkedHashMap<>();
        for (var l : lots.findByRemainingQtyGreaterThanAndStatusIn(0, List.of("AVAILABLE", "DEPLETED"))) {
            String warehouse = l.getWarehouseName() == null || l.getWarehouseName().isBlank() ? "Main" : l.getWarehouseName().trim();
            Integer productId = l.getProduct().getId();
            String key = warehouse + "|" + productId;
            var row = grouped.computeIfAbsent(key, k -> org.sspd.servicemgmt.stockoptions.lotoptions.dto.WarehouseBalanceDTO.builder()
                    .warehouseName(warehouse).productId(productId).productCode(l.getProduct().getProductCode())
                    .productName(l.getProduct().getName()).remainingQty(0).receivedQty(0).lotCount(0).build());
            row.setRemainingQty(row.getRemainingQty() + (l.getRemainingQty() == null ? 0 : l.getRemainingQty()));
            row.setReceivedQty(row.getReceivedQty() + (l.getReceivedQty() == null ? 0 : l.getReceivedQty()));
            row.setLotCount(row.getLotCount() + 1);
        }
        return new ArrayList<>(grouped.values());
    }

    private String normalizeWh(String name) {
        return name == null || name.isBlank() ? "Main" : name.trim();
    }
}
