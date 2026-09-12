package com.sspd.servicemgmt.feature.service.job

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.network.TechnicianVisitDTO
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.util.JobFormDraftStore
import com.sspd.servicemgmt.core.util.PreferenceManager
import org.osmdroid.util.GeoPoint

@Composable
fun TechnicianJobHero(
    job: ServiceJobDTO,
    onCall: () -> Unit,
    onNavigate: () -> Unit,
    canNavigate: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = listOfNotNull(job.customerName, job.itemName).joinToString(" — ").ifBlank { job.jobNo ?: "Job" },
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain
            )
            Text(
                text = listOfNotNull(
                    if (isOutdoorJob(job)) "အပြင်ထွက်အလုပ်" else "ဆိုင်တွင်းအလုပ်",
                    job.estimatedCompletion?.take(16)?.replace("T", " ")
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = TextMuted
            )
            job.problemDesc?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, fontSize = 13.sp, color = TextMain)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCall,
                    enabled = !job.customerPhone.isNullOrBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Call, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ဖုန်းခေါ်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onNavigate,
                    enabled = canNavigate,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Map, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("လမ်းညွှန်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CompactAssignmentCard(
    assignment: AssignmentDTO?,
    loading: Boolean,
    blockedReason: String?,
    onAccept: () -> Unit,
    onMore: () -> Unit
) {
    if (assignment == null) return
    val pending = assignment.status.equals("PENDING", true)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pending) Color(0xFFFEF3C7) else Color(0xFFF0FDF4)
        ),
        border = BorderStroke(
            1.dp,
            if (pending) Color(0xFFFDE68A) else Color(0xFFBBF7D0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (pending) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (pending) Color(0xFFF59E0B) else Color(0xFF16A34A))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (pending) Color(0xFFD97706) else Color(0xFF16A34A))
                        )
                        Text(
                            text = if (pending) "တာဝန်အသစ် ရောက်ရှိပါပြီ" else "တာဝန်လက်ခံပြီးပါပြီ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pending) Color(0xFFB45309) else Color(0xFF15803D)
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(0.8f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = assignment.role ?: "TECHNICIAN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF374151),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                assignment.assignedBy?.takeIf { it.isNotBlank() }?.let { assigner ->
                    Text(
                        text = "တာဝန်ပေးသူ: $assigner",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pending) Color(0xFF92400E) else Color(0xFF14532D)
                    )
                }
                assignment.taskDescription?.takeIf { it.isNotBlank() }?.let { task ->
                    Text(
                        text = "လုပ်ဆောင်ရန်: $task",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                }
            }

            blockedReason?.let { reason ->
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = reason,
                            fontSize = 11.sp,
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (pending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = !loading && blockedReason == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("တာဝန်လက်ခံမည်", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onMore,
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("အခြားရွေးချယ်မှု", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkLogDraftDialog(
    jobId: Int,
    assignment: AssignmentDTO,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (completedWork: String, serviceDetails: String, partsDetails: String, note: String, onSuccess: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }
    val store = remember { JobFormDraftStore(context) }
    val form = "work_log_${assignment.id}"
    val saved = remember { store.load(prefs.staffId, prefs.serverUrl, jobId, form) }
    var completedWork by remember { mutableStateOf(saved["completedWork"].orEmpty()) }
    var serviceDetails by remember { mutableStateOf(saved["serviceDetails"].orEmpty()) }
    var partsDetails by remember { mutableStateOf(saved["partsDetails"].orEmpty()) }
    var note by remember { mutableStateOf(saved["note"].orEmpty()) }
    var restoreAsk by remember { mutableStateOf(saved.isNotEmpty()) }

    LaunchedEffect(completedWork, serviceDetails, partsDetails, note) {
        store.save(
            prefs.staffId, prefs.serverUrl, jobId, form,
            mapOf(
                "completedWork" to completedWork,
                "serviceDetails" to serviceDetails,
                "partsDetails" to partsDetails,
                "note" to note
            )
        )
    }

    if (restoreAsk) {
        AlertDialog(
            onDismissRequest = { restoreAsk = false },
            title = { Text("မသိမ်းရသေးသော မှတ်တမ်းရှိသည်", fontWeight = FontWeight.ExtraBold) },
            text = { Text("ဖုန်းထဲသိမ်းထားသည်။ ဆိုင်စနစ်သို့ မရောက်သေးပါ။ ဆက်ရေးမလား။") },
            confirmButton = { TextButton(onClick = { restoreAsk = false }) { Text("ဆက်ရေးမည်") } },
            dismissButton = {
                TextButton(onClick = {
                    store.clear(prefs.staffId, prefs.serverUrl, jobId, form)
                    completedWork = ""; serviceDetails = ""; partsDetails = ""; note = ""
                    restoreAsk = false
                }) { Text("ဖျက်မည်") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("လုပ်ဆောင်ချက် ဖြည့်မည်", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ဖုန်းထဲ draft အလိုအလျောက် သိမ်းနေသည်", fontSize = 11.sp, color = TextMuted)
                OutlinedTextField(value = serviceDetails, onValueChange = { serviceDetails = it }, label = { Text("ဘာပြဿနာတွေ့သလဲ") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !loading)
                OutlinedTextField(value = completedWork, onValueChange = { completedWork = it }, label = { Text("ဘာလုပ်ခဲ့သလဲ") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !loading)
                OutlinedTextField(value = partsDetails, onValueChange = { partsDetails = it }, label = { Text("ဘာပစ္စည်းသုံးသလဲ") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !loading)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ဘာကျန်သေးသလဲ / မှတ်ချက်") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !loading)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    store.save(prefs.staffId, prefs.serverUrl, jobId, form, mapOf(
                        "completedWork" to completedWork, "serviceDetails" to serviceDetails,
                        "partsDetails" to partsDetails, "note" to note
                    ))
                    onSave(completedWork.trim(), serviceDetails.trim(), partsDetails.trim(), note.trim()) {
                        store.clear(prefs.staffId, prefs.serverUrl, jobId, form)
                        onDismiss()
                    }
                },
                enabled = !loading && listOf(completedWork, serviceDetails, partsDetails, note).any { it.isNotBlank() },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(if (loading) "သိမ်းနေသည်…" else "ဆိုင်စနစ်သို့ သိမ်းမည်") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("မူကြမ်းသိမ်းပြီး ပိတ်မည်") } }
    )
}

@Composable
fun FinalCheckSummaryCard(
    job: ServiceJobDTO,
    team: TeamSnapshotDTO?,
    onFillNote: () -> Unit,
    onOpenWorkLog: () -> Unit,
    onOpenEdit: () -> Unit
) {
    val hasWork = team?.assignments.orEmpty().any { !it.logs.isNullOrEmpty() || !it.workStartedAt.isNullOrBlank() }
    val parts = job.productParts.orEmpty()
    val serialOk = parts.all { part ->
        val need = (part.qty ?: 1) <= 0 || !part.serialNumbers.isNullOrEmpty() || part.serialNumbers == null
        (part.serialNumbers?.size ?: 0) >= 0
    }
    val noteNeeded = team?.canComplete == true && team.leadFinalCheckStatus != true
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("စစ်ဆေးရန် မတင်မီ", fontWeight = FontWeight.ExtraBold, color = Color(0xFF065F46))
            CheckLine(hasWork, "လုပ်ဆောင်ချက် ဖြည့်ပြီး", onOpenWorkLog)
            CheckLine(parts.isNotEmpty(), if (parts.isEmpty()) "သုံးပစ္စည်း မရှိသေး / မလိုအပ်နိုင်" else "သုံးပစ္စည်း ${parts.size} မျိုး", onOpenEdit)
            CheckLine(serialOk, "Serial ရွေးပြီး", onOpenEdit)
            CheckLine(!noteNeeded, "Final Check မှတ်ချက်", onFillNote)
            team?.finalReturnReason?.takeIf { it.isNotBlank() }?.let {
                Text("ပြန်ပြင်ရန်: $it", fontSize = 12.sp, color = Danger, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CheckLine(ok: Boolean, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text("${if (ok) "✓" else "!"} $text", color = if (ok) Color(0xFF047857) else Warning, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianMoreActionsSheet(
    job: ServiceJobDTO,
    canHandover: Boolean,
    sentPending: Boolean,
    onDismiss: () -> Unit,
    onHold: () -> Unit,
    onHandover: () -> Unit,
    onNotify: () -> Unit,
    onEdit: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("အခြားလုပ်ဆောင်ချက်များ", fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = { onDismiss(); onHold() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("အလုပ်ခဏရပ်မည် / ပစ္စည်းစောင့်")
            }
            TextButton(
                onClick = { if (!sentPending) { onDismiss(); onHandover() } },
                enabled = canHandover && !sentPending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if (sentPending) "သင်လွှဲပေးထားသည် — လက်ခံချက်စောင့်နေသည်" else "တာဝန်လွှဲမည်")
            }
            TextButton(onClick = { onDismiss(); onNotify() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Supervisor / ဖောက်သည် ဆက်သွယ်မည်")
            }
            TextButton(onClick = { onDismiss(); onEdit() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("အလုပ်အသေးစိတ်ပြင်မည်")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

fun openCustomerDial(context: android.content.Context, phone: String?) {
    val number = phone?.trim().orEmpty()
    if (number.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
}

fun openCustomerNavigation(context: android.content.Context, lat: Double?, lng: Double?) {
    if (lat == null || lng == null) return
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng")).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (appIntent.resolveActivity(context.packageManager) != null) context.startActivity(appIntent)
    else context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
    )
}

fun jobDestination(job: ServiceJobDTO, visit: TechnicianVisitDTO?): GeoPoint? {
    val lat = job.customerLatitude ?: visit?.customerLatitude
    val lng = job.customerLongitude ?: visit?.customerLongitude
    return if (lat != null && lng != null) GeoPoint(lat, lng) else null
}

internal fun previewWorkJob(
    status: String = "RECEIVED",
    outdoor: Boolean = true,
    assignmentStatus: String = "PENDING",
    leadFinal: Boolean? = null,
): ServiceJobDTO = ServiceJobDTO(
    id = 125,
    jobNo = "SJ-00125",
    customerName = "ဦးအောင်",
    customerPhone = "09 123 456 789",
    itemName = "Laptop ဖွင့်မရ",
    serviceMode = if (outdoor) "OUTDOOR" else "INDOOR",
    status = status,
    problemDesc = "Power နှိပ်လျှင် မီးမလင်းပါ",
    estimatedCompletion = "2026-09-09T14:00:00",
    customerLatitude = 16.798,
    customerLongitude = 96.137,
    myAssignmentStatus = assignmentStatus,
    myAssignmentRole = "LEAD",
    leadFinalCheckStatus = leadFinal,
    estimatedCost = 45000.0,
    estimateApproved = true,
)

internal fun previewWorkAssignment(status: String = "PENDING") = AssignmentDTO(
    id = 11,
    staffId = 7,
    staffName = "ကိုမင်း",
    role = "LEAD",
    status = status,
    assignedBy = "ကိုမင်း",
    taskDescription = "စက်စစ်ဆေးပြီး ပြုပြင်ရန်",
    mine = true,
    workStartedAt = if (status == "ACTIVE") "2026-09-09T09:00:00" else null,
)

@Composable
internal fun TechnicianWorkScreenPreviewPane(
    job: ServiceJobDTO,
    assignment: AssignmentDTO?,
    primary: WorkPrimaryAction,
    showFinal: Boolean = false,
    loading: Boolean = false,
) {
    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        TabRow(selectedTabIndex = 0, containerColor = CardBg) {
            listOf("အလုပ်လုပ်ရန်", "အသေးစိတ်", "မှတ်တမ်း").forEachIndexed { i, label ->
                Tab(selected = i == 0, onClick = {}, text = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) })
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TechnicianJobHero(job = job, onCall = {}, onNavigate = {}, canNavigate = job.customerLatitude != null)
            if (primary.kind != WorkPrimaryKind.ACCEPT_ASSIGNMENT) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("လက်ရှိအဆင့်", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF312E81))
                        Text(primary.stepTitle, fontWeight = FontWeight.ExtraBold, color = TextMain)
                        Text(primary.stepHint, fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
            CompactAssignmentCard(
                assignment = assignment,
                loading = loading,
                blockedReason = primary.blockedReason,
                onAccept = {},
                onMore = {}
            )
            if (showFinal) {
                FinalCheckSummaryCard(
                    job = job,
                    team = TeamSnapshotDTO(canComplete = true, leadFinalCheckStatus = false, assignments = listOfNotNull(assignment)),
                    onFillNote = {},
                    onOpenWorkLog = {},
                    onOpenEdit = {}
                )
            }
        }
        if (primary.kind != WorkPrimaryKind.ACCEPT_ASSIGNMENT) {
            Surface(shadowElevation = 4.dp, color = CardBg) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("အခြား", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {},
                            enabled = !loading && primary.blockedReason == null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("သိမ်းနေသည်…", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Text(primary.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Work — လက်ခံရန်", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun WorkAcceptPreview() {
    val job = previewWorkJob()
    val assignment = previewWorkAssignment("PENDING")
    AppTheme {
        TechnicianWorkScreenPreviewPane(
            job = job,
            assignment = assignment,
            primary = resolveWorkPrimaryAction(job, TeamSnapshotDTO(assignments = listOf(assignment)), null, 7, true, false)
        )
    }
}

@Preview(name = "Work — ထွက်ခွာမည်", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun WorkDepartPreview() {
    val job = previewWorkJob(assignmentStatus = "ACTIVE")
    val assignment = previewWorkAssignment("ACTIVE")
    AppTheme {
        TechnicianWorkScreenPreviewPane(
            job = job,
            assignment = assignment,
            primary = resolveWorkPrimaryAction(job, TeamSnapshotDTO(assignments = listOf(assignment)), null, 7, true, false)
        )
    }
}

@Preview(name = "Work — ရောက်ရှိပြီ", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun WorkArrivePreview() {
    val job = previewWorkJob(status = "INSPECTING", assignmentStatus = "ACTIVE")
    val assignment = previewWorkAssignment("ACTIVE")
    val visit = TechnicianVisitDTO(id = 1, jobId = 125, status = "EN_ROUTE", motionStatus = "MOVING", distanceMeters = 420.0)
    AppTheme {
        TechnicianWorkScreenPreviewPane(
            job = job,
            assignment = assignment,
            primary = resolveWorkPrimaryAction(job, TeamSnapshotDTO(assignments = listOf(assignment)), visit, 7, true, false)
        )
    }
}

@Preview(name = "Work — လုပ်နေဆဲ", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun WorkLogPreview() {
    val job = previewWorkJob(status = "IN_PROGRESS", outdoor = false, assignmentStatus = "ACTIVE")
    val assignment = previewWorkAssignment("ACTIVE")
    AppTheme {
        TechnicianWorkScreenPreviewPane(
            job = job,
            assignment = assignment,
            primary = resolveWorkPrimaryAction(job, TeamSnapshotDTO(assignments = listOf(assignment)), null, 7, false, false)
        )
    }
}

@Preview(name = "Work — Final Check", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun WorkFinalPreview() {
    val job = previewWorkJob(status = "IN_PROGRESS", outdoor = false, assignmentStatus = "ACTIVE", leadFinal = false)
    val assignment = previewWorkAssignment("ACTIVE")
    val team = TeamSnapshotDTO(canComplete = true, leadFinalCheckStatus = false, assignments = listOf(assignment))
    AppTheme {
        TechnicianWorkScreenPreviewPane(
            job = job,
            assignment = assignment,
            primary = resolveWorkPrimaryAction(job, team, null, 7, false, false),
            showFinal = true
        )
    }
}

@Preview(name = "Work — saving", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun WorkSavingPreview() {
    val job = previewWorkJob()
    val assignment = previewWorkAssignment("PENDING")
    AppTheme {
        TechnicianWorkScreenPreviewPane(
            job = job,
            assignment = assignment,
            primary = resolveWorkPrimaryAction(job, TeamSnapshotDTO(assignments = listOf(assignment)), null, 7, true, false),
            loading = true
        )
    }
}

@Preview(name = "Hero + ဖုန်း/လမ်းညွှန်", showBackground = true, widthDp = 390)
@Composable
private fun JobHeroPreview() {
    AppTheme {
        Box(Modifier.background(ScreenBg).padding(16.dp)) {
            TechnicianJobHero(job = previewWorkJob(), onCall = {}, onNavigate = {}, canNavigate = true)
        }
    }
}

@Preview(name = "Assignment card — လက်ခံရန်", showBackground = true, widthDp = 390)
@Composable
private fun CompactAssignmentPreview() {
    AppTheme {
        Box(Modifier.background(ScreenBg).padding(16.dp)) {
            CompactAssignmentCard(previewWorkAssignment(), loading = false, blockedReason = null, onAccept = {}, onMore = {})
        }
    }
}

@Preview(name = "Final Check အနှစ်ချုပ်", showBackground = true, widthDp = 390)
@Composable
private fun FinalCheckSummaryPreview() {
    AppTheme {
        Box(Modifier.background(ScreenBg).padding(16.dp)) {
            FinalCheckSummaryCard(
                job = previewWorkJob(status = "IN_PROGRESS", outdoor = false),
                team = TeamSnapshotDTO(canComplete = true, leadFinalCheckStatus = false, finalReturnReason = "Serial ပြန်စစ်ပါ"),
                onFillNote = {},
                onOpenWorkLog = {},
                onOpenEdit = {}
            )
        }
    }
}

