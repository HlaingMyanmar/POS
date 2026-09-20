package org.sspd.servicemgmt.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsernamePolicyTest {

    @Test
    void rejectsBlankShortLongAndSpacedUsernames() {
        assertThrows(IllegalArgumentException.class, () -> UsernamePolicy.requireValid(null));
        assertThrows(IllegalArgumentException.class, () -> UsernamePolicy.requireValid(""));
        assertThrows(IllegalArgumentException.class, () -> UsernamePolicy.requireValid("ab"));
        assertThrows(IllegalArgumentException.class, () -> UsernamePolicy.requireValid("a".repeat(51)));
        assertThrows(IllegalArgumentException.class, () -> UsernamePolicy.requireValid("bad name"));
    }

    @Test
    void trimsAndAcceptsValidUsernames() {
        assertEquals("admin", UsernamePolicy.requireValid("  admin  "));
    }
}
