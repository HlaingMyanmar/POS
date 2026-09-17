package com.sspd.servicemgmt.feature.service.job

import com.sspd.servicemgmt.core.network.AssignmentDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.network.TeamSnapshotDTO
import com.sspd.servicemgmt.core.network.TechnicianVisitDTO

enum class WorkPrimaryKind {
    ACCEPT_ASSIGNMENT,
    ACCEPT_HANDOVER,
    DEPART,
    ARRIVE,
    START_WORK,
    LOG_WORK,
    SUBMIT_FINAL,
    APPROVE_FINAL,
    VIEW_RETURN,
    REFRESH,
    SETTLE,
    NONE
}

data class WorkPrimaryAction(
    val kind: WorkPrimaryKind,
    val label: String,
    val stepTitle: String,
    val stepHint: String,
    val assignmentId: Int? = null,
    val handoverId: Int? = null,
    val blockedReason: String? = null
)

fun myAssignment(team: TeamSnapshotDTO?, job: ServiceJobDTO, myStaffId: Int): AssignmentDTO? {
    val mine = team?.assignments.orEmpty().firstOrNull { it.mine || (myStaffId > 0 && it.staffId == myStaffId) }
    return mine
}

fun isOutdoorJob(job: ServiceJobDTO): Boolean =
    job.serviceMode.equals("OUTDOOR", ignoreCase = true)

fun resolveWorkPrimaryAction(
    job: ServiceJobDTO,
    team: TeamSnapshotDTO?,
    visit: TechnicianVisitDTO?,
    myStaffId: Int,
    canStartVisit: Boolean,
    canSupervise: Boolean
): WorkPrimaryAction {
    val status = job.status?.uppercase().orEmpty()
    if (status in setOf("CANCELLED")) {
        return WorkPrimaryAction(WorkPrimaryKind.NONE, "", "ပယ်ဖျက်ထားသည်", "ဤအလုပ်ကို ဆက်မလုပ်နိုင်ပါ")
    }

    val assignment = myAssignment(team, job, myStaffId)
    val pendingHandover = team?.myPendingHandovers.orEmpty().firstOrNull { it.status.equals("PENDING", true) }
        ?: if (job.pendingHandoverForMe == true) team?.handovers.orEmpty().firstOrNull {
            it.status.equals("PENDING", true) && (it.targetMine || it.toStaffId == myStaffId)
        } else null

    if (pendingHandover?.id != null) {
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.ACCEPT_HANDOVER,
            label = "အလုပ်လက်ခံမည်",
            stepTitle = "တာဝန်လွှဲလာသည် — လက်ခံရန်",
            stepHint = listOfNotNull(pendingHandover.fromStaffName?.let { "$it ထံမှ" }, pendingHandover.remainingWork)
                .joinToString(" · ").ifBlank { "Hand Over ကို လက်ခံပြီးမှ ဆက်လုပ်ပါ" },
            handoverId = pendingHandover.id
        )
    }

    val approvalPending = assignment?.role.equals("HELPER", true)
        && assignment?.approvalStatus.equals("PENDING", true)
    if (assignment?.status.equals("PENDING", true)) {
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.ACCEPT_ASSIGNMENT,
            label = "အလုပ်လက်ခံမည်",
            stepTitle = "တာဝန်အသစ်ရောက် — အလုပ်လက်ခံရန်",
            stepHint = assignment?.taskDescription?.takeIf { it.isNotBlank() }
                ?: "Assignment လက်ခံပြီးမှ နောက်အဆင့် ပေါ်ပါမည်",
            assignmentId = assignment?.id,
            blockedReason = if (approvalPending) "Supervisor အတည်ပြုပြီးမှ လက်ခံနိုင်ပါမယ်" else null
        )
    }

    val jobVisit = visit?.takeIf { it.jobId == job.id }
    val otherVisit = visit?.takeIf { it.jobId != null && it.jobId != job.id }
    val accepted = assignment?.status?.uppercase() in setOf("ACTIVE", "PAUSED")
        || job.myAssignmentStatus?.uppercase() in setOf("ACTIVE", "PAUSED", "ACCEPTED")

    if (isOutdoorJob(job) && accepted && canStartVisit) {
        if (otherVisit != null && jobVisit == null) {
            return WorkPrimaryAction(
                kind = WorkPrimaryKind.NONE,
                label = "",
                stepTitle = "တခြား Job သို့ သွားနေသည်",
                stepHint = "${otherVisit.jobNo ?: otherVisit.jobId} ကို အရင် ပြီးအောင်လုပ်ပါ",
                blockedReason = "လက်ရှိ Visit မပြီးသေးပါ"
            )
        }
        if (jobVisit == null) {
            return WorkPrimaryAction(
                kind = WorkPrimaryKind.DEPART,
                label = "ထွက်ခွာမည်",
                stepTitle = "အလုပ်လက်ခံပြီး — ထွက်ခွာရန် အသင့်",
                stepHint = "Job screen ကနေ ခရီးစတင်နိုင်သည်။ လိုမှ မြေပုံဖွင့်ပါ။"
            )
        }
        when (jobVisit.status) {
            "EN_ROUTE" -> return WorkPrimaryAction(
                kind = WorkPrimaryKind.ARRIVE,
                label = "ရောက်ရှိပြီ",
                stepTitle = "လမ်းခရီးတွင်",
                stepHint = listOfNotNull(jobVisit.motionStatus, jobVisit.distanceMeters?.let { "${it.toInt()} m" })
                    .joinToString(" · ").ifBlank { "Customer ဆီ ရောက်လျှင် မှတ်တမ်းတင်ပါ" }
            )
            "ON_SITE" -> { /* fall through to start/log */ }
            "RETURNING" -> return WorkPrimaryAction(
                kind = WorkPrimaryKind.REFRESH,
                label = "နောက်ဆုံးအခြေအနေ စစ်မည်",
                stepTitle = "Customer ဆီမှ ပြန်လာနေသည်",
                stepHint = "ဆိုင်ရောက်လျှင် Visit ကို ပြီးဆုံးအောင်လုပ်ပါ"
            )
        }
    }

    if (!team?.finalReturnReason.isNullOrBlank() && team?.leadFinalCheckStatus != true) {
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.VIEW_RETURN,
            label = "ပြန်ပြင်ရမည့်အချက် ကြည့်မည်",
            stepTitle = "ပြန်ပြင်ခိုင်းထားသည်",
            stepHint = team?.finalReturnReason.orEmpty()
        )
    }

    val mayLeadCheck = team?.canComplete == true && team.leadFinalCheckStatus != true
        && (assignment?.role.equals("LEAD", true) || canSupervise)
    if (mayLeadCheck) {
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.SUBMIT_FINAL,
            label = "စစ်ဆေးရန် တင်မည်",
            stepTitle = "ပြုပြင်ပြီး — စစ်ဆေးရန် တင်ရန်",
            stepHint = team?.completionBlockReason?.takeIf { it.isNotBlank() }
                ?: "လုပ်ခဲ့တာပြန်စစ်ပြီး Lead Final Check တင်ပါ"
        )
    }

    val pendingSupervisor = team?.leadFinalCheckStatus == true
        && team.supervisorApprovalRequired && !team.finalApprovalStatus
    if (pendingSupervisor) {
        return if (canSupervise) WorkPrimaryAction(
            kind = WorkPrimaryKind.APPROVE_FINAL,
            label = "စစ်ဆေးရန် တင်မည်",
            stepTitle = "Supervisor အတည်ပြုရန်",
            stepHint = "Lead Final Check ရောက်ပြီးပါပြီ"
        ) else WorkPrimaryAction(
            kind = WorkPrimaryKind.REFRESH,
            label = "နောက်ဆုံးအခြေအနေ စစ်မည်",
            stepTitle = "အတည်ပြုချက် စောင့်နေသည်",
            stepHint = "Supervisor အတည်ပြုပြီးမှ ဆက်လုပ်ပါ"
        )
    }

    if (status == "COMPLETED") {
        val unpaid = job.paymentStatus.isNullOrBlank() || (job.dueAmount ?: 0.0) > 0
        if (unpaid) {
            return WorkPrimaryAction(
                kind = WorkPrimaryKind.SETTLE,
                label = "နောက်ဆုံးအခြေအနေ စစ်မည်",
                stepTitle = "ပြီးစီးပြီး — ငွေရှင်း / လွှဲအပ်ရန်",
                stepHint = "ငွေရှင်းမှုကို အသေးစိတ် tab မှ ဆက်လုပ်ပါ"
            )
        }
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.NONE,
            label = "",
            stepTitle = "အတည်ပြုပြီး — လွှဲအပ်ရန်",
            stepHint = "ပစ္စည်းပြန်ပေးရန် အသေးစိတ် tab မှ ဆက်လုပ်ပါ"
        )
    }

    if (status == "DELIVERED") {
        return WorkPrimaryAction(WorkPrimaryKind.NONE, "", "ပစ္စည်းပြန်ပေးပြီး", "Job ပြီးဆုံးပါပြီ")
    }

    val needsStart = assignment?.status.equals("ACTIVE", true)
        && assignment?.workStartedAt.isNullOrBlank()
        && status in setOf("RECEIVED", "ASSIGNED", "INSPECTING", "IN_PROGRESS", "")
    if (needsStart || (accepted && status in setOf("RECEIVED", "ASSIGNED"))) {
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.START_WORK,
            label = "အလုပ်စလုပ်မည်",
            stepTitle = if (isOutdoorJob(job)) "နေရာရောက် — အလုပ်စလုပ်ရန်" else "ဆိုင်တွင်းအလုပ် — စလုပ်ရန်",
            stepHint = "လုပ်ငန်းစချိန် မှတ်တမ်းတင်ပါ",
            assignmentId = assignment?.id
        )
    }

    if (status == "WAITING_PARTS" || job.lines.orEmpty().any { it.confirmationStatus == "CUSTOMER_HOLD" }
        || ((job.estimatedCost ?: 0.0) > 0 && job.estimateApproved != true && status == "INSPECTING")
    ) {
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.REFRESH,
            label = "နောက်ဆုံးအခြေအနေ စစ်မည်",
            stepTitle = when {
                status == "WAITING_PARTS" -> "ပစ္စည်းစောင့်နေသည်"
                job.lines.orEmpty().any { it.confirmationStatus == "CUSTOMER_HOLD" } -> "Customer အတည်ပြုချက် စောင့်နေသည်"
                else -> "Estimate အတည်ပြုချက် စောင့်နေသည်"
            },
            stepHint = job.holdReason?.takeIf { it.isNotBlank() } ?: "အခြေအနေ ပြောင်းလျှင် ပြန်ဖတ်ပါ"
        )
    }

    if (accepted || status in setOf("INSPECTING", "IN_PROGRESS")) {
        return WorkPrimaryAction(
            kind = WorkPrimaryKind.LOG_WORK,
            label = "လုပ်ဆောင်ချက် ဖြည့်မည်",
            stepTitle = "လုပ်နေဆဲ",
            stepHint = "စစ်ဆေးချက်၊ ပြုပြင်ချက်၊ သုံးပစ္စည်းကို မှတ်တမ်းတင်ပါ",
            assignmentId = assignment?.id
        )
    }

    return WorkPrimaryAction(
        kind = WorkPrimaryKind.REFRESH,
        label = "နောက်ဆုံးအခြေအနေ စစ်မည်",
        stepTitle = "Job အချက်အလက် စစ်ပါ",
        stepHint = "Job အချက်အလက်ကို စစ်ဆေးပါ"
    )
}

fun technicianBucket(job: ServiceJobDTO): TechnicianHomeBucket {
    val status = job.status?.uppercase().orEmpty()
    if (job.pendingHandoverForMe == true || job.myAssignmentStatus.equals("PENDING", true)) {
        return TechnicianHomeBucket.ACCEPT
    }
    if (!job.finalReturnReason.isNullOrBlank() && job.leadFinalCheckStatus != true) {
        return TechnicianHomeBucket.FINAL
    }
    if (job.leadFinalCheckStatus != true && status in setOf("IN_PROGRESS", "COMPLETED")
        && job.myAssignmentRole.equals("LEAD", true)
    ) {
        return TechnicianHomeBucket.FINAL
    }
    if (job.leadFinalCheckStatus == true && job.supervisorApprovalRequired == true && job.finalApprovalStatus != true) {
        return TechnicianHomeBucket.FINAL
    }
    if (status == "WAITING_PARTS" || job.lines.orEmpty().any { it.confirmationStatus == "CUSTOMER_HOLD" }
        || ((job.estimatedCost ?: 0.0) > 0 && job.estimateApproved != true && status in setOf("RECEIVED", "INSPECTING"))
    ) {
        return TechnicianHomeBucket.WAITING
    }
    if (status in setOf("IN_PROGRESS", "INSPECTING") || job.myAssignmentStatus.equals("ACTIVE", true)) {
        return TechnicianHomeBucket.ACTIVE
    }
    if (status == "COMPLETED") return TechnicianHomeBucket.FINAL
    return TechnicianHomeBucket.TODAY
}

enum class TechnicianHomeBucket { ACCEPT, TODAY, ACTIVE, WAITING, FINAL, HANDOVER }

fun technicianBucketLabel(bucket: TechnicianHomeBucket): String = when (bucket) {
    TechnicianHomeBucket.ACCEPT -> "လက်ခံရန်"
    TechnicianHomeBucket.TODAY -> "ယနေ့လုပ်ရန်"
    TechnicianHomeBucket.ACTIVE -> "လုပ်ဆောင်နေဆဲ"
    TechnicianHomeBucket.WAITING -> "စောင့်ဆိုင်းနေသည်"
    TechnicianHomeBucket.FINAL -> "စစ်ဆေးရန် / ပြီးစီးပြီး"
    TechnicianHomeBucket.HANDOVER -> "Hand Over"
}

fun technicianStepLabel(job: ServiceJobDTO): String {
    if (job.pendingHandoverForMe == true) return "တာဝန်လွှဲလာသည် — လက်ခံရန်"
    if (job.myAssignmentStatus.equals("PENDING", true)) return "အလုပ်လက်ခံရန်"
    return when (job.status?.uppercase()) {
        "RECEIVED", "ASSIGNED" -> "အလုပ်စလုပ်ရန်"
        "INSPECTING" -> "စစ်ဆေးဆဲ"
        "IN_PROGRESS" -> "လုပ်နေဆဲ"
        "WAITING_PARTS" -> "ပစ္စည်းစောင့်နေသည်"
        "COMPLETED" -> if (job.leadFinalCheckStatus == true) "ပြီးစီးပြီး" else "စစ်ဆေးရန် တင်ရန်"
        "DELIVERED" -> "ပြန်ပေးပြီး"
        else -> job.status ?: "—"
    }
}

object TechnicianQueueFocus {
    @Volatile var bucket: TechnicianHomeBucket? = null
}
