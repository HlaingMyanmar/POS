package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.model.Permission;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.enums.PermissionName;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.repository.PermissionRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class PermissionSeeder implements CommandLineRunner {

    private final PermissionRepository repository;

    @Override
    public void run(String... args) {

        for (PermissionName permissionName : PermissionName.values()) {


            if (!repository.existsByName(permissionName.name())) {

                Permission permission = new Permission();
                // ဒီမှာလည်း String အဖြစ် သိမ်းပါ
                permission.setName(permissionName.name());
                permission.setDescription(permissionName.getDescription());

                repository.save(permission);
            }
        }

        log.info("Permission seeding completed");
    }
}
