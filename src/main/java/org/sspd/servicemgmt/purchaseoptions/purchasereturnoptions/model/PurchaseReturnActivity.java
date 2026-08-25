package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="purchase_return_activities") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseReturnActivity { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_return_id") private PurchaseReturn purchaseReturn; private String eventType; private String fromStatus; private String toStatus; @Column(length=1000) private String note; private String actor; private LocalDateTime occurredAt; }
