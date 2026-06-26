package org.sspd.servicemgmt.barcodesettingoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "barcode_label_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarcodeLabelSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Builder.Default
    @Column(name = "code_type", length = 10)
    private String codeType = "BARCODE";

    @Builder.Default
    @Column(name = "label_preset", length = 20)
    private String labelPreset = "40x30";

    @Builder.Default
    @Column(name = "custom_w")
    private Double customW = 40.0;

    @Builder.Default
    @Column(name = "custom_h")
    private Double customH = 30.0;

    @Builder.Default
    @Column(name = "custom_cols")
    private Integer customCols = 4;

    @Builder.Default
    @Column(name = "code_barcode_h")
    private Integer codeBarcodeH = 20;

    @Builder.Default
    @Column(name = "code_barcode_w")
    private Double codeBarcodeW = 1.1;

    @Builder.Default
    @Column(name = "code_qr_px")
    private Integer codeQrPx = 80;

    @Builder.Default
    @Column(name = "label_font_size")
    private Integer labelFontSize = 10;

    @Builder.Default
    @Column(name = "sub_label_font_size")
    private Integer subLabelFontSize = 9;

    @Builder.Default
    @Column(name = "show_price")
    private Boolean showPrice = true;

    @Builder.Default
    @Column(name = "show_product_code")
    private Boolean showProductCode = true;

    @Builder.Default
    @Column(name = "show_warranty")
    private Boolean showWarranty = true;

    @Builder.Default
    @Column(name = "margin_top")
    private Double marginTop = 4.0;

    @Builder.Default
    @Column(name = "margin_bottom")
    private Double marginBottom = 4.0;

    @Builder.Default
    @Column(name = "margin_left")
    private Double marginLeft = 4.0;

    @Builder.Default
    @Column(name = "margin_right")
    private Double marginRight = 4.0;

    @Builder.Default
    @Column(name = "paper_size_key", length = 20)
    private String paperSizeKey = "A4";

    @Builder.Default
    @Column(name = "custom_paper_w")
    private Double customPaperW = 210.0;

    @Builder.Default
    @Column(name = "custom_paper_h")
    private Double customPaperH = 297.0;
}
