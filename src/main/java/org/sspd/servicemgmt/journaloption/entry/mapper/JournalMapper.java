package org.sspd.servicemgmt.journaloption.entry.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.detail.model.JournalDetail;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.model.JournalEntry;

@Mapper(componentModel = "spring")
public interface JournalMapper {
    JournalMapper INSTANCE = Mappers.getMapper(JournalMapper.class);

    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    JournalEntryDTO toDto(JournalEntry entity);

    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.accountName", target = "accountName")
    JournalDetailDTO toDto(JournalDetail entity);

    // Entity သို့ ပြန်ပြောင်းတဲ့အခါ Relationship တွေကို Service မှာပဲ handle လုပ်ပါမယ်
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "details", ignore = true)
    JournalEntry toEntity(JournalEntryDTO dto);
}
