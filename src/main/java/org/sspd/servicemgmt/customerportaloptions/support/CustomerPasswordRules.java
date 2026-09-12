package org.sspd.servicemgmt.customerportaloptions.support;

/**
 * Shared password rules for customer app register / reset / change-password.
 */
public final class CustomerPasswordRules {

    public static final int MIN_LENGTH = 8;
    public static final String HINT_MY =
            "အနည်းဆုံး ၈ လုံး — အက္ခရာ၊ ဂဏန်း နှင့် အထူးအက္ခရာ (!@#$…) ရောထည့်ပါ";

    private CustomerPasswordRules() {}

    public static void requireStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("စကားဝှက် အနည်းဆုံး " + MIN_LENGTH + " လုံး ဖြစ်ရပါမည်");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c)) hasSpecial = true;
        }
        if (!hasLetter || !hasDigit || !hasSpecial) {
            throw new IllegalArgumentException(HINT_MY);
        }
    }

    public static boolean isStrong(String password) {
        try {
            requireStrong(password);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
