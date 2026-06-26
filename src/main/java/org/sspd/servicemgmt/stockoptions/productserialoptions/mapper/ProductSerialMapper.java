package org.sspd.servicemgmt.stockoptions.productserialoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.stockoptions.productserialoptions.dto.ProductSerialDTO;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;

@Mapper(componentModel = "spring")
public interface ProductSerialMapper {

    ProductSerialMapper INSTANCE = Mappers.getMapper(ProductSerialMapper.class);

    // Entity -> DTO
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    ProductSerialDTO toDto(ProductSerial entity);

    // DTO -> Entity
    @Mapping(target = "product", ignore = true) // Manual mapping in service layer
    ProductSerial toEntity(ProductSerialDTO dto);

    // Update
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    void updateEntityFromDto(ProductSerialDTO dto, @MappingTarget ProductSerial entity);
}
