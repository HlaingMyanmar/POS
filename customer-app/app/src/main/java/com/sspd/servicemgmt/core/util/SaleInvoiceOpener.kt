package com.sspd.servicemgmt.core.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sspd.servicemgmt.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import java.io.File

object SaleInvoiceOpener {

    /**
     * Downloads the official POS sale voucher PDF for an order and returns the local File.
     */
    suspend fun fetchOrderPdfFile(
        context: Context,
        orderId: Int,
        saleCode: String? = null
    ): Result<File> = downloadPdfFile(context, saleCode ?: "order-$orderId") {
        ApiClient.service.orderInvoicePdf(
            ApiClient.bearer(PreferenceManager(context).authToken),
            orderId
        )
    }

    /**
     * Downloads the official POS sale voucher PDF for a purchase/sale and returns the local File.
     */
    suspend fun fetchPurchasePdfFile(
        context: Context,
        saleId: Int,
        saleCode: String? = null
    ): Result<File> = downloadPdfFile(context, saleCode ?: "sale-$saleId") {
        ApiClient.service.purchaseInvoicePdf(
            ApiClient.bearer(PreferenceManager(context).authToken),
            saleId
        )
    }

    /** Money-received confirmation PDF (after shop APPROVE; before or after sale fulfill). */
    suspend fun fetchPaymentReceiptPdfFile(
        context: Context,
        orderId: Int,
        orderNo: String? = null
    ): Result<File> = downloadPdfFile(
        context,
        "receipt-${orderNo ?: orderId}",
        emptyMessage = "ငွေလက်ခံပြေစာ ဗလာဖြစ်နေသည်",
        fallbackMessage = "ငွေလက်ခံပြေစာ မရနိုင်သေးပါ"
    ) {
        ApiClient.service.paymentReceiptPdf(
            ApiClient.bearer(PreferenceManager(context).authToken),
            orderId
        )
    }

    private suspend fun downloadPdfFile(
        context: Context,
        fileLabel: String,
        emptyMessage: String = "Invoice ဗလာဖြစ်နေသည်",
        fallbackMessage: String = "Sale invoice မရနိုင်သေးပါ",
        fetch: suspend () -> Response<ResponseBody>
    ): Result<File> {
        val token = PreferenceManager(context).authToken
        if (token.isBlank()) return Result.failure(IllegalStateException("အကောင့် ပြန်ဝင်ပါ"))

        return withContext(Dispatchers.IO) {
            try {
                cleanOldCache(context)

                val response = fetch()
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    val msg = response.errorBody()?.string().orEmpty()
                    val errorMsg = runCatching {
                        JSONObject(msg).optString("message")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: "$fallbackMessage (${response.code()})"
                    return@withContext Result.failure(IllegalStateException(errorMsg))
                }
                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException(emptyMessage))
                }

                val safeName = fileLabel.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val file = File(context.cacheDir, "invoice-$safeName.pdf")
                file.writeBytes(bytes)
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun cleanOldCache(context: Context) {
        try {
            val cacheDir = context.cacheDir ?: return
            val invoiceFiles = cacheDir.listFiles { _, name ->
                name.startsWith("invoice-") || name.startsWith("receipt-")
            } ?: return
            val sortedFiles = invoiceFiles.sortedBy { it.lastModified() }
            val currentTime = System.currentTimeMillis()
            val maxAgeMillis = 7L * 24 * 60 * 60 * 1000L // 7 days
            for (file in sortedFiles) {
                val age = currentTime - file.lastModified()
                if (age > maxAgeMillis || sortedFiles.size > 15) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Opens the PDF file using an external app intent.
     */
    fun openFileInExternalApp(
        context: Context,
        file: File,
        share: Boolean = false,
        title: String? = null
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val chooserTitle = title ?: if (share) "Send Invoice" else "Sale Invoice"
            val intent = if (share) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
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
            context.startActivity(
                Intent.createChooser(intent, chooserTitle)
            )
        } catch (_: Exception) {
            Toast.makeText(context, "PDF ပို့/ဖတ်မည့် app မရှိပါ", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Downloads the official POS sale voucher PDF from the server and opens it via external app.
     * @return error message, or null on success
     */
    suspend fun openForOrder(context: Context, orderId: Int, saleCode: String? = null): String? {
        val result = fetchOrderPdfFile(context, orderId, saleCode)
        return result.fold(
            onSuccess = { file ->
                withContext(Dispatchers.Main) {
                    openFileInExternalApp(context, file, share = false)
                }
                null
            },
            onFailure = { it.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ" }
        )
    }

    suspend fun shareForOrder(context: Context, orderId: Int, saleCode: String? = null): String? {
        val result = fetchOrderPdfFile(context, orderId, saleCode)
        return result.fold(
            onSuccess = { file ->
                withContext(Dispatchers.Main) {
                    openFileInExternalApp(context, file, share = true)
                }
                null
            },
            onFailure = { it.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ" }
        )
    }

    suspend fun openForPurchase(context: Context, saleId: Int, saleCode: String? = null): String? {
        val result = fetchPurchasePdfFile(context, saleId, saleCode)
        return result.fold(
            onSuccess = { file ->
                withContext(Dispatchers.Main) {
                    openFileInExternalApp(context, file, share = false)
                }
                null
            },
            onFailure = { it.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ" }
        )
    }

    suspend fun shareForPurchase(context: Context, saleId: Int, saleCode: String? = null): String? {
        val result = fetchPurchasePdfFile(context, saleId, saleCode)
        return result.fold(
            onSuccess = { file ->
                withContext(Dispatchers.Main) {
                    openFileInExternalApp(context, file, share = true)
                }
                null
            },
            onFailure = { it.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ" }
        )
    }

    suspend fun openPaymentReceipt(context: Context, orderId: Int, orderNo: String? = null): String? {
        val result = fetchPaymentReceiptPdfFile(context, orderId, orderNo)
        return result.fold(
            onSuccess = { file ->
                withContext(Dispatchers.Main) {
                    openFileInExternalApp(context, file, share = false, title = "ငွေလက်ခံပြေစာ")
                }
                null
            },
            onFailure = { it.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ" }
        )
    }

    suspend fun sharePaymentReceipt(context: Context, orderId: Int, orderNo: String? = null): String? {
        val result = fetchPaymentReceiptPdfFile(context, orderId, orderNo)
        return result.fold(
            onSuccess = { file ->
                withContext(Dispatchers.Main) {
                    openFileInExternalApp(context, file, share = true, title = "ငွေလက်ခံပြေစာ ပို့မည်")
                }
                null
            },
            onFailure = { it.message ?: "ချိတ်ဆက်မှု ပြန်စစ်ပါ" }
        )
    }
}
