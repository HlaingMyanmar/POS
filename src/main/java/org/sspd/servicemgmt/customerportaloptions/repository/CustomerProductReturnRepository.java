package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerProductReturn;

import java.util.List;
import java.util.Optional;

public interface CustomerProductReturnRepository extends JpaRepository<CustomerProductReturn, Integer> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct r from CustomerProductReturn r left join fetch r.lines left join fetch r.customer where r.id = :id")
    Optional<CustomerProductReturn> findLockedByIdWithLines(@Param("id") Integer id);

    @Query("select distinct r from CustomerProductReturn r left join fetch r.lines left join fetch r.customer where r.id = :id")
    Optional<CustomerProductReturn> findByIdWithLines(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"lines", "customer", "order"})
    List<CustomerProductReturn> findByCustomer_IdOrderByIdDesc(Integer customerId);

    @EntityGraph(attributePaths = {"lines", "customer", "order"})
    List<CustomerProductReturn> findByOrder_IdOrderByIdDesc(Integer orderId);

    @EntityGraph(attributePaths = {"lines", "customer", "order"})
    List<CustomerProductReturn> findBySaleIdOrderByIdDesc(Integer saleId);

    @EntityGraph(attributePaths = {"lines", "customer", "order"})
    List<CustomerProductReturn> findAllByOrderByIdDesc();
}
