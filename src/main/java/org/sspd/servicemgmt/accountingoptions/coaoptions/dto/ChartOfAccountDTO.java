package org.sspd.servicemgmt.accountingoptions.coaoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;

import java.util.List;

@Data
public class ChartOfAccountDTO {
    private Integer id;
    private String accountName;
    private AccountType accountType;
    private String code;

    private Integer parentId;
    private String parentName;

    private List<ChartOfAccountDTO> children; // Tree View အတွက်
}