package com.sspd.servicemgmt.feature.service.job

import com.sspd.servicemgmt.core.network.ServiceJobDTO

typealias WorkTab = String

const val WORK_TAB_ACTIVE   = "active"
const val WORK_TAB_PAYMENT  = "payment"
const val WORK_TAB_HANDOVER = "handover"
const val WORK_TAB_TRANSFER = "transfer"
const val WORK_TAB_SENT     = "sent"
const val WORK_TAB_CLOSED   = "closed"
const val WORK_TAB_ALL      = "all"

private val ACTIVE_STATUSES = setOf("RECEIVED", "ASSIGNED", "INSPECTING", "IN_PROGRESS", "WAITING_PARTS")
private val CLOSED_STATUSES = setOf("COMPLETED", "DELIVERED", "CLOSED", "CANCELLED")

fun needsPayment(job: ServiceJobDTO): Boolean =
    job.status == "COMPLETED" && (job.paymentStatus.isNullOrBlank() || (job.dueAmount ?: 0.0) > 0)

fun readyForHandover(job: ServiceJobDTO): Boolean =
    job.status == "COMPLETED" && !job.paymentStatus.isNullOrBlank() && (job.dueAmount ?: 0.0) <= 0

fun pendingHandover(job: ServiceJobDTO): Boolean = job.pendingHandoverForMe == true

fun workQueueCounts(jobs: List<ServiceJobDTO>, sentPending: Int = 0): Map<WorkTab, Int> = mapOf(
    WORK_TAB_TRANSFER  to jobs.count { pendingHandover(it) },
    WORK_TAB_SENT      to sentPending,
    WORK_TAB_ACTIVE   to jobs.count { it.status?.uppercase() in ACTIVE_STATUSES },
    WORK_TAB_PAYMENT  to jobs.count { needsPayment(it) },
    WORK_TAB_HANDOVER to jobs.count { readyForHandover(it) },
    WORK_TAB_CLOSED   to jobs.count { it.status?.uppercase() in CLOSED_STATUSES }
)

fun filterByWorkTab(jobs: List<ServiceJobDTO>, tab: WorkTab): List<ServiceJobDTO> = when (tab) {
    WORK_TAB_TRANSFER -> jobs.filter { pendingHandover(it) }
    WORK_TAB_ACTIVE   -> jobs.filter { it.status?.uppercase() in ACTIVE_STATUSES }
    WORK_TAB_PAYMENT  -> jobs.filter { needsPayment(it) }
    WORK_TAB_HANDOVER -> jobs.filter { readyForHandover(it) }
    WORK_TAB_CLOSED   -> jobs.filter { it.status?.uppercase() in CLOSED_STATUSES }
    else              -> jobs
}

fun workTabIgnoresDateFilter(tab: WorkTab): Boolean =
    tab == WORK_TAB_ACTIVE || tab == WORK_TAB_PAYMENT || tab == WORK_TAB_HANDOVER || tab == WORK_TAB_TRANSFER || tab == WORK_TAB_SENT
