package org.sspd.servicemgmt.cashdraweroptions.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_drawer_sessions", indexes = {
        @Index(name = "idx_drawer_status", columnList = "status"),
        @Index(name = "idx_drawer_opened_at", columnList = "opened_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CashDrawerSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "opened_by", nullable = false, length = 100)
    private String openedBy;
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;
    @Column(name = "opening_cash", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingCash;
    @Builder.Default @Column(name = "cash_sales", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashSales = BigDecimal.ZERO;
    @Builder.Default @Column(name = "cash_refunds", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashRefunds = BigDecimal.ZERO;
    @Builder.Default @Column(name = "cash_in", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashIn = BigDecimal.ZERO;
    @Builder.Default @Column(name = "cash_out", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashOut = BigDecimal.ZERO;
    @Column(name = "expected_cash", precision = 15, scale = 2)
    private BigDecimal expectedCash;
    @Column(name = "counted_cash", precision = 15, scale = 2)
    private BigDecimal countedCash;
    @Column(name = "difference_amount", precision = 15, scale = 2)
    private BigDecimal differenceAmount;
    @Column(name = "closed_by", length = 100)
    private String closedBy;
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String note;
}
