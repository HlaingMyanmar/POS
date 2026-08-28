package org.sspd.servicemgmt.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserSeederTest {

    @Test
    void doesNothingWhenBootstrapIsDisabled() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        new UserSeeder(users, roles, encoder, new MockEnvironment()).run();

        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void rejectsEnabledBootstrapWithoutCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.bootstrap-admin.enabled", "true");

        UserSeeder seeder = new UserSeeder(
                mock(UserRepository.class),
                mock(RoleRepository.class),
                mock(PasswordEncoder.class),
                environment
        );

        assertThrows(IllegalStateException.class, seeder::run);
    }

    @Test
    void createsAdministratorFromExternalConfiguration() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Role administrator = mock(Role.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.bootstrap-admin.enabled", "true")
                .withProperty("app.bootstrap-admin.email", "admin@example.invalid")
                .withProperty("app.bootstrap-admin.username", "administrator")
                .withProperty("app.bootstrap-admin.password", "a-secure-password");

        when(users.existsByEmail("admin@example.invalid")).thenReturn(false);
        when(roles.findByName("ADMINISTRATOR")).thenReturn(Optional.of(administrator));
        when(encoder.encode("a-secure-password")).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new UserSeeder(users, roles, encoder, environment).run();

        verify(encoder).encode("a-secure-password");
        verify(users).save(org.mockito.ArgumentMatchers.argThat(user ->
                "admin@example.invalid".equals(user.getEmail())
                        && "administrator".equals(user.getUsername())
                        && "encoded-password".equals(user.getPassword())
                        && user.getRoles().contains(administrator)
        ));
    }
}
