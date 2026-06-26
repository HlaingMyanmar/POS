package org.sspd.servicemgmt.brandoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.brandoptions.model.Brand;

import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand,Long> {


    boolean existsByName(String name);

    Optional<Brand> findByName(String brandName);

}
