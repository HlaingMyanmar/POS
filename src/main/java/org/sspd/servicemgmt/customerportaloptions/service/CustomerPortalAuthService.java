package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.creditoptions.repository.CustomerCreditTermRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalAuthResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalForgotRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalGoogleLoginRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalLoginRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalPasswordChangeRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalProfileRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalRegisterRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalResetRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppAccount;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPasswordRules;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerAppAccountRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;
import org.sspd.servicemgmt.jwt.JwtService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerPortalAuthService {

    private final CustomerRepository customerRepository;
    private final CustomerAppAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final CustomerMailService customerMailService;
    private final CustomerCreditTermRepository creditTermRepository;
    private final CustomerAppActivityService activityService;

    @Value("${app.customer-portal.reset-minutes:5}")
    private int resetMinutes;

    @Transactional
    public CustomerPortalAuthResponse register(CustomerPortalRegisterRequest req) {
        String phone = CustomerPortalAuth.normalizePhone(req.getPhone());
        String name = trim(req.getName());
        String address = trim(req.getAddress());
        String email = normalizeEmail(req.getEmail());
        String password = req.getPassword() == null ? "" : req.getPassword();
        if (phone.length() < 6) throw new IllegalArgumentException("ဖုန်းနံပါတ် မှန်ကန်စွာ ထည့်ပါ");
        if (email == null) throw new IllegalArgumentException("Email ထည့်ပါ");
        CustomerPasswordRules.requireStrong(password);

        CustomerAppAccount existingByEmail = findAccountByEmail(email);
        if (existingByEmail != null) {
            sendResetEmail(existingByEmail, email);
            return resetSentResponse(email);
        }

        if (findAccountByPhone(phone) != null) {
            throw new IllegalArgumentException("ဤဖုန်းနံပါတ်ဖြင့် အကောင့်ရှိပြီးသား ဖြစ်သည်");
        }

        Customer byPhone = findCustomerByPhone(phone);
        Customer byEmail = customerRepository.findByEmail(email).orElse(null);
        if (byPhone != null && byEmail != null && !byPhone.getId().equals(byEmail.getId())) {
            throw new IllegalArgumentException("ဤဖုန်းနှင့် email သည် customer မတူပါ");
        }
        Customer customer = byPhone != null ? byPhone : byEmail;
        if (customer == null) {
            if (name == null) throw new IllegalArgumentException("အမည် ထည့်ပါ");
            customer = customerRepository.save(
                    Customer.builder()
                            .name(clip(name, 50))
                            .phone(phone)
                            .email(email)
                            .address(address != null ? address : "Pending")
                            .creditHold(Boolean.TRUE)
                            .creditHoldReason("Customer app – pending credit review")
                            .blacklisted(Boolean.FALSE)
                            .advanceBalance(java.math.BigDecimal.ZERO)
                            .build());
        } else {
            if (customer.getEmail() == null) customer.setEmail(email);
            if (accountRepository.findByCustomer_Id(customer.getId()).isPresent()) {
                throw new IllegalArgumentException("ဤဖုန်းနံပါတ်ဖြင့် အကောင့်ရှိပြီးသား ဖြစ်သည်");
            }
        }

        CustomerAppAccount account = CustomerAppAccount.builder()
                .customer(customer)
                .phone(phone)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .tokenVersion(1)
                .enabled(Boolean.TRUE)
                .profileComplete(!isPlaceholderPhone(customer.getPhone())
                        && customer.getAddress() != null
                        && !"Pending".equalsIgnoreCase(customer.getAddress()))
                .build();
        accountRepository.save(account);
        activityService.recordSession(account, "REGISTER", "PHONE");
        accountRepository.save(account);
        return toAuthResponse(account, customer, true);
    }

    @Transactional
    public CustomerPortalAuthResponse forgotPassword(CustomerPortalForgotRequest req) {
        String email = normalizeEmail(req == null ? null : req.getEmail());
        if (email == null) throw new IllegalArgumentException("Email ထည့်ပါ");
        CustomerAppAccount account = findAccountByEmail(email);
        if (account == null) {
            throw new IllegalArgumentException(
                    "ဤ Email ဖြင့် Customer App အကောင့် မရှိပါ။ အကောင့်နှင့် ချိတ်ထားသော Email သာ သုံးပါ");
        }
        String linked = linkedAppEmail(account);
        if (linked == null) {
            throw new IllegalArgumentException("ဤ App အကောင့်တွင် Email မချိတ်ရသေးပါ။ Admin သို့ ဆက်သွယ်ပါ");
        }
        if (!linked.equals(email)) {
            throw new IllegalArgumentException(
                    "Reset link ကို အကောင့်နှင့် ချိတ်ထားသော Email သို့သာ ပို့နိုင်ပါသည်");
        }
        sendResetEmail(account, linked);
        return resetSentResponse(linked);
    }

    /**
     * Admin-triggered reset email to the account's linked Customer App email.
     */
    @Transactional
    public String sendPasswordResetForAccount(Integer accountId) {
        CustomerAppAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer app account not found"));
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            throw new IllegalArgumentException("ဤ App အကောင့် ပိတ်ထားပါသည်");
        }
        String linked = linkedAppEmail(account);
        if (linked == null) {
            throw new IllegalArgumentException("App အကောင့်တွင် Email မချိတ်ရသေးပါ။ အရင် Email ချိတ်ပါ");
        }
        sendResetEmail(account, linked);
        activityService.record(account, "ADMIN_PASSWORD_RESET_SENT", linked);
        return linked;
    }

    /** Canonical email used for Customer App login / reset (account email preferred). */
    private static String linkedAppEmail(CustomerAppAccount account) {
        String fromAccount = normalizeEmail(account.getEmail());
        if (fromAccount != null) return fromAccount;
        if (account.getCustomer() != null) {
            return normalizeEmail(account.getCustomer().getEmail());
        }
        return null;
    }

    @Transactional
    public CustomerPortalAuthResponse resetPassword(CustomerPortalResetRequest req) {
        String token = req == null || req.getToken() == null ? "" : req.getToken().trim();
        String password = req == null || req.getPassword() == null ? "" : req.getPassword();
        String googleIdToken = req == null ? null : req.getGoogleIdToken();
        if (token.isBlank()) throw new IllegalArgumentException("Reset link မမှန်ကန်ပါ");
        CustomerPasswordRules.requireStrong(password);

        CustomerAppAccount account = accountRepository.findByResetTokenHash(sha256(token))
                .orElseThrow(() -> new IllegalArgumentException("Reset link မမှန်ကန်ပါ သို့မဟုတ် သက်တမ်းကုန်ပါပြီ"));
        if (account.getResetTokenExpiresAt() == null
                || account.getResetTokenExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset link သက်တမ်းကုန်ပါပြီ။ အသစ်တောင်းပါ");
        }

        // Must prove ownership via the same Google account / email that received the link
        GoogleIdTokenVerifier.GoogleIdentity identity = googleIdTokenVerifier.verify(googleIdToken);
        requireGoogleMatchesResetAccount(account, identity);

        account.setPasswordHash(passwordEncoder.encode(password));
        if (account.getGoogleSub() == null || account.getGoogleSub().isBlank()) {
            account.setGoogleSub(identity.getSub());
        }
        if (account.getEmail() == null || account.getEmail().isBlank()) {
            account.setEmail(identity.getEmail());
        }
        account.setResetTokenHash(null);
        account.setResetTokenExpiresAt(null);
        activityService.recordSession(account, "LOGIN", "PASSWORD_RESET");
        bumpToken(account);
        return toAuthResponse(account, account.getCustomer(), true);
    }

    private void requireGoogleMatchesResetAccount(
            CustomerAppAccount account,
            GoogleIdTokenVerifier.GoogleIdentity identity
    ) {
        String googleEmail = identity.getEmail() == null ? "" : identity.getEmail().trim().toLowerCase();
        String accountEmail = normalizeEmail(account.getEmail());
        if (accountEmail == null && account.getCustomer() != null) {
            accountEmail = normalizeEmail(account.getCustomer().getEmail());
        }
        boolean emailMatch = accountEmail != null && accountEmail.equals(googleEmail);
        boolean subMatch = account.getGoogleSub() != null
                && !account.getGoogleSub().isBlank()
                && account.getGoogleSub().equals(identity.getSub());
        if (!emailMatch && !subMatch) {
            throw new IllegalArgumentException(
                    "Reset email နဲ့ တူညီသော Google အကောင့်ဖြင့် ဝင်ပါ (ယခု ဝင်ထားသော Gmail မကိုက်ပါ)");
        }
    }

    @Transactional
    public CustomerPortalAuthResponse login(CustomerPortalLoginRequest req) {
        String loginId = req == null ? "" : req.resolveLoginId();
        if (loginId.isBlank()) {
            throw new IllegalArgumentException("ဖုန်း၊ Email သို့မဟုတ် အမည် ထည့်ပါ");
        }
        ResolvedLogin resolved = resolveAccountForLogin(loginId);
        CustomerAppAccount account = resolved.account();
        if (account == null) {
            throw new IllegalArgumentException("အကောင့် သို့မဟုတ် စကားဝှက် မှားနေပါသည်");
        }
        if (account.getPasswordHash() == null || account.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("ဤအကောင့်ကို Gmail ဖြင့် ဝင်ပါ");
        }
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            throw new IllegalArgumentException("အကောင့် ပိတ်ထားပါသည်");
        }
        if (!passwordEncoder.matches(req.getPassword() == null ? "" : req.getPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("အကောင့် သို့မဟုတ် စကားဝှက် မှားနေပါသည်");
        }
        activityService.recordSession(account, "LOGIN", resolved.channel());
        bumpToken(account);
        return toAuthResponse(account, account.getCustomer(), true);
    }

    private record ResolvedLogin(CustomerAppAccount account, String channel) {}

    private ResolvedLogin resolveAccountForLogin(String loginId) {
        String email = normalizeEmail(loginId);
        if (email != null) {
            CustomerAppAccount byEmail = findAccountByEmail(email);
            return new ResolvedLogin(byEmail, "EMAIL");
        }
        String digits = loginId.replaceAll("\\D", "");
        if (digits.length() >= 6) {
            CustomerAppAccount byPhone = findAccountByPhone(CustomerPortalAuth.normalizePhone(loginId));
            if (byPhone != null) return new ResolvedLogin(byPhone, "PHONE");
            if (findCustomerByPhone(CustomerPortalAuth.normalizePhone(loginId)) != null) {
                throw new IllegalArgumentException("ဤဖုန်းဖြင့် App အကောင့် မရှိသေးပါ။ အကောင့်ဖွင့်ရန် နှိပ်ပါ");
            }
            return new ResolvedLogin(null, "PHONE");
        }
        // Name lookup (case-insensitive). Ambiguous names must use phone/email.
        java.util.List<CustomerAppAccount> byName = accountRepository.findAllByCustomerNameIgnoreCase(loginId);
        if (byName.isEmpty()) return new ResolvedLogin(null, "NAME");
        if (byName.size() > 1) {
            throw new IllegalArgumentException("အမည် တူညီသော အကောင့်များ ရှိသည်။ ဖုန်း သို့မဟုတ် Email ဖြင့် ဝင်ပါ");
        }
        return new ResolvedLogin(byName.get(0), "NAME");
    }

    @Transactional
    public CustomerPortalAuthResponse loginWithGoogle(CustomerPortalGoogleLoginRequest req) {
        GoogleIdTokenVerifier.GoogleIdentity identity = googleIdTokenVerifier.verify(req == null ? null : req.getIdToken());
        CustomerAppAccount account = accountRepository.findByGoogleSub(identity.getSub())
                .or(() -> accountRepository.findByEmail(identity.getEmail()))
                .orElse(null);
        if (account == null) {
            account = createGoogleAccount(identity);
            activityService.recordSession(account, "REGISTER", "GOOGLE");
        } else {
            if (account.getGoogleSub() == null) account.setGoogleSub(identity.getSub());
            if (account.getEmail() == null) account.setEmail(identity.getEmail());
            Customer customer = account.getCustomer();
            if (customer.getEmail() == null) customer.setEmail(identity.getEmail());
            if (!Boolean.TRUE.equals(account.getEnabled())) {
                throw new BadCredentialsException("အကောင့် ပိတ်ထားပါသည်");
            }
            activityService.recordSession(account, "LOGIN", "GOOGLE");
        }
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            throw new BadCredentialsException("အကောင့် ပိတ်ထားပါသည်");
        }
        bumpToken(account);
        return toAuthResponse(account, account.getCustomer(), true);
    }

    @Transactional
    public CustomerPortalAuthResponse completeProfile(CustomerPortalProfileRequest req) {
        var me = CustomerPortalAuth.require();
        CustomerAppAccount account = accountRepository.findByCustomer_Id(me.getCustomerId())
                .orElseThrow(() -> new UsernameNotFoundException("Customer account not found"));
        String phone = CustomerPortalAuth.normalizePhone(req.getPhone());
        String address = trim(req.getAddress());
        String name = trim(req.getName());
        if (phone.length() < 6 || phone.startsWith("g-")) {
            throw new IllegalArgumentException("ဖုန်းနံပါတ် မှန်ကန်စွာ ထည့်ပါ");
        }
        if (address == null) throw new IllegalArgumentException("လိပ်စာ ထည့်ပါ");
        Customer customer = account.getCustomer();
        CustomerAppAccount taken = findAccountByPhone(phone);
        if (taken != null && !taken.getId().equals(account.getId())) {
            throw new IllegalArgumentException("ဤဖုန်းနံပါတ်ဖြင့် အကောင့်ရှိပြီးသား ဖြစ်သည်");
        }
        Customer other = findCustomerByPhone(phone);
        if (other != null && !other.getId().equals(customer.getId())) {
            throw new IllegalArgumentException("ဤဖုန်းနံပါတ်ကို အခြား customer သုံးပြီးသား ဖြစ်သည်");
        }
        if (name != null) customer.setName(clip(name, 50));
        customer.setPhone(phone);
        customer.setAddress(address);
        account.setPhone(phone);
        account.setProfileComplete(Boolean.TRUE);
        accountRepository.save(account);
        activityService.record(account, "PROFILE_COMPLETED", phone);
        return toAuthResponse(account, customer, false);
    }

    @Transactional(readOnly = true)
    public CustomerPortalAuthResponse me() {
        var me = CustomerPortalAuth.require();
        CustomerAppAccount account = accountRepository.findByCustomer_Id(me.getCustomerId())
                .orElseThrow(() -> new UsernameNotFoundException("Customer account not found"));
        return toAuthResponse(account, account.getCustomer(), false);
    }

    @Transactional
    public CustomerPortalAuthResponse changePassword(CustomerPortalPasswordChangeRequest req) {
        var me = CustomerPortalAuth.require();
        CustomerAppAccount account = accountRepository.findByCustomer_Id(me.getCustomerId())
                .orElseThrow(() -> new UsernameNotFoundException("Customer account not found"));
        String currentPassword = req == null || req.getCurrentPassword() == null ? "" : req.getCurrentPassword();
        String newPassword = req == null || req.getNewPassword() == null ? "" : req.getNewPassword();
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("လက်ရှိ စကားဝှက် မမှန်ပါ");
        }
        CustomerPasswordRules.requireStrong(newPassword);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("စကားဝှက်အသစ်သည် လက်ရှိစကားဝှက်နှင့် မတူရပါ");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setResetTokenHash(null);
        account.setResetTokenExpiresAt(null);
        activityService.record(account, "PASSWORD_CHANGED", null);
        bumpToken(account);
        return toAuthResponse(account, account.getCustomer(), true);
    }

    public CustomerPortalUserDetails loadUserDetails(String username) {
        Integer customerId = CustomerPortalAuth.parseCustomerId(username);
        CustomerAppAccount account = customerId != null
                ? accountRepository.findByCustomer_Id(customerId).orElse(null)
                : findAccountByPhone(username.substring(CustomerPortalAuth.USERNAME_PREFIX.length()));
        if (account == null) throw new UsernameNotFoundException("Customer account not found");
        return toUserDetails(account);
    }

    public CustomerPortalUserDetails toUserDetails(CustomerAppAccount account) {
        Customer customer = account.getCustomer();
        String password = account.getPasswordHash() == null ? "" : account.getPasswordHash();
        return new CustomerPortalUserDetails(
                CustomerPortalAuth.usernameForCustomer(customer.getId()),
                password,
                Boolean.TRUE.equals(account.getEnabled()),
                List.of(new SimpleGrantedAuthority(CustomerPortalAuth.ROLE)),
                account.getTokenVersion() == null ? 0 : account.getTokenVersion(),
                customer.getId(),
                customer.getName(),
                customer.getPhone()
        );
    }

    public void requireCompleteProfile(Integer customerId) {
        CustomerAppAccount account = accountRepository.findByCustomer_Id(customerId)
                .orElseThrow(() -> new UsernameNotFoundException("Customer account not found"));
        if (!Boolean.TRUE.equals(account.getProfileComplete()) || isPlaceholderPhone(account.getCustomer().getPhone())) {
            throw new IllegalArgumentException("Service ခေါ်ရန် / အော်ဒါတင်ရန် ဖုန်းနှင့် လိပ်စာ ဖြည့်ပါ");
        }
    }

    private CustomerAppAccount createGoogleAccount(GoogleIdTokenVerifier.GoogleIdentity identity) {
        Customer customer = customerRepository.findByEmail(identity.getEmail()).orElse(null);
        if (customer != null && accountRepository.findByCustomer_Id(customer.getId()).isPresent()) {
            throw new IllegalArgumentException("ဤ Gmail ဖြင့် အကောင့်ရှိပြီးသား ဖြစ်သည်");
        }
        if (customer == null) {
            String placeholder = placeholderPhone(identity.getSub());
            while (customerRepository.findByPhone(placeholder).isPresent()) {
                placeholder = clip("g-" + Long.toString(Math.abs(System.nanoTime()), 36), 20);
            }
            customer = customerRepository.save(Customer.builder()
                    .name(clip(identity.getName(), 50))
                    .phone(placeholder)
                    .email(identity.getEmail())
                    .address("Pending")
                    .creditHold(Boolean.TRUE)
                    .creditHoldReason("Customer app – pending credit review")
                    .blacklisted(Boolean.FALSE)
                    .advanceBalance(java.math.BigDecimal.ZERO)
                    .build());
        } else if (customer.getEmail() == null) {
            customer.setEmail(identity.getEmail());
        }
        CustomerAppAccount account = CustomerAppAccount.builder()
                .customer(customer)
                .phone(customer.getPhone())
                .email(identity.getEmail())
                .googleSub(identity.getSub())
                .tokenVersion(1)
                .enabled(Boolean.TRUE)
                .profileComplete(!isPlaceholderPhone(customer.getPhone()) && customer.getAddress() != null
                        && !"Pending".equalsIgnoreCase(customer.getAddress()))
                .build();
        return accountRepository.save(account);
    }

    private void bumpToken(CustomerAppAccount account) {
        int next = (account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1;
        account.setTokenVersion(next);
        accountRepository.save(account);
    }

    private CustomerPortalAuthResponse toAuthResponse(CustomerAppAccount account, Customer customer, boolean bumpAlreadySaved) {
        CustomerPortalUserDetails details = toUserDetails(account);
        String token = jwtService.generateToken(details, details.getTokenVersion());
        CustomerPortalAuthResponse res = new CustomerPortalAuthResponse();
        res.setAccessToken(token);
        res.setCustomerId(customer.getId());
        res.setName(customer.getName());
        res.setPhone(isPlaceholderPhone(customer.getPhone()) ? "" : customer.getPhone());
        res.setAddress("Pending".equalsIgnoreCase(customer.getAddress()) ? "" : customer.getAddress());
        res.setEmail(account.getEmail() != null ? account.getEmail() : customer.getEmail());
        res.setNeedsProfile(!Boolean.TRUE.equals(account.getProfileComplete())
                || isPlaceholderPhone(customer.getPhone())
                || customer.getAddress() == null
                || "Pending".equalsIgnoreCase(customer.getAddress()));
        var creditTerm = creditTermRepository.findByCustomerId(customer.getId()).orElse(null);
        boolean onHold = Boolean.TRUE.equals(customer.getCreditHold()) || Boolean.TRUE.equals(customer.getBlacklisted());
        boolean configured = creditTerm != null && Boolean.TRUE.equals(creditTerm.getCreditAllowed());
        res.setCreditAllowed(configured && !onHold);
        res.setCreditLimit(creditTerm != null && creditTerm.getCreditLimit() != null
                ? creditTerm.getCreditLimit()
                : java.math.BigDecimal.ZERO);
        res.setCreditDays(creditTerm != null && creditTerm.getCreditDays() != null ? creditTerm.getCreditDays() : 0);
        res.setCreditHold(onHold);
        res.setCreditStatusReason(Boolean.TRUE.equals(customer.getBlacklisted())
                ? customer.getBlacklistReason()
                : customer.getCreditHoldReason());
        return res;
    }

    private CustomerPortalAuthResponse resetSentResponse(String email) {
        CustomerPortalAuthResponse res = new CustomerPortalAuthResponse();
        res.setEmail(email);
        res.setResetSent(true);
        return res;
    }

    private CustomerAppAccount findAccountByEmail(String email) {
        if (email == null) return null;
        return accountRepository.findByAccountOrCustomerEmail(email).orElse(null);
    }

    private void sendResetEmail(CustomerAppAccount account, String email) {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        String token = java.util.HexFormat.of().formatHex(raw);
        account.setResetTokenHash(sha256(token));
        account.setResetTokenExpiresAt(java.time.LocalDateTime.now().plusMinutes(Math.max(1, resetMinutes)));
        accountRepository.save(account);
        customerMailService.sendPasswordReset(email, token);
    }

    private static String sha256(String token) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalizeEmail(String email) {
        String t = trim(email);
        if (t == null) return null;
        t = t.toLowerCase();
        if (!t.contains("@") || t.length() < 6) return null;
        return t;
    }

    private CustomerAppAccount findAccountByPhone(String phone) {
        for (String key : CustomerPortalAuth.phoneLookupKeys(phone)) {
            CustomerAppAccount account = accountRepository.findByPhone(key).orElse(null);
            if (account != null) return account;
        }
        java.util.List<String> digits = phoneDigitKeys(phone);
        if (!digits.isEmpty()) {
            CustomerAppAccount byDigits = accountRepository.findFirstByPhoneDigitsIn(digits).orElse(null);
            if (byDigits != null) return byDigits;
        }
        Customer customer = findCustomerByPhone(phone);
        if (customer == null) return null;
        return accountRepository.findByCustomer_Id(customer.getId()).orElse(null);
    }

    private Customer findCustomerByPhone(String phone) {
        for (String key : CustomerPortalAuth.phoneLookupKeys(phone)) {
            Customer customer = customerRepository.findByPhone(key).orElse(null);
            if (customer != null) return customer;
        }
        java.util.List<String> digits = phoneDigitKeys(phone);
        if (digits.isEmpty()) return null;
        return customerRepository.findFirstByPhoneDigitsIn(digits).orElse(null);
    }

    private static java.util.List<String> phoneDigitKeys(String phone) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (String key : CustomerPortalAuth.phoneLookupKeys(phone)) {
            String digits = key.replaceAll("\\D", "");
            if (digits.length() >= 6) keys.add(digits);
        }
        return new java.util.ArrayList<>(keys);
    }

    private static boolean isPlaceholderPhone(String phone) {
        return phone == null || phone.isBlank() || phone.startsWith("g-");
    }

    private static String placeholderPhone(String sub) {
        String tail = sub == null ? "user" : sub.replaceAll("[^0-9a-zA-Z]", "");
        if (tail.length() > 17) tail = tail.substring(tail.length() - 17);
        return clip("g-" + tail, 20);
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
