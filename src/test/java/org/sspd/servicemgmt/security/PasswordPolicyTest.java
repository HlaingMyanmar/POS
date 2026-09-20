package org.sspd.servicemgmt.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void rejectsNullEmptyAndShortPasswords() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.requireValid(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.requireValid(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.requireValid("1"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.requireValid("1234567"));
    }

    @Test
    void acceptsPasswordsOfAtLeastEightCharacters() {
        assertEquals("password", PasswordPolicy.requireValid("password"));
        assertTrue(PasswordPolicy.isValid("password1"));
        assertFalse(PasswordPolicy.isValid("short"));
    }
}
