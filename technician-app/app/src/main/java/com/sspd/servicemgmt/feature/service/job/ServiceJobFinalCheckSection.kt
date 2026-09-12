package com.sspd.servicemgmt.feature.service.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.ui.theme.*

@Composable
fun ServiceJobFinalCheckSection(
    team: TeamSnapshotDTO?,
    loading: Boolean,
    canSupervise: Boolean,
    onLeadFinalCheck: (String) -> Unit,
    onApproveFinal: () -> Unit,
    onReturnForRework: (String) -> Unit
) {
  if (team == null) return
  val lead = team.assignments.orEmpty().firstOrNull { it.role.equals("LEAD", true) }
  val checked = team.leadFinalCheckStatus
  val mayLeadCheck = team.canComplete && !checked && (lead?.mine == true || canSupervise)
  val pendingSupervisor = checked && team.supervisorApprovalRequired && !team.finalApprovalStatus

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
  ) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Final Completion", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF065F46))
      FinalCheckStep(ok = team.canComplete, text = "Member/Helper နှင့် Lead Assignment အားလုံး Completed")
      FinalCheckStep(ok = checked, text = "Lead Technician Final Check")
      if (team.supervisorApprovalRequired) {
        FinalCheckStep(ok = team.finalApprovalStatus, text = if (pendingSupervisor) "Supervisor အတည်ပြုချက်စောင့်နေ" else "Supervisor Approval")
      }
      team.finalReturnReason?.takeIf { it.isNotBlank() && !checked }?.let {
        Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
          Text("ပြန်ပြင်ရန်: $it", modifier = Modifier.padding(10.dp), fontSize = 12.sp, color = Danger, fontWeight = FontWeight.Bold)
        }
      }
      if (checked) {
        Text(
          "${team.leadFinalCheckedBy ?: "Lead Technician"} · ${team.leadFinalCheckNote ?: "Final check completed"}",
          fontSize = 11.sp, color = Color(0xFF047857)
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mayLeadCheck) {
          var showDialog by remember { mutableStateOf(false) }
          Button(
            onClick = { showDialog = true },
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Color.White, disabledContainerColor = BorderColor, disabledContentColor = TextMuted),
            modifier = Modifier.height(40.dp)
          ) { Text("Lead Final Check တင်မည်", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
          if (showDialog) {
            NoteInputDialog(
              title = "Lead Technician Final Check",
              label = "မှတ်ချက်",
              onDismiss = { showDialog = false },
              onConfirm = { note -> showDialog = false; onLeadFinalCheck(note) }
            )
          }
        }
        if (pendingSupervisor && canSupervise) {
          var showReturn by remember { mutableStateOf(false) }
          Button(
            onClick = onApproveFinal,
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White, disabledContainerColor = BorderColor, disabledContentColor = TextMuted),
            modifier = Modifier.height(40.dp)
          ) { Text("Supervisor အတည်ပြုမည်", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
          OutlinedButton(
            onClick = { showReturn = true },
            enabled = !loading,
            modifier = Modifier.height(40.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
            border = BorderStroke(1.dp, Danger)
          ) { Text("ပြန်ပြင်ရန်", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
          if (showReturn) {
            NoteInputDialog(
              title = "ပြန်ပြင်ရမည့်အကြောင်း",
              label = "အကြောင်းပြချက် *",
              required = true,
              onDismiss = { showReturn = false },
              onConfirm = { reason -> showReturn = false; onReturnForRework(reason) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FinalCheckStep(ok: Boolean, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Surface(
      shape = RoundedCornerShape(50),
      color = if (ok) Success else Color.White,
      border = if (!ok) BorderStroke(1.dp, BorderColor) else null,
      modifier = Modifier.size(24.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(if (ok) "✓" else "·", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (ok) Color.White else TextMuted)
      }
    }
    Text(text, fontSize = 12.sp, fontWeight = if (ok) FontWeight.Bold else FontWeight.Medium, color = if (ok) Color(0xFF065F46) else TextMuted)
  }
}

@Composable
fun CompleteWorkDialog(
  assignment: AssignmentDTO,
  onDismiss: () -> Unit,
  onConfirm: (completedWork: String, serviceDetails: String, partsDetails: String, note: String) -> Unit
) {
  var completedWork by remember { mutableStateOf("") }
  var serviceDetails by remember { mutableStateOf("") }
  var partsDetails by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("လုပ်ငန်းပြီးစီးမှတ်တမ်း", fontWeight = FontWeight.ExtraBold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(assignment.staffName.orEmpty(), fontSize = 12.sp, color = TextMuted)
        OutlinedTextField(
          value = completedWork,
          onValueChange = { completedWork = it },
          label = { Text("လုပ်ပြီးသောအလုပ် *") },
          placeholder = { Text("ပြုပြင်ပြီးသောအလုပ်ကို ရေးပါ") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 2
        )
        OutlinedTextField(
          value = serviceDetails,
          onValueChange = { serviceDetails = it },
          label = { Text("Service အသေးစိတ်") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 2
        )
        OutlinedTextField(
          value = partsDetails,
          onValueChange = { partsDetails = it },
          label = { Text("အသုံးပြုသော Parts") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 2
        )
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("အခြားမှတ်ချက်") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(completedWork.trim(), serviceDetails.trim(), partsDetails.trim(), note.trim()) },
        enabled = completedWork.isNotBlank()
      ) { Text("ပြီးစီးမည်", fontWeight = FontWeight.Bold) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") } }
  )
}

@Composable
fun WorkLogNoteDialog(
  assignment: AssignmentDTO,
  onDismiss: () -> Unit,
  onConfirm: (completedWork: String, serviceDetails: String, partsDetails: String, note: String) -> Unit
) {
  var completedWork by remember { mutableStateOf("") }
  var serviceDetails by remember { mutableStateOf("") }
  var partsDetails by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val canSave = listOf(completedWork, serviceDetails, partsDetails, note).any { it.isNotBlank() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("လုပ်ငန်းမှတ်တမ်းတင်ရန်", fontWeight = FontWeight.ExtraBold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(assignment.staffName.orEmpty(), fontSize = 12.sp, color = TextMuted)
        OutlinedTextField(value = completedWork, onValueChange = { completedWork = it }, label = { Text("လုပ်ပြီးသောအလုပ်") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(value = serviceDetails, onValueChange = { serviceDetails = it }, label = { Text("Service အသေးစိတ်") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(value = partsDetails, onValueChange = { partsDetails = it }, label = { Text("အသုံးပြုသော Parts") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("အခြားမှတ်ချက်") }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(completedWork.trim(), serviceDetails.trim(), partsDetails.trim(), note.trim()) },
        enabled = canSave
      ) { Text("မှတ်တမ်းတင်မည်", fontWeight = FontWeight.Bold) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") } }
  )
}

@Composable
fun NoteInputDialog(
  title: String,
  label: String,
  required: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  var text by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, fontWeight = FontWeight.ExtraBold) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3
      )
    },
    confirmButton = {
      Button(onClick = { onConfirm(text.trim()) }, enabled = !required || text.isNotBlank()) {
        Text("အိုကေ", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") } }
  )
}
