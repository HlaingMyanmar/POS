package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper.PurchaseReturnMapper;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.repository.PurchaseReturnDetailRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseReturnDetailService {

    private final PurchaseReturnDetailRepository detailRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final ProductRepository productRepository;
    private final PurchaseReturnMapper mapper;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_CREATE')")
    @Transactional
    public PurchaseReturnDetailDTO save(PurchaseReturnDetailDTO dto) {
        if (dto.getReturnId() == null) {
            throw new RuntimeException("Return ID is required");
        }
        PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(dto.getReturnId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found"));

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (dto.getSerialNumbers() != null && !Objects.equals(dto.getSerialNumbers().size(), dto.getQty())) {
            throw new RuntimeException("Serial count must match qty");
        }

        BigDecimal subtotal = dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQty()));

        PurchaseReturnDetail detail = PurchaseReturnDetail.builder()
                .purchaseReturn(purchaseReturn)
                .product(product)
                .qty(dto.getQty())
                .unitPrice(dto.getUnitPrice())
                .subtotal(subtotal)
                .serialNumber(joinSerials(dto.getSerialNumbers()))
                .build();

        PurchaseReturnDetail saved = detailRepository.save(detail);
        recalcReturnTotal(purchaseReturn.getId());
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_READ')")
    @Transactional(readOnly = true)
    public List<PurchaseReturnDetailDTO> findAll() {
        return detailRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_READ')")
    @Transactional(readOnly = true)
    public PurchaseReturnDetailDTO findById(Integer id) {
        PurchaseReturnDetail detail = detailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return detail not found with id: " + id));
        return mapper.toDto(detail);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_UPDATE')")
    @Transactional
    public PurchaseReturnDetailDTO update(Integer id, PurchaseReturnDetailDTO dto) {
        PurchaseReturnDetail existing = detailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return detail not found with id: " + id));

        Integer oldReturnId = existing.getPurchaseReturn().getId();

        if (dto.getReturnId() != null && !dto.getReturnId().equals(oldReturnId)) {
            PurchaseReturn newReturn = purchaseReturnRepository.findById(dto.getReturnId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found"));
            existing.setPurchaseReturn(newReturn);
        }

        if (dto.getProductId() != null) {
            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            existing.setProduct(product);
        }

        if (dto.getQty() != null) {
            existing.setQty(dto.getQty());
        }
        if (dto.getUnitPrice() != null) {
            existing.setUnitPrice(dto.getUnitPrice());
        }
        if (dto.getSerialNumbers() != null) {
            if (!Objects.equals(dto.getSerialNumbers().size(), existing.getQty())) {
                throw new RuntimeException("Serial count must match qty");
            }
            existing.setSerialNumber(joinSerials(dto.getSerialNumbers()));
        }

        BigDecimal subtotal = existing.getUnitPrice().multiply(BigDecimal.valueOf(existing.getQty()));
        existing.setSubtotal(subtotal);

        PurchaseReturnDetail saved = detailRepository.save(existing);

        recalcReturnTotal(oldReturnId);
        recalcReturnTotal(saved.getPurchaseReturn().getId());

        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_DELETE')")
    @Transactional
    public void delete(Integer id) {
        PurchaseReturnDetail existing = detailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return detail not found with id: " + id));
        Integer returnId = existing.getPurchaseReturn().getId();
        detailRepository.delete(existing);
        recalcReturnTotal(returnId);
    }

    private void recalcReturnTotal(Integer returnId) {
        PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found"));

        BigDecimal total = detailRepository.findAllByPurchaseReturnId(returnId).stream()
                .map(PurchaseReturnDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        purchaseReturn.setTotalReturnAmount(total);
        purchaseReturnRepository.save(purchaseReturn);
    }

    private String joinSerials(List<String> serials) {
        if (serials == null) {
            return null;
        }
        return serials.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
    }
}
