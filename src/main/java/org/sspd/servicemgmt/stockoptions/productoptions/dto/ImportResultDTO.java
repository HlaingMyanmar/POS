package org.sspd.servicemgmt.stockoptions.productoptions.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResultDTO {
    private int successCount;
    private int errorCount;
    private List<RowError> errors = new ArrayList<>();

    @Data
    public static class RowError {
        private int row;
        private String message;

        public RowError(int row, String message) {
            this.row = row;
            this.message = message;
        }
    }
}
