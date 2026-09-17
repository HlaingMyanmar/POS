package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierPayment;
import java.util.List;
import java.util.Optional;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Integer> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SupplierPayment p where p.id = :id")
    Optional<SupplierPayment> findByIdForUpdate(@Param("id") Integer id);

    List<SupplierPayment> findBySupplierIdOrderByIdDesc(Integer supplierId);

    boolean existsBySupplier_Id(Integer supplierId);
}
