package com.sspd.servicemgmt.feature.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.AppVersionDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class VersionCheckViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun check() {
        if (_state.value.checked) return
        doCheck()
    }

    fun checkForce() {
        _state.update { it.copy(checked = false, update = null, downloadProgress = null, downloadError = null, apkFile = null, installStarted = false) }
        doCheck()
    }

    private fun doCheck() {
        viewModelScope.launch {
            try {
                val res = ApiClient.service.getTechnicianAppVersion()
                val dto = res.body()?.data ?: return@launch
                val update = dto.copy(
                    downloadUrl = ApiClient.resolveApkDownloadUrl(dto.downloadUrl, "technician.apk")
                )
                if (update.versionCode > BuildConfig.VERSION_CODE) {
                    _state.update { it.copy(update = update, checked = true) }
                } else {
                    _state.update { it.copy(checked = true) }
                }
            } catch (_: Exception) {
                _state.update { it.copy(checked = true) }
            }
        }
    }

    fun dismiss() {
        if (_state.value.update?.forceUpdate == true) return
        _state.update { it.copy(update = null, downloadProgress = null, downloadError = null, apkFile = null, installStarted = false) }
    }

    fun downloadAndInstall() {
        val url = _state.value.update?.downloadUrl ?: return
        if (url.isBlank()) return
        if (_state.value.downloadProgress != null) return   // already downloading
        _state.update { it.copy(downloadProgress = 0f, downloadError = null, apkFile = null, installStarted = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = buildDownloadClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val body = response.body ?: throw Exception("Empty response")
                val contentLength = body.contentLength()
                val outFile = File(getApplication<Application>().cacheDir, "technician-update.apk")
                outFile.outputStream().use { out ->
                    var downloaded = 0L
                    body.byteStream().use { inp ->
                        val buf = ByteArray(8 * 1024)
                        var read: Int
                        while (inp.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            if (contentLength > 0) {
                                _state.update { it.copy(downloadProgress = downloaded.toFloat() / contentLength) }
                            }
                        }
                    }
                }
                _state.update { it.copy(downloadProgress = 1f, apkFile = outFile.absolutePath) }
            } catch (e: Exception) {
                _state.update { it.copy(downloadProgress = null, downloadError = e.message ?: "Download မအောင်မြင်ပါ") }
            }
        }
    }

    fun triggerInstall(context: Context) {
        val current = _state.value
        if (current.installStarted) return

        val path = current.apkFile ?: return
        val file = File(path)
        if (!file.exists()) return

        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.update {
                it.copy(downloadError = "Install permission လိုအပ်ပါသည်။ Settings တွင် Allow from this source ဖွင့်ပြီး Install APK ကိုပြန်နှိပ်ပါ။")
            }
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            _state.update { it.copy(installStarted = true, downloadError = null) }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.update { it.copy(installStarted = false, downloadError = e.message ?: "APK install ဖွင့်မရပါ") }
        }
    }

    private fun buildDownloadClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    data class State(
        val update:           AppVersionDTO? = null,
        val checked:          Boolean        = false,
        val downloadProgress: Float?         = null,   // null=idle, 0..1=downloading, 1=done
        val apkFile:          String?        = null,   // local path after download
        val downloadError:    String?        = null,
        val installStarted:   Boolean        = false
    )
}
