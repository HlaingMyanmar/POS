package org.sspd.servicemgmt.accountingoptions.incomeoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.dto.IncomeDTO;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.model.Income;

@Mapper(componentModel = "spring")
public interface IncomeMapper {
    IncomeMapper INSTANCE = Mappers.getMapper(IncomeMapper.class);

    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.accountName", target = "accountName")
    @Mapping(source = "paymentMethod.id", target = "paymentMethodId")
    @Mapping(source = "paymentMethod.methodName", target = "paymentMethodName")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    IncomeDTO toDto(Income entity);

    @Mapping(target = "account", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "staff", ignore = true)
    Income toEntity(IncomeDTO dto);
}
