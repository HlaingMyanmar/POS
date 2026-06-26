package org.sspd.servicemgmt.creditoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.creditoptions.dto.CreditAlertDTO;
import org.sspd.servicemgmt.creditoptions.dto.CustomerCreditTermDTO;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO;
import org.sspd.servicemgmt.creditoptions.model.CreditAlert;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditTerm;
import org.sspd.servicemgmt.creditoptions.model.CustomerPayment;

@Mapper(componentModel = "spring")
public interface CreditMapper {

    CreditMapper INSTANCE = Mappers.getMapper(CreditMapper.class);

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "customer.name", target = "customerName")
    CustomerCreditTermDTO toDto(CustomerCreditTerm entity);

    @Mapping(target = "customer", ignore = true)
    CustomerCreditTerm toEntity(CustomerCreditTermDTO dto);

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "customer.name", target = "customerName")
    @Mapping(source = "sale.id", target = "saleId")
    @Mapping(source = "sale.saleCode", target = "saleCode")
    @Mapping(source = "paymentMethod.methodName", target = "paymentMethodName")
    @Mapping(source = "paymentMethod.id", target = "paymentMethodId")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    CustomerPaymentDTO toDto(CustomerPayment entity);

    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "sale", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "staff", ignore = true)
    CustomerPayment toEntity(CustomerPaymentDTO dto);

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "customer.name", target = "customerName")
    @Mapping(source = "sale.id", target = "saleId")
    @Mapping(source = "sale.saleCode", target = "saleCode")
    CreditAlertDTO toDto(CreditAlert entity);

    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "sale", ignore = true)
    CreditAlert toEntity(CreditAlertDTO dto);
}
