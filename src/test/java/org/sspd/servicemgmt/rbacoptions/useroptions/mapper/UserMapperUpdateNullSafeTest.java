package org.sspd.servicemgmt.rbacoptions.useroptions.mapper;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.rbacoptions.useroptions.dto.UserDTO;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperUpdateNullSafeTest {

    private final UserMapper mapper = UserMapper.INSTANCE;

    @Test
    void updateIgnoresNullDtoFields() {
        User existing = new User();
        existing.setId(1L);
        existing.setUsername("keeper");
        existing.setEmail("keeper@example.com");
        existing.setIsActive(true);
        existing.setName("Keep Name");
        existing.setPhone("09111");
        existing.setAuthProvider("LOCAL");
        existing.setProviderId("prov-1");
        existing.setTokenVersion(3);
        existing.setPassword("hash");

        UserDTO partial = new UserDTO();
        partial.setName("New Name");
        // username, email, isActive, phone, authProvider intentionally null

        mapper.updateEntityFromDto(partial, existing);

        assertEquals("keeper", existing.getUsername());
        assertEquals("keeper@example.com", existing.getEmail());
        assertEquals(Boolean.TRUE, existing.getIsActive());
        assertEquals("New Name", existing.getName());
        assertEquals("09111", existing.getPhone());
        assertEquals("LOCAL", existing.getAuthProvider());
        assertEquals("prov-1", existing.getProviderId());
        assertEquals(3, existing.getTokenVersion());
        assertEquals("hash", existing.getPassword());
    }

    @Test
    void updateAppliesExplicitFalseIsActive() {
        User existing = new User();
        existing.setIsActive(true);

        UserDTO dto = new UserDTO();
        dto.setIsActive(false);

        mapper.updateEntityFromDto(dto, existing);

        assertEquals(Boolean.FALSE, existing.getIsActive());
    }
}
