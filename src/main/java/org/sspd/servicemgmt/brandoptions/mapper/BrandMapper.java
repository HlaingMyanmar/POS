package org.sspd.servicemgmt.brandoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.brandoptions.dto.BrandDTO;
import org.sspd.servicemgmt.brandoptions.model.Brand;

@Mapper(componentModel = "spring")
public interface BrandMapper {

    BrandMapper INSTANCE = Mappers.getMapper(BrandMapper.class);

    // Entity -> DTO
    BrandDTO toDto(Brand entity);

    // DTO -> Entity
    Brand toEntitiy(BrandDTO dto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(BrandDTO dto, @MappingTarget Brand entity);


}
