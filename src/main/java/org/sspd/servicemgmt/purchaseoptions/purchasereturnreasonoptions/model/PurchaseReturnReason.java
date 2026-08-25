package org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_return_reasons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseReturnReason {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;
}
