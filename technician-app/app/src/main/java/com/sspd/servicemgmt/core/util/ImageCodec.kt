package com.sspd.servicemgmt.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

object ImageCodec {
    /** Upload-ready JPEG data URI. Default 1600px matches server product/booking storage. */
    fun bitmapToDataUri(bitmap: Bitmap, maxDim: Int = 1600, quality: Int = 88): String {
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            bitmap.scale((bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt())
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeDataUri(uri: String, maxTargetDim: Int = 512): Bitmap? = runCatching {
        val raw = uri.substringAfter("base64,", uri)
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val (w, h) = options.outWidth to options.outHeight
        if (w <= 0 || h <= 0) return null

        var inSampleSize = 1
        if (h > maxTargetDim || w > maxTargetDim) {
            val halfH = h / 2
            val halfW = w / 2
            while (halfH / inSampleSize >= maxTargetDim && halfW / inSampleSize >= maxTargetDim) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }.getOrNull()
}
