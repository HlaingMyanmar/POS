package org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "manufacturing_formula_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManufacturingFormulaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formula_id", nullable = false)
    private ManufacturingFormula formula;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "qty")
    @Builder.Default
    private Integer qty = 1;

    @Column(name = "unit_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;
}
