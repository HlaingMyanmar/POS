package org.sspd.servicemgmt.accountingoptions.expenseoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.dto.ExpenseDTO;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.model.Expense;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {
    ExpenseMapper INSTANCE = Mappers.getMapper(ExpenseMapper.class);

    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.accountName", target = "accountName")
    @Mapping(source = "paymentMethod.id", target = "paymentMethodId")
    @Mapping(source = "paymentMethod.methodName", target = "paymentMethodName")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    ExpenseDTO toDto(Expense entity);

    @Mapping(target = "account", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "staff", ignore = true)
    Expense toEntity(ExpenseDTO dto);
}
