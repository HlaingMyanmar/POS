package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sspd.servicemgmt.customeroptions.model.Customer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_order_ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "sale_id")
    private Integer saleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Average of product + service, kept for list/sort. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer rating;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "product_rating", nullable = false)
    private Integer productRating;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "service_rating", nullable = false)
    private Integer serviceRating;

    @Column(length = 1000)
    private String review;

    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "hidden_by", length = 255)
    private String hiddenBy;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hide_reason", length = 1000)
    private String hideReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "editable_until")
    private LocalDateTime editableUntil;

    @Builder.Default
    @OneToMany(mappedBy = "orderRating", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderRatingLine> lines = new ArrayList<>();
}
