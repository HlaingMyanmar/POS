package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.creditoptions.model.CustomerPayment;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Integer> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerPayment p where p.id = :id")
    Optional<CustomerPayment> findByIdForUpdate(@Param("id") Integer id);

    List<CustomerPayment> findByCustomerId(Integer customerId);
    List<CustomerPayment> findByCustomerIdOrderByIdDesc(Integer customerId);
    List<CustomerPayment> findBySaleId(Integer saleId);
}
