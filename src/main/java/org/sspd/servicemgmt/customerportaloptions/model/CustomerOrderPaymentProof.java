package org.sspd.servicemgmt.customerportaloptions.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="customer_order_payment_proofs") @Getter @Setter @NoArgsConstructor
public class CustomerOrderPaymentProof {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 private Integer orderId;
 private Integer paymentMethodId;
    @Column(name = "transaction_reference", length = 120)
    private String transactionReference;
 private BigDecimal amount;
 @Lob @Column(name="image_data", columnDefinition="MEDIUMBLOB") private byte[] imageData;
 private String imageType;
 private LocalDateTime submittedAt;
 private String reviewState;
 private String reviewedBy;
 private String reviewNote;
}
