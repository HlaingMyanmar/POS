package com.sspd.servicemgmt.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageCodec {
    /** Upload-ready JPEG data URI. Default 1600px matches server product/booking storage. */
    fun bitmapToDataUri(bitmap: Bitmap, maxDim: Int = 1600, quality: Int = 88): String {
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeDataUri(uri: String): Bitmap? = runCatching {
        val raw = uri.substringAfter("base64,", uri)
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
