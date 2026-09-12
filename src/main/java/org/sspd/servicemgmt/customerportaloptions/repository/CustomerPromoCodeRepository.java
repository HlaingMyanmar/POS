package org.sspd.servicemgmt.customerportaloptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPromoCode;

import java.util.List;
import java.util.Optional;

public interface CustomerPromoCodeRepository extends JpaRepository<CustomerPromoCode, Integer> {

    Optional<CustomerPromoCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerPromoCode p where p.code = :code")
    Optional<CustomerPromoCode> findLockedByCode(@Param("code") String code);

    @EntityGraph(attributePaths = {"products", "categories"})
    List<CustomerPromoCode> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {"products", "categories"})
    @Query("select p from CustomerPromoCode p where p.id = :id")
    Optional<CustomerPromoCode> findWithScopeById(@Param("id") Integer id);
}
