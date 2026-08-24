package org.sspd.servicemgmt.cashdraweroptions.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_drawer_movements", indexes = @Index(name = "idx_drawer_movement_session", columnList = "session_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CashDrawerMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CashDrawerSession session;
    @Column(nullable = false, length = 10)
    private String type;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 100)
    private String actor;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
}
