package org.sspd.servicemgmt.customerportaloptions.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderPaymentProof;
import java.util.Optional;
public interface CustomerOrderPaymentProofRepository extends JpaRepository<CustomerOrderPaymentProof,Integer> {
 Optional<CustomerOrderPaymentProof> findByPaymentMethodIdAndTransactionReference(Integer methodId, String reference);
}
