package com.sspd.servicemgmt.core.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sspd.servicemgmt.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SaleInvoiceOpener {
    /**
     * Downloads the official POS sale voucher PDF from the server and opens it.
     * @return error message, or null on success
     */
    suspend fun openForOrder(context: Context, orderId: Int, saleCode: String? = null): String? =
        launchPdf(context, saleCode ?: "order-$orderId", share = false) {
            ApiClient.service.orderInvoicePdf(ApiClient.bearer(PreferenceManager(context).authToken), orderId)
        }

    suspend fun shareForOrder(context: Context, orderId: Int, saleCode: String? = null): String? =
        launchPdf(context, saleCode ?: "order-$orderId", share = true) {
            ApiClient.service.orderInvoicePdf(ApiClient.bearer(PreferenceManager(context).authToken), orderId)
        }

    suspend fun openForPurchase(context: Context, saleId: Int, saleCode: String? = null): String? =
        launchPdf(context, saleCode ?: "sale-$saleId", share = false) {
            ApiClient.service.purchaseInvoicePdf(ApiClient.bearer(PreferenceManager(context).authToken), saleId)
        }

    suspend fun shareForPurchase(context: Context, saleId: Int, saleCode: String? = null): String? =
        launchPdf(context, saleCode ?: "sale-$saleId", share = true) {
            ApiClient.service.purchaseInvoicePdf(ApiClient.bearer(PreferenceManager(context).authToken), saleId)
        }

    private suspend fun launchPdf(
        context: Context,
        fileLabel: String,
        share: Boolean,
        fetch: suspend () -> retrofit2.Response<okhttp3.ResponseBody>
    ): String? {
        val token = PreferenceManager(context).authToken
        if (token.isBlank()) return "အကောင့် ပြန်ဝင်ပါ"

        return withContext(Dispatchers.IO) {
            try {
                val response = fetch()
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    val msg = response.errorBody()?.string().orEmpty()
                    return@withContext runCatching {
                        org.json.JSONObject(msg).optString("message")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: "Sale invoice မရနိုင်သေးပါ (${response.code()})"
                }
                val bytes = body.bytes()
                if (bytes.isEmpty()) return@withContext "Invoice ဗလာဖြစ်နေသည်"

                val safeName = fileLabel.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val file = File(context.cacheDir, "invoice-$safeName.pdf")
                file.writeBytes(bytes)

                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file
                )
                val intent = if (share) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Sale Invoice $fileLabel")
                        clipData = ClipData.newRawUri("invoice", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                withContext(Dispatchers.Main) {
                    try {
                        context.startActivity(
                            Intent.createChooser(intent, if (share) "Send Invoice" else "Sale Invoice")
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "PDF ပို့/ဖတ်မည့် app မရှိပါ", Toast.LENGTH_LONG).show()
                    }
                }
                null
            } catch (e: Exception) {
                e.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ"
            }
        }
    }
}
