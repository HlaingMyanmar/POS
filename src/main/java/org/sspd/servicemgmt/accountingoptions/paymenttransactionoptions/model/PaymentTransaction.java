package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "payment_transactions")
@Getter @Setter @NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer referenceId; // Purchase ID သို့မဟုတ် Sale ID

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private ReferenceType referenceType; // Sale, Purchase, etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    private BigDecimal amount;
    private LocalDateTime paymentDate = LocalDateTime.now();
    private String transactionNo;
}