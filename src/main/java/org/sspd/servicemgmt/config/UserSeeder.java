package org.sspd.servicemgmt.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
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
public class UserSeeder  implements CommandLineRunner {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        String adminEmail =  "hlainghtun2018@gmail.com";


        if(!repository.existsByEmail(adminEmail)){
            User user = new User();

            user.setAuthProvider("LOCAL");
            user.setEmail(adminEmail);
            user.setIsActive(true);
            user.setUsername("HlaingHtun");
            user.setPassword(passwordEncoder.encode("21101998"));

            Role adminRole = roleRepository.findByName("ADMINISTRATOR")
                    .orElseThrow(() -> new RuntimeException("Error: Role ADMINISTRATOR not found. Make sure RoleSeeder runs first."));

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            user.setRoles(roles);

            repository.save(user);

            log.info("Default admin user created successfully");

        }
        else {
            log.info("Default admin user already exists");
        }
    }
}
