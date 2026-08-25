package org.sspd.servicemgmt.creditoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.saleoptions.model.Sale;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_payment_allocations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerPaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_payment_id", nullable = false)
    private CustomerPayment customerPayment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
}
