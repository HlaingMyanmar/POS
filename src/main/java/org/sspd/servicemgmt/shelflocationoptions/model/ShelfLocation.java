package org.sspd.servicemgmt.shelflocationoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shelf_locations", indexes = {
    @Index(name = "idx_shelf_code", columnList = "code")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ShelfLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
