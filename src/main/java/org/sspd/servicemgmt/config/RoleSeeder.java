package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.model.Permission;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.repository.PermissionRepository;
import org.sspd.servicemgmt.rbacoptions.roleoptions.enums.RoleName;
import org.sspd.servicemgmt.rbacoptions.roleoptions.model.Role;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;

import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class RoleSeeder implements CommandLineRunner {

    private final RoleRepository repository;

    private final PermissionRepository permissionRepository;


    @Override
    public void run(String... args) throws Exception {

        List<Permission> allPermissions = permissionRepository.findAll();

        for(RoleName roleName : RoleName.values()){


            Role role = repository.findByName(roleName.name())
                    .orElse(new Role());

            role.setName(roleName.name());
            role.setDescription(roleName.getDescription());

            if (roleName == RoleName.ADMINISTRATOR) {
                role.setPermissions(new HashSet<>(allPermissions));
            }

            repository.save(role);

        }

        log.info("Role seeding completed; ADMINISTRATOR got all permissions");

    }
}
