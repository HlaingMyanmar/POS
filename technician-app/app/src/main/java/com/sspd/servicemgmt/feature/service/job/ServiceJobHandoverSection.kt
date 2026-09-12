package com.sspd.servicemgmt.feature.service.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.HandoverDTO
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.ui.theme.*

private fun canActOnHandover(item: HandoverDTO, myStaffId: Int, canSupervise: Boolean): Boolean {
    if (canSupervise) return true
    if (item.targetMine) return true
    val targetId = item.toStaffId ?: return false
    return myStaffId > 0 && myStaffId == targetId
}

@Composable
fun ServiceJobHandoverSection(
    team: TeamSnapshotDTO?,
    assignments: List<AssignmentDTO>,
    staff: List<StaffDTO>,
    myStaffId: Int,
    canSupervise: Boolean,
    loading: Boolean,
    onAccept: (Int) -> Unit,
    onReject: (Int, String?) -> Unit,
    onRequest: (fromAssignmentId: Int, toStaffId: Int, completedWork: String?, remainingWork: String, diagnosisNote: String?) -> Unit
) {
    val handovers = team?.handovers.orEmpty()
    val pendingMine = team?.myPendingHandovers?.takeIf { it.isNotEmpty() }
        ?: handovers.filter {
            it.status.equals("PENDING", true) && canActOnHandover(it, myStaffId, canSupervise)
        }
    val sendable = assignments.filter {
        it.mine && it.status?.uppercase() in listOf("ACTIVE", "PAUSED")
    }
    if (pendingMine.isEmpty() && handovers.isEmpty() && sendable.isEmpty()) return

    var rejectTarget by remember { mutableStateOf<HandoverDTO?>(null) }
    var requestTarget by remember { mutableStateOf<AssignmentDTO?>(null) }

    rejectTarget?.id?.let { handoverId ->
        NoteInputDialog(
            title = "Hand Over ငြင်းပယ်ရသည့်အကြောင်း",
            label = "အကြောင်းပြချက်",
            required = true,
            onDismiss = { rejectTarget = null },
            onConfirm = { reason ->
                rejectTarget = null
                onReject(handoverId, reason.ifBlank { null })
            }
        )
    }

    requestTarget?.let { assignment ->
        HandoverRequestDialog(
            assignment = assignment,
            staff = staff.filter { person -> person.id != assignment.staffId },
            onDismiss = { requestTarget = null },
            onConfirm = { toStaffId, completedWork, remainingWork, diagnosisNote ->
                requestTarget = null
                assignment.id?.let { fromId ->
                    onRequest(fromId, toStaffId, completedWork, remainingWork, diagnosisNote)
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
        border = BorderStroke(1.dp, Color(0xFFE9D5FF))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Service Hand Over", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF581C87))

            pendingMine.forEach { item ->
                PendingHandoverCard(
                    item = item,
                    loading = loading,
                    onAccept = { item.id?.let(onAccept) },
                    onReject = { rejectTarget = item }
                )
            }

            if (sendable.isNotEmpty()) {
                Text("Hand Over ပို့ရန်", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7E22CE))
                sendable.forEach { assignment ->
                    OutlinedButton(
                        onClick = { requestTarget = assignment },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("${assignment.staffName} — Hand Over ပို့", fontSize = 11.sp)
                    }
                }
            }

            handovers.filter { it.status.equals("PENDING", true) && it !in pendingMine }.forEach { item ->
                HandoverHistoryCard(item = item)
            }
            handovers.filter { !it.status.equals("PENDING", true) }.forEach { item ->
                HandoverHistoryCard(item = item)
            }
        }
    }
}

@Composable
private fun PendingHandoverCard(
    item: HandoverDTO,
    loading: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFFCD34D))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.fromStaffName} → ${item.toStaffName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                HandoverStatusBadge(item.status)
            }
            item.completedWork?.takeIf { it.isNotBlank() }?.let {
                Text("လုပ်ပြီးသောအလုပ်: $it", fontSize = 11.sp, color = TextMain)
            }
            Text("ကျန်ရှိသောအလုပ်: ${item.remainingWork.orEmpty()}", fontSize = 11.sp, color = TextMain)
            item.diagnosisNote?.takeIf { it.isNotBlank() }?.let {
                Text("Diagnosis: $it", fontSize = 10.sp, color = TextMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onAccept, enabled = !loading, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Hand Over လက်ခံ", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onReject, enabled = !loading, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("ငြင်းပယ်", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun HandoverHistoryCard(item: HandoverDTO) {
    Surface(color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.fromStaffName} → ${item.toStaffName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                HandoverStatusBadge(item.status)
            }
            item.completedWork?.takeIf { it.isNotBlank() }?.let {
                Text("လုပ်ပြီးသောအလုပ်: $it", fontSize = 10.sp, color = TextMuted)
            }
            Text("ကျန်ရှိ: ${item.remainingWork.orEmpty()}", fontSize = 10.sp, color = TextMuted)
            item.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                Text("ငြင်းပယ်ရသည့်အကြောင်း: $it", fontSize = 10.sp, color = Danger)
            }
        }
    }
}

@Composable
private fun HandoverStatusBadge(status: String?) {
    val normalized = status?.uppercase().orEmpty()
    val (bg, fg, label) = when (normalized) {
        "ACCEPTED" -> Triple(Color(0xFFD1FAE5), Color(0xFF065F46), "လက်ခံပြီး")
        "REJECTED" -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "ငြင်းပယ်ပြီး")
        else -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "စောင့်ဆိုင်းနေ")
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun HandoverRequestDialog(
    assignment: AssignmentDTO,
    staff: List<StaffDTO>,
    onDismiss: () -> Unit,
    onConfirm: (toStaffId: Int, completedWork: String?, remainingWork: String, diagnosisNote: String?) -> Unit
) {
    var toStaffId by remember { mutableStateOf<Int?>(null) }
    var completedWork by remember { mutableStateOf("") }
    var remainingWork by remember { mutableStateOf(assignment.taskDescription.orEmpty()) }
    var diagnosisNote by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val selectedName = staff.firstOrNull { it.id == toStaffId }?.name ?: "လက်ခံမည့် Technician ရွေးပါ"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hand Over Request", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedName, fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        staff.forEach { person ->
                            DropdownMenuItem(
                                text = { Text(person.name) },
                                onClick = {
                                    toStaffId = person.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(value = completedWork, onValueChange = { completedWork = it }, label = { Text("လုပ်ပြီးသောအလုပ်") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = remainingWork, onValueChange = { remainingWork = it }, label = { Text("ကျန်ရှိသောအလုပ် *") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = diagnosisNote, onValueChange = { diagnosisNote = it }, label = { Text("Diagnosis / မှတ်ချက်") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = toStaffId ?: return@Button
                    onConfirm(
                        target,
                        completedWork.ifBlank { null },
                        remainingWork.trim(),
                        diagnosisNote.ifBlank { null }
                    )
                },
                enabled = toStaffId != null && remainingWork.isNotBlank()
            ) { Text("Hand Over ပို့", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") } }
    )
}
