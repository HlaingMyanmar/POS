package org.sspd.servicemgmt.creditoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.saleoptions.model.Sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_credit_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerCreditApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "application_no", nullable = false, unique = true, length = 50)
    private String applicationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(name = "service_job_id")
    private Integer serviceJobId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "applied_by", length = 120)
    private String appliedBy;

    @Column(length = 500)
    private String reason;
}
