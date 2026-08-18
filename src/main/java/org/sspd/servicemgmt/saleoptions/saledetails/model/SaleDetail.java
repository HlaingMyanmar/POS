package org.sspd.servicemgmt.saleoptions.saledetails.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sale_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer qty;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /** Voucher-only unit price. It never participates in sale totals or payments. */
    @Column(name = "custom_voucher_price", precision = 15, scale = 2)
    private BigDecimal customVoucherPrice;

    /** Stored separately so reseller/customer margin does not alter revenue or profit. */
    @Builder.Default
    @Column(name = "customer_margin", precision = 15, scale = 2)
    private BigDecimal customerMargin = BigDecimal.ZERO;

    private BigDecimal subtotal;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "cost_price_snapshot", precision = 15, scale = 2)
    private BigDecimal costPriceSnapshot;

    @Builder.Default
    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_foc")
    private Boolean foc = Boolean.FALSE;

    @Builder.Default
    @Column(name = "warranty_months")
    private Integer warrantyMonths = 0;

    @Column(name = "warranty_expiry_date")
    private LocalDate warrantyExpiryDate;
}
