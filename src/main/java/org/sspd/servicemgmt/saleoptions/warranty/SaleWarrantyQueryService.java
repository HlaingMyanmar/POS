package org.sspd.servicemgmt.saleoptions.warranty;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.saleoptions.saledetails.repository.SaleDetailRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleWarrantyQueryService {

    private final SaleDetailRepository details;

    @Transactional(readOnly = true)
    public List<SaleWarrantyDTO> search(Integer saleId, Integer customerId, String serial, String status) {
        String serialKey = serial == null || serial.isBlank() ? null : serial.trim();
        String wanted = status == null ? "" : status.trim().toUpperCase();
        LocalDate today = LocalDate.now();
        return details.searchWarranties(saleId, customerId, serialKey).stream()
                .map(d -> toDto(d, today))
                .filter(dto -> wanted.isBlank() || wanted.equals("ALL") || wanted.equals(dto.getStatus()))
                .toList();
    }

    public SaleWarrantyDTO toDto(SaleDetail d, LocalDate today) {
        Sale sale = d.getSale();
        return SaleWarrantyDTO.builder()
                .saleDetailId(d.getId())
                .saleId(sale.getId())
                .saleCode(sale.getSaleCode())
                .saleDate(sale.getSaleDate())
                .customerId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .customerName(sale.getCustomer() != null ? sale.getCustomer().getName() : null)
                .productId(d.getProduct() != null ? d.getProduct().getId() : null)
                .productName(d.getProduct() != null ? d.getProduct().getName() : null)
                .serialNumber(d.getSerialNumber())
                .qty(d.getQty())
                .warrantyMonths(d.getWarrantyMonths())
                .warrantyStartDate(d.getWarrantyStartDate())
                .warrantyEndDate(d.getWarrantyExpiryDate())
                .status(SaleWarrantyCalculator.status(d.getWarrantyMonths(), d.getWarrantyExpiryDate(), today))
                .daysRemaining(SaleWarrantyCalculator.daysRemaining(d.getWarrantyMonths(), d.getWarrantyExpiryDate(), today))
                .build();
    }
}
