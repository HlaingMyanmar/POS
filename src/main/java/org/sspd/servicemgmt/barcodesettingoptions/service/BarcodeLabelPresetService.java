package org.sspd.servicemgmt.barcodesettingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.barcodesettingoptions.dto.BarcodeLabelPresetDTO;
import org.sspd.servicemgmt.barcodesettingoptions.model.BarcodeLabelPreset;
import org.sspd.servicemgmt.barcodesettingoptions.repository.BarcodeLabelPresetRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BarcodeLabelPresetService {

    private final BarcodeLabelPresetRepository repository;

    @Transactional(readOnly = true)
    public List<BarcodeLabelPresetDTO> getAll() {
        return repository.findAllByOrderByIdAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public BarcodeLabelPresetDTO create(BarcodeLabelPresetDTO dto) {
        BarcodeLabelPreset p = new BarcodeLabelPreset();
        applyDto(p, dto);
        return toDto(repository.save(p));
    }

    @Transactional
    public BarcodeLabelPresetDTO update(Integer id, BarcodeLabelPresetDTO dto) {
        BarcodeLabelPreset p = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Preset not found: " + id));
        applyDto(p, dto);
        return toDto(repository.save(p));
    }

    @Transactional
    public void delete(Integer id) {
        repository.deleteById(id);
    }

    private void applyDto(BarcodeLabelPreset p, BarcodeLabelPresetDTO dto) {
        if (dto.getName() != null)            p.setName(dto.getName().trim());
        if (dto.getCodeType() != null)        p.setCodeType(dto.getCodeType());
        if (dto.getLabelPreset() != null)     p.setLabelPreset(dto.getLabelPreset());
        if (dto.getCustomW() != null)         p.setCustomW(dto.getCustomW());
        if (dto.getCustomH() != null)         p.setCustomH(dto.getCustomH());
        if (dto.getCustomCols() != null)      p.setCustomCols(dto.getCustomCols());
        if (dto.getCodeBarcodeH() != null)    p.setCodeBarcodeH(dto.getCodeBarcodeH());
        if (dto.getCodeBarcodeW() != null)    p.setCodeBarcodeW(dto.getCodeBarcodeW());
        if (dto.getCodeQrPx() != null)        p.setCodeQrPx(dto.getCodeQrPx());
        if (dto.getLabelFontSize() != null)   p.setLabelFontSize(dto.getLabelFontSize());
        if (dto.getSubLabelFontSize() != null) p.setSubLabelFontSize(dto.getSubLabelFontSize());
        if (dto.getShowPrice() != null)       p.setShowPrice(dto.getShowPrice());
        if (dto.getShowProductCode() != null) p.setShowProductCode(dto.getShowProductCode());
        if (dto.getShowWarranty() != null)    p.setShowWarranty(dto.getShowWarranty());
        if (dto.getMarginTop() != null)       p.setMarginTop(dto.getMarginTop());
        if (dto.getMarginBottom() != null)    p.setMarginBottom(dto.getMarginBottom());
        if (dto.getMarginLeft() != null)      p.setMarginLeft(dto.getMarginLeft());
        if (dto.getMarginRight() != null)     p.setMarginRight(dto.getMarginRight());
        if (dto.getPaperSizeKey() != null)    p.setPaperSizeKey(dto.getPaperSizeKey());
        if (dto.getCustomPaperW() != null)    p.setCustomPaperW(dto.getCustomPaperW());
        if (dto.getCustomPaperH() != null)    p.setCustomPaperH(dto.getCustomPaperH());
    }

    private BarcodeLabelPresetDTO toDto(BarcodeLabelPreset p) {
        BarcodeLabelPresetDTO dto = new BarcodeLabelPresetDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setCodeType(p.getCodeType());
        dto.setLabelPreset(p.getLabelPreset());
        dto.setCustomW(p.getCustomW());
        dto.setCustomH(p.getCustomH());
        dto.setCustomCols(p.getCustomCols());
        dto.setCodeBarcodeH(p.getCodeBarcodeH());
        dto.setCodeBarcodeW(p.getCodeBarcodeW());
        dto.setCodeQrPx(p.getCodeQrPx());
        dto.setLabelFontSize(p.getLabelFontSize());
        dto.setSubLabelFontSize(p.getSubLabelFontSize());
        dto.setShowPrice(p.getShowPrice());
        dto.setShowProductCode(p.getShowProductCode());
        dto.setShowWarranty(p.getShowWarranty());
        dto.setMarginTop(p.getMarginTop());
        dto.setMarginBottom(p.getMarginBottom());
        dto.setMarginLeft(p.getMarginLeft());
        dto.setMarginRight(p.getMarginRight());
        dto.setPaperSizeKey(p.getPaperSizeKey());
        dto.setCustomPaperW(p.getCustomPaperW());
        dto.setCustomPaperH(p.getCustomPaperH());
        return dto;
    }
}
