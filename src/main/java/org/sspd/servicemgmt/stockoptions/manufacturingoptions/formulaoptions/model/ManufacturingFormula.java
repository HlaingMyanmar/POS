package org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "manufacturing_formulas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManufacturingFormula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "finished_product_name")
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

    @OneToMany(mappedBy = "formula", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ManufacturingFormulaItem> items = new ArrayList<>();
}
