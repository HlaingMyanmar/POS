package org.sspd.servicemgmt.quotationoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.quotationoptions.dto.QuotationDTO;
import org.sspd.servicemgmt.quotationoptions.model.*;
import org.sspd.servicemgmt.quotationoptions.repository.QuotationRepository;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuotationService {
    private final QuotationRepository repository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SaleService saleService;
    private final SaleRepository saleRepository;

    @Transactional
    public QuotationDTO create(QuotationDTO dto) {
        Quotation quote = new Quotation();
        quote.setQuotationCode("PENDING");
        quote.setCustomer(customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
        quote.setQuotationDate(dto.getQuotationDate() != null ? dto.getQuotationDate() : LocalDateTime.now());
        quote.setValidUntil(dto.getValidUntil() != null ? dto.getValidUntil() : LocalDate.now().plusDays(30));
        quote.setStatus(QuotationStatus.DRAFT);
        quote.setTerms(dto.getTerms());
        quote.setRemark(dto.getRemark());
        applyDetailsAndTotals(quote, dto);
        Quotation saved = repository.save(quote);
        saved.setQuotationCode(String.format("QUO-%06d", saved.getId()));
        return toDto(repository.save(saved));
    }

    @Transactional
    public QuotationDTO update(Integer id, QuotationDTO dto) {
        Quotation quote = get(id);
        requireDraft(quote);
        if (dto.getCustomerId() != null) quote.setCustomer(customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
        if (dto.getQuotationDate() != null) quote.setQuotationDate(dto.getQuotationDate());
        if (dto.getValidUntil() != null) quote.setValidUntil(dto.getValidUntil());
        if (dto.getTerms() != null) quote.setTerms(dto.getTerms());
        if (dto.getRemark() != null) quote.setRemark(dto.getRemark());
        if (dto.getDetails() != null) applyDetailsAndTotals(quote, dto);
        return toDto(repository.save(quote));
    }

    @Transactional(readOnly = true)
    public List<QuotationDTO> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public QuotationDTO findById(Integer id) { return toDto(get(id)); }

    @Transactional
    public QuotationDTO changeStatus(Integer id, QuotationStatus target) {
        Quotation quote = get(id);
        if (quote.getStatus() == QuotationStatus.CONVERTED_TO_SALE)
            throw new IllegalStateException("Converted quotation cannot be changed");
        if (target == QuotationStatus.CONVERTED_TO_SALE)
            throw new IllegalArgumentException("Use the convert-to-sale endpoint");
        if (target == QuotationStatus.EXPIRED && !quote.getValidUntil().isBefore(LocalDate.now()))
            throw new IllegalStateException("Quotation is not expired yet");
        quote.setStatus(target);
        return toDto(repository.save(quote));
    }

    @Transactional
    public SaleDTO convertToSale(Integer id, SaleDTO saleRequest) {
        Quotation quote = get(id);
        if (quote.getStatus() == QuotationStatus.CONVERTED_TO_SALE || quote.getConvertedSaleId() != null)
            throw new IllegalStateException("Quotation has already been converted");
        if (quote.getStatus() != QuotationStatus.ACCEPTED)
            throw new IllegalStateException("Only an accepted quotation can be converted");
        if (quote.getValidUntil().isBefore(LocalDate.now())) {
            quote.setStatus(QuotationStatus.EXPIRED);
            repository.save(quote);
            throw new IllegalStateException("Quotation has expired");
        }

        SaleDTO request = saleRequest != null ? saleRequest : new SaleDTO();
        request.setCustomerId(quote.getCustomer().getId());
        request.setDiscountAmount(quote.getDiscountAmount());
        request.setRemark(appendReference(request.getRemark(), quote.getQuotationCode()));
        request.setDetails(toSaleDetails(quote, request.getDetails()));
        SaleDTO created = saleService.save(request);

        Sale sale = saleRepository.findById(created.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Converted sale not found"));
        sale.setQuotationId(quote.getId());
        sale.setQuotationCode(quote.getQuotationCode());
        saleRepository.save(sale);
        quote.setStatus(QuotationStatus.CONVERTED_TO_SALE);
        quote.setConvertedSaleId(sale.getId());
        quote.setConvertedBy(actor());
        quote.setConvertedAt(LocalDateTime.now());
        repository.save(quote);
        created.setQuotationId(quote.getId());
        created.setQuotationCode(quote.getQuotationCode());
        return created;
    }

    private void applyDetailsAndTotals(Quotation quote, QuotationDTO dto) {
        if (dto.getDetails() == null || dto.getDetails().isEmpty())
            throw new IllegalArgumentException("Quotation details are required");
        quote.getDetails().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (SaleDetailDTO line : dto.getDetails()) {
            if (line.getQty() == null || line.getQty() <= 0 || line.getUnitPrice() == null || line.getUnitPrice().signum() < 0)
                throw new IllegalArgumentException("Valid quantity and unit price are required");
            var product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            BigDecimal discount = line.getDiscountAmount() != null ? line.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal gross = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQty()));
            if (discount.signum() < 0 || discount.compareTo(gross) > 0)
                throw new IllegalArgumentException("Invalid line discount");
            BigDecimal subtotal = gross.subtract(discount);
            quote.getDetails().add(QuotationDetail.builder().quotation(quote).product(product).qty(line.getQty())
                    .unitPrice(line.getUnitPrice()).discountAmount(discount).subtotal(subtotal).build());
            total = total.add(subtotal);
        }
        BigDecimal discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.signum() < 0 || discount.compareTo(total) > 0)
            throw new IllegalArgumentException("Invalid quotation discount");
        quote.setTotalAmount(total);
        quote.setDiscountAmount(discount);
        quote.setNetAmount(total.subtract(discount));
    }

    private List<SaleDetailDTO> toSaleDetails(Quotation quote, List<SaleDetailDTO> conversionDetails) {
        Map<Integer, SaleDetailDTO> overrides = new HashMap<>();
        if (conversionDetails != null) conversionDetails.forEach(d -> overrides.put(d.getProductId(), d));
        List<SaleDetailDTO> result = new ArrayList<>();
        for (QuotationDetail detail : quote.getDetails()) {
            SaleDetailDTO line = new SaleDetailDTO();
            line.setProductId(detail.getProduct().getId());
            line.setQty(detail.getQty());
            line.setUnitPrice(detail.getUnitPrice());
            line.setDiscountAmount(detail.getDiscountAmount());
            SaleDetailDTO override = overrides.get(detail.getProduct().getId());
            if (override != null) {
                line.setSerialNumbers(override.getSerialNumbers());
                line.setWarrantyMonths(override.getWarrantyMonths());
            }
            result.add(line);
        }
        return result;
    }

    private QuotationDTO toDto(Quotation q) {
        QuotationDTO dto = new QuotationDTO();
        dto.setId(q.getId()); dto.setQuotationCode(q.getQuotationCode());
        dto.setCustomerId(q.getCustomer().getId()); dto.setCustomerName(q.getCustomer().getName());
        dto.setQuotationDate(q.getQuotationDate()); dto.setValidUntil(q.getValidUntil());
        dto.setStatus(q.getStatus().name()); dto.setTotalAmount(q.getTotalAmount());
        dto.setDiscountAmount(q.getDiscountAmount()); dto.setNetAmount(q.getNetAmount());
        dto.setTerms(q.getTerms()); dto.setRemark(q.getRemark()); dto.setConvertedSaleId(q.getConvertedSaleId());
        dto.setConvertedBy(q.getConvertedBy()); dto.setConvertedAt(q.getConvertedAt());
        dto.setDetails(q.getDetails().stream().map(d -> {
            SaleDetailDTO line = new SaleDetailDTO();
            line.setProductId(d.getProduct().getId()); line.setProductName(d.getProduct().getName());
            line.setQty(d.getQty()); line.setUnitPrice(d.getUnitPrice()); line.setDiscountAmount(d.getDiscountAmount());
            line.setSubtotal(d.getSubtotal()); return line;
        }).toList());
        return dto;
    }

    private Quotation get(Integer id) { return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Quotation not found")); }
    private void requireDraft(Quotation q) { if (q.getStatus() != QuotationStatus.DRAFT)
        throw new IllegalStateException("Only draft quotations can be edited"); }
    private String actor() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null ? auth.getName() : "system"; }
    private String appendReference(String remark, String code) { return (remark == null || remark.isBlank() ? "" : remark + " | ") + "Converted from " + code; }
}
