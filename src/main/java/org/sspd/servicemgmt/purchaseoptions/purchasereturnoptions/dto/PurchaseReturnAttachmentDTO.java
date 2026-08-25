package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto; import lombok.Data; import java.time.LocalDateTime;
@Data public class PurchaseReturnAttachmentDTO { private Integer id; private String attachmentType; private String fileName; private String contentType; private String dataUrl; private String uploadedBy; private LocalDateTime uploadedAt; }
