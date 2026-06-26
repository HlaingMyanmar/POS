package org.sspd.servicemgmt.stockoptions.stockmovementoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Integer> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Integer productId);

    List<StockMovement> findByReferenceIdAndReferenceType(Integer referenceId, String referenceType);

    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.product ORDER BY sm.createdAt DESC")
    List<StockMovement> findAllWithProduct();

    List<StockMovement> findByProductIdAndCreatedAtBetweenOrderByCreatedAtDesc(Integer productId, LocalDateTime from, LocalDateTime to);

    List<StockMovement> findByMovementTypeOrderByCreatedAtDesc(MovementType movementType);
}
