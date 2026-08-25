package org.sspd.servicemgmt.stockoptions.lotoptions.model;
import jakarta.persistence.*;import lombok.*;import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
@Entity @Table(name="sale_lot_allocations")@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleLotAllocation{@Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Integer id;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="sale_detail_id",nullable=false)private SaleDetail saleDetail;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="stock_lot_id",nullable=false)private StockLot stockLot;@Column(nullable=false)private Integer allocatedQty;@Builder.Default @Column(nullable=false)private Integer returnedQty=0;}
