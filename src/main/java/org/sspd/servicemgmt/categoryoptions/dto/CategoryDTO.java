package org.sspd.servicemgmt.categoryoptions.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CategoryDTO {
    private Integer id;
    @NotBlank(message = "Required Category Name")
    private String name;
    private String description;
    private boolean isActive;
    private Long parentId; // ဒါကို မဖြစ်မနေ ထည့်ပေးရပါမယ် (Parent ချိတ်ဖို့အတွက်)
    private String parentName; // ဒါကတော့ UI မှာ ပြဖို့အတွက်ပါ

    // အကယ်၍ Tree structure (Sub-categories list) ပြချင်ရင် ဒါကိုသုံးပါ
    private List<CategoryDTO> children;
}
