package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_app_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAppAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(unique = true, length = 190)
    private String email;

    @Column(name = "google_sub", unique = true, length = 64)
    private String googleSub;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Builder.Default
    @Column(name = "profile_complete", nullable = false)
    private Boolean profileComplete = Boolean.TRUE;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder.Default
    @Column(name = "login_count", nullable = false)
    private Integer loginCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (tokenVersion == null) tokenVersion = 0;
        if (enabled == null) enabled = Boolean.TRUE;
        if (profileComplete == null) profileComplete = Boolean.TRUE;
        if (loginCount == null) loginCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
