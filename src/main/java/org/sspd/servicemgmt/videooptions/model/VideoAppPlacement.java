package org.sspd.servicemgmt.videooptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "video_app_placements",
        uniqueConstraints = @UniqueConstraint(name = "uk_video_app_placement", columnNames = {"video_id", "app_type"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoAppPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false, length = 32)
    private VideoAppType appType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean featured;

    @Column(nullable = false)
    private Boolean active;

    @PrePersist
    public void onCreate() {
        if (sortOrder == null) {
            sortOrder = 1;
        }
        if (featured == null) {
            featured = Boolean.FALSE;
        }
        if (active == null) {
            active = Boolean.TRUE;
        }
    }
}
