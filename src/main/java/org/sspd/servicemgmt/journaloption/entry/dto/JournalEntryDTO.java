package org.sspd.servicemgmt.journaloption.entry.dto;

import lombok.Data;
import lombok.ToString;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;

import java.time.LocalDateTime;
import java.util.List;

@ToString
@Data
public class JournalEntryDTO {
    private Integer id;
    private LocalDateTime entryDate;
    private String referenceNo;
    private String description;
    private Integer staffId;
    private String staffName;
    private List<JournalDetailDTO> details;
}