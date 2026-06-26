package org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.dto.StockAdjustmentDTO;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model.StockAdjustment;

@Mapper(componentModel = "spring")
public interface StockAdjustmentMapper {

    StockAdjustmentMapper INSTANCE = Mappers.getMapper(StockAdjustmentMapper.class);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    StockAdjustmentDTO toDto(StockAdjustment entity);

    @Mapping(target = "product", ignore = true)
    @Mapping(target = "staff", ignore = true)
    StockAdjustment toEntity(StockAdjustmentDTO dto);
}
