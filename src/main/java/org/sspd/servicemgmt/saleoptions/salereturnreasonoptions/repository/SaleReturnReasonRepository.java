package org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.model.SaleReturnReason;

import java.util.List;
import java.util.Optional;

public interface SaleReturnReasonRepository extends JpaRepository<SaleReturnReason, Integer> {
    List<SaleReturnReason> findAllByOrderByNameAsc();
    List<SaleReturnReason> findByActiveTrueOrderByNameAsc();
    Optional<SaleReturnReason> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);
}
