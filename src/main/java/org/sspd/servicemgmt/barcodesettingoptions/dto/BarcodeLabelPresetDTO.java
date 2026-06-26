package org.sspd.servicemgmt.barcodesettingoptions.dto;

import lombok.Data;

@Data
public class BarcodeLabelPresetDTO {
    private Integer id;
    private String name;
    private String codeType;
    private String labelPreset;
    private Double customW;
    private Double customH;
    private Integer customCols;
    private Integer codeBarcodeH;
    private Double codeBarcodeW;
    private Integer codeQrPx;
    private Integer labelFontSize;
    private Integer subLabelFontSize;
    private Boolean showPrice;
    private Boolean showProductCode;
    private Boolean showWarranty;
    private Double marginTop;
    private Double marginBottom;
    private Double marginLeft;
    private Double marginRight;
    private String paperSizeKey;
    private Double customPaperW;
    private Double customPaperH;
}
