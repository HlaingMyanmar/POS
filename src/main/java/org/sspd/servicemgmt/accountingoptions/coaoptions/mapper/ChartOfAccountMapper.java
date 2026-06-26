package org.sspd.servicemgmt.accountingoptions.coaoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.coaoptions.dto.ChartOfAccountDTO;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;

@Mapper(componentModel = "spring")
public interface ChartOfAccountMapper {

    ChartOfAccountMapper INSTANCE = Mappers.getMapper(ChartOfAccountMapper.class);

    @Mapping(source = "parent.id", target = "parentId")
    @Mapping(source = "parent.accountName", target = "parentName")
    ChartOfAccountDTO toDto(ChartOfAccount entity);

    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "children", ignore = true)
    ChartOfAccount toEntity(ChartOfAccountDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    void updateEntityFromDto(ChartOfAccountDTO dto, @MappingTarget ChartOfAccount entity);
}
