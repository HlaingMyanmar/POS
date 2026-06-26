package org.sspd.servicemgmt.barcodesettingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.barcodesettingoptions.dto.BarcodeLabelSettingsDTO;
import org.sspd.servicemgmt.barcodesettingoptions.model.BarcodeLabelSettings;
import org.sspd.servicemgmt.barcodesettingoptions.repository.BarcodeLabelSettingsRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BarcodeLabelSettingsService {

    private final BarcodeLabelSettingsRepository repository;

    @Transactional(readOnly = true)
    public BarcodeLabelSettingsDTO getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional
    public BarcodeLabelSettingsDTO saveSettings(BarcodeLabelSettingsDTO dto) {
        BarcodeLabelSettings s = getOrCreate();
        if (dto.getCodeType() != null)       s.setCodeType(dto.getCodeType());
        if (dto.getLabelPreset() != null)    s.setLabelPreset(dto.getLabelPreset());
        if (dto.getCustomW() != null)        s.setCustomW(dto.getCustomW());
        if (dto.getCustomH() != null)        s.setCustomH(dto.getCustomH());
        if (dto.getCustomCols() != null)     s.setCustomCols(dto.getCustomCols());
        if (dto.getCodeBarcodeH() != null)   s.setCodeBarcodeH(dto.getCodeBarcodeH());
        if (dto.getCodeBarcodeW() != null)   s.setCodeBarcodeW(dto.getCodeBarcodeW());
        if (dto.getCodeQrPx() != null)       s.setCodeQrPx(dto.getCodeQrPx());
        if (dto.getLabelFontSize() != null)  s.setLabelFontSize(dto.getLabelFontSize());
        if (dto.getSubLabelFontSize() != null) s.setSubLabelFontSize(dto.getSubLabelFontSize());
        if (dto.getShowPrice() != null)      s.setShowPrice(dto.getShowPrice());
        if (dto.getShowProductCode() != null) s.setShowProductCode(dto.getShowProductCode());
        if (dto.getShowWarranty() != null)   s.setShowWarranty(dto.getShowWarranty());
        if (dto.getMarginTop() != null)      s.setMarginTop(dto.getMarginTop());
        if (dto.getMarginBottom() != null)   s.setMarginBottom(dto.getMarginBottom());
        if (dto.getMarginLeft() != null)     s.setMarginLeft(dto.getMarginLeft());
        if (dto.getMarginRight() != null)    s.setMarginRight(dto.getMarginRight());
        if (dto.getPaperSizeKey() != null)   s.setPaperSizeKey(dto.getPaperSizeKey());
        if (dto.getCustomPaperW() != null)   s.setCustomPaperW(dto.getCustomPaperW());
        if (dto.getCustomPaperH() != null)   s.setCustomPaperH(dto.getCustomPaperH());
        return toDto(repository.save(s));
    }

    private BarcodeLabelSettings getOrCreate() {
        List<BarcodeLabelSettings> all = repository.findAll();
        if (!all.isEmpty()) return all.get(0);
        return repository.save(BarcodeLabelSettings.builder().build());
    }

    private BarcodeLabelSettingsDTO toDto(BarcodeLabelSettings s) {
        BarcodeLabelSettingsDTO dto = new BarcodeLabelSettingsDTO();
        dto.setId(s.getId());
        dto.setCodeType(s.getCodeType());
        dto.setLabelPreset(s.getLabelPreset());
        dto.setCustomW(s.getCustomW());
        dto.setCustomH(s.getCustomH());
        dto.setCustomCols(s.getCustomCols());
        dto.setCodeBarcodeH(s.getCodeBarcodeH());
        dto.setCodeBarcodeW(s.getCodeBarcodeW());
        dto.setCodeQrPx(s.getCodeQrPx());
        dto.setLabelFontSize(s.getLabelFontSize());
        dto.setSubLabelFontSize(s.getSubLabelFontSize());
        dto.setShowPrice(s.getShowPrice());
        dto.setShowProductCode(s.getShowProductCode());
        dto.setShowWarranty(s.getShowWarranty());
        dto.setMarginTop(s.getMarginTop());
        dto.setMarginBottom(s.getMarginBottom());
        dto.setMarginLeft(s.getMarginLeft());
        dto.setMarginRight(s.getMarginRight());
        dto.setPaperSizeKey(s.getPaperSizeKey());
        dto.setCustomPaperW(s.getCustomPaperW());
        dto.setCustomPaperH(s.getCustomPaperH());
        return dto;
    }
}
