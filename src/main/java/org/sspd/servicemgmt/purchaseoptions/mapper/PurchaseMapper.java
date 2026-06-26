package org.sspd.servicemgmt.purchaseoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto.PurchaseDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;

@Mapper(componentModel = "spring")
public interface PurchaseMapper {

    PurchaseMapper INSTANCE = Mappers.getMapper(PurchaseMapper.class);

    @Mapping(source = "supplier.id", target = "supplierId")
    @Mapping(source = "supplier.name", target = "supplierName")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    PurchaseDTO toDto(Purchase entity);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    PurchaseDetailDTO toDto(PurchaseDetail detail);

    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "details", ignore = true)
    Purchase toEntity(PurchaseDTO dto);

    @Mapping(source = "paymentMethod.id", target = "paymentMethodId")
    @Mapping(source = "paymentMethod.methodName", target = "paymentMethodName")
    PaymentTransactionDTO toDto(PaymentTransaction savedEntity);
}
