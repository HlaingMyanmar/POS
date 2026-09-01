package org.sspd.servicemgmt.stockoptions.productoptions.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ProductDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;


@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE
)
public interface ProductMapper {

    ProductMapper INSTANCE = Mappers.getMapper(ProductMapper.class);

    // Entity -> DTO (ID နဲ့ Name တွေကို ဆွဲထုတ်မယ်)
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "brand.id", target = "brandId")
    @Mapping(source = "brand.name", target = "brandName")
    @Mapping(source = "unit.id", target = "unitId")
    @Mapping(source = "unit.unitName", target = "unitName") // Unit Entity ထဲက field name အတိုင်းပေးပါ
    @Mapping(source = "hasSerial", target = "hasSerial")
    @Mapping(source = "stockQty", target = "stockQty")
    ProductDTO toDto(Product entity);

    // DTO -> Entity (Relationship object တွေကို Service ထဲမှာပဲ Manual ထည့်မှာဖြစ်လို့ ignore လုပ်ထားမယ်)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "brand", ignore = true)
    @Mapping(target = "unit", ignore = true)
    @Mapping(target = "serials", ignore = true)
    @Mapping(target = "lastPurchaseCost", ignore = true)
    @Mapping(target = "archived", expression = "java(dto.getArchived() != null ? dto.getArchived() : Boolean.FALSE)")
    @Mapping(target = "quarantinedQty", expression = "java(dto.getQuarantinedQty() != null ? dto.getQuarantinedQty() : 0)")
    Product toEntity(ProductDTO dto);

    // Update Method
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "brand", ignore = true)
    @Mapping(target = "unit", ignore = true)
    @Mapping(target = "serials", ignore = true)
    @Mapping(target = "photoBase64", ignore = true)
    @Mapping(target = "lastPurchaseCost", ignore = true)
    void updateEntityFromDto(ProductDTO dto, @MappingTarget Product entity);

    @AfterMapping
    default void neverPersistNullRequiredFields(@MappingTarget Product product) {
        if (product.getArchived() == null) {
            product.setArchived(Boolean.FALSE);
        }
        if (product.getQuarantinedQty() == null) {
            product.setQuarantinedQty(0);
        }
        if (product.getStockQty() == null) {
            product.setStockQty(0);
        }
        if (product.getHasSerial() == null) {
            product.setHasSerial(Boolean.TRUE);
        }
        if (product.getReorderLevel() == null) {
            product.setReorderLevel(0);
        }
        if (product.getWarrantyMonths() == null) {
            product.setWarrantyMonths(0);
        }
    }
}
