package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;

@Mapper(componentModel = "spring")
public interface PaymentTransactionMapper {

    PaymentTransaction INSTANCE = Mappers.getMapper(PaymentTransaction.class);

    @Mapping(source = "paymentMethod.id", target = "paymentMethodId")
    @Mapping(source = "paymentMethod.methodName", target = "paymentMethodName")
    PaymentTransactionDTO toDto(PaymentTransaction entity);

    @Mapping(target = "paymentMethod", ignore = true)
    PaymentTransaction toEntity(PaymentTransactionDTO dto);

}
