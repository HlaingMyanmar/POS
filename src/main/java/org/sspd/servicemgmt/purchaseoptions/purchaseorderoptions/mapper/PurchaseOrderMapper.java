package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrder;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrderDetail;

@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {

    @Mapping(source = "supplier.id", target = "supplierId")
    @Mapping(source = "supplier.name", target = "supplierName")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    PurchaseOrderDTO toDto(PurchaseOrder entity);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "product.hasSerial", target = "hasSerial")
    PurchaseOrderDetailDTO toDto(PurchaseOrderDetail detail);

    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "details", ignore = true)
    PurchaseOrder toEntity(PurchaseOrderDTO dto);
}
