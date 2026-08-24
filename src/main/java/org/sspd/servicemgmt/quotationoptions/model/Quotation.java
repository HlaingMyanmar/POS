package org.sspd.servicemgmt.quotationoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "quotations", indexes = {
        @Index(name = "idx_quote_code", columnList = "quotation_code"),
        @Index(name = "idx_quote_status", columnList = "status"),
        @Index(name = "idx_quote_customer", columnList = "customer_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quotation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "quotation_code", nullable = false, unique = true, length = 50)
    private String quotationCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @Column(name = "quotation_date", nullable = false)
    private LocalDateTime quotationDate;
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private QuotationStatus status;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;
    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;
    @Column(columnDefinition = "TEXT") private String terms;
    @Column(columnDefinition = "TEXT") private String remark;
    @Column(name = "converted_sale_id") private Integer convertedSaleId;
    @Column(name = "converted_by", length = 100) private String convertedBy;
    @Column(name = "converted_at") private LocalDateTime convertedAt;
    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<QuotationDetail> details = new ArrayList<>();
}
