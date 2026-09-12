package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_wishlists",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_customer_product", columnNames = {"customer_id", "product_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerWishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
