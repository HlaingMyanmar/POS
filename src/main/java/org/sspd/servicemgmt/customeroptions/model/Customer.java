package org.sspd.servicemgmt.customeroptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "customer")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String address;

    @Column(name = "credit_hold")
    private Boolean creditHold = Boolean.FALSE;

    @Column(name = "credit_hold_reason", columnDefinition = "TEXT")
    private String creditHoldReason;

    @Column(name = "blacklisted")
    private Boolean blacklisted = Boolean.FALSE;

    @Column(name = "blacklist_reason", columnDefinition = "TEXT")
    private String blacklistReason;
}
