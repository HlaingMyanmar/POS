package org.sspd.servicemgmt.journaloption.detail.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class JournalDetailDTO {
    private Integer accountId;
    private String accountName;
    private BigDecimal debit;
    private BigDecimal credit;
}
