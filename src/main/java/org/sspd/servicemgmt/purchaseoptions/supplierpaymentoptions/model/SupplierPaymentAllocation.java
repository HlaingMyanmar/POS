package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import java.math.BigDecimal;

@Entity @Table(name = "supplier_payment_allocations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SupplierPaymentAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private SupplierPayment supplierPayment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Purchase purchase;
    private BigDecimal amount;
}
