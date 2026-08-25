package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierPayment;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Integer> {
    List<SupplierPayment> findBySupplierIdOrderByIdDesc(Integer supplierId);
}
