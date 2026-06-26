package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "booking_devices")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BookingDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "accessories", columnDefinition = "TEXT")
    private String accessories;

    @Column(name = "problem_desc", columnDefinition = "TEXT")
    private String problemDesc;

    @Column(name = "device_conditions", columnDefinition = "TEXT")
    private String deviceConditions;
}
