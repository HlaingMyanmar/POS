package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_balances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    private String fiscalYear; // ဥပမာ - "2024"
    private BigDecimal openingBalance = BigDecimal.ZERO;
    private BigDecimal currentBalance = BigDecimal.ZERO;
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
