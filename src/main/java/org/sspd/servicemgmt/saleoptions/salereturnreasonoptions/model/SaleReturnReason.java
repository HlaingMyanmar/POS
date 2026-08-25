package org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sale_return_reasons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleReturnReason {
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
