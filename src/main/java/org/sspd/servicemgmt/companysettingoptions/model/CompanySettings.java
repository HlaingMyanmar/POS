package org.sspd.servicemgmt.companysettingoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "company_settings")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName = "SSPD IT Solution Center";

    @Column(name = "company_address", length = 1000)
    private String companyAddress;

    @Column(name = "company_phone", length = 100)
    private String companyPhone;

    @Column(name = "company_email", length = 150)
    private String companyEmail;

    @Column(name = "invoice_title", length = 255)
    private String invoiceTitle = "Sales Invoice";

    @Column(name = "footer_note", length = 500)
    private String footerNote = "Thank you for your business";

    @Column(name = "tagline_mm", length = 255)
    private String taglineMm;

    @Lob
    @Column(name = "logo_base64", columnDefinition = "LONGTEXT")
    private String logoBase64;

    @Lob
    @Column(name = "voucher_config_json", columnDefinition = "LONGTEXT")
    private String voucherConfigJson;

    @Builder.Default
    @Column(name = "setup_complete")
    private Boolean setupComplete = false;

    @Builder.Default
    @Column(name = "sale_prefix", length = 20)
    private String salePrefix = "INV";

    @Builder.Default
    @Column(name = "sale_digits")
    private Integer saleDigits = 5;

    @Builder.Default
    @Column(name = "purchase_prefix", length = 20)
    private String purchasePrefix = "PUR";

    @Builder.Default
    @Column(name = "purchase_digits")
    private Integer purchaseDigits = 5;

    @Builder.Default
    @Column(name = "booking_prefix", length = 20)
    private String bookingPrefix = "BK";

    @Builder.Default
    @Column(name = "booking_digits")
    private Integer bookingDigits = 6;
}
