package org.sspd.servicemgmt.customeroptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.customeroptions.dto.CustomerDTO;
import org.sspd.servicemgmt.customeroptions.model.Customer;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerMapper INSTANCE = Mappers.getMapper(CustomerMapper.class);

    // Entity မှ DTO သို့ ပြောင်းလဲခြင်း
    CustomerDTO toDto(Customer entity);

    @Mapping(target = "advanceBalance", ignore = true)
    Customer toEntity(CustomerDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "advanceBalance", ignore = true)
    void updateEntityFromDto(CustomerDTO dto, @MappingTarget Customer entity);
}