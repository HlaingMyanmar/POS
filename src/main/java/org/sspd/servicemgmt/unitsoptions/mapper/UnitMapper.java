package org.sspd.servicemgmt.unitsoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.unitsoptions.dto.UnitDTO;
import org.sspd.servicemgmt.unitsoptions.model.Unit;

@Mapper(componentModel = "spring")
public interface UnitMapper {

    UnitMapper INSTANCE= Mappers.getMapper(UnitMapper.class);


    // Entity -> DTO
    UnitDTO toDto(Unit entity);

    // DTO -> Entity
    Unit toEntity(UnitDTO dto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(UnitDTO dto,@MappingTarget Unit entity);

}
