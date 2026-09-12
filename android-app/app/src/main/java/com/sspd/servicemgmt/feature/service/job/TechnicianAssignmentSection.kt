package com.sspd.servicemgmt.feature.service.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.ui.component.AppPickerSheet
import com.sspd.servicemgmt.core.ui.theme.*

@Composable
fun TechnicianAssignmentSection(
    team: TeamSnapshotDTO?,
    staff: List<StaffDTO>,
    loading: Boolean,
    onAssign: (staffId: Int, role: String, task: String) -> Unit,
    onCancelAssignment: (Int) -> Unit
) {
    var showAssignSheet by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("LEAD") }
    var taskDescription by remember { mutableStateOf("") }

    if (showAssignSheet) {
        AppPickerSheet(
            title = "Technician ရွေးပါ ($selectedRole)",
            items = staff,
            label = { it.name },
            subtitle = { it.role },
            onSelect = { s ->
                onAssign(s.id, selectedRole, taskDescription)
                showAssignSheet = false
                taskDescription = ""
            },
            onDismiss = { showAssignSheet = false }
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Technician Team", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF312E81))
                if (!loading) {
                    IconButton(onClick = { showAssignSheet = true }) {
                        Icon(Icons.Outlined.PersonAdd, "Assign", tint = Primary)
                    }
                }
            }

            team?.completionBlockReason?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = Warning)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("LEAD" to "Lead", "MEMBER" to "Member", "HELPER" to "Helper").forEach { (k, v) ->
                    FilterChip(
                        selected = selectedRole == k,
                        onClick = { selectedRole = k },
                        label = { Text(v, fontSize = 11.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = taskDescription,
                onValueChange = { taskDescription = it },
                label = { Text("လုပ်ငန်းတာဝန်") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(10.dp)
            )

            val assignments = team?.assignments.orEmpty()
            if (assignments.isEmpty()) {
                Text("Technician မသတ်မှတ်ရသေးပါ", fontSize = 12.sp, color = TextMuted)
            } else {
                assignments.forEach { assignment ->
                    AssignmentRow(assignment, loading, onCancelAssignment)
                }
            }
        }
    }
}

@Composable
private fun AssignmentRow(assignment: AssignmentDTO, loading: Boolean, onCancel: (Int) -> Unit) {
    val roleLabel = when (assignment.role?.uppercase()) {
        "LEAD" -> "Lead"
        "MEMBER" -> "Member"
        "HELPER" -> "Helper"
        else -> assignment.role.orEmpty()
    }
    val statusLabel = when (assignment.status?.uppercase()) {
        "PENDING" -> "စောင့်ဆိုင်း"
        "ACTIVE" -> "လုပ်ဆောင်"
        "PAUSED" -> "ရပ်နား"
        "COMPLETED" -> "ပြီးစီး"
        else -> assignment.status.orEmpty()
    }
    Surface(color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(assignment.staffName.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("$roleLabel · $statusLabel", fontSize = 11.sp, color = TextMuted)
                assignment.taskDescription?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.sp, color = TextMuted)
                }
                Text("အချိန်: ${assignment.accumulatedMinutes ?: 0} မိနစ်", fontSize = 10.sp, color = TextMuted)
            }
            if (!loading && assignment.status?.uppercase() !in listOf("COMPLETED", "CANCELED")) {
                assignment.id?.let { id ->
                    IconButton(onClick = { onCancel(id) }) {
                        Icon(Icons.Outlined.Close, "Remove", tint = Danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
