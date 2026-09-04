package org.sspd.servicemgmt.printingoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintLineItem {
    private int rowNo;
    private String productName;
    private String serialInfo;
    private String warrantyLabel;
    private int qty;
    private String unitPrice;
    private String subtotal;
    /** Optional: discount per line, shown when non-zero */
    private String discount;
    /** True when this sale line was given free of charge. */
    private boolean foc;

    /** Serial column cell: {@code SN:ABC-2048} or dash. */
    public String getSerialDisplay() {
        if (serialInfo == null || serialInfo.isBlank()) return "—";
        String sn = serialInfo.trim().replaceFirst("(?i)^SN\\s*:\\s*", "");
        return sn.isBlank() ? "—" : "SN:" + sn;
    }

    /** Warranty column cell: {@code 1Year} or dash. */
    public String getWarrantyDisplay() {
        if (warrantyLabel == null || warrantyLabel.isBlank()) return "—";
        return warrantyLabel.trim().replaceAll("\\s+", "");
    }

    /** Line-discount column cell: amount or dash. */
    public String getDiscountDisplay() {
        if (foc || discount == null || discount.isBlank()) return "—";
        String d = discount.trim();
        if ("0".equals(d) || "0.00".equals(d)) return "—";
        return d;
    }

    /**
     * Compact one-line for POS / fallback when columns are hidden:
     * {@code SN:ABC-2048,Warranty:1Year,Dis 5,000}
     */
    public String getSerialInfoLine() {
        return getSerialInfoLine(true, true, true);
    }

    public String getSerialInfoLine(boolean includeSerial) {
        return getSerialInfoLine(includeSerial, true, true);
    }

    public String getSerialInfoLine(boolean includeSerial, boolean includeWarranty, boolean includeDiscount) {
        List<String> parts = new ArrayList<>(3);
        if (includeSerial) {
            String sn = getSerialDisplay();
            if (!"—".equals(sn)) parts.add(sn);
        }
        if (includeWarranty) {
            String war = getWarrantyDisplay();
            if (!"—".equals(war)) parts.add("Warranty:" + war);
        }
        if (includeDiscount) {
            String disc = getDiscountDisplay();
            if (!"—".equals(disc)) parts.add("Dis " + disc);
        }
        return parts.isEmpty() ? "—" : String.join(",", parts);
    }
}
