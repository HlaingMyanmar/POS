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
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class RoleSeeder implements CommandLineRunner {

    private static final List<String> TECHNICIAN_PERMISSIONS = List.of(
            "CAN_ACCESS_CUSTOMER_READ",
            "CAN_ACCESS_PRODUCT_READ",
            "CAN_ACCESS_SERVICE_READ",
            "CAN_ACCESS_SERVICE_JOB_READ",
            "CAN_ACCESS_SERVICE_JOB_UPDATE",
            "CAN_ACCESS_SERVICE_JOB_REWORK",
            "CAN_ACCESS_BOOKING_READ",
            "CAN_ACCESS_BOOKING_UPDATE",
            "CAN_ACCESS_STAFF_READ"
    );

    private static final List<String> CASHIER_PERMISSIONS = List.of(
            "CAN_ACCESS_CUSTOMER_CREATE",
            "CAN_ACCESS_CUSTOMER_READ",
            "CAN_ACCESS_PRODUCT_READ",
            "CAN_ACCESS_SERVICE_READ",
            "CAN_ACCESS_SERVICE_JOB_READ",
            "CAN_ACCESS_SERVICE_JOB_CREATE",
            "CAN_ACCESS_SERVICE_JOB_UPDATE",
            "CAN_ACCESS_SERVICE_JOB_SETTLE",
            "CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN",
            "CAN_ACCESS_BOOKING_CREATE",
            "CAN_ACCESS_BOOKING_READ",
            "CAN_ACCESS_BOOKING_UPDATE",
            "CAN_ACCESS_BOOKING_CONVERT_JOB",
            "CAN_ACCESS_SALE_READ",
            "CAN_ACCESS_SALE_CREATE",
            "CAN_ACCESS_SALE_UPDATE",
            "CAN_ACCESS_PAYMENT_TRANSACTION_CREATE"
    );

    private final RoleRepository repository;
    private final PermissionRepository permissionRepository;

    @Override
    public void run(String... args) {
        List<Permission> allPermissions = permissionRepository.findAll();

        for (RoleName roleName : RoleName.values()) {
            Role role = repository.findByName(roleName.name()).orElseGet(Role::new);
            role.setName(roleName.name());
            role.setDescription(roleName.getDescription());
            if (roleName == RoleName.ADMINISTRATOR) {
                role.setPermissions(new HashSet<>(allPermissions));
            }
            repository.save(role);
        }

        fillIfEmpty("TECHNICIAN", TECHNICIAN_PERMISSIONS, allPermissions);
        fillIfEmpty("CASHIER", CASHIER_PERMISSIONS, allPermissions);
        for (Role role : repository.findAll()) {
            String name = role.getName() == null ? "" : role.getName().toUpperCase();
            if (name.contains("TECH") && (role.getPermissions() == null || role.getPermissions().isEmpty())) {
                role.setPermissions(named(TECHNICIAN_PERMISSIONS, allPermissions));
                repository.save(role);
                log.info("Filled default technician permissions for role {}", role.getName());
            }
        }

        log.info("Role seeding completed; ADMINISTRATOR got all permissions");
    }

    private void fillIfEmpty(String roleName, List<String> permissionNames, List<Permission> allPermissions) {
        Role role = repository.findByName(roleName).orElse(null);
        if (role == null || (role.getPermissions() != null && !role.getPermissions().isEmpty())) return;
        role.setPermissions(named(permissionNames, allPermissions));
        repository.save(role);
        log.info("Filled default permissions for empty role {}", roleName);
    }

    private Set<Permission> named(List<String> names, List<Permission> allPermissions) {
        return allPermissions.stream()
                .filter(permission -> names.contains(permission.getName()))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
