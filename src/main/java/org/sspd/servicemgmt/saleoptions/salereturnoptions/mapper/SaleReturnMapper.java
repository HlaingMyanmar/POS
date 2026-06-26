package org.sspd.servicemgmt.saleoptions.salereturnoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.dto.SaleReturnDTO;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;
import org.sspd.servicemgmt.saleoptions.salereturndetails.dto.SaleReturnDetailDTO;
import org.sspd.servicemgmt.saleoptions.salereturndetails.model.SaleReturnDetail;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SaleReturnMapper {

    SaleReturnMapper INSTANCE = Mappers.getMapper(SaleReturnMapper.class);

    @Mapping(source = "sale.id", target = "saleId")
    @Mapping(source = "sale.saleCode", target = "saleCode")
    @Mapping(source = "sale.customer.id", target = "customerId")
    @Mapping(source = "sale.customer.name", target = "customerName")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(target = "paymentMethodId", ignore = true)
    SaleReturnDTO toDto(SaleReturn entity);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "saleReturn.id", target = "returnId")
    @Mapping(target = "serialNumbers", source = "serialNumber", qualifiedByName = "serialStringToList")
    SaleReturnDetailDTO toDto(SaleReturnDetail detail);

    @Mapping(target = "sale", ignore = true)
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "returnCode", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    SaleReturn toEntity(SaleReturnDTO dto);

    @Named("serialStringToList")
    default List<String> serialStringToList(String serials) {
        if (serials == null || serials.isBlank()) {
            return List.of();
        }
        return Arrays.stream(serials.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
