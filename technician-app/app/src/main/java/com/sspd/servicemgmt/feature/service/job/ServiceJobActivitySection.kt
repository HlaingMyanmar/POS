package com.sspd.servicemgmt.feature.service.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.ServiceJobActivityDTO
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@Composable
fun ServiceJobActivitySection(activities: List<ServiceJobActivityDTO>?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("လုပ်ဆောင်မှတ်တမ်း", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            val items = activities.orEmpty()
            if (items.isEmpty()) {
                Text("မှတ်တမ်းမရှိသေးပါ", fontSize = 12.sp, color = TextMuted)
            } else {
                items.forEachIndexed { index, activity ->
                    val isReject = activity.eventType?.uppercase().orEmpty().contains("REJECT")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOfNotNull(
                                activityLabel(activity.eventType),
                                activity.fromStatus?.let { "$it → ${activity.toStatus ?: "-"}" }
                            ).joinToString(" · "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isReject) Danger else TextMain
                        )
                        if (!activity.note.isNullOrBlank()) {
                            Text(
                                if (isReject) "အကြောင်းရင်း: ${activity.note}" else activity.note!!,
                                fontSize = 11.sp,
                                fontWeight = if (isReject) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isReject) Danger else TextMuted
                            )
                        }
                        Text(
                            listOfNotNull(
                                activity.actor,
                                activity.occurredAt?.take(16)?.replace("T", " ")
                            ).joinToString(" · "),
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    if (index < items.lastIndex) HorizontalDivider(color = BorderColor)
                }
            }
        }
    }
}

private fun activityLabel(eventType: String?): String = when (eventType?.uppercase()) {
    "ASSIGNMENT_REJECTED" -> "Assignment ငြင်းပယ်"
    "HANDOVER_REJECTED" -> "Hand Over ငြင်းပယ်"
    "ESTIMATE_REJECTED" -> "Job (Estimate) ငြင်းပယ်"
    "ESTIMATE_HOLD" -> "Estimate Hold"
    "ESTIMATE_APPROVED" -> "Estimate အတည်ပြု"
    "HELPER_APPROVED" -> "Helper အတည်ပြု"
    "ASSIGNMENT_ACCEPTED" -> "Assignment လက်ခံ"
    "VOIDED" -> "Settlement Void"
    else -> eventType ?: "—"
}
