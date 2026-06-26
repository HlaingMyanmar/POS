package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditTerm;

import java.util.Optional;

@Repository
public interface CustomerCreditTermRepository extends JpaRepository<CustomerCreditTerm, Integer> {
    Optional<CustomerCreditTerm> findByCustomerId(Integer customerId);
}
