package com.sspd.servicemgmt.feature.home

import com.sspd.servicemgmt.core.network.CustomerOrder

internal fun moneyValue(amount: Double?): Double {
    val safe = amount?.takeIf { it.isFinite() } ?: 0.0
    return if (safe < 0) 0.0 else safe
}

internal fun isRemainderReady(order: CustomerOrder, remaining: Double): Boolean {
    val state = order.paymentState?.trim()?.uppercase().orEmpty()
    return state == "DEPOSIT_PAID" &&
        remaining > 0.0 &&
        (order.orderType != "DELIVERY" ||
            order.deliveryHandler.equals("HANDOFF", ignoreCase = true) ||
            order.deliveryStatus in setOf(
                "HANDED_TO_RIDER",
                "OUT_FOR_DELIVERY",
                "IN_TRANSIT",
                "DELIVERED"
            ))
}

/** Amount the customer must transfer now: deposit only, or the remaining balance. */
internal fun orderPayNowAmount(order: CustomerOrder): Double {
    val state = order.paymentState?.trim()?.uppercase().orEmpty()
    val total = moneyValue(order.total)
    val deposit = moneyValue(order.depositAmount)
    val remaining = moneyValue(order.remainingAmount ?: (total - deposit))
    val remainderSubmitted = state in setOf("REMAINDER_PROOF_SUBMITTED", "REMAINDER_CHECKING")
    val remainderConfirmed =
        state in setOf("PAID", "FULFILLED") && moneyValue(order.collectionAmount) > 0.0
    val depositTransferred = state in setOf(
        "DEPOSIT_PAID",
        "REMAINDER_PROOF_SUBMITTED",
        "REMAINDER_CHECKING",
        "PAID",
        "FULFILLED",
        "PROOF_SUBMITTED",
        "CHECKING",
        "REVIEW",
        "LATE_REVIEW"
    )
    return when {
        state in setOf("PAID", "FULFILLED") || remainderConfirmed || remainderSubmitted -> 0.0
        isRemainderReady(order, remaining) -> remaining
        depositTransferred && deposit > 0.0 -> remaining
        depositTransferred -> 0.0
        deposit > 0.0 -> deposit
        else -> total
    }
}
