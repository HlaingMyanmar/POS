package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_product_return_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProductReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private CustomerProductReturn productReturn;

    @Column(name = "order_line_id")
    private Integer orderLineId;

    @Column(name = "sale_detail_id")
    private Integer saleDetailId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(length = 20)
    private String disposition;

    @Column(name = "replacement_product_id")
    private Integer replacementProductId;

    @Column(name = "replacement_serial_number", length = 120)
    private String replacementSerialNumber;
}
