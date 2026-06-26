package org.sspd.servicemgmt.stockoptions.manufacturingoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "manufacturing_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManufacturingOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ManufacturingOrder order;

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

    @ElementCollection
    @CollectionTable(name = "mfg_order_item_serials", joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "serial_id")
    @Builder.Default
    private List<Integer> selectedSerialIds = new ArrayList<>();
}
