package org.sspd.servicemgmt.setupoptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.companysettingoptions.model.CompanySettings;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SetupService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final ChartOfAccountRepository coaRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public SetupStatusDTO getStatus() {
        boolean hasPaymentMethods = paymentMethodRepository.count() > 0;
        CompanySettings cs = companySettingsRepository.findAll().stream().findFirst().orElse(null);
        boolean hasAdministrator = userRepository.existsByRoleName("ADMINISTRATOR");
        boolean needsInitialAdmin = userRepository.count() == 0;

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

        return new SetupStatusDTO(complete, hasPaymentMethods, companyConfigured, hasAdministrator, needsInitialAdmin);
    }

    @Transactional
    public void createInitialAdministrator(InitialAdminDTO dto) {
        if (userRepository.count() > 0) {
            throw new IllegalStateException("Initial administrator can only be created when no users exist.");
        }

        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        String email = dto.getEmail() == null ? "" : dto.getEmail().trim();
        String password = dto.getPassword() == null ? "" : dto.getPassword();

        if (username.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        }
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("A valid email is required.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        Role adminRole = roleRepository.findByName("ADMINISTRATOR")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ADMINISTRATOR not found. Make sure RoleSeeder ran at startup."));

        User user = new User();
        user.setAuthProvider("LOCAL");
        user.setUsername(username);
        user.setEmail(email);
        user.setIsActive(true);
        user.setPassword(passwordEncoder.encode(password));
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        user.setRoles(roles);
        userRepository.save(user);
        log.info("Initial ADMINISTRATOR created for {}", email);
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
