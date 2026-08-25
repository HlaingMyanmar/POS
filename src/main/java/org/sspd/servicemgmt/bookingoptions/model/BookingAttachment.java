package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking_attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookingAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private String attachmentType;
    private String fileName;
    private String contentType;

    @Lob @Column(name = "data_url", columnDefinition = "LONGTEXT")
    private String dataUrl;

    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
