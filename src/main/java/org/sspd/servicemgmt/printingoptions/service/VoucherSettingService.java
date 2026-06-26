package org.sspd.servicemgmt.printingoptions.service;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfig;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfigRegistry;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest.DocumentType;
import org.sspd.servicemgmt.printingoptions.dto.VoucherSettingDto;
import org.sspd.servicemgmt.printingoptions.entity.VoucherSetting;
import org.sspd.servicemgmt.printingoptions.repository.VoucherSettingRepository;

import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for voucher print settings.
 *
 * <h3>Auto-seeding</h3>
 * On startup ({@link PostConstruct}), any {@link DocumentType} that has no row
 * in {@code voucher_settings} is seeded with values cloned from
 * {@link PrintLayoutConfigRegistry}.  This means a fresh database gets sensible
 * defaults without manual SQL.
 *
 * <h3>Reset-to-defaults</h3>
 * {@link #resetToDefaults(DocumentType, String)} deletes the current row and
 * re-seeds it, giving the admin a one-click undo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VoucherSettingService {

    private final VoucherSettingRepository  repo;
    private final PrintLayoutConfigRegistry registry;
    private final DynamicPrintConfigService dynamicConfig;

    // ── Auto-seed ─────────────────────────────────────────────────────────────

    @PostConstruct
    void ensureDefaultsExist() {
        Arrays.stream(DocumentType.values()).forEach(type -> {
            if (!repo.existsByDocumentType(type)) {
                seedDefault(type);
                log.info("[VoucherSetting] seeded defaults for {}", type);
            }
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<VoucherSettingDto> getAll() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    public VoucherSettingDto getByDocumentType(DocumentType type) {
        return repo.findByDocumentType(type)
                .map(this::toDto)
                .orElseGet(() -> toDto(seedDefault(type)));
    }

    /** Raw entity access for the print pipeline (avoids double DB round-trip). */
    public Optional<VoucherSetting> findEntity(DocumentType type) {
        return repo.findByDocumentType(type);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public VoucherSettingDto save(VoucherSettingDto dto, String updatedBy) {
        DocumentType type = DocumentType.valueOf(dto.documentType());
        VoucherSetting entity = repo.findByDocumentType(type)
                .orElse(VoucherSetting.builder().documentType(type).build());
        applyDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        return toDto(repo.save(entity));
    }

    public VoucherSettingDto resetToDefaults(DocumentType type, String updatedBy) {
        repo.findByDocumentType(type).ifPresent(repo::delete);
        repo.flush();
        VoucherSetting fresh = seedDefault(type);
        fresh.setUpdatedAt(LocalDateTime.now());
        fresh.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        return toDto(repo.save(fresh));
    }

    // ── Seeding ───────────────────────────────────────────────────────────────

    private @NonNull VoucherSetting seedDefault(DocumentType type) {
        String paperSize = defaultPaperFor(type);
        PrintLayoutConfig base = registry.get(paperSize);
        VoucherSetting s = VoucherSetting.builder()
                .documentType(type)
                .paperSize(paperSize)
                // Mirror the registry values so the admin sees exactly what's in use
                .marginTopMm(   (int) base.getMarginTopMm())
                .marginBottomMm((int) base.getMarginBottomMm())
                .marginLeftMm(  (int) base.getMarginLeftMm())
                .marginRightMm( (int) base.getMarginRightMm())
                .headerHeightPx(     base.getHeaderHeightPx())
                .contHeaderHeightPx( base.getContHeaderHeightPx())
                .infoBlocksHeightPx( base.getInfoBlocksHeightPx())
                .tableHeaderHeightPx(base.getTableHeaderHeightPx())
                .rowHeightPx(        base.getRowHeightPx())
                .totalsAreaHeightPx( base.getTotalsAreaHeightPx())
                .footerHeightPx(     base.getFooterHeightPx())
                .safetyMarginPx(     base.getSafetyMarginPx())
                // Sensible per-type display defaults
                .showLogo(true)
                .showQrCode(false)
                .showSignatures(type == DocumentType.SERVICE_JOB || type == DocumentType.SERVICE_DONE)
                .showPaymentHistory(type != DocumentType.BOOKING)
                .showSerial(type == DocumentType.SALE || type == DocumentType.SERVICE_JOB || type == DocumentType.SERVICE_DONE)
                .sign1Label("Prepared By")
                .sign2Label("Received By")
                .voucherTitle(defaultTitleFor(type))
                .footerNote("")
                .customerNotice("")
                .build();
        return repo.save(s);
    }

    private String defaultPaperFor(DocumentType type) {
        return switch (type) {
            case SALE         -> "A4";
            case SERVICE_JOB  -> "A5";
            case SERVICE_DONE -> "A5";
            case BOOKING      -> "A5";
            case PURCHASE     -> "A4";
        };
    }

    private String defaultTitleFor(DocumentType type) {
        return switch (type) {
            case SALE         -> "SALES INVOICE";
            case SERVICE_JOB  -> "SERVICE VOUCHER";
            case SERVICE_DONE -> "SERVICE DONE VOUCHER";
            case BOOKING      -> "DEVICE INTAKE RECEIPT";
            case PURCHASE     -> "PURCHASE ORDER";
        };
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private VoucherSettingDto toDto(VoucherSetting s) {
        PrintLayoutConfig cfg = dynamicConfig.resolveConfig(s, null);
        return new VoucherSettingDto(
                s.getId(),
                s.getDocumentType().name(),
                s.getPaperSize(),
                s.getMarginTopMm(),    s.getMarginBottomMm(),
                s.getMarginLeftMm(),   s.getMarginRightMm(),
                s.getHeaderHeightPx(), s.getContHeaderHeightPx(),
                s.getInfoBlocksHeightPx(), s.getTableHeaderHeightPx(),
                s.getRowHeightPx(),    s.getTotalsAreaHeightPx(),
                s.getFooterHeightPx(), s.getSafetyMarginPx(),
                s.getShowLogo(),            s.getShowQrCode(),
                s.getShowSignatures(),      s.getShowPaymentHistory(),
                s.getShowSerial(),
                s.getSign1Label(),  s.getSign2Label(),
                s.getVoucherTitle(),
                s.getFooterNote(),  s.getCustomerNotice(),
                s.getHeaderFontFamily(),      s.getHeaderFontSizePx(),
                s.getInfoFontFamily(),        s.getInfoFontSizePx(),
                s.getTableHeaderFontFamily(), s.getTableHeaderFontSizePx(),
                s.getTableDataFontFamily(),   s.getTableDataFontSizePx(),
                s.getFooterFontFamily(),      s.getFooterFontSizePx(),
                s.getNoticeFontFamily(),      s.getNoticeFontSizePx(),
                cfg.isThermal() ? null : cfg.getRowsOnFirstPage(),
                cfg.isThermal() ? null : cfg.getRowsOnContinuationPage(),
                s.getUpdatedAt(),   s.getUpdatedBy()
        );
    }

    private void applyDto(VoucherSettingDto dto, VoucherSetting s) {
        s.setPaperSize(dto.paperSize());
        s.setMarginTopMm(dto.marginTopMm());
        s.setMarginBottomMm(dto.marginBottomMm());
        s.setMarginLeftMm(dto.marginLeftMm());
        s.setMarginRightMm(dto.marginRightMm());
        s.setHeaderHeightPx(dto.headerHeightPx());
        s.setContHeaderHeightPx(dto.contHeaderHeightPx());
        s.setInfoBlocksHeightPx(dto.infoBlocksHeightPx());
        s.setTableHeaderHeightPx(dto.tableHeaderHeightPx());
        s.setRowHeightPx(dto.rowHeightPx());
        s.setTotalsAreaHeightPx(dto.totalsAreaHeightPx());
        s.setFooterHeightPx(dto.footerHeightPx());
        s.setSafetyMarginPx(dto.safetyMarginPx());
        s.setShowLogo(dto.showLogo());
        s.setShowQrCode(dto.showQrCode());
        s.setShowSignatures(dto.showSignatures());
        s.setShowPaymentHistory(dto.showPaymentHistory());
        s.setShowSerial(dto.showSerial());
        s.setSign1Label(dto.sign1Label());
        s.setSign2Label(dto.sign2Label());
        s.setVoucherTitle(dto.voucherTitle());
        s.setFooterNote(dto.footerNote());
        s.setCustomerNotice(dto.customerNotice());
        s.setHeaderFontFamily(dto.headerFontFamily());
        s.setHeaderFontSizePx(dto.headerFontSizePx());
        s.setInfoFontFamily(dto.infoFontFamily());
        s.setInfoFontSizePx(dto.infoFontSizePx());
        s.setTableHeaderFontFamily(dto.tableHeaderFontFamily());
        s.setTableHeaderFontSizePx(dto.tableHeaderFontSizePx());
        s.setTableDataFontFamily(dto.tableDataFontFamily());
        s.setTableDataFontSizePx(dto.tableDataFontSizePx());
        s.setFooterFontFamily(dto.footerFontFamily());
        s.setFooterFontSizePx(dto.footerFontSizePx());
        s.setNoticeFontFamily(dto.noticeFontFamily());
        s.setNoticeFontSizePx(dto.noticeFontSizePx());
    }
}
