package org.sspd.servicemgmt.stockoptions.stockmovementoptions.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ProductStockHistoryDTO {
    Integer productId;
    String productName;
    Integer currentStock;
    Integer openingBalance;
    Integer totalIn;
    Integer totalOut;
    Integer closingBalance;
    Integer page;
    Integer size;
    Integer totalPages;
    Long totalElements;
    List<MovementRow> movements;

    @Value
    @Builder
    public static class MovementRow {
        Integer id;
        Integer productId;
        String productName;
        String productCode;
        LocalDateTime date;
        String type;
        Integer referenceId;
        String referenceNumber;
        String partyName;
        Integer quantityIn;
        Integer quantityOut;
        Integer balance;
    }
}
