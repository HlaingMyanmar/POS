package com.sspd.servicemgmt.feature.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sspd.servicemgmt.core.network.VideoDTO

private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val YOUTUBE_ID_IN_URL = Regex(
    "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/)|[?&]v=)([A-Za-z0-9_-]{11})"
)

internal fun extractYoutubeId(video: VideoDTO): String? =
    parseYoutubeId(video.providerVideoId)
        ?: parseYoutubeId(video.sourceUrl)
        ?: parseYoutubeId(video.youtubeUrl)

internal fun parseYoutubeId(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val value = raw.trim()
    if (YOUTUBE_ID.matches(value)) return value
    return YOUTUBE_ID_IN_URL.find(value)?.groupValues?.getOrNull(1)
}

internal fun isValidYoutubeId(id: String?): Boolean =
    !id.isNullOrBlank() && YOUTUBE_ID.matches(id.trim())

internal fun youtubeWatchUri(youtubeId: String): Uri =
    Uri.parse("https://www.youtube.com/watch?v=$youtubeId")

internal fun openInYoutubeApp(context: Context, youtubeId: String): Boolean {
    val watch = youtubeWatchUri(youtubeId)
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeId")).apply {
        setPackage("com.google.android.youtube")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val packagedWeb = Intent(Intent.ACTION_VIEW, watch).apply {
        setPackage("com.google.android.youtube")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val browser = Intent(Intent.ACTION_VIEW, watch).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        when {
            appIntent.resolveActivity(context.packageManager) != null -> context.startActivity(appIntent)
            packagedWeb.resolveActivity(context.packageManager) != null -> context.startActivity(packagedWeb)
            else -> context.startActivity(browser)
        }
    }.isSuccess || runCatching { context.startActivity(browser) }.isSuccess
}

internal fun videoThumbUrl(video: VideoDTO): String? {
    val existing = video.thumbnailUrl?.trim()
    if (existing != null && isAllowedThumbUrl(existing)) return existing
    val id = extractYoutubeId(video)
    return if (id != null) "https://img.youtube.com/vi/$id/hqdefault.jpg" else null
}

internal fun isAllowedThumbUrl(raw: String): Boolean {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase() != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return host == "img.youtube.com" || host == "i.ytimg.com" || host.endsWith(".ytimg.com")
}
