package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "device_type", length = 80)
    private String deviceType;

    @Column(name = "serial_no", length = 120)
    private String serialNo;

    @Column(length = 80)
    private String color;

    @Column(columnDefinition = "TEXT")
    private String accessories;

    @Column(name = "problem_desc", columnDefinition = "TEXT")
    private String problemDesc;

    @Column(name = "item_condition", columnDefinition = "TEXT")
    private String itemCondition;

    @Column(columnDefinition = "TEXT")
    private String noticed;

    @Column(name = "converted_job_id")
    private Integer convertedJobId;

    @Builder.Default
    @OneToMany(mappedBy = "bookingItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slot ASC")
    private List<BookingItemPhoto> photos = new ArrayList<>();
}
