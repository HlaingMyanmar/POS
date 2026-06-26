package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "booking_device_infos")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BookingDeviceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "name", length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String notice;
}
