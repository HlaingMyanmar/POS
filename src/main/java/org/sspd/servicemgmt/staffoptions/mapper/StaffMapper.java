package org.sspd.servicemgmt.staffoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.staffoptions.dto.StaffDTO;
import org.sspd.servicemgmt.staffoptions.model.Staff;

@Mapper(componentModel = "spring")
public interface StaffMapper {

    StaffMapper INSTANCE = Mappers.getMapper(StaffMapper.class);

    StaffDTO toDto(Staff entity);
    Staff toEntity(StaffDTO dto);
    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(StaffDTO dto, @MappingTarget Staff entity);
}