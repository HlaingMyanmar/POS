package org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.dto.ManufacturingFormulaDTO;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.dto.ManufacturingFormulaItemDTO;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.model.ManufacturingFormula;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.model.ManufacturingFormulaItem;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.repository.ManufacturingFormulaRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManufacturingFormulaService {

    private final ManufacturingFormulaRepository formulaRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;

    @Transactional(readOnly = true)
    public List<ManufacturingFormulaDTO> getAll() {
        return formulaRepository.findAllByOrderByNameAsc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ManufacturingFormulaDTO getById(Integer id) {
        return toDTO(find(id));
    }

    @Transactional
    public ManufacturingFormulaDTO create(ManufacturingFormulaDTO dto) {
        ManufacturingFormula formula = ManufacturingFormula.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .finishedProductName(dto.getFinishedProductName())
                .finishedProductBrandId(dto.getFinishedProductBrandId())
                .finishedProductCategoryId(dto.getFinishedProductCategoryId())
                .finishedProductUnitId(dto.getFinishedProductUnitId())
                .finishedProductType(dto.getFinishedProductType() != null ? dto.getFinishedProductType() : "New")
                .finishedProductSellingPrice(dto.getFinishedProductSellingPrice() != null ? dto.getFinishedProductSellingPrice() : BigDecimal.ZERO)
                .build();

        addItems(formula, dto.getItems());
        return toDTO(formulaRepository.save(formula));
    }

    @Transactional
    public ManufacturingFormulaDTO update(Integer id, ManufacturingFormulaDTO dto) {
        ManufacturingFormula formula = find(id);
        formula.setName(dto.getName());
        formula.setDescription(dto.getDescription());
        formula.setFinishedProductName(dto.getFinishedProductName());
        formula.setFinishedProductBrandId(dto.getFinishedProductBrandId());
        formula.setFinishedProductCategoryId(dto.getFinishedProductCategoryId());
        formula.setFinishedProductUnitId(dto.getFinishedProductUnitId());
        formula.setFinishedProductType(dto.getFinishedProductType() != null ? dto.getFinishedProductType() : "New");
        formula.setFinishedProductSellingPrice(dto.getFinishedProductSellingPrice() != null ? dto.getFinishedProductSellingPrice() : BigDecimal.ZERO);
        formula.getItems().clear();
        addItems(formula, dto.getItems());
        return toDTO(formulaRepository.save(formula));
    }

    @Transactional
    public void delete(Integer id) {
        formulaRepository.delete(find(id));
    }

    private void addItems(ManufacturingFormula formula, List<ManufacturingFormulaItemDTO> itemDTOs) {
        if (itemDTOs == null) return;
        for (ManufacturingFormulaItemDTO i : itemDTOs) {
            Product product = productRepository.findById(i.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + i.getProductId()));
            formula.getItems().add(ManufacturingFormulaItem.builder()
                    .formula(formula)
                    .productId(i.getProductId())
                    .productName(product.getName())
                    .qty(i.getQty() != null ? i.getQty() : 1)
                    .unitCost(i.getUnitCost() != null ? i.getUnitCost() : (product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO))
                    .build());
        }
    }

    private ManufacturingFormula find(Integer id) {
        return formulaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formula not found: " + id));
    }

    private ManufacturingFormulaDTO toDTO(ManufacturingFormula f) {
        String brandName = f.getFinishedProductBrandId() != null
                ? brandRepository.findById(f.getFinishedProductBrandId().longValue()).map(b -> b.getName()).orElse(null) : null;
        String categoryName = f.getFinishedProductCategoryId() != null
                ? categoryRepository.findById(f.getFinishedProductCategoryId().longValue()).map(c -> c.getName()).orElse(null) : null;
        String unitName = f.getFinishedProductUnitId() != null
                ? unitRepository.findById(f.getFinishedProductUnitId().longValue()).map(u -> u.getUnitName()).orElse(null) : null;

        List<ManufacturingFormulaItemDTO> itemDTOs = new ArrayList<>();
        for (ManufacturingFormulaItem item : f.getItems()) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            itemDTOs.add(ManufacturingFormulaItemDTO.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .productCode(product != null ? product.getProductCode() : null)
                    .hasSerial(product != null ? product.getHasSerial() : null)
                    .qty(item.getQty())
                    .unitCost(item.getUnitCost())
                    .build());
        }

        return ManufacturingFormulaDTO.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .finishedProductName(f.getFinishedProductName())
                .finishedProductBrandId(f.getFinishedProductBrandId())
                .finishedProductBrandName(brandName)
                .finishedProductCategoryId(f.getFinishedProductCategoryId())
                .finishedProductCategoryName(categoryName)
                .finishedProductUnitId(f.getFinishedProductUnitId())
                .finishedProductUnitName(unitName)
                .finishedProductType(f.getFinishedProductType())
                .finishedProductSellingPrice(f.getFinishedProductSellingPrice())
                .items(itemDTOs)
                .build();
    }
}
