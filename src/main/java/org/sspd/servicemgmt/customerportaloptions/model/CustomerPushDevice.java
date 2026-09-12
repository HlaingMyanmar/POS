package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_push_devices", uniqueConstraints =
        @UniqueConstraint(name = "uk_customer_push_token", columnNames = "token"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerPushDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false) private Customer customer;
    @Column(nullable = false, length = 512) private String token;
    @Column(nullable = false, length = 20) private String platform;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "last_seen_at", nullable = false) private LocalDateTime lastSeenAt;
}
