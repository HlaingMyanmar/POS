package org.sspd.servicemgmt.stockoptions.lotoptions.model;
import jakarta.persistence.*;import lombok.*;import org.sspd.servicemgmt.saleoptions.salereturndetails.model.SaleReturnDetail;
@Entity @Table(name="sale_return_lot_allocations")@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleReturnLotAllocation{@Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Integer id;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="sale_return_detail_id",nullable=false)private SaleReturnDetail saleReturnDetail;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="sale_lot_allocation_id",nullable=false)private SaleLotAllocation saleLotAllocation;@Column(nullable=false)private Integer qty;}
