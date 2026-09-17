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

    @Column(length = 30)
    @Enumerated(EnumType.STRING)
    private ReferenceType referenceType; // Sale, Purchase, etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    private BigDecimal amount;
    private LocalDateTime paymentDate = LocalDateTime.now();
    @Column(unique = true)
    private String transactionNo;
    @Column(nullable = false)
    private Boolean reversed = false;
    private LocalDateTime reversedAt;
    private String reversedBy;
    @Column(columnDefinition = "TEXT")
    private String reversalReason;

    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Integer sourceId;

    public static final String SOURCE_SUPPLIER_PAYMENT = "Supplier_Payment";
    public static final String SOURCE_CUSTOMER_PAYMENT = "Customer_Payment";

    @PostPersist
    public void assignGeneratedNumberIfBlank() {
        if (id != null && (transactionNo == null || transactionNo.isBlank())) {
            transactionNo = String.format("TXN-%06d", id);
        }
    }
}
