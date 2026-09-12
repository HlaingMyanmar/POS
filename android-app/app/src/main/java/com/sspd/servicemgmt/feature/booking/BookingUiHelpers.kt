package com.sspd.servicemgmt.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.ui.theme.*

fun bookingNextAction(booking: BookingDTO): String {
  val status = booking.status?.uppercase().orEmpty()
  val linked = booking.linkedJobs.orEmpty()
  val unconverted = booking.unconvertedItemCount ?: 0L
  return when (status) {
    "CONFIRMED" -> when {
      linked.isNotEmpty() -> "Outdoor Job ပြောင်းပြီး"
      else -> "Outdoor ပြောင်း / ပစ္စည်းလက်ခံ"
    }
    "ARRIVED" -> when {
      unconverted > 0 -> "Indoor Job ပြောင်းရန်"
      booking.fullyConverted == true -> "Items အားလုံးပြောင်းပြီး"
      else -> "ပစ္စည်းလက်ခံပြီး"
    }
    "CANCELED", "CANCELLED" -> "ပယ်ဖျက်ထား"
    else -> "—"
  }
}

@Composable
fun BookingStatusBadge(status: String?) {
  val (bg, color, label) = when (status?.uppercase()) {
    "CONFIRMED" -> Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), "အတည်ပြုပြီး")
    "ARRIVED"   -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "ပစ္စည်းလက်ခံပြီး")
    "CANCELED", "CANCELLED" -> Triple(DangerBg, Danger, "ပယ်ဖျက်ထား")
    "PENDING"   -> Triple(WarningBg, Warning, "စောင့်ဆိုင်း")
    "IN_STORAGE" -> Triple(VioletBg, Violet, "သိမ်းထားပြီး")
    "CONVERTED" -> Triple(SuccessBg, Success, "အလုပ်ပြောင်းပြီး")
    else        -> Triple(BorderColor, TextMuted, status ?: "—")
  }
  Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
    Text(
      label,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = color
    )
  }
}
