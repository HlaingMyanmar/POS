package org.sspd.servicemgmt.stockoptions.manufacturingoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.enums.ManufacturingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "manufacturing_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManufacturingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_code", nullable = false, length = 30)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ManufacturingStatus status = ManufacturingStatus.DRAFT;

    @Column(name = "finished_product_name", nullable = false)
    private String finishedProductName;

    @Column(name = "finished_product_brand_id")
    private Integer finishedProductBrandId;

    @Column(name = "finished_product_category_id")
    private Integer finishedProductCategoryId;

    @Column(name = "finished_product_unit_id")
    private Integer finishedProductUnitId;

    @Column(name = "finished_product_type", length = 20)
    @Builder.Default
    private String finishedProductType = "New";

    @Column(name = "finished_product_selling_price", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal finishedProductSellingPrice = BigDecimal.ZERO;

    @Column(name = "production_qty", nullable = false)
    @Builder.Default
    private Integer productionQty = 1;

    @Column(name = "labor_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "overhead_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal overheadCost = BigDecimal.ZERO;

    @Column(name = "waste_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal wasteCost = BigDecimal.ZERO;

    @Column(name = "finished_product_id")
    private Integer finishedProductId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ManufacturingOrderItem> items = new ArrayList<>();
}
