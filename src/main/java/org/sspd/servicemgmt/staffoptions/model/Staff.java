package org.sspd.servicemgmt.staffoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "staff")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String role; // ဥပမာ - Receptionist, Technician, Manager

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "basic_salary", precision = 15, scale = 2)
    private java.math.BigDecimal basicSalary = java.math.BigDecimal.ZERO;
}