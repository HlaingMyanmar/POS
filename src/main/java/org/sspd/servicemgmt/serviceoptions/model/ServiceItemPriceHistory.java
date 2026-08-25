package org.sspd.servicemgmt.serviceoptions.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_item_price_history", indexes = @Index(name = "idx_service_price_history_item", columnList = "service_item_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceItemPriceHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_item_id", nullable = false)
    private ServiceItem serviceItem;
    @Column(name = "old_price", precision = 15, scale = 2) private BigDecimal oldPrice;
    @Column(name = "new_price", precision = 15, scale = 2) private BigDecimal newPrice;
    @Column(name = "old_cost", precision = 15, scale = 2) private BigDecimal oldCost;
    @Column(name = "new_cost", precision = 15, scale = 2) private BigDecimal newCost;
    @Column(name = "changed_by", length = 120) private String changedBy;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;
}
