package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customer_order_rating_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderRatingLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rating_id", nullable = false)
    private CustomerOrderRating orderRating;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer rating;
}
