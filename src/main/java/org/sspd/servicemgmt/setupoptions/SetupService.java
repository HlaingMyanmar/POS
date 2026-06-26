package org.sspd.servicemgmt.setupoptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.companysettingoptions.model.CompanySettings;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SetupService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final ChartOfAccountRepository coaRepository;
    private final CompanySettingsRepository companySettingsRepository;

    @Transactional(readOnly = true)
    public SetupStatusDTO getStatus() {
        boolean hasPaymentMethods = paymentMethodRepository.count() > 0;
        CompanySettings cs = companySettingsRepository.findAll().stream().findFirst().orElse(null);

        // Primary: explicit flag set by initialize()
        boolean complete = cs != null && Boolean.TRUE.equals(cs.getSetupComplete());

        // Backward compat: existing installs that had payment methods before this flag existed
        if (!complete && hasPaymentMethods && cs != null
                && cs.getCompanyName() != null && !cs.getCompanyName().isBlank()) {
            complete = true;
        }

        boolean companyConfigured = cs != null
                && cs.getCompanyName() != null
                && !cs.getCompanyName().isBlank();

        return new SetupStatusDTO(complete, hasPaymentMethods, companyConfigured);
    }

    @Transactional
    public void initialize(SetupInitDTO dto) {
        // ── Company Info ────────────────────────────────────────────────────
        List<CompanySettings> all = companySettingsRepository.findAll();
        CompanySettings cs = all.isEmpty() ? new CompanySettings() : all.get(0);
        if (dto.getCompanyName() != null && !dto.getCompanyName().isBlank())
            cs.setCompanyName(dto.getCompanyName().trim());
        if (dto.getCompanyAddress() != null) cs.setCompanyAddress(dto.getCompanyAddress());
        if (dto.getCompanyPhone()   != null) cs.setCompanyPhone(dto.getCompanyPhone());
        if (dto.getCompanyEmail()   != null) cs.setCompanyEmail(dto.getCompanyEmail());
        if (cs.getInvoiceTitle() == null) cs.setInvoiceTitle("Sales Invoice");
        if (cs.getFooterNote()   == null) cs.setFooterNote("Thank you for your business");
        cs.setSetupComplete(true);
        companySettingsRepository.save(cs);

        // ── Default Payment Methods ─────────────────────────────────────────
        List<Map<String, String>> defaults = List.of(
            Map.of("name", "Cash",      "code", AccountCode.CASH),
            Map.of("name", "KBZ Bank",  "code", AccountCode.BANK_KBZ),
            Map.of("name", "KPay",      "code", AccountCode.KPAY),
            Map.of("name", "Wave Pay",  "code", AccountCode.WAVE_PAY)
        );

        List<String> selected = dto.getPaymentMethods() != null ? dto.getPaymentMethods()
                : List.of("Cash", "KBZ Bank");

        List<String> created = new ArrayList<>();
        for (Map<String, String> def : defaults) {
            String name = def.get("name");
            if (!selected.contains(name)) continue;
            if (paymentMethodRepository.existsByMethodName(name)) continue;

            coaRepository.findByCode(def.get("code")).ifPresent(account -> {
                PaymentMethod pm = new PaymentMethod();
                pm.setMethodName(name);
                pm.setAccount(account);
                pm.setActive(true);
                paymentMethodRepository.save(pm);
                created.add(name);
            });
        }
        log.info("Setup complete. Payment methods created: {}", created);
    }
}
