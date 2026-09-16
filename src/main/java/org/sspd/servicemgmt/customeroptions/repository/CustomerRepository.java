package org.sspd.servicemgmt.customeroptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Integer> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByPhone(String phone);
    Optional<Customer> findByPhone(String phone);
    Optional<Customer> findByEmail(String email);

    @Query(value = """
            SELECT * FROM customer
            WHERE REGEXP_REPLACE(IFNULL(phone, ''), '[^0-9]+', '') IN (:digits)
            LIMIT 1
            """, nativeQuery = true)
    Optional<Customer> findFirstByPhoneDigitsIn(@Param("digits") Collection<String> digits);
}
