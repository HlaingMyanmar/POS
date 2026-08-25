package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto; import lombok.Data; import java.time.LocalDateTime;
@Data public class PurchaseReturnActivityDTO { private Integer id; private String eventType; private String fromStatus; private String toStatus; private String note; private String actor; private LocalDateTime occurredAt; }
