package org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;

@Entity
@Table(name = "payment_methods")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "method_name", length = 50, nullable = false, unique = true)
    private String methodName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    @Column(name = "is_active")
    private boolean active = true;

    /** Customer-facing receiver name (not COA ledger). */
    @Column(name = "payee_name", length = 120)
    private String payeeName;

    /** Bank / wallet / phone account number shown for transfers. */
    @Column(name = "payee_account_no", length = 80)
    private String payeeAccountNo;

    @Column(name = "payee_hint", length = 255)
    private String payeeHint;

    @Builder.Default
    @Column(name = "show_on_customer_app", nullable = false)
    private Boolean showOnCustomerApp = Boolean.TRUE;
}