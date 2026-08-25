package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "supplier_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SupplierPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, unique = true) private String paymentNo;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Supplier supplier;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private BigDecimal allocatedAmount;
    private BigDecimal advanceAmount;
    private LocalDateTime paymentDate;
    private String transactionNo;
    private String paidBy;
    private String remark;
    private Boolean voided;
    private LocalDateTime voidedAt;
    private String voidedBy;
    private String voidReason;
    @OneToMany(mappedBy = "supplierPayment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<SupplierPaymentAllocation> allocations = new ArrayList<>();
}
