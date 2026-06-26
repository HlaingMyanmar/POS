package org.sspd.servicemgmt.creditoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_credit_term_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCreditTermHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "old_credit_limit", precision = 15, scale = 2)
    private BigDecimal oldCreditLimit;

    @Column(name = "new_credit_limit", precision = 15, scale = 2)
    private BigDecimal newCreditLimit;

    @Column(name = "old_credit_days")
    private Integer oldCreditDays;

    @Column(name = "new_credit_days")
    private Integer newCreditDays;

    @Column(name = "old_credit_allowed")
    private Boolean oldCreditAllowed;

    @Column(name = "new_credit_allowed")
    private Boolean newCreditAllowed;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @PrePersist
    public void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
