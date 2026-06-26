package org.sspd.servicemgmt.categoryoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.categoryoptions.dto.CategoryDTO;
import org.sspd.servicemgmt.categoryoptions.model.Category;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    // Entity -> DTO
    CategoryDTO toDto(Category entity);

    // DTO -> Entity
    Category toEntity(CategoryDTO dto);

    @Mapping(target = "parent", ignore = true)
    void updateEntityFromDto(CategoryDTO dto, @MappingTarget Category entity);

}
