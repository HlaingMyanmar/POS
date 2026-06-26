package org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;

import java.util.List;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Integer> {
    boolean existsByMethodName(String methodName);

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<PaymentMethod> findAllByActiveTrue();
}