package org.sspd.servicemgmt.serviceoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_type")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active")
    private boolean isActive = true;
}
