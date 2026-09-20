package org.sspd.servicemgmt.security;

/**
 * Shared username rules for staff/business users.
 */
public final class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 50;
    public static final String MESSAGE =
            "Username must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters.";

    private UsernamePolicy() {}

    public static String requireValid(String username) {
        String value = username == null ? "" : username.trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(MESSAGE);
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Username must not contain spaces.");
        }
        return value;
    }
}
