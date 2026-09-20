package org.sspd.servicemgmt.security;

/**
 * Shared password rules for staff/business users (create, update, initial admin).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final String MESSAGE = "Password must be at least " + MIN_LENGTH + " characters.";

    private PasswordPolicy() {}

    /**
     * @return the password when valid
     * @throws IllegalArgumentException when null, empty, or shorter than {@link #MIN_LENGTH}
     */
    public static String requireValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(MESSAGE);
        }
        return password;
    }

    public static boolean isValid(String password) {
        return password != null && password.length() >= MIN_LENGTH;
    }
}
