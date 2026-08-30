package org.sspd.servicemgmt.stockoptions.productserialoptions.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.stockoptions.productserialoptions.dto.ProductSerialDTO;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ProductSerialMapper {

    ProductSerialMapper INSTANCE = Mappers.getMapper(ProductSerialMapper.class);

    // Entity -> DTO
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "warehouse.id", target = "warehouseId")
    @Mapping(source = "warehouse.code", target = "warehouseCode")
    @Mapping(source = "warehouse.name", target = "warehouseName")
    ProductSerialDTO toDto(ProductSerial entity);

    // DTO -> Entity
    @Mapping(target = "product", ignore = true) // Manual mapping in service layer
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "status", expression = "java(dto.getStatus() != null ? dto.getStatus() : org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus.Available)")
    ProductSerial toEntity(ProductSerialDTO dto);

    // Update
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    void updateEntityFromDto(ProductSerialDTO dto, @MappingTarget ProductSerial entity);

    @AfterMapping
    default void neverPersistNullRequiredFields(@MappingTarget ProductSerial entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(SerialStatus.Available);
        }
    }
}
