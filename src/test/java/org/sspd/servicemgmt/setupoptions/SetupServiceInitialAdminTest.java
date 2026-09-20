package org.sspd.servicemgmt.setupoptions;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
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
    private static final String SETUP_TOKEN = "test-setup-token-at-least-32-characters";

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
        ReflectionTestUtils.setField(service, "initialAdminToken", SETUP_TOKEN);

        InitialAdminDTO dto = new InitialAdminDTO();
        dto.setUsername("admin");
        dto.setEmail("admin@example.com");
        dto.setPassword("password12ab");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.createInitialAdministrator(dto, SETUP_TOKEN));
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
        when(encoder.encode("password12ab")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SetupService service = new SetupService(
                mock(PaymentMethodRepository.class),
                mock(ChartOfAccountRepository.class),
                mock(CompanySettingsRepository.class),
                users,
                roles,
                encoder
        );
        ReflectionTestUtils.setField(service, "initialAdminToken", SETUP_TOKEN);

        InitialAdminDTO dto = new InitialAdminDTO();
        dto.setUsername("HlaingHtun");
        dto.setEmail("admin@example.com");
        dto.setPassword("password12ab");
        service.createInitialAdministrator(dto, SETUP_TOKEN);

        verify(users).save(any(User.class));
    }

    @Test
    void rejectsMissingOrIncorrectSetupTokenBeforeCheckingDatabase() {
        UserRepository users = mock(UserRepository.class);
        SetupService service = new SetupService(
                mock(PaymentMethodRepository.class),
                mock(ChartOfAccountRepository.class),
                mock(CompanySettingsRepository.class),
                users,
                mock(RoleRepository.class),
                mock(PasswordEncoder.class)
        );
        ReflectionTestUtils.setField(service, "initialAdminToken", SETUP_TOKEN);
        InitialAdminDTO dto = new InitialAdminDTO();

        assertThrows(AccessDeniedException.class,
                () -> service.createInitialAdministrator(dto, null));
        assertThrows(AccessDeniedException.class,
                () -> service.createInitialAdministrator(dto, "wrong-token"));
        verify(users, never()).count();
        verify(users, never()).save(any(User.class));
    }

    @Test
    void rejectsShortPasswordForInitialAdmin() {
        UserRepository users = mock(UserRepository.class);
        when(users.count()).thenReturn(0L);
        SetupService service = new SetupService(
                mock(PaymentMethodRepository.class),
                mock(ChartOfAccountRepository.class),
                mock(CompanySettingsRepository.class),
                users,
                mock(RoleRepository.class),
                mock(PasswordEncoder.class)
        );
        ReflectionTestUtils.setField(service, "initialAdminToken", SETUP_TOKEN);

        InitialAdminDTO dto = new InitialAdminDTO();
        dto.setUsername("admin");
        dto.setEmail("admin@example.com");
        dto.setPassword("short");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createInitialAdministrator(dto, SETUP_TOKEN));
        assertTrue(ex.getMessage().toLowerCase().contains("12")
                || ex.getMessage().toLowerCase().contains("administrator"));
        verify(users, never()).save(any(User.class));
    }
}
