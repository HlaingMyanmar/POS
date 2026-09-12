package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppAccount;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface CustomerAppAccountRepository extends JpaRepository<CustomerAppAccount, Integer> {
    @EntityGraph(attributePaths = "customer")
    Optional<CustomerAppAccount> findByPhone(String phone);

    @EntityGraph(attributePaths = "customer")
    Optional<CustomerAppAccount> findByEmail(String email);

    @EntityGraph(attributePaths = "customer")
    Optional<CustomerAppAccount> findByGoogleSub(String googleSub);

    boolean existsByPhone(String phone);

    @EntityGraph(attributePaths = "customer")
    Optional<CustomerAppAccount> findByCustomer_Id(Integer customerId);

    @EntityGraph(attributePaths = "customer")
    java.util.List<CustomerAppAccount> findAllByOrderByIdDesc();

    @Query(value = """
            SELECT * FROM customer_app_account
            WHERE phone IS NOT NULL
              AND REGEXP_REPLACE(phone, '[^0-9]+', '') IN (:digits)
            LIMIT 1
            """, nativeQuery = true)
    Optional<CustomerAppAccount> findFirstByPhoneDigitsIn(@Param("digits") Collection<String> digits);

    @EntityGraph(attributePaths = "customer")
    Optional<CustomerAppAccount> findByResetTokenHash(String resetTokenHash);

    @Query("""
            SELECT a FROM CustomerAppAccount a
            JOIN a.customer c
            WHERE LOWER(a.email) = LOWER(:email)
               OR LOWER(c.email) = LOWER(:email)
            """)
    @EntityGraph(attributePaths = "customer")
    Optional<CustomerAppAccount> findByAccountOrCustomerEmail(@Param("email") String email);

    @Query("""
            SELECT a FROM CustomerAppAccount a
            JOIN FETCH a.customer c
            WHERE LOWER(TRIM(c.name)) = LOWER(TRIM(:name))
            """)
    java.util.List<CustomerAppAccount> findAllByCustomerNameIgnoreCase(@Param("name") String name);
}
