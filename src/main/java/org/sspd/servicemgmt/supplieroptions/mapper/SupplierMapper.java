package org.sspd.servicemgmt.supplieroptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.supplieroptions.dto.SupplierDTO;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SupplierMapper {

    SupplierMapper INSTANCE = Mappers.getMapper(SupplierMapper.class);
    SupplierDTO toDto(Supplier entity);
    @Mapping(target = "code", ignore = true)
    Supplier toEntity(SupplierDTO dto);
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "openingBalance", ignore = true)
    @Mapping(target = "currentBalance", ignore = true)
    void updateEntityFromDto(SupplierDTO dto, @MappingTarget Supplier entity);
}