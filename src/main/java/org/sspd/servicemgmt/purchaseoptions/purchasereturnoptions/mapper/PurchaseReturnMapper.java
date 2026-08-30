package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PurchaseReturnMapper {

    PurchaseReturnMapper INSTANCE = Mappers.getMapper(PurchaseReturnMapper.class);

    @Mapping(source = "purchase.id", target = "purchaseId")
    @Mapping(target = "paymentMethodId", ignore = true)
    @Mapping(target = "transactionNo", ignore = true)
    @Mapping(target = "shippingPaymentMethodName", ignore = true)
    @Mapping(target = "shippingPaymentTransaction", ignore = true)
    PurchaseReturnDTO toDto(PurchaseReturn entity);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "purchaseReturn.id", target = "returnId")
    @Mapping(source = "reason.id", target = "reasonId")
    @Mapping(source = "reason.code", target = "reasonCode")
    @Mapping(source = "reason.name", target = "reasonName")
    @Mapping(target = "serialNumbers", source = "serialNumber", qualifiedByName = "serialStringToList")
    PurchaseReturnDetailDTO toDto(PurchaseReturnDetail detail);

    @Mapping(target = "purchase", ignore = true)
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "shippingCostAmount", expression = "java(dto.getShippingCostAmount() != null ? dto.getShippingCostAmount() : java.math.BigDecimal.ZERO)")
    @Mapping(target = "shippingPayerResponsibility", expression = "java(dto.getShippingPayerResponsibility() != null && !dto.getShippingPayerResponsibility().isBlank() ? dto.getShippingPayerResponsibility() : \"COMPANY\")")
    @Mapping(target = "companyShippingPortion", expression = "java(dto.getCompanyShippingPortion() != null ? dto.getCompanyShippingPortion() : java.math.BigDecimal.ZERO)")
    @Mapping(target = "supplierShippingPortion", expression = "java(dto.getSupplierShippingPortion() != null ? dto.getSupplierShippingPortion() : java.math.BigDecimal.ZERO)")
    @Mapping(target = "shippingAllocationMethod", expression = "java(dto.getShippingAllocationMethod() != null && !dto.getShippingAllocationMethod().isBlank() ? dto.getShippingAllocationMethod() : \"VALUE\")")
    PurchaseReturn toEntity(PurchaseReturnDTO dto);

    @AfterMapping
    default void neverPersistNullRequiredFields(@MappingTarget PurchaseReturn entity) {
        if (entity.getShippingCostAmount() == null) {
            entity.setShippingCostAmount(BigDecimal.ZERO);
        }
        if (entity.getShippingPayerResponsibility() == null || entity.getShippingPayerResponsibility().isBlank()) {
            entity.setShippingPayerResponsibility("COMPANY");
        }
        if (entity.getCompanyShippingPortion() == null) {
            entity.setCompanyShippingPortion(BigDecimal.ZERO);
        }
        if (entity.getSupplierShippingPortion() == null) {
            entity.setSupplierShippingPortion(BigDecimal.ZERO);
        }
        if (entity.getShippingAllocationMethod() == null || entity.getShippingAllocationMethod().isBlank()) {
            entity.setShippingAllocationMethod("VALUE");
        }
    }

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
