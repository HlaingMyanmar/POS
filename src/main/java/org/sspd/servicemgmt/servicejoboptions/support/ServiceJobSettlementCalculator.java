package org.sspd.servicemgmt.servicejoboptions.support;

import org.sspd.servicemgmt.servicejoboptions.model.DiscountAllocationMethod;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ServiceJobSettlementCalculator {

    private ServiceJobSettlementCalculator() {}

    public static ServiceJobSettlementBreakdown compute(
            ServiceJob job,
            BigDecimal overallDiscount,
            DiscountAllocationMethod method,
            boolean foc
    ) {
        BigDecimal laborBalance = laborBalance(job);
        BigDecimal partsBalance = partsBalance(job);
        BigDecimal gross = laborBalance.add(partsBalance);

        if (foc) {
            return new ServiceJobSettlementBreakdown(
                    laborBalance, partsBalance, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, gross, BigDecimal.ZERO);
        }

        BigDecimal discount = nz(overallDiscount);
        if (discount.compareTo(gross) > 0) {
            throw new IllegalArgumentException("Overall discount cannot exceed gross amount.");
        }

        BigDecimal[] allocated = allocateDiscount(laborBalance, partsBalance, discount, method);
        BigDecimal laborNet = allocated[0];
        BigDecimal partsNet = allocated[1];
        BigDecimal net = laborNet.add(partsNet);

        return new ServiceJobSettlementBreakdown(
                laborBalance, partsBalance, discount, laborNet, partsNet, gross, net);
    }

    public static BigDecimal laborBalance(ServiceJob job) {
        if (job.getLines() == null) return BigDecimal.ZERO;
        return job.getLines().stream()
                .filter(ServiceJobLine::isBillable)
                .map(ServiceJobSettlementCalculator::lineBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal partsBalance(ServiceJob job) {
        if (job.getProductParts() == null) return BigDecimal.ZERO;
        return job.getProductParts().stream()
                .map(ServiceJobSettlementCalculator::partBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal lineBalance(ServiceJobLine line) {
        if (!line.isBillable() || Boolean.TRUE.equals(line.getWarrantyCovered())) {
            return BigDecimal.ZERO;
        }
        if (line.getSubtotal() != null) {
            return line.getSubtotal().max(BigDecimal.ZERO);
        }
        int qty = line.getQty() != null ? line.getQty() : 1;
        BigDecimal gross = line.chargeUnitPrice().multiply(BigDecimal.valueOf(qty));
        BigDecimal discount = nz(line.getDiscountAmount());
        return gross.subtract(discount).max(BigDecimal.ZERO);
    }

    public static BigDecimal partBalance(ServiceJobPart part) {
        if (Boolean.TRUE.equals(part.getWarrantyCovered())) {
            return BigDecimal.ZERO;
        }
        if (part.getSubtotal() != null) {
            return part.getSubtotal().max(BigDecimal.ZERO);
        }
        int qty = part.getQty() != null ? part.getQty() : 1;
        BigDecimal unit = part.getUnitPrice() != null ? part.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal gross = unit.multiply(BigDecimal.valueOf(qty));
        BigDecimal discount = nz(part.getDiscountAmount());
        return gross.subtract(discount).max(BigDecimal.ZERO);
    }

    static BigDecimal[] allocateDiscount(
            BigDecimal laborBalance,
            BigDecimal partsBalance,
            BigDecimal overallDiscount,
            DiscountAllocationMethod method
    ) {
        BigDecimal discount = nz(overallDiscount);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal[] { laborBalance, partsBalance };
        }

        BigDecimal total = laborBalance.add(partsBalance);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
        }
        if (discount.compareTo(total) > 0) {
            throw new IllegalArgumentException("Overall discount cannot exceed gross amount.");
        }

        BigDecimal laborShare;
        BigDecimal partsShare;
        switch (method != null ? method : DiscountAllocationMethod.PRO_RATA) {
            case LABOR_FIRST -> {
                laborShare = discount.min(laborBalance);
                partsShare = discount.subtract(laborShare);
            }
            case PARTS_FIRST -> {
                partsShare = discount.min(partsBalance);
                laborShare = discount.subtract(partsShare);
            }
            default -> {
                laborShare = discount.multiply(laborBalance)
                        .divide(total, 2, RoundingMode.HALF_UP);
                partsShare = discount.subtract(laborShare);
            }
        }

        return new BigDecimal[] {
                laborBalance.subtract(laborShare).max(BigDecimal.ZERO),
                partsBalance.subtract(partsShare).max(BigDecimal.ZERO)
        };
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
