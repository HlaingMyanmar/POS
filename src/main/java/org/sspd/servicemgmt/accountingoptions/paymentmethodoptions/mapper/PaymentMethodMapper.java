package org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.dto.PaymentMethodDTO;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;


@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PaymentMethodMapper {

    PaymentMethodMapper INSTANCE = Mappers.getMapper(PaymentMethodMapper.class);

    // Entity -> DTO (COA ရဲ့ ID နဲ့ Name ကို ဆွဲထုတ်မယ်)
    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.accountName", target = "accountName")
    PaymentMethodDTO toDto(PaymentMethod entity);

    // DTO -> Entity (Account object ကို Service ထဲမှာ manual ရှာပြီးထည့်မှာမို့လို့ ignore လုပ်ထားမယ်)
    @Mapping(target = "account", ignore = true)
    PaymentMethod toEntity(PaymentMethodDTO dto);

    // Update Method
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    void updateEntityFromDto(PaymentMethodDTO dto, @MappingTarget PaymentMethod entity);
}