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

    // DTO မှ Entity သို့ ပြောင်းလဲခြင်း
    Customer toEntity(CustomerDTO dto);

    // ရှိပြီးသား Entity ကို DTO ပါအချက်အလက်များဖြင့် Update လုပ်ခြင်း
    @Mapping(target = "id", ignore = true) // ID ကို update လုပ်ခွင့်မပြုပါ
    void updateEntityFromDto(CustomerDTO dto, @MappingTarget Customer entity);
}