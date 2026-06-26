package org.sspd.servicemgmt.stockoptions.stockmovementoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.dto.StockMovementDTO;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    StockMovementDTO toDto(StockMovement entity);

    @Mapping(target = "product", ignore = true)
    StockMovement toEntity(StockMovementDTO dto);
}