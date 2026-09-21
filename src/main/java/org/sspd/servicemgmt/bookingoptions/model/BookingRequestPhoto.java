package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_request_photos", uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_request_photo_slot", columnNames = {"booking_id", "slot"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookingRequestPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
    @Column(nullable = false)
    private Integer slot;
    @Column(name = "file_name", length = 255)
    private String fileName;
    @Column(name = "content_type", length = 120)
    private String contentType;
    @Column(name = "image_path", length = 500, nullable = false)
    private String imagePath;
    @Column(name = "thumbnail_path", length = 500, nullable = false)
    private String thumbnailPath;
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    @PrePersist
    void onCreate() { if (uploadedAt == null) uploadedAt = LocalDateTime.now(); }
}
