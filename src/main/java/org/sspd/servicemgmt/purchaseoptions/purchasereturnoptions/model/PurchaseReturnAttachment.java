package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="purchase_return_attachments") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseReturnAttachment { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_return_id") private PurchaseReturn purchaseReturn; private String attachmentType; private String fileName; private String contentType; @Lob @Column(columnDefinition="LONGTEXT") private String dataUrl; private String uploadedBy; private LocalDateTime uploadedAt; }
