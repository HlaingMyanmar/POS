package com.sspd.servicemgmt.core.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sspd.servicemgmt.CustomerMainActivity
import com.sspd.servicemgmt.R

object CustomerOrderAlerts {
    const val EXTRA_ORDER_ID = "focus_order_id"
    const val EXTRA_OPEN_ORDERS = "open_orders_tab"

    fun show(context: Context, notification: CustomerNotification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("customer_orders", "Order updates", NotificationManager.IMPORTANCE_HIGH)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val cancelled = notification.status.equals("CANCELLED", ignoreCase = true)
            || notification.status.equals("EXPIRED", ignoreCase = true)
        val title = when {
            cancelled -> if (notification.status.equals("EXPIRED", ignoreCase = true)
                || notification.note?.contains("ပြန်မှာယူ") == true
            ) "ငွေလွှဲချိန်ကုန်ပြီ" else "အော်ဒါ ပယ်ဖျက်ခံရသည်"
            notification.status.equals("CONFIRMED", ignoreCase = true) -> "အော်ဒါ လက်ခံပြီး"
            notification.status.equals("NOT_RECEIVED", ignoreCase = true) -> "ပစ္စည်း မရောက်သေး"
            notification.note?.contains("အတည်ပြုပါ") == true -> "ပစ္စည်းရောက်ကြောင်း အတည်ပြုရန်"
            notification.status.equals("PROOF_SUBMITTED", ignoreCase = true) -> "ငွေလွှဲအချက်အလက် ရောက်ပြီး"
            notification.status.equals("CHECKING", ignoreCase = true) -> "စစ်ဆေးနေသည်"
            else -> notification.orderNo ?: "Customer Order"
        }
        val body = notification.note.orEmpty().ifBlank {
            if (notification.status.equals("EXPIRED", ignoreCase = true))
                "ငွေလွှဲချိန်ကုန်ပါပြီ။ ငွေလွှဲပြီးသားဆို အထောက်အထားတင်ပါ။ မလွှဲရသေးရင် ပြန်မှာယူပါ။"
            else if (cancelled) "ဆိုင်မှ အော်ဒါကို ပယ်ဖျက်လိုက်ပါသည်။ စရံလွှဲပြီးပါက စရံငွေ ဆုံးရှုံးနိုင်သည်။"
            else "အော်ဒါအခြေအနေ အသစ်ရှိပါသည်။"
        }
        val intent = Intent(context, CustomerMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            notification.orderId?.let { putExtra(EXTRA_ORDER_ID, it) }
            putExtra(EXTRA_OPEN_ORDERS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            notification.orderId ?: notification.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // NotificationManager ids must be unique; order alerts use negative DTO ids → map to positive.
        val notifyId = when {
            notification.orderId != null -> 1_000_000 + notification.orderId
            notification.id < 0 -> 1_000_000 - notification.id
            else -> notification.id
        }
        manager.notify(
            notifyId,
            NotificationCompat.Builder(context, "customer_orders")
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }
}
