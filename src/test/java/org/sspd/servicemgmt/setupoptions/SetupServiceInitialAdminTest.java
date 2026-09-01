package org.sspd.servicemgmt.setupoptions;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupServiceInitialAdminTest {

    @Test
    void rejectsInitialAdminWhenUsersAlreadyExist() {
        UserRepository users = mock(UserRepository.class);
        when(users.count()).thenReturn(1L);

        SetupService service = new SetupService(
                mock(PaymentMethodRepository.class),
                mock(ChartOfAccountRepository.class),
                mock(CompanySettingsRepository.class),
                users,
                mock(RoleRepository.class),
                mock(PasswordEncoder.class)
        );

        InitialAdminDTO dto = new InitialAdminDTO();
        dto.setUsername("admin");
        dto.setEmail("admin@example.com");
        dto.setPassword("password1");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.createInitialAdministrator(dto));
        assertTrue(ex.getMessage().contains("no users exist"));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void createsAdministratorWhenDatabaseHasNoUsers() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.count()).thenReturn(0L);
        Role adminRole = new Role();
        adminRole.setName("ADMINISTRATOR");
        when(roles.findByName("ADMINISTRATOR")).thenReturn(Optional.of(adminRole));
        when(encoder.encode("password1")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SetupService service = new SetupService(
                mock(PaymentMethodRepository.class),
                mock(ChartOfAccountRepository.class),
                mock(CompanySettingsRepository.class),
                users,
                roles,
                encoder
        );

        InitialAdminDTO dto = new InitialAdminDTO();
        dto.setUsername("HlaingHtun");
        dto.setEmail("admin@example.com");
        dto.setPassword("password1");
        service.createInitialAdministrator(dto);

        verify(users).save(any(User.class));
    }
}
