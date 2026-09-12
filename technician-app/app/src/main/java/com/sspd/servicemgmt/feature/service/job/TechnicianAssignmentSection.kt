package com.sspd.servicemgmt.feature.service.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.ui.theme.*

/**
 * Technician Assignment — Web POS နှင့် တူညီသော လက်ခံ / စတင် / မှတ်ချက် / လုပ်ငန်းပြီးစီး flow။
 * လုပ်ငန်းပြီးစီး နှိပ်ရင် CompleteWorkDialog (လုပ်ငန်းပြီးစီးမှတ်တမ်း) ပေါ်သည်။
 */
@Composable
fun TechnicianAssignmentSection(
    team: TeamSnapshotDTO?,
    teamError: String?,
    myStaffId: Int,
    loading: Boolean,
    onAccept: (Int) -> Unit,
    onReject: (Int, String?) -> Unit,
    onWork: (Int, String, String?, String?, String?, String?) -> Unit
) {
    val assignments = team?.assignments.orEmpty()
        .filter { it.status?.uppercase() in VISIBLE_STATUSES }
    val liveAssignments = assignments.filter { it.status?.uppercase() != "REJECTED" }
    val rejectedAssignments = assignments.filter { it.status.equals("REJECTED", true) }
    var completeTarget by remember { mutableStateOf<AssignmentDTO?>(null) }
    var noteTarget by remember { mutableStateOf<AssignmentDTO?>(null) }
    var rejectTarget by remember { mutableStateOf<AssignmentDTO?>(null) }

    completeTarget?.let { target ->
        CompleteWorkDialog(
            assignment = target,
            onDismiss = { completeTarget = null },
            onConfirm = { work, service, parts, note ->
                completeTarget = null
                target.id?.let { onWork(it, "COMPLETE", work, service, parts, note) }
            }
        )
    }
    noteTarget?.let { target ->
        WorkLogNoteDialog(
            assignment = target,
            onDismiss = { noteTarget = null },
            onConfirm = { work, service, parts, note ->
                noteTarget = null
                target.id?.let { onWork(it, "NOTE", work, service, parts, note) }
            }
        )
    }
    rejectTarget?.id?.let { assignmentId ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Assignment ငြင်းပယ်မည်", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("အကြောင်းပြချက် *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    isError = reason.isBlank()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val r = reason.trim()
                        if (r.isBlank()) return@TextButton
                        rejectTarget = null
                        onReject(assignmentId, r)
                    },
                    enabled = reason.isNotBlank()
                ) { Text("ငြင်းပယ်မည်", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("မလုပ်တော့ပါ") }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Technician Assignment", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF14532D))

            when {
                !teamError.isNullOrBlank() -> Text(teamError, fontSize = 12.sp, color = Danger)
                team == null && !loading -> Text(
                    "Assignment အချက်အလက် မရပါ။ ပြန်ဖွင့်ကြည့်ပါ။",
                    fontSize = 12.sp,
                    color = Warning
                )
                liveAssignments.isEmpty() && rejectedAssignments.isEmpty() -> Text(
                    "သင့်အတွက် Assignment မရှိသေးပါ။ Manager / Web က Technician တာဝန်ပေးပြီးမှ လက်ခံနိုင်ပါမယ်။",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            team?.completionBlockReason?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = Warning)
            }

            liveAssignments.forEach { assignment ->
                val isMine = assignment.mine || (myStaffId > 0 && assignment.staffId == myStaffId)
                AssignmentWorkCard(
                    assignment = assignment,
                    isMine = isMine,
                    loading = loading,
                    onAccept = { assignment.id?.let(onAccept) },
                    onReject = { rejectTarget = assignment },
                    onStart = { assignment.id?.let { onWork(it, "START", null, null, null, null) } },
                    onPause = { assignment.id?.let { onWork(it, "PAUSE", null, null, null, null) } },
                    onResume = { assignment.id?.let { onWork(it, "RESUME", null, null, null, null) } },
                    onNote = { noteTarget = assignment },
                    onComplete = { completeTarget = assignment }
                )
            }

            if (rejectedAssignments.isNotEmpty()) {
                Text(
                    "ငြင်းပယ်ထားသော Assignment",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Danger
                )
                rejectedAssignments.forEach { assignment ->
                    RejectedAssignmentCard(assignment)
                }
            }
        }
    }
}

@Composable
private fun AssignmentWorkCard(
    assignment: AssignmentDTO,
    isMine: Boolean,
    loading: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNote: () -> Unit,
    onComplete: () -> Unit
) {
    val roleLabel = when (assignment.role?.uppercase()) {
        "LEAD" -> "Lead"
        "MEMBER" -> "Member"
        "HELPER" -> "Helper"
        else -> assignment.role.orEmpty()
    }
    val status = assignment.status?.uppercase().orEmpty()
    val statusLabel = when (status) {
        "PENDING" -> "လက်ခံရန်"
        "ACTIVE" -> "လုပ်ဆောင်ဆဲ"
        "PAUSED" -> "ရပ်နား"
        "COMPLETED" -> "ပြီးစီး"
        "REJECTED" -> "ငြင်းပယ်"
        else -> assignment.status.orEmpty()
    }
    val approvalPending = assignment.role.equals("HELPER", true)
        && assignment.approvalStatus.equals("PENDING", true)

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(assignment.staffName.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (isMine) {
                    Text("သင့်တာဝန်", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                }
            }
            Text("$roleLabel · $statusLabel", fontSize = 11.sp, color = TextMuted)
        }
        assignment.taskDescription?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = TextMuted)
        }
        Text("အချိန်: ${assignment.accumulatedMinutes ?: 0} မိနစ်", fontSize = 11.sp, color = TextMuted)

        if (approvalPending) {
            Text(
                "Supervisor အတည်ပြုမှု စောင့်နေသည် — အတည်ပြုပြီးမှ လက်ခံနိုင်ပါမယ်",
                fontSize = 11.sp,
                color = Warning,
                fontWeight = FontWeight.SemiBold
            )
        }

        assignment.logs.orEmpty()
            .filter { it.action == "COMPLETE" || it.action == "NOTE" }
            .takeLast(2)
            .forEach { log ->
                log.completedWork?.takeIf { it.isNotBlank() }?.let {
                    Text("လုပ်ပြီးသောအလုပ်: $it", fontSize = 11.sp, color = TextMain)
                }
                log.serviceDetails?.takeIf { it.isNotBlank() }?.let {
                    Text("Service: $it", fontSize = 10.sp, color = TextMuted)
                }
                log.partsDetails?.takeIf { it.isNotBlank() }?.let {
                    Text("Parts: $it", fontSize = 10.sp, color = TextMuted)
                }
            }

        if (isMine && !loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (status) {
                    "PENDING" -> {
                        if (!approvalPending) {
                            Button(
                                onClick = onAccept,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) { Text("လက်ခံ", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                        ) { Text("ငြင်းပယ်", fontSize = 12.sp) }
                    }
                    "ACTIVE", "PAUSED" -> {
                        if (status == "ACTIVE") {
                            if (assignment.workStartedAt.isNullOrBlank()) {
                                Button(
                                    onClick = onStart,
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) { Text("စတင်", fontSize = 11.sp) }
                            } else {
                                OutlinedButton(
                                    onClick = onPause,
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) { Text("ခေတ္တရပ်", fontSize = 11.sp) }
                            }
                        } else {
                            Button(
                                onClick = onResume,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) { Text("ပြန်စတင်", fontSize = 11.sp) }
                        }
                        OutlinedButton(
                            onClick = onNote,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text("မှတ်ချက်", fontSize = 11.sp) }
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) { Text("လုပ်ငန်းပြီးစီး", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private val VISIBLE_STATUSES = setOf("PENDING", "ACTIVE", "PAUSED", "COMPLETED", "REJECTED")

@Composable
private fun RejectedAssignmentCard(assignment: AssignmentDTO) {
    val reason = assignment.rejectionReason?.takeIf { it.isNotBlank() }
        ?: assignment.completionNote?.takeIf { it.isNotBlank() }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF1F2), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(assignment.staffName.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Danger)
            Text("ငြင်းပယ်", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger)
        }
        assignment.taskDescription?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = TextMuted)
        }
        Text(
            if (reason != null) "အကြောင်းရင်း: $reason" else "အကြောင်းရင်း မရှိပါ",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Danger
        )
        assignment.endedAt?.takeIf { it.isNotBlank() }?.let {
            Text(it.take(16).replace("T", " "), fontSize = 10.sp, color = TextMuted)
        }
    }
}

private fun previewAssignment(
    id: Int,
    name: String,
    role: String,
    status: String,
    mine: Boolean = true,
    workStarted: Boolean = false,
    task: String = "AC စစ်ဆေးမှုနှင့် ပြုပြင်မှု"
) = AssignmentDTO(
    id = id,
    staffId = if (mine) 7 else 99,
    staffName = name,
    role = role,
    status = status,
    approvalStatus = "APPROVED",
    taskDescription = task,
    workStartedAt = if (workStarted) "2026-09-03T10:00:00" else null,
    accumulatedMinutes = if (status == "ACTIVE" || status == "PAUSED") 45 else 0,
    mine = mine,
    logs = if (status == "COMPLETED") listOf(
        com.sspd.servicemgmt.core.network.AssignmentLogDTO(
            action = "COMPLETE",
            completedWork = "Compressor စစ်ပြီး refrigerant ဖြည့်ပြီး",
            serviceDetails = "Gas refill",
            partsDetails = "R32 gas"
        )
    ) else emptyList()
)

private fun previewTeam(vararg assignments: AssignmentDTO) = TeamSnapshotDTO(
    serviceJobId = 101,
    jobNo = "SJ-2026-0012",
    canComplete = false,
    completionBlockReason = "Technician Assignment 1 ခု မပြီးသေး",
    assignments = assignments.toList()
)

@Preview(name = "Assignment — လက်ခံရန်", showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun TechnicianAssignmentPendingPreview() {
    AppTheme {
        Surface(modifier = Modifier.padding(12.dp), color = Color.White) {
            TechnicianAssignmentSection(
                team = previewTeam(
                    previewAssignment(1, "ကိုမင်း", "LEAD", "PENDING")
                ),
                teamError = null,
                myStaffId = 7,
                loading = false,
                onAccept = {},
                onReject = { _, _ -> },
                onWork = { _, _, _, _, _, _ -> }
            )
        }
    }
}

@Preview(name = "Assignment — လုပ်ငန်းပြီးစီး", showBackground = true, widthDp = 390, heightDp = 480)
@Composable
private fun TechnicianAssignmentActivePreview() {
    AppTheme {
        Surface(modifier = Modifier.padding(12.dp), color = Color.White) {
            TechnicianAssignmentSection(
                team = previewTeam(
                    previewAssignment(1, "ကိုမင်း", "LEAD", "ACTIVE", workStarted = true),
                    previewAssignment(2, "မောင်လတ်", "HELPER", "COMPLETED", mine = false)
                ),
                teamError = null,
                myStaffId = 7,
                loading = false,
                onAccept = {},
                onReject = { _, _ -> },
                onWork = { _, _, _, _, _, _ -> }
            )
        }
    }
}

@Preview(name = "Assignment — မရှိသေး", showBackground = true, widthDp = 390, heightDp = 220)
@Composable
private fun TechnicianAssignmentEmptyPreview() {
    AppTheme {
        Surface(modifier = Modifier.padding(12.dp), color = Color.White) {
            TechnicianAssignmentSection(
                team = previewTeam(),
                teamError = null,
                myStaffId = 7,
                loading = false,
                onAccept = {},
                onReject = { _, _ -> },
                onWork = { _, _, _, _, _, _ -> }
            )
        }
    }
}

@Preview(name = "လုပ်ငန်းပြီးစီးမှတ်တမ်း", showBackground = true, widthDp = 390, heightDp = 560)
@Composable
private fun CompleteWorkDialogPreview() {
    AppTheme {
        Surface(color = Color.White) {
            CompleteWorkDialog(
                assignment = previewAssignment(1, "ကိုမင်း", "LEAD", "ACTIVE", workStarted = true),
                onDismiss = {},
                onConfirm = { _, _, _, _ -> }
            )
        }
    }
}

@Preview(name = "လုပ်ငန်းမှတ်တမ်းတင်ရန်", showBackground = true, widthDp = 390, heightDp = 560)
@Composable
private fun WorkLogNoteDialogPreview() {
    AppTheme {
        Surface(color = Color.White) {
            WorkLogNoteDialog(
                assignment = previewAssignment(1, "ကိုမင်း", "LEAD", "ACTIVE", workStarted = true),
                onDismiss = {},
                onConfirm = { _, _, _, _ -> }
            )
        }
    }
}
