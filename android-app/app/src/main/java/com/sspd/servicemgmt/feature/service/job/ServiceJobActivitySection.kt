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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.ServiceJobActivityDTO
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOfNotNull(
                                activity.eventType,
                                activity.fromStatus?.let { "$it → ${activity.toStatus ?: "-"}" }
                            ).joinToString(" · "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        if (!activity.note.isNullOrBlank()) {
                            Text(activity.note, fontSize = 11.sp, color = TextMuted)
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
