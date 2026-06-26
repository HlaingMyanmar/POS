package org.sspd.servicemgmt.companysettingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.model.CompanySettings;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    private final CompanySettingsRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompanySettingsDTO getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional
    public CompanySettingsDTO saveSettings(CompanySettingsDTO dto) {
        CompanySettings s = getOrCreate();
        s.setCompanyName(dto.getCompanyName() != null && !dto.getCompanyName().isBlank()
            ? dto.getCompanyName().trim()
            : s.getCompanyName());
        s.setCompanyAddress(dto.getCompanyAddress());
        s.setCompanyPhone(dto.getCompanyPhone());
        s.setCompanyEmail(dto.getCompanyEmail());
        s.setInvoiceTitle(dto.getInvoiceTitle() != null && !dto.getInvoiceTitle().isBlank()
            ? dto.getInvoiceTitle().trim()
            : "Sales Invoice");
        s.setFooterNote(dto.getFooterNote());
        s.setTaglineMm(dto.getTaglineMm());
        s.setLogoBase64(dto.getLogoBase64());
        s.setVoucherConfigJson(dto.getVoucherConfigJson());
        if (dto.getSalePrefix() != null) s.setSalePrefix(dto.getSalePrefix().isBlank() ? "INV" : dto.getSalePrefix().trim());
        if (dto.getSaleDigits() != null && dto.getSaleDigits() >= 1 && dto.getSaleDigits() <= 10) s.setSaleDigits(dto.getSaleDigits());
        if (dto.getPurchasePrefix() != null) s.setPurchasePrefix(dto.getPurchasePrefix().isBlank() ? "PUR" : dto.getPurchasePrefix().trim());
        if (dto.getPurchaseDigits() != null && dto.getPurchaseDigits() >= 1 && dto.getPurchaseDigits() <= 10) s.setPurchaseDigits(dto.getPurchaseDigits());
        if (dto.getBookingPrefix() != null) s.setBookingPrefix(dto.getBookingPrefix().isBlank() ? "BK" : dto.getBookingPrefix().trim());
        if (dto.getBookingDigits() != null && dto.getBookingDigits() >= 1 && dto.getBookingDigits() <= 10) s.setBookingDigits(dto.getBookingDigits());
        return toDto(repository.save(s));
    }

    private CompanySettings getOrCreate() {
        List<CompanySettings> all = repository.findAll();
        if (!all.isEmpty()) return all.get(0);
        return repository.save(CompanySettings.builder()
            .companyName("SSPD IT Solution Center")
            .companyAddress("No. 38/Kha, 56 Ward, Lhaw Kar Main Road, South Dagon, Yangon")
            .companyPhone("09-252425319")
            .companyEmail("")
            .invoiceTitle("Sales Invoice")
            .footerNote("Thank you for your business")
            .taglineMm("ဝန်ဆောင်မှုဌာန")
            .build());
    }

    private CompanySettingsDTO toDto(CompanySettings s) {
        CompanySettingsDTO dto = new CompanySettingsDTO();
        dto.setId(s.getId());
        dto.setCompanyName(s.getCompanyName());
        dto.setCompanyAddress(s.getCompanyAddress());
        dto.setCompanyPhone(s.getCompanyPhone());
        dto.setCompanyEmail(s.getCompanyEmail());
        dto.setInvoiceTitle(s.getInvoiceTitle());
        dto.setFooterNote(s.getFooterNote());
        dto.setTaglineMm(s.getTaglineMm());
        dto.setLogoBase64(s.getLogoBase64());
        dto.setVoucherConfigJson(s.getVoucherConfigJson());
        dto.setSalePrefix(s.getSalePrefix() != null ? s.getSalePrefix() : "INV");
        dto.setSaleDigits(s.getSaleDigits() != null ? s.getSaleDigits() : 5);
        dto.setPurchasePrefix(s.getPurchasePrefix() != null ? s.getPurchasePrefix() : "PUR");
        dto.setPurchaseDigits(s.getPurchaseDigits() != null ? s.getPurchaseDigits() : 5);
        dto.setBookingPrefix(s.getBookingPrefix() != null ? s.getBookingPrefix() : "BK");
        dto.setBookingDigits(s.getBookingDigits() != null ? s.getBookingDigits() : 6);
        return dto;
    }
}
