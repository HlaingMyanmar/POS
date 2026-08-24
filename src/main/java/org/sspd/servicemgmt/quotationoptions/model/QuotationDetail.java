package org.sspd.servicemgmt.quotationoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuotationDetail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quotation_id")
    private Quotation quotation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id")
    private Product product;
    @Column(nullable = false) private Integer qty;
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2) private BigDecimal unitPrice;
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2) private BigDecimal discountAmount;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal subtotal;
}
