package org.sspd.servicemgmt.supplieroptions.repository;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Integer> {

    Optional<Supplier> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByName(String name);

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<Supplier> findByNameContainingIgnoreCase(String name);

    Optional<Supplier> findTopByOrderByIdDesc();
}