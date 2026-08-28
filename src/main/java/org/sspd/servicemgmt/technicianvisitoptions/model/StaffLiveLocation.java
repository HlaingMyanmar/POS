package org.sspd.servicemgmt.technicianvisitoptions.model;
import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="staff_live_location",uniqueConstraints=@UniqueConstraint(name="uk_live_staff",columnNames="staff_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffLiveLocation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="staff_id",nullable=false) private Staff staff;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="active_visit_id") private TechnicianVisit activeVisit;
 @Column(precision=10,scale=7) private BigDecimal latitude; @Column(precision=10,scale=7) private BigDecimal longitude;
 @Column(precision=8,scale=2) private BigDecimal accuracy; private LocalDateTime recordedAt; private LocalDateTime serverReceivedAt;
}
