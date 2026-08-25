package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "supplier_credit_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SupplierCreditApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, unique = true) private String applicationNo;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Supplier supplier;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Purchase targetPurchase;
    private BigDecimal amount;
    private BigDecimal advanceUsed;
    private BigDecimal returnCreditUsed;
    private LocalDateTime appliedAt;
    private String appliedBy;
    private String reason;
}
