package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto;

import lombok.Builder;
import lombok.Data;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;

@Data
@Builder
public class PurchaseOrderReceiveResultDTO {
    private PurchaseOrderDTO order;
    private PurchaseDTO purchase;
}
