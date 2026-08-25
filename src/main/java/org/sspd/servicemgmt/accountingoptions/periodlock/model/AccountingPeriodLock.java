package org.sspd.servicemgmt.accountingoptions.periodlock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "accounting_period_locks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountingPeriodLock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false) private LocalDate dateFrom;
    @Column(nullable = false) private LocalDate dateTo;
    @Column(nullable = false) private Boolean active;
    @Column(nullable = false, length = 500) private String reason;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private String unlockedBy;
    private LocalDateTime unlockedAt;
}
