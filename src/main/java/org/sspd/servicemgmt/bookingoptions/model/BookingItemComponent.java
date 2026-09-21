package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "booking_item_components")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingItemComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_item_id", nullable = false)
    private BookingItem bookingItem;

    @Column(name = "component_type", nullable = false, length = 40)
    private String componentType;

    @Column(length = 120)
    private String brand;

    @Column(length = 160)
    private String model;

    @Column(length = 255)
    private String specification;

    @Column(name = "serial_no", length = 160)
    private String serialNo;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "condition_note", columnDefinition = "TEXT")
    private String conditionNote;
}
