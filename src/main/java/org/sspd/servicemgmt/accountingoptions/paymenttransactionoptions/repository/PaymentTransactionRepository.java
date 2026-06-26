package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {

    // Reference ID (Purchase/Sale ID) နဲ့ Type အလိုက် ငွေပေးချေမှုမှတ်တမ်း ရှာရန်
    List<PaymentTransaction> findByReferenceIdAndReferenceType(Integer referenceId, ReferenceType referenceType);

    Optional<PaymentTransaction> findTopByOrderByIdDesc();
}