package org.sspd.servicemgmt.companysettingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.model.CompanySettings;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    private final CompanySettingsRepository repository;

    public record MailSmtpConfig(
            String host,
            int port,
            String username,
            String password,
            String from,
            boolean auth,
            boolean startTls
    ) {
        public boolean isConfigured() {
            return StringUtils.hasText(host)
                    && StringUtils.hasText(username)
                    && StringUtils.hasText(password);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompanySettingsDTO getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional(readOnly = true)
    public MailSmtpConfig resolveMailSmtp() {
        List<CompanySettings> all = repository.findAll();
        if (all.isEmpty()) {
            return new MailSmtpConfig(null, 587, null, null, null, true, true);
        }
        CompanySettings s = all.get(0);
        String host = trimToNull(s.getMailSmtpHost());
        String username = trimToNull(s.getMailSmtpUsername());
        String password = trimToNull(s.getMailSmtpPassword());
        String from = trimToNull(s.getMailSmtpFrom());
        if (from == null) from = username;
        int port = s.getMailSmtpPort() != null && s.getMailSmtpPort() > 0 ? s.getMailSmtpPort() : 587;
        boolean auth = !Boolean.FALSE.equals(s.getMailSmtpAuth());
        boolean startTls = !Boolean.FALSE.equals(s.getMailSmtpStartTls());
        return new MailSmtpConfig(host, port, username, password, from, auth, startTls);
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
        s.setNotificationSoundBase64(dto.getNotificationSoundBase64());
        s.setVoucherConfigJson(dto.getVoucherConfigJson());
        if (dto.getSalePrefix() != null) s.setSalePrefix(dto.getSalePrefix().isBlank() ? "INV" : dto.getSalePrefix().trim());
        if (dto.getSaleDigits() != null && dto.getSaleDigits() >= 1 && dto.getSaleDigits() <= 10) s.setSaleDigits(dto.getSaleDigits());
        if (dto.getPurchasePrefix() != null) s.setPurchasePrefix(dto.getPurchasePrefix().isBlank() ? "PUR" : dto.getPurchasePrefix().trim());
        if (dto.getPurchaseDigits() != null && dto.getPurchaseDigits() >= 1 && dto.getPurchaseDigits() <= 10) s.setPurchaseDigits(dto.getPurchaseDigits());
        if (dto.getBookingPrefix() != null) s.setBookingPrefix(dto.getBookingPrefix().isBlank() ? "BK" : dto.getBookingPrefix().trim());
        if (dto.getBookingDigits() != null && dto.getBookingDigits() >= 1 && dto.getBookingDigits() <= 10) s.setBookingDigits(dto.getBookingDigits());
        if (dto.getPoPrefix() != null) s.setPoPrefix(dto.getPoPrefix().isBlank() ? "PO" : dto.getPoPrefix().trim());
        if (dto.getPoDigits() != null && dto.getPoDigits() >= 1 && dto.getPoDigits() <= 10) s.setPoDigits(dto.getPoDigits());
        if (dto.getPurchaseReturnPrefix() != null) s.setPurchaseReturnPrefix(dto.getPurchaseReturnPrefix().isBlank() ? "PRN" : dto.getPurchaseReturnPrefix().trim());
        if (dto.getPurchaseReturnDigits() != null && dto.getPurchaseReturnDigits() >= 1 && dto.getPurchaseReturnDigits() <= 10) s.setPurchaseReturnDigits(dto.getPurchaseReturnDigits());
        s.setPoFinalApprovalThreshold(dto.getPoFinalApprovalThreshold());
        if (dto.getServiceSupervisorApprovalRequired() != null)
            s.setServiceSupervisorApprovalRequired(dto.getServiceSupervisorApprovalRequired());
        if (dto.getServiceAllowDeliveryWithDue() != null)
            s.setServiceAllowDeliveryWithDue(dto.getServiceAllowDeliveryWithDue());
        if (dto.getPickupDepositPercent() != null) {
            java.math.BigDecimal pct = dto.getPickupDepositPercent();
            if (pct.compareTo(java.math.BigDecimal.ONE) < 0 || pct.compareTo(new java.math.BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("ဆိုင်မှာလာယူ စရံရာခိုင်နှုန်း ၁ မှ ၁၀၀ အတွင်း ထည့်ပါ");
            }
            s.setPickupDepositPercent(pct.setScale(2, java.math.RoundingMode.HALF_UP));
        }

        if (dto.getMailSmtpHost() != null) s.setMailSmtpHost(trimToNull(dto.getMailSmtpHost()));
        if (dto.getMailSmtpPort() != null) {
            int port = dto.getMailSmtpPort();
            s.setMailSmtpPort(port > 0 ? port : 587);
        }
        if (dto.getMailSmtpUsername() != null) s.setMailSmtpUsername(trimToNull(dto.getMailSmtpUsername()));
        if (dto.getMailSmtpFrom() != null) s.setMailSmtpFrom(trimToNull(dto.getMailSmtpFrom()));
        if (dto.getMailSmtpAuth() != null) s.setMailSmtpAuth(dto.getMailSmtpAuth());
        if (dto.getMailSmtpStartTls() != null) s.setMailSmtpStartTls(dto.getMailSmtpStartTls());
        String pwd = dto.getMailSmtpPassword();
        if (StringUtils.hasText(pwd) && !"********".equals(pwd.trim())) {
            s.setMailSmtpPassword(pwd.trim());
        }

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
        dto.setNotificationSoundBase64(s.getNotificationSoundBase64());
        dto.setVoucherConfigJson(s.getVoucherConfigJson());
        dto.setSalePrefix(s.getSalePrefix() != null ? s.getSalePrefix() : "INV");
        dto.setSaleDigits(s.getSaleDigits() != null ? s.getSaleDigits() : 5);
        dto.setPurchasePrefix(s.getPurchasePrefix() != null ? s.getPurchasePrefix() : "PUR");
        dto.setPurchaseDigits(s.getPurchaseDigits() != null ? s.getPurchaseDigits() : 5);
        dto.setBookingPrefix(s.getBookingPrefix() != null ? s.getBookingPrefix() : "BK");
        dto.setBookingDigits(s.getBookingDigits() != null ? s.getBookingDigits() : 6);
        dto.setPoPrefix(s.getPoPrefix() != null ? s.getPoPrefix() : "PO");
        dto.setPoDigits(s.getPoDigits() != null ? s.getPoDigits() : 5);
        dto.setPurchaseReturnPrefix(s.getPurchaseReturnPrefix() != null ? s.getPurchaseReturnPrefix() : "PRN");
        dto.setPurchaseReturnDigits(s.getPurchaseReturnDigits() != null ? s.getPurchaseReturnDigits() : 5);
        dto.setPoFinalApprovalThreshold(s.getPoFinalApprovalThreshold());
        dto.setServiceSupervisorApprovalRequired(!Boolean.FALSE.equals(s.getServiceSupervisorApprovalRequired()));
        dto.setServiceAllowDeliveryWithDue(Boolean.TRUE.equals(s.getServiceAllowDeliveryWithDue()));
        dto.setPickupDepositPercent(s.getPickupDepositPercent() != null
                ? s.getPickupDepositPercent()
                : new java.math.BigDecimal("30.00"));
        dto.setMailSmtpHost(s.getMailSmtpHost());
        dto.setMailSmtpPort(s.getMailSmtpPort() != null ? s.getMailSmtpPort() : 587);
        dto.setMailSmtpUsername(s.getMailSmtpUsername());
        dto.setMailSmtpFrom(s.getMailSmtpFrom());
        dto.setMailSmtpAuth(!Boolean.FALSE.equals(s.getMailSmtpAuth()));
        dto.setMailSmtpStartTls(!Boolean.FALSE.equals(s.getMailSmtpStartTls()));
        boolean configured = StringUtils.hasText(s.getMailSmtpHost())
                && StringUtils.hasText(s.getMailSmtpUsername())
                && StringUtils.hasText(s.getMailSmtpPassword());
        dto.setMailSmtpConfigured(configured);
        dto.setMailSmtpPassword(null);
        return dto;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
