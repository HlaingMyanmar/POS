package org.sspd.servicemgmt.authoption;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "refresh_sessions", indexes = {
        @Index(name = "idx_refresh_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_refresh_sessions_family_id", columnList = "family_id"),
        @Index(name = "idx_refresh_sessions_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "jti", nullable = false, unique = true, length = 36)
    private String jti;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_jti", length = 36)
    private String replacedByJti;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** First login for this refresh family — absolute timeout anchor (not reset on rotate). */
    @Column(name = "session_started_at", nullable = false)
    private Instant sessionStartedAt;

    /** Last successful refresh / unlock — idle timeout anchor. */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }

    public Instant effectiveSessionStartedAt() {
        return sessionStartedAt != null ? sessionStartedAt : createdAt;
    }

    public Instant effectiveLastSeenAt() {
        return lastSeenAt != null ? lastSeenAt : createdAt;
    }
}
