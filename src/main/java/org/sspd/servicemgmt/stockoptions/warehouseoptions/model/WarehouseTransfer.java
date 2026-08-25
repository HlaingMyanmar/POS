package org.sspd.servicemgmt.stockoptions.warehouseoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_transfers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WarehouseTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true, length = 40)
    private String transferNo;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "from_warehouse_id", nullable = false)
    private Warehouse fromWarehouse;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "to_warehouse_id", nullable = false)
    private Warehouse toWarehouse;
    @Column(nullable = false)
    private Integer qty;
    private LocalDateTime transferredAt;
    private String transferredBy;
    @Column(length = 255)
    private String remark;
}
