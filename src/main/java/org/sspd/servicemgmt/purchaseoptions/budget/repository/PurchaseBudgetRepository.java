package org.sspd.servicemgmt.purchaseoptions.budget.repository;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.purchaseoptions.budget.model.PurchaseBudget; import java.time.LocalDate; import java.util.List;
public interface PurchaseBudgetRepository extends JpaRepository<PurchaseBudget,Integer> {
 List<PurchaseBudget> findAllByOrderByDateFromDesc();
 @Query("select b from PurchaseBudget b where b.active=true and :date between b.dateFrom and b.dateTo")
 List<PurchaseBudget> findActiveForDate(@Param("date") LocalDate date);
}
