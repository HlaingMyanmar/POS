package org.sspd.servicemgmt.stockoptions.productoptions.mapper;

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
}
