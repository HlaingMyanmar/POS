package org.sspd.servicemgmt.rbacoptions.permissionoptions.mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.dto.PermissionDTO;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.model.Permission;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    // Interface ကို တိုက်ရိုက်ခေါ်သုံးလို့ရအောင် လုပ်တာပါ
   PermissionMapper INSTANCE = Mappers.getMapper(PermissionMapper.class);

    // Entity -> DTO
   PermissionDTO toDto(Permission entity);

    // DTO -> Entity
   Permission toEntity(PermissionDTO dto);

    @Mapping(target = "id", ignore = true) // ID ကိုတော့ မပြင်အောင် ignore လုပ်ထားတာပါ
    void updateEntityFromDto(PermissionDTO dto, @MappingTarget Permission entity);


}
