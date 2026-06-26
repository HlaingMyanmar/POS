package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.dto.AccountBalanceDTO;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;

@Mapper(componentModel = "spring")
public interface AccountBalanceMapper {
    AccountBalanceMapper INSTANCE = Mappers.getMapper(AccountBalanceMapper.class);

    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.accountName", target = "accountName")
    AccountBalanceDTO toDto(AccountBalance entity);
}
