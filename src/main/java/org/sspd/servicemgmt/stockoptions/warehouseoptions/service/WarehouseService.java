package org.sspd.servicemgmt.stockoptions.warehouseoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.lotoptions.model.StockLot;
import org.sspd.servicemgmt.stockoptions.lotoptions.repository.StockLotRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.dto.WarehouseDTO;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.dto.WarehouseTransferDTO;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.model.Warehouse;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.model.WarehouseTransfer;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.repository.WarehouseRepository;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.repository.WarehouseTransferRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final WarehouseTransferRepository transferRepository;
    private final StockLotRepository stockLotRepository;
    private final ProductRepository productRepository;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_PURCHASE_WAREHOUSE','CAN_ACCESS_STOCK_READ')")
    @Transactional(readOnly = true)
    public List<WarehouseDTO> list(boolean activeOnly) {
        return (activeOnly ? warehouseRepository.findByActiveTrueOrderByNameAsc() : warehouseRepository.findAllByOrderByNameAsc())
                .stream().map(this::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_WAREHOUSE')")
    @Transactional
    public WarehouseDTO save(WarehouseDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) throw new IllegalArgumentException("Warehouse name is required.");
        String code = dto.getCode() == null || dto.getCode().isBlank()
                ? dto.getName().trim().toUpperCase().replaceAll("\\s+", "-")
                : dto.getCode().trim().toUpperCase();
        Warehouse entity = dto.getId() == null ? new Warehouse()
                : warehouseRepository.findById(dto.getId()).orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
        if ((dto.getId() == null || !code.equalsIgnoreCase(entity.getCode())) && warehouseRepository.existsByCodeIgnoreCase(code))
            throw new IllegalStateException("Warehouse code already exists: " + code);
        entity.setCode(code);
        entity.setName(dto.getName().trim());
        entity.setAddress(dto.getAddress());
        entity.setActive(dto.getActive() == null || dto.getActive());
        return toDto(warehouseRepository.save(entity));
    }

    /** Full-lot FEFO transfer between named warehouses (partial only if destination has matching batch/expiry lot). */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_WAREHOUSE')")
    @Transactional
    public WarehouseTransferDTO transfer(WarehouseTransferDTO dto) {
        if (dto.getProductId() == null || dto.getFromWarehouseId() == null || dto.getToWarehouseId() == null || dto.getQty() == null || dto.getQty() <= 0)
            throw new IllegalArgumentException("Product, warehouses and positive qty are required.");
        if (dto.getFromWarehouseId().equals(dto.getToWarehouseId()))
            throw new IllegalArgumentException("From/To warehouse must differ.");
        var product = productRepository.findById(dto.getProductId()).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        var from = warehouseRepository.findById(dto.getFromWarehouseId()).orElseThrow(() -> new ResourceNotFoundException("From warehouse not found"));
        var to = warehouseRepository.findById(dto.getToWarehouseId()).orElseThrow(() -> new ResourceNotFoundException("To warehouse not found"));

        String fromName = from.getName();
        String toName = to.getName();
        List<StockLot> lots = stockLotRepository.findSellableFefo(product.getId(), java.time.LocalDate.now()).stream()
                .filter(l -> fromName.equalsIgnoreCase(normalizeWh(l.getWarehouseName())))
                .toList();
        int need = dto.getQty();
        int available = lots.stream().mapToInt(l -> l.getRemainingQty() == null ? 0 : l.getRemainingQty()).sum();
        if (available < need) throw new IllegalStateException("Insufficient warehouse stock. Available " + available);

        for (StockLot lot : lots) {
            if (need <= 0) break;
            int rem = lot.getRemainingQty() == null ? 0 : lot.getRemainingQty();
            if (rem <= 0) continue;
            int take = Math.min(need, rem);
            if (take == rem) {
                lot.setWarehouseName(toName);
                stockLotRepository.save(lot);
            } else {
                StockLot dest = stockLotRepository.findSellableFefo(product.getId(), java.time.LocalDate.now()).stream()
                        .filter(l -> toName.equalsIgnoreCase(normalizeWh(l.getWarehouseName())))
                        .filter(l -> Objects.equals(l.getBatchNumber(), lot.getBatchNumber()))
                        .filter(l -> Objects.equals(l.getExpiryDate(), lot.getExpiryDate()))
                        .findFirst().orElse(null);
                if (dest == null) {
                    throw new IllegalStateException("Partial transfer needs a matching destination lot (same batch/expiry). Move full lot qty " + rem + " instead.");
                }
                lot.setRemainingQty(rem - take);
                stockLotRepository.save(lot);
                dest.setRemainingQty(dest.getRemainingQty() + take);
                dest.setReceivedQty(dest.getReceivedQty() + take);
                stockLotRepository.save(dest);
            }
            need -= take;
        }

        WarehouseTransfer saved = transferRepository.save(WarehouseTransfer.builder()
                .transferNo("PENDING").product(product).fromWarehouse(from).toWarehouse(to)
                .qty(dto.getQty()).transferredAt(LocalDateTime.now()).transferredBy(currentUser()).remark(dto.getRemark())
                .build());
        saved.setTransferNo(String.format("WT-%06d", saved.getId()));
        return toTransferDto(transferRepository.save(saved));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_PURCHASE_WAREHOUSE','CAN_ACCESS_STOCK_READ')")
    @Transactional(readOnly = true)
    public List<WarehouseTransferDTO> transferHistory() {
        return transferRepository.findAllByOrderByIdDesc().stream().map(this::toTransferDto).toList();
    }

    private String normalizeWh(String name) { return name == null || name.isBlank() ? "Main" : name; }

    private WarehouseDTO toDto(Warehouse w) {
        return WarehouseDTO.builder().id(w.getId()).code(w.getCode()).name(w.getName()).address(w.getAddress()).active(w.getActive()).build();
    }

    private WarehouseTransferDTO toTransferDto(WarehouseTransfer t) {
        return WarehouseTransferDTO.builder()
                .id(t.getId()).transferNo(t.getTransferNo())
                .productId(t.getProduct().getId()).productName(t.getProduct().getName())
                .fromWarehouseId(t.getFromWarehouse().getId()).fromWarehouseName(t.getFromWarehouse().getName())
                .toWarehouseId(t.getToWarehouse().getId()).toWarehouseName(t.getToWarehouse().getName())
                .qty(t.getQty()).transferredAt(t.getTransferredAt()).transferredBy(t.getTransferredBy()).remark(t.getRemark())
                .build();
    }

    private String currentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }
}
