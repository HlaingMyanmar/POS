package org.sspd.servicemgmt.journaloption.detail.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.detail.model.JournalDetail;

@Mapper(componentModel = "spring")
public interface JournalDetailMapper {

    JournalDetailMapper INSTANCE = Mappers.getMapper(JournalDetailMapper.class);

    // Entity -> DTO (COA ရဲ့ ID နဲ့ နာမည်ကို ဆွဲထုတ်ယူမယ်)
    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.accountName", target = "accountName")
    JournalDetailDTO toDto(JournalDetail entity);

    // DTO -> Entity (Account object ကို Service ထဲမှာ manual ရှာပြီးထည့်မှာမို့လို့ ignore လုပ်ထားမယ်)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "journalEntry", ignore = true)
    @Mapping(target = "account", ignore = true)
    JournalDetail toEntity(JournalDetailDTO dto);

    // Update Logic
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "journalEntry", ignore = true)
    @Mapping(target = "account", ignore = true)
    void updateEntityFromDto(JournalDetailDTO dto, @MappingTarget JournalDetail entity);
}