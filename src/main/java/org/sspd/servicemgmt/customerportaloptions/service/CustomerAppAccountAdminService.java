package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountAdminResult;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountLinkRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppAccount;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerAppAccountRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPasswordRules;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerAppAccountAdminService {

    private final CustomerRepository customerRepository;
    private final CustomerAppAccountRepository accountRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerAppActivityService activityService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CustomerAppAccountAdminResult createForCustomer(CustomerAppAccountLinkRequest req) {
        if (req == null || req.getCustomerId() == null) {
            throw new IllegalArgumentException("POS ဖောက်သည် ရွေးပါ");
        }
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (accountRepository.findByCustomer_Id(customer.getId()).isPresent()) {
            throw new IllegalArgumentException("ဤဖောက်သည်တွင် App အကောင့် ရှိပြီးသား ဖြစ်သည်");
        }

        String phone = resolvePhone(customer, req.getPhone());
        assertPhoneFree(phone, null);
        String email = resolveEmail(customer, req.getEmail());
        if (email != null) assertEmailFree(email, null);

        if (isPlaceholderPhone(customer.getPhone()) || (req.getPhone() != null && !req.getPhone().isBlank())) {
            customer.setPhone(phone);
        }
        if (email != null && (customer.getEmail() == null || customer.getEmail().isBlank())) {
            customer.setEmail(email);
        }

        String rawPassword = resolvePassword(req);
        CustomerAppAccount account = CustomerAppAccount.builder()
                .customer(customer)
                .phone(phone)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .tokenVersion(1)
                .enabled(Boolean.TRUE)
                .profileComplete(isProfileComplete(customer, phone))
                .build();
        CustomerAppAccount saved = accountRepository.save(account);
        activityService.record(saved, "ADMIN_CREATED", "POS customer #" + customer.getId());
        return result(saved, rawPassword);
    }

    @Transactional
    public CustomerAppAccountAdminResult linkToCustomer(Integer accountId, CustomerAppAccountLinkRequest req) {
        if (req == null || req.getCustomerId() == null) {
            throw new IllegalArgumentException("ချိတ်မည့် POS ဖောက်သည် ရွေးပါ");
        }
        CustomerAppAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer app account not found"));
        Customer target = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Customer source = account.getCustomer();
        if (source != null && source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("ဤအကောင့်သည် ထိုဖောက်သည်နှင့် ချိတ်ပြီးသား ဖြစ်သည်");
        }
        CustomerAppAccount taken = accountRepository.findByCustomer_Id(target.getId()).orElse(null);
        if (taken != null && !taken.getId().equals(account.getId())) {
            throw new IllegalArgumentException("ရွေးထားသော ဖောက်သည်တွင် App အကောင့် ရှိပြီးသား ဖြစ်သည်");
        }

        String phone = resolvePhone(target, req.getPhone() != null ? req.getPhone() : account.getPhone());
        assertPhoneFree(phone, account.getId());
        String email = resolveEmail(target, firstNonBlank(req.getEmail(), account.getEmail(), target.getEmail()));
        if (email != null) assertEmailFree(email, account.getId());

        if (source != null && !source.getId().equals(target.getId())) {
            List<CustomerOrder> orders = orderRepository.findByCustomer_IdOrderByIdDesc(source.getId());
            for (CustomerOrder order : orders) {
                order.setCustomer(target);
            }
        }

        account.setCustomer(target);
        account.setPhone(phone);
        if (email != null) account.setEmail(email);
        if (target.getEmail() == null && email != null) target.setEmail(email);
        if (isPlaceholderPhone(target.getPhone())) target.setPhone(phone);
        account.setProfileComplete(isProfileComplete(target, phone));
        account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            CustomerPasswordRules.requireStrong(req.getPassword());
            account.setPasswordHash(passwordEncoder.encode(req.getPassword().trim()));
        }
        CustomerAppAccount saved = accountRepository.save(account);
        activityService.record(saved, "ADMIN_LINKED",
                "from #" + (source == null ? "?" : source.getId()) + " to #" + target.getId());
        return result(saved, null);
    }

    private String resolvePhone(Customer customer, String requested) {
        String phone = CustomerPortalAuth.normalizePhone(firstNonBlank(requested, customer.getPhone()));
        if (phone.length() < 6 || isPlaceholderPhone(phone)) {
            throw new IllegalArgumentException("ဖုန်းနံပါတ် မှန်ကန်စွာ ထည့်ပါ (POS ဖောက်သည် သို့မဟုတ် ဤဖောင်)");
        }
        return phone;
    }

    private String resolveEmail(Customer customer, String requested) {
        String raw = firstNonBlank(requested, customer == null ? null : customer.getEmail());
        if (raw == null) return null;
        String email = raw.trim().toLowerCase();
        if (!email.contains("@") || email.length() < 6) {
            throw new IllegalArgumentException("Email မှန်ကန်စွာ ထည့်ပါ");
        }
        return email;
    }

    private String resolvePassword(CustomerAppAccountLinkRequest req) {
        String password = req.getPassword() == null ? "" : req.getPassword().trim();
        if (password.isEmpty() || Boolean.TRUE.equals(req.getGeneratePassword())) {
            return generatePassword();
        }
        CustomerPasswordRules.requireStrong(password);
        return password;
    }

    private void assertPhoneFree(String phone, Integer exceptAccountId) {
        for (String key : CustomerPortalAuth.phoneLookupKeys(phone)) {
            CustomerAppAccount found = accountRepository.findByPhone(key).orElse(null);
            if (found != null && (exceptAccountId == null || !found.getId().equals(exceptAccountId))) {
                throw new IllegalArgumentException("ဤဖုန်းနံပါတ်ဖြင့် App အကောင့် ရှိပြီးသား ဖြစ်သည်");
            }
        }
    }

    private void assertEmailFree(String email, Integer exceptAccountId) {
        CustomerAppAccount found = accountRepository.findByAccountOrCustomerEmail(email).orElse(null);
        if (found != null && (exceptAccountId == null || !found.getId().equals(exceptAccountId))) {
            throw new IllegalArgumentException("ဤ Email ဖြင့် App အကောင့် ရှိပြီးသား ဖြစ်သည်");
        }
    }

    private static boolean isProfileComplete(Customer customer, String phone) {
        return !isPlaceholderPhone(phone)
                && customer.getAddress() != null
                && !"Pending".equalsIgnoreCase(customer.getAddress());
    }

    private static boolean isPlaceholderPhone(String phone) {
        return phone == null || phone.isBlank() || phone.startsWith("g-");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String generatePassword() {
        int n = new SecureRandom().nextInt(900000) + 100000;
        return "Shop" + n + "!";
    }

    private CustomerAppAccountAdminResult result(CustomerAppAccount account, String temporaryPassword) {
        CustomerAppAccountAdminResult dto = new CustomerAppAccountAdminResult();
        dto.setAccount(toDto(account));
        dto.setTemporaryPassword(temporaryPassword);
        return dto;
    }

    private CustomerAppAccountDTO toDto(CustomerAppAccount account) {
        var customer = account.getCustomer();
        String phone = account.getPhone();
        if (phone != null && phone.startsWith("g-")) phone = "";
        CustomerAppAccountDTO dto = new CustomerAppAccountDTO();
        dto.setId(account.getId());
        dto.setCustomerId(customer != null ? customer.getId() : null);
        dto.setCustomerName(customer != null ? customer.getName() : null);
        dto.setCustomerPhone(customer != null && customer.getPhone() != null && !customer.getPhone().startsWith("g-")
                ? customer.getPhone() : phone);
        dto.setPhone(phone);
        dto.setEmail(account.getEmail() != null ? account.getEmail() : (customer != null ? customer.getEmail() : null));
        dto.setHasPassword(account.getPasswordHash() != null && !account.getPasswordHash().isBlank());
        dto.setHasGoogle(account.getGoogleSub() != null && !account.getGoogleSub().isBlank());
        dto.setProfileComplete(Boolean.TRUE.equals(account.getProfileComplete()));
        dto.setEnabled(Boolean.TRUE.equals(account.getEnabled()));
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setLoginCount(account.getLoginCount() == null ? 0 : account.getLoginCount());
        dto.setHasUsedApp(account.getLastLoginAt() != null || (account.getLoginCount() != null && account.getLoginCount() > 0));
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        return dto;
    }
}
