package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.unitsoptions.enums.UnitName;
import org.sspd.servicemgmt.unitsoptions.model.Unit;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class UnitSeeder implements CommandLineRunner {

    private final UnitRepository repository;

    @Override
    public void run(String... args) throws Exception {

        for(UnitName unitName:UnitName.values()){
            if(!repository.existsByUnitName(unitName.name())){
                Unit unit = new Unit();
                unit.setUnitName(unitName.name());
                unit.setDescription(unitName.getDescription());
                repository.save(unit);
            }
        }
        log.info("Unit seeding completed");

    }
}
