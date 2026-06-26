package org.sspd.servicemgmt.stockoptions.stockmovementoptions.model;



import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements", indexes = {
    @Index(name = "idx_sm_product",    columnList = "product_id"),
    @Index(name = "idx_sm_created_at", columnList = "created_at"),
    @Index(name = "idx_sm_ref",        columnList = "reference_type,reference_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    private MovementType movementType; // IN, OUT, RETURN, ADJUST

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "reference_id")
    private Integer referenceId; // Purchase ID သို့မဟုတ် Sale ID
    @Column(name = "reference_type")
    private String referenceType; // 'Purchase', 'Sale', 'Return'

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

