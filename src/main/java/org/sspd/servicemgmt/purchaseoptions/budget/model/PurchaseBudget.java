package org.sspd.servicemgmt.purchaseoptions.budget.model;
import jakarta.persistence.*; import lombok.*; import org.sspd.servicemgmt.categoryoptions.model.Category; import java.math.BigDecimal; import java.time.LocalDate;
@Entity @Table(name="purchase_budgets") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseBudget {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false) private LocalDate dateFrom;
 @Column(nullable=false) private LocalDate dateTo;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="category_id") private Category category;
 @Column(nullable=false,precision=18,scale=2) private BigDecimal limitAmount;
 @Column(nullable=false,length=10) private String enforcement;
 @Builder.Default @Column(nullable=false) private Boolean active=true;
}
