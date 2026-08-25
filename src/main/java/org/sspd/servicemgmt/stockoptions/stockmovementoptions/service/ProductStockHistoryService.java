package org.sspd.servicemgmt.stockoptions.stockmovementoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model.AdjustmentType;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model.StockAdjustment;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.repository.StockAdjustmentRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.dto.ProductStockHistoryDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductStockHistoryService {

    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final SaleRepository saleRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    @Transactional(readOnly = true)
    public ProductStockHistoryDTO getHistory(
            Integer productId,
            LocalDateTime from,
            LocalDateTime to,
            String movementTypeFilter,
            String search,
            int page,
            int size
    ) {

        Product product = null;
        if (productId != null) {
            product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
        }

        List<HistoryItem> items = collectItems(productId);

        items.sort(
                Comparator.comparing(HistoryItem::date, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(HistoryItem::productName, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(HistoryItem::referenceNumber, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        List<Product> products = product != null
                ? List.of(product)
                : productRepository.findAll();

        Map<Integer, Integer> balanceByProduct = new HashMap<>();
        int currentStock = 0;
        for (Product tracked : products) {
            int stock = currentStock(tracked);
            currentStock += stock;
            balanceByProduct.put(tracked.getId(), stock);
        }

        for (HistoryItem historyItem : items) {
            balanceByProduct.merge(
                    historyItem.productId(),
                    -(historyItem.quantityIn() - historyItem.quantityOut()),
                    Integer::sum
            );
        }

        if (from != null) {
            for (HistoryItem historyItem : items) {
                if (historyItem.date().isBefore(from)) {
                    balanceByProduct.merge(
                            historyItem.productId(),
                            historyItem.quantityIn() - historyItem.quantityOut(),
                            Integer::sum
                    );
                }
            }
        }

        int openingBalance = balanceByProduct.values().stream().mapToInt(Integer::intValue).sum();

        List<HistoryItem> filteredItems = items.stream()
                .filter(historyItem -> from == null || !historyItem.date().isBefore(from))
                .filter(historyItem -> to == null || !historyItem.date().isAfter(to))
                .filter(historyItem ->
                        movementTypeFilter == null
                                || movementTypeFilter.isBlank()
                                || movementTypeFilter.equalsIgnoreCase(historyItem.type()))
                .filter(historyItem -> matchesSearch(historyItem, search))
                .toList();

        int filteredIn = filteredItems.stream().mapToInt(HistoryItem::quantityIn).sum();
        int filteredOut = filteredItems.stream().mapToInt(HistoryItem::quantityOut).sum();

        List<ProductStockHistoryDTO.MovementRow> allRows = new ArrayList<>();
        int rowId = 1;
        for (HistoryItem historyItem : filteredItems) {
            int nextBalance = balanceByProduct.merge(
                    historyItem.productId(),
                    historyItem.quantityIn() - historyItem.quantityOut(),
                    Integer::sum
            );

            allRows.add(
                    ProductStockHistoryDTO.MovementRow.builder()
                            .id(rowId++)
                            .productId(historyItem.productId())
                            .productName(historyItem.productName())
                            .productCode(historyItem.productCode())
                            .date(historyItem.date())
                            .type(historyItem.type())
                            .referenceId(historyItem.referenceId())
                            .referenceNumber(historyItem.referenceNumber())
                            .partyName(historyItem.partyName())
                            .quantityIn(historyItem.quantityIn())
                            .quantityOut(historyItem.quantityOut())
                            .balance(nextBalance)
                            .build()
            );
        }

        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int totalElements = allRows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        return ProductStockHistoryDTO.builder()
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : "All products")
                .currentStock(currentStock)
                .openingBalance(openingBalance)
                .totalIn(filteredIn)
                .totalOut(filteredOut)
                .closingBalance(openingBalance + filteredIn - filteredOut)
                                .page(safePage)
                                .size(safeSize)
                                .totalPages(totalPages)
                                .totalElements((long) totalElements)
                                .movements(allRows.subList(fromIndex, toIndex))
                .build();
    }

        private boolean matchesSearch(HistoryItem item, String search) {
                if (search == null || search.isBlank()) return true;
                String needle = search.trim().toLowerCase();
                return contains(item.productName(), needle)
                                || contains(item.productCode(), needle)
                                || contains(item.partyName(), needle)
                                || contains(item.referenceNumber(), needle)
                                || contains(item.type(), needle);
        }

        private boolean contains(String value, String needle) {
                return value != null && value.toLowerCase().contains(needle);
        }

    private List<HistoryItem> collectItems(Integer productId) {
        List<HistoryItem> items = new ArrayList<>();

        for (Purchase purchase : purchaseRepository.findAll()) {
            if (!purchase.isEffectivelyConfirmed() || purchase.getDetails() == null) {
                continue;
            }
            purchase.getDetails().stream()
                    .filter(detail -> matchesProduct(detail.getProduct(), productId))
                    .forEach(detail -> items.add(item(
                            detail.getProduct(),
                            purchase.getPurchaseDate(),
                            "PURCHASE",
                            purchase.getId(),
                            purchase.getPurchaseCode(),
                            purchase.getSupplier() != null ? purchase.getSupplier().getName() : null,
                            positive(detail.getQty()),
                            0
                    )));
        }

        for (Sale sale : saleRepository.findAll()) {
            if (sale.getDetails() == null) {
                continue;
            }
            sale.getDetails().stream()
                    .filter(detail -> matchesProduct(detail.getProduct(), productId))
                    .forEach(detail -> items.add(item(
                            detail.getProduct(),
                            sale.getSaleDate(),
                            "SALE",
                            sale.getId(),
                            sale.getSaleCode(),
                            sale.getCustomer() != null ? sale.getCustomer().getName() : null,
                            0,
                            positive(detail.getQty())
                    )));
        }

        for (PurchaseReturn purchaseReturn : purchaseReturnRepository.findAll()) {
            if (purchaseReturn.getVoidedAt() != null || purchaseReturn.getDetails() == null) {
                continue;
            }
            purchaseReturn.getDetails().stream()
                    .filter(detail -> matchesProduct(detail.getProduct(), productId))
                    .forEach(detail -> items.add(item(
                            detail.getProduct(),
                            purchaseReturn.getReturnDate(),
                            "PURCHASE_RETURN",
                            purchaseReturn.getId(),
                            purchaseReturn.getReturnNo(),
                            purchaseReturn.getPurchase() != null
                                    && purchaseReturn.getPurchase().getSupplier() != null
                                    ? purchaseReturn.getPurchase().getSupplier().getName()
                                    : null,
                            0,
                            positive(detail.getQty())
                    )));
        }

        for (SaleReturn saleReturn : saleReturnRepository.findAllByDeletedFalse()) {
            if (saleReturn.getDetails() == null) {
                continue;
            }
            saleReturn.getDetails().stream()
                    .filter(detail -> matchesProduct(detail.getProduct(), productId))
                    .forEach(detail -> items.add(item(
                            detail.getProduct(),
                            saleReturn.getReturnDate(),
                            "SALE_RETURN",
                            saleReturn.getId(),
                            saleReturn.getReturnCode(),
                            saleReturn.getSale() != null
                                    && saleReturn.getSale().getCustomer() != null
                                    ? saleReturn.getSale().getCustomer().getName()
                                    : null,
                            positive(detail.getQty()),
                            0
                    )));
        }

        List<StockAdjustment> adjustments = productId != null
                ? stockAdjustmentRepository.findByProductId(productId)
                : stockAdjustmentRepository.findAll();

        for (StockAdjustment adjustment : adjustments) {
            if (!matchesProduct(adjustment.getProduct(), productId)) {
                continue;
            }

            int change = adjustment.getQtyChange() != null ? adjustment.getQtyChange() : 0;
            if (change == 0) {
                continue;
            }

            boolean inbound = change > 0;
            String movementType =
                    adjustment.getAdjustmentType() == AdjustmentType.DAMAGE
                            || adjustment.getAdjustmentType() == AdjustmentType.LOSS
                            ? "DAMAGE"
                            : "STOCK_ADJUSTMENT";

            items.add(item(
                    adjustment.getProduct(),
                    adjustment.getCreatedAt(),
                    movementType,
                    adjustment.getId(),
                    null,
                    adjustment.getStaff() != null ? adjustment.getStaff().getName() : adjustment.getReason(),
                    inbound ? change : 0,
                    inbound ? 0 : Math.abs(change)
            ));
        }

        return items;
    }

    private boolean matchesProduct(Product product, Integer productId) {
        if (product == null || product.getId() == null) {
            return false;
        }
        return productId == null || productId.equals(product.getId());
    }

    private HistoryItem item(
            Product product,
            LocalDateTime date,
            String type,
            Integer referenceId,
            String referenceNumber,
            String partyName,
            int quantityIn,
            int quantityOut
    ) {
        return new HistoryItem(
                product.getId(),
                product.getName(),
                product.getProductCode(),
                date != null ? date : LocalDateTime.MIN,
                type,
                referenceId,
                referenceNumber,
                partyName,
                quantityIn,
                quantityOut
        );
    }

    private int currentStock(Product product) {
        if (Boolean.FALSE.equals(product.getHasSerial())) {
            return positive(product.getStockQty());
        }
        if (product.getSerials() != null) {
            return (int) product.getSerials().stream()
                    .filter(serial ->
                            serial.getStatus() != null
                                    && "Available".equalsIgnoreCase(serial.getStatus().name()))
                    .count();
        }
        return positive(product.getStockQty());
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private record HistoryItem(
            Integer productId,
            String productName,
            String productCode,
            LocalDateTime date,
            String type,
            Integer referenceId,
            String referenceNumber,
            String partyName,
            int quantityIn,
            int quantityOut
    ) {
    }
}
