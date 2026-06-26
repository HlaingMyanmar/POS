package org.sspd.servicemgmt.stockoptions.productoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.sspd.servicemgmt.brandoptions.model.Brand;
import org.sspd.servicemgmt.categoryoptions.model.Category;
import org.sspd.servicemgmt.stockoptions.productoptions.enums.ProductType;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.unitsoptions.model.Unit;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "product_code", length = 20, nullable = false)
    private String productCode;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type")
    private ProductType productType = ProductType.New;

    @Column(name = "selling_price", precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String remark;

    // Relationship များ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ProductSerial> serials;

    @Column(name = "has_serial")
    private Boolean hasSerial = Boolean.TRUE;

    @Column(name = "stock_qty")
    private Integer stockQty = 0;

    @Builder.Default
    @Column(name = "reorder_level")
    private Integer reorderLevel = 0;

    @Builder.Default
    @Column(name = "warranty_months")
    private Integer warrantyMonths = 0;

    @Column(name = "warranty_terms", length = 255)
    private String warrantyTerms;

    @Column(name = "photo_base64", columnDefinition = "LONGTEXT")
    private String photoBase64;
}
