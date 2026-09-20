package com.sspd.servicemgmt.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sspd.servicemgmt.core.network.CustomerJob
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Warning
import com.sspd.servicemgmt.core.ui.theme.WarningBg

@Composable
fun JobServiceModeChip(job: CustomerJob) {
    val outdoor = job.isOutdoor()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (outdoor) WarningBg else PrimaryLight,
        border = BorderStroke(1.dp, (if (outdoor) Warning else Primary).copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (outdoor) Icons.Outlined.Place else Icons.Outlined.Home,
                contentDescription = null,
                tint = if (outdoor) Warning else Primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                job.serviceModeLabel(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (outdoor) Warning else Primary
            )
        }
    }
}

@Composable
fun JobBookingNoChip(job: CustomerJob) {
    val bookingNo = job.bookingNo?.takeIf { it.isNotBlank() } ?: return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SuccessBg,
        border = BorderStroke(1.dp, Success.copy(alpha = 0.25f))
    ) {
        Text(
            "Booking $bookingNo",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Success
        )
    }
}

@Composable
fun JobTimelineAndCrew(job: CustomerJob) {
    val bookingNo = job.bookingNo?.takeIf { it.isNotBlank() }
    val hasDates = listOf(
        job.receivedDate,
        job.appointmentDate,
        job.workStartedAt,
        job.completedDate,
        job.deliveredDate
    ).any { !it.isNullOrBlank() } || bookingNo != null
    val crew = job.crewMembers()

    if (hasDates) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "ရက်စွဲ မှတ်တမ်း",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (bookingNo != null) {
                        JobTimelineRow("Booking နံပါတ်", bookingNo, dateValue = false)
                    }
                    JobTimelineRow("လက်ခံသည့်ရက်", job.receivedDate)
                    JobTimelineRow("ချိန်းဆိုချိန်", job.appointmentDate)
                    JobTimelineRow("အလုပ်စတင်သည့်အချိန်", job.workStartedAt)
                    JobTimelineRow("ပြီးစီးသည့်ရက်", job.completedDate)
                    JobTimelineRow("အပ်နှံသည့်ရက်", job.deliveredDate)
                }
            }
        }
    }

    if (crew.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Technician / Helper",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    crew.forEach { member ->
                        val helper = member.isHelper()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (helper) WarningBg else SuccessBg
                            ) {
                                Icon(
                                    Icons.Outlined.Groups,
                                    contentDescription = null,
                                    tint = if (helper) Warning else Success,
                                    modifier = Modifier.padding(8.dp).size(16.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    member.name.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMain
                                )
                                Text(
                                    member.roleLabel?.takeIf { it.isNotBlank() }
                                        ?: if (helper) "Helper" else "Technician",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            if (helper) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = WarningBg
                                ) {
                                    Text(
                                        "Helper",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Warning
                                    )
                                }
                            }
                        }
                    }
                    if (job.hasHelper == true || crew.any { it.isHelper() }) {
                        Text(
                            "Helper ပါဝင်သည်",
                            style = MaterialTheme.typography.labelSmall,
                            color = Warning,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "Helper မပါပါ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JobTimelineRow(label: String, dateRaw: String?, dateValue: Boolean = true) {
    val value = dateRaw?.takeIf { it.isNotBlank() }?.let {
        if (dateValue) it.replace('T', ' ').take(16) else it
    } ?: "—"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (value == "—") TextMuted else TextMain
        )
    }
}
