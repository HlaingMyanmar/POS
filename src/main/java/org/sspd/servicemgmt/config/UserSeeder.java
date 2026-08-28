package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
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
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true", matchIfMissing = true)
public class UserSeeder implements CommandLineRunner {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email:hlainghtun2018@gmail.com}")
    private String adminEmail;

    @Value("${app.bootstrap-admin.username:HlaingHtun}")
    private String adminUsername;

    @Value("${app.bootstrap-admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (repository.existsByRoleName("ADMINISTRATOR")) {
            log.info("Administrator already exists; skipping bootstrap admin");
            return;
        }

        if (isBlank(adminEmail) || isBlank(adminUsername) || isBlank(adminPassword)
                || "CHANGE_ME".equals(adminPassword)) {
            log.warn("No ADMINISTRATOR user found. Set BOOTSTRAP_ADMIN_PASSWORD (and optional EMAIL/USERNAME) to create one.");
            return;
        }

        if (repository.existsByEmail(adminEmail.trim())) {
            log.warn("User {} exists but has no ADMINISTRATOR role; not overwriting. Assign ADMINISTRATOR in Role Management.",
                    adminEmail.trim());
            return;
        }

        Role adminRole = roleRepository.findByName("ADMINISTRATOR")
                .orElseThrow(() -> new RuntimeException(
                        "Error: Role ADMINISTRATOR not found. Make sure RoleSeeder runs first."));

        User user = new User();
        user.setAuthProvider("LOCAL");
        user.setEmail(adminEmail.trim());
        user.setIsActive(true);
        user.setUsername(adminUsername.trim());
        user.setPassword(passwordEncoder.encode(adminPassword));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        user.setRoles(roles);

        repository.save(user);
        log.info("Bootstrap ADMINISTRATOR created for {}", adminEmail.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
