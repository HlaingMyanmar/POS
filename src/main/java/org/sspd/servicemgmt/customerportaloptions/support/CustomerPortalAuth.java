package org.sspd.servicemgmt.customerportaloptions.support;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;

public final class CustomerPortalAuth {
    public static final String USERNAME_PREFIX = "customer:";
    public static final String ROLE = "ROLE_CUSTOMER";

    private CustomerPortalAuth() {}

    public static String usernameForCustomer(Integer customerId) {
        return USERNAME_PREFIX + "id:" + customerId;
    }

    public static String usernameForPhone(String phone) {
        return USERNAME_PREFIX + normalizePhone(phone);
    }

    public static Integer parseCustomerId(String username) {
        if (username == null || !username.startsWith(USERNAME_PREFIX + "id:")) return null;
        try {
            return Integer.valueOf(username.substring((USERNAME_PREFIX + "id:").length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("959") && digits.length() >= 11) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("95") && digits.length() >= 10) {
            digits = "0" + digits.substring(2);
        } else if (digits.length() == 9 && digits.startsWith("9")) {
            digits = "0" + digits;
        }
        return digits;
    }

    public static java.util.List<String> phoneLookupKeys(String phone) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        String raw = phone == null ? "" : phone.trim();
        if (!raw.isEmpty()) keys.add(raw);
        String n = normalizePhone(phone);
        if (!n.isEmpty()) {
            keys.add(n);
            if (n.startsWith("0") && n.length() > 1) {
                keys.add(n.substring(1));
                keys.add("+95" + n.substring(1));
                keys.add("95" + n.substring(1));
            }
        }
        return new java.util.ArrayList<>(keys);
    }

    public static boolean isCustomerUsername(String username) {
        return username != null && username.startsWith(USERNAME_PREFIX);
    }

    public static CustomerPortalUserDetails require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomerPortalUserDetails details) {
            return details;
        }
        throw new AccessDeniedException("Customer login required");
    }
}
