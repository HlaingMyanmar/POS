package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class UserSeeder implements CommandLineRunner {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    public void run(String... args) {
        boolean bootstrapEnabled = environment.getProperty(
                "app.bootstrap-admin.enabled",
                Boolean.class,
                false
        );
        if (!bootstrapEnabled) {
            log.info("Administrator bootstrap is disabled");
            return;
        }

        String adminEmail = requiredBootstrapValue("app.bootstrap-admin.email");
        String adminUsername = requiredBootstrapValue("app.bootstrap-admin.username");
        String adminPassword = requiredBootstrapValue("app.bootstrap-admin.password");
        if (adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "Bootstrap administrator password must contain at least 12 characters"
            );
        }

        if (!repository.existsByEmail(adminEmail)) {
            User user = new User();
            user.setAuthProvider("LOCAL");
            user.setEmail(adminEmail);
            user.setIsActive(true);
            user.setUsername(adminUsername);
            user.setPassword(passwordEncoder.encode(adminPassword));

            Role adminRole = roleRepository.findByName("ADMINISTRATOR")
                    .orElseThrow(() -> new RuntimeException(
                            "Error: Role ADMINISTRATOR not found. Make sure RoleSeeder runs first."
                    ));

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            user.setRoles(roles);
            repository.save(user);

            log.info("Bootstrap administrator created successfully");
        } else {
            log.info("Bootstrap administrator already exists");
        }
    }

    private String requiredBootstrapValue(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    propertyName + " is required when app.bootstrap-admin.enabled=true"
            );
        }
        return value.trim();
    }
}
