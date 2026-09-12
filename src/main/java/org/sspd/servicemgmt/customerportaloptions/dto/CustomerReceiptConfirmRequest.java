package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerReceiptConfirmRequest {
    /** true = ပစ္စည်းလက်ထဲ ရောက်ပါပြီ, false = မရောက်သေးပါ */
    private Boolean received;
    private String note;
}
