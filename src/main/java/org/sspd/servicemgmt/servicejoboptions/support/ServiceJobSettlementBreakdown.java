package org.sspd.servicemgmt.servicejoboptions.support;

import java.math.BigDecimal;

public record ServiceJobSettlementBreakdown(
        BigDecimal laborBalance,
        BigDecimal partsBalance,
        BigDecimal overallDiscount,
        BigDecimal laborNet,
        BigDecimal partsNet,
        BigDecimal gross,
        BigDecimal net
) {}
