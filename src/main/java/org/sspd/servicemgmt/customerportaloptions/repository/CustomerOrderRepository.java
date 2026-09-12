package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findLocked(@Param("id") Integer id);

    @Query("select o.id from CustomerOrder o where o.reservationActive = true and o.paymentState in ('AWAITING_PAYMENT', 'AWAITING_COLLECTION') and o.reservationExpiresAt <= :now")
    List<Integer> findExpired(@Param("now") java.time.LocalDateTime now);

    @EntityGraph(attributePaths = {"customer", "lines", "lines.product"})
    List<CustomerOrder> findByCustomer_IdOrderByIdDesc(Integer customerId);

    @EntityGraph(attributePaths = {"customer", "lines", "lines.product"})
    List<CustomerOrder> findAllByOrderByIdDesc();

    @Query("""
            select distinct o from CustomerOrder o
            left join fetch o.customer
            left join fetch o.lines l
            left join fetch l.product
            where o.id = :id
            """)
    Optional<CustomerOrder> findByIdWithLines(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"customer", "lines", "lines.product"})
    Optional<CustomerOrder> findByCustomer_IdAndIdempotencyKey(Integer customerId, String idempotencyKey);

    @EntityGraph(attributePaths = {"customer", "lines", "lines.product"})
    Optional<CustomerOrder> findFirstByCompletedSaleId(Integer completedSaleId);
}
