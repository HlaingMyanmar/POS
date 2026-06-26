package org.sspd.servicemgmt.printingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfig;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfigRegistry;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest.DocumentType;
import org.sspd.servicemgmt.printingoptions.entity.VoucherSetting;
import org.sspd.servicemgmt.printingoptions.repository.VoucherSettingRepository;

/**
 * Bridges the {@code VoucherSetting} database record to a {@link PrintLayoutConfig}
 * that the existing print pipeline ({@link HtmlPdfService}, {@link PaginationService})
 * can consume without modification.
 *
 * <h3>Priority chain</h3>
 * <pre>
 *   1. paperSizeOverride (explicit request param)  — highest priority
 *   2. VoucherSetting.paperSize               (DB setting)
 *   3. "A4"                                         — fallback default
 *
 *   For each dimension:
 *   1. VoucherSetting.xxxPx / xxxMm (non-null DB value)
 *   2. PrintLayoutConfigRegistry base value for the resolved paper size
 * </pre>
 *
 * <h3>Row capacity</h3>
 * Rows per page are <em>never stored</em>. They are always derived from the
 * effective dimension values by the {@link PrintLayoutConfig} physics formula,
 * guaranteeing that the frontend preview and the backend PDF always agree.
 */
@Service
@RequiredArgsConstructor
public class DynamicPrintConfigService {

    private final VoucherSettingRepository  repo;
    private final PrintLayoutConfigRegistry registry;

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Resolves the effective config, performing a DB lookup for {@code docType}.
     *
     * @param docType          document type used to look up the {@code VoucherSetting};
     *                         may be {@code null} (falls back to registry default)
     * @param paperSizeOverride explicit paper-size from the HTTP request; {@code null}
     *                         means "use whatever the DB setting says (or A4)"
     */
    public PrintLayoutConfig resolveConfig(DocumentType docType, String paperSizeOverride) {
        VoucherSetting setting = (docType != null)
                ? repo.findByDocumentType(docType).orElse(null)
                : null;
        return resolveConfig(setting, paperSizeOverride);
    }

    /**
     * Resolves the effective config from an already-loaded {@link VoucherSetting}.
     * Use this overload when the setting is already available (avoids second DB hit).
     */
    public PrintLayoutConfig resolveConfig(VoucherSetting setting, String paperSizeOverride) {
        String paperSize = resolvePaperSize(setting, paperSizeOverride);
        PrintLayoutConfig base = registry.get(paperSize);

        if (setting == null) return base;

        return PrintLayoutConfig.builder()
                .name(paperSize)
                // Page physical dimensions come from the registry (they don't change)
                .pageWidthMm(base.getPageWidthMm())
                .pageHeightMm(base.getPageHeightMm())
                // Margins — override with DB value when non-null
                .marginTopMm(   coalesceF(setting.getMarginTopMm(),    base.getMarginTopMm()))
                .marginBottomMm(coalesceF(setting.getMarginBottomMm(), base.getMarginBottomMm()))
                .marginLeftMm(  coalesceF(setting.getMarginLeftMm(),   base.getMarginLeftMm()))
                .marginRightMm( coalesceF(setting.getMarginRightMm(),  base.getMarginRightMm()))
                // Component heights — override with DB value when non-null
                .headerHeightPx(      coalesceI(setting.getHeaderHeightPx(),       base.getHeaderHeightPx()))
                .contHeaderHeightPx(  coalesceI(setting.getContHeaderHeightPx(),   base.getContHeaderHeightPx()))
                .infoBlocksHeightPx(  coalesceI(setting.getInfoBlocksHeightPx(),   base.getInfoBlocksHeightPx()))
                .tableHeaderHeightPx( coalesceI(setting.getTableHeaderHeightPx(),  base.getTableHeaderHeightPx()))
                .rowHeightPx(         coalesceI(setting.getRowHeightPx(),          base.getRowHeightPx()))
                .totalsAreaHeightPx(  coalesceI(setting.getTotalsAreaHeightPx(),   base.getTotalsAreaHeightPx()))
                .footerHeightPx(      coalesceI(setting.getFooterHeightPx(),       base.getFooterHeightPx()))
                .safetyMarginPx(      coalesceI(setting.getSafetyMarginPx(),       base.getSafetyMarginPx()))
                // CSS identifiers come from the registry (tied to paper size)
                .cssPageSize(base.getCssPageSize())
                .cssClassName(base.getCssClassName())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolvePaperSize(VoucherSetting setting, String override) {
        if (override != null && !override.isBlank()) return override.toUpperCase();
        if (setting  != null && setting.getPaperSize() != null) return setting.getPaperSize();
        return "A4";
    }

    private static float coalesceF(Integer dbValue, float registryDefault) {
        return dbValue != null ? dbValue.floatValue() : registryDefault;
    }

    private static int coalesceI(Integer dbValue, int registryDefault) {
        return dbValue != null ? dbValue : registryDefault;
    }
}
