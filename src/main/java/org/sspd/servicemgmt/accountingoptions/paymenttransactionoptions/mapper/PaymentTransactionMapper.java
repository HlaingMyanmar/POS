package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;

@Mapper(componentModel = "spring")
public interface PaymentTransactionMapper {

    PaymentTransactionMapper INSTANCE = Mappers.getMapper(PaymentTransactionMapper.class);

    @Mapping(source = "paymentMethod.id", target = "paymentMethodId")
    @Mapping(source = "paymentMethod.methodName", target = "paymentMethodName")
    PaymentTransactionDTO toDto(PaymentTransaction entity);

    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "reversed", expression = "java(dto.getReversed() != null ? dto.getReversed() : Boolean.FALSE)")
    PaymentTransaction toEntity(PaymentTransactionDTO dto);

    @AfterMapping
    default void neverPersistNullRequiredFields(@MappingTarget PaymentTransaction entity) {
        if (entity.getReversed() == null) {
            entity.setReversed(Boolean.FALSE);
        }
    }

}
