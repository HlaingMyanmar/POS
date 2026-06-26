package org.sspd.servicemgmt.saleoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SaleMapper {

    SaleMapper INSTANCE = Mappers.getMapper(SaleMapper.class);

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "customer.name", target = "customerName")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    @Mapping(target = "paymentStatus", expression = "java(entity.getPaymentStatus() != null ? entity.getPaymentStatus().name() : null)")
    @Mapping(target = "creditStatus", expression = "java(entity.getCreditStatus() != null ? entity.getCreditStatus().name() : null)")
    @Mapping(source = "foc", target = "foc")
    SaleDTO toDto(Sale entity);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(target = "serialNumbers", source = "serialNumber", qualifiedByName = "serialStringToList")
    @Mapping(source = "discountAmount", target = "discountAmount")
    @Mapping(source = "foc", target = "foc")
    @Mapping(source = "warrantyMonths", target = "warrantyMonths")
    @Mapping(source = "warrantyExpiryDate", target = "warrantyExpiryDate")
    SaleDetailDTO toDto(SaleDetail detail);

    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "creditStatus", ignore = true)
    Sale toEntity(SaleDTO dto);

    @Named("serialStringToList")
    default List<String> serialStringToList(String serials) {
        if (serials == null || serials.isBlank()) return List.of();
        return Arrays.stream(serials.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
